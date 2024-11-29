package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import main.java.utils.JSON;
import main.java.utils.RedisCache;
import redis.clients.jedis.Jedis;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;

public class JavaUsers implements Users {

	private static final String USERS_PREFIX = "users:";

	private static final int USER_TTL = 5; // 3 seconds
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	
	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}
	
	private JavaUsers() {}
	
	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) )
				return error(BAD_REQUEST);

		var res = DB.insertOne(user);

		if( !res.isOK())
			return Result.error(res.error());

		return Result.ok(user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		return validatedUserOrError(getUser(userId), pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(getUser(userId), pwd), user -> {
			var updatedUser = user.updateFrom(other);

			cacheUser(updatedUser);

			return DB.updateOne( updatedUser );
		});
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(getUser(userId), pwd), user -> {

			if(RedisCache.isEnabled()) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.del(USERS_PREFIX + userId);
				}		
			}
			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();
			
			return DB.deleteOne( user);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM app_user WHERE UPPER(userId) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}

	private Result<User> getUser (String userId) {
		if(RedisCache.isEnabled()) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = USERS_PREFIX + userId;
				var value = jedis.get(key);
				if (value != null) {
					jedis.expire(key, USER_TTL);
					return Result.ok(JSON.decode(value, User.class));
				}
			}
		}
		return DB.getOne(userId, User.class);

	}

	private void cacheUser(User user) {
		if(RedisCache.isEnabled()) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = USERS_PREFIX + user.getUserId();
				var value = JSON.encode(user);
				jedis.setex(key, USER_TTL, value);
			}
		}
	}
}
