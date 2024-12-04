package tukano.impl;

import static java.lang.String.format;

import java.net.URI;
import java.util.UUID;
import java.util.logging.Logger;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import main.java.tukano.api.Users;
import main.java.tukano.impl.data.Session;
import main.java.tukano.impl.rest.utils.RequestCookies;
import redis.clients.jedis.Jedis;
import tukano.api.Auth;
import tukano.api.Result;
import utils.JSON;
import utils.RedisCache;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.error;



public class JavaAuth implements Auth{

    static final String COOKIE_KEY = "scc:session";
    private static final int MAX_COOKIE_AGE = 3600;
    private static final String REDIRECT_TO_AFTER_LOGIN = "*******CHANGE***********";
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
    public Result<Void> login(String userId, String password ) {
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

        return Result.ok();
        /**
         * A logica de manter e cookie e guardada no cliente. So tenho de manter o mapping de uid/userId na cache com o record.
         * A cookie e mantida durante o pedido na thread local
         */

        //FakeRedisLayer.getInstance().putSession( new Session( uid, user));	
        
        // return Response.seeOther(URI.create( REDIRECT_TO_AFTER_LOGIN ))
        //         .cookie(cookie) 
        //         .build();
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

    static public String validateSession(String userId) throws NotAuthorizedException {
		var cookies = RequestCookies.get();
		return validateSession( cookies.get(COOKIE_KEY ), userId );
	}
	
	static public String validateSession(Cookie cookie, String userId) throws NotAuthorizedException {

		if (cookie == null )
			throw new NotAuthorizedException("No session initialized");
		
		var cookieOwner = "";
        try (Jedis jedis = RedisCache.getCachePool().getResource()) {
            cookieOwner = jedis.get(cookie.getValue());
        }
			
		if (cookieOwner == null || cookieOwner.length() == 0) 
			throw new NotAuthorizedException("No valid session initialized");
		
		if (!cookieOwner.equals(userId))
			throw new NotAuthorizedException("Invalid user : " + cookieOwner);
		
		return cookieOwner;
	}
}
