package tukano.impl;

import static java.lang.String.format;
import java.util.UUID;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import redis.clients.jedis.Jedis;
import tukano.api.Auth;
import tukano.api.Result;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;
import static tukano.api.Result.error;
import tukano.impl.auth.RequestCookies;
import utils.RedisCache;



public class JavaAuth implements Auth {

    static final String COOKIE_KEY = "scc:session";
    private static final int MAX_COOKIE_AGE = 3600;
    //private static final String REDIRECT_TO_AFTER_LOGIN = "*******CHANGE***********";
    static final String LOGIN_PAGE = "login.html";

    private static Auth instance;

    private static Logger Log = Logger.getLogger(JavaAuth.class.getName());

    synchronized public static Auth getInstance() {
		if( instance == null )
			instance = new JavaAuth();
		return instance;
	}

    private JavaAuth() {}

    @Override
    public Result<Response> login(String userId, String password ) {
		Log.info(() -> format("login : userId = %s, pwd = %s\n", userId, password));
		var pwdOk = JavaUsers.getInstance().getUser(userId, password);
		if (!pwdOk.isOK())
            return error(pwdOk.error());

        String uid = UUID.randomUUID().toString();
        var cookie = new NewCookie.Builder(COOKIE_KEY)
                .value(uid).path("/")
                .comment("sessionid")
                .maxAge(MAX_COOKIE_AGE)
                .secure(false) //ideally it should be true to only work for https requests
                .httpOnly(true)
                .build();
        
        try (Jedis jedis = RedisCache.getCachePool().getResource()) {
            jedis.setex(cookie.getValue(), MAX_COOKIE_AGE, userId);
        }
        /**
         * A logica de manter e cookie e guardada no cliente. So tenho de manter o mapping de uid/userId na cache com o record.
         * A cookie e mantida durante o pedido na thread local
         */

        //FakeRedisLayer.getInstance().putSession( new Session( uid, user));	
        
        return Result.ok(Response.ok("Login successful")
                .cookie(cookie) 
                .build());
	}

    @Override
	public Result<String> login() {
		try {
			var in = getClass().getClassLoader().getResourceAsStream(LOGIN_PAGE);
			return  Result.ok(new String( in.readAllBytes()));			
		} catch( Exception x ) {
			return Result.error( INTERNAL_ERROR );
		}
	}


    // These 2 functions to verify a cookie is valid (a user is logged in -- any user)

    static public Result<String> validateSession() {
		var cookies = RequestCookies.get();
		return validateSession( cookies.get(COOKIE_KEY));
	}
	
	static public Result<String> validateSession(Cookie cookie) {

		if (cookie == null )
            return Result.error(NOT_FOUND);
		
		var cookieOwner = "";
        try (Jedis jedis = RedisCache.getCachePool().getResource()) {
            cookieOwner = jedis.get(cookie.getValue());
        }
			
		if (cookieOwner == null || cookieOwner.length() == 0)
            return Result.error(NOT_FOUND);
		
		return Result.ok(cookieOwner);
	}

    // These 2 functions to verify the cookie belongs to the user (the specific user is logged in)

    static public Result<String> validateSession(String userId) {
		var cookies = RequestCookies.get();
		return validateSession( cookies.get(COOKIE_KEY), userId );
	}

    static public Result<String> validateSession(Cookie cookie, String userId) {

        var sessionResult = validateSession(cookie);

        if(!sessionResult.isOK())
            return sessionResult;

        var cookieOwner = sessionResult.value();

        if (!cookieOwner.equals(userId))
            return Result.error(FORBIDDEN);

        return Result.ok(cookieOwner);
    }
}
