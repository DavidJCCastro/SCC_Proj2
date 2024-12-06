package tukano.impl;

import static java.lang.String.format;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import redis.clients.jedis.Jedis;
import tukano.api.Result;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.ok;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.JSON;
import utils.RedisCache;

public class JavaUsers implements Users {

	private static final String USERS_PREFIX = "users:";

	private static final int USER_TTL = 5; // 3 seconds

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		var res = DB.insertOne(user);

		if (!res.isOK())
			return Result.error(res.error());

		return Result.ok(user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		return validatedUserOrError(getUser(userId), pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(getUser(userId), pwd), user -> {
			var updatedUser = user.updateFrom(other);

			cacheUser(updatedUser);

			return DB.updateOne(updatedUser);
		});
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(getUser(userId), pwd), user -> {
			// Delete user from Redis
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.del(USERS_PREFIX + userId);
			}

			// Delete user shorts and blobs asynchronously with a new Hibernate session
			Executors.defaultThreadFactory().newThread(() -> {
				try {
					DB.transaction(hibernate -> {
						JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
						JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
					});
				} catch (Exception e) {
					Log.severe("Error in asynchronous deletion: " + e.getMessage());
				}
			}).start();

			// Delete the user
			return DB.deleteOne(user);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM app_user WHERE UPPER(userId) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	private Result<User> validatedUserOrError(Result<User> res, String pwd) {
		if (res == null || pwd == null) {
			return error(BAD_REQUEST);
		}

		if (res.isOK()) {
			return pwd.equals(res.value().getPwd()) ? res : error(FORBIDDEN);
		} else {
			return res;
		}
	}

	private boolean badUserInfo(User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
	}

	private Result<User> getUser(String userId) {
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			var key = USERS_PREFIX + userId;
			var value = jedis.get(key);

			if (value != null) {
				jedis.expire(key, USER_TTL);
				Log.info(() -> "User found in Redis cache: " + userId);
				return Result.ok(JSON.decode(value, User.class));
			}
		} catch (Exception e) {
			Log.severe(() -> "Error accessing Redis: " + e.getMessage());
		}

		var dbResult = DB.getOne(userId, User.class);

		if (dbResult.isOK()) {
			cacheUser(dbResult.value());
			Log.info(() -> "User found in DB and added to cache: " + userId);
		} else {
			Log.warning(() -> "User not found in DB: " + userId);
		}

		return dbResult;
	}

	private void cacheUser(User user) {
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			var key = USERS_PREFIX + user.getUserId();
			var value = JSON.encode(user);
			jedis.setex(key, USER_TTL, value);
			Log.info(() -> "User cached: " + user.getUserId());
		} catch (Exception e) {
			Log.severe(() -> "Error caching user: " + e.getMessage());
		}
	}
}
