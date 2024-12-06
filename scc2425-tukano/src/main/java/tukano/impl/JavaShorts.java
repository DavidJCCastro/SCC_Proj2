package tukano.impl;

import static java.lang.String.format;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import redis.clients.jedis.Jedis;
import tukano.api.Blobs;
import tukano.api.Result;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import static utils.DB.getOne;
import utils.JSON;
import utils.RedisCache;

public class JavaShorts implements Shorts {

	private static final String SHORTS_PREFIX = "shorts:";

	private static final int SHORT_TTL = 5; // 5 seconds

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {
			
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId); 
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(DB.insertOne(shrt), s -> {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var key = SHORTS_PREFIX + shortId;
					var value = JSON.encode(shrt);
					jedis.setex(key, SHORT_TTL, value);
				}
				
				return s.copyWithLikes_And_Token(0);});
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		var query = format("SELECT COUNT(*) FROM Likes WHERE shortId = '%s'", shortId);
		var likes = DB.sql(query, Long.class);

		Result<Short> res = null;

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			var key = SHORTS_PREFIX + shortId;
			var value = jedis.get(key);
			if( value != null ) {
				jedis.expire(key, SHORT_TTL); // Reset TTL, may change later
				var shrt = JSON.decode(value, Short.class);
				res = Result.ok(shrt);
			}
		}
		
		if( res == null )
			res = getOne(shortId, Short.class);
		
		return errorOrValue( res, shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
	}


	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));
		
		return errorOrResult( getShort(shortId), shrt -> {
			
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.del(SHORTS_PREFIX + shortId);
				}
				
				return DB.transaction( hibernate -> {

					hibernate.remove( shrt);
					
					var query = format("DELETE FROM Likes WHERE shortId = '%s'", shortId);
					hibernate.createNativeQuery( query, Likes.class).executeUpdate();
					
					JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
				});
			});	
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT shortId FROM Short WHERE ownerId = '%s'", userId);
		return errorOrValue( okUser(userId), DB.sql( query, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
	
		
		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));	
		});			
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT follower FROM Following WHERE followee = '%s'", userId);
		return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));	
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT userId FROM Likes WHERE shortId = '%s'", shortId);					
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT shortId 
				FROM Short WHERE ownerId
				IN (SELECT followee FROM Following WHERE follower = '%s')
				ORDER BY timestamp DESC""";

		return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId), String.class));		
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}
	
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
		
		return DB.transaction( (hibernate) -> {
						
			//delete shorts
			var query1 = format("DELETE FROM Short WHERE ownerId = '%s'", userId);		
			hibernate.createQuery(query1, Short.class).executeUpdate();
			
			//delete follows
			var query2 = format("DELETE FROM Following WHERE follower = '%s' OR followee = '%s'", userId, userId);		
			hibernate.createQuery(query2, Following.class).executeUpdate();
			
			//delete likes
			var query3 = format("DELETE FROM Like WHERE ownerId = '%s' OR userId = '%s'", userId, userId);		
			hibernate.createQuery(query3, Likes.class).executeUpdate();
			
		});
	}
	
}