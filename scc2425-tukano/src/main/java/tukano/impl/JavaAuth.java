package tukano.impl;

import static java.lang.String.format;

import java.net.URI;
import java.util.UUID;
import java.util.logging.Logger;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import main.java.tukano.impl.data.Session;
import redis.clients.jedis.Jedis;
import utils.JSON;
import utils.RedisCache;

public class JavaAuth {

    private static final String COOKIE_KEY = "scc:session";
    private static final int MAX_COOKIE_AGE = 3600;
    private static final String REDIRECT_TO_AFTER_LOGIN = "*******CHANGE***********";

    private static Logger Log = Logger.getLogger(JavaAuth.class.getName());

    public JavaAuth() {}

    public Response login(String userId, String password ) {
		Log.info(() -> format("login : userId = %s, pwd = %s\n", userId, password));
		boolean pwdOk = JavaUsers.getInstance().getUser(userId, password).isOK();
		if (pwdOk) {
			String uid = UUID.randomUUID().toString();
			var cookie = new NewCookie.Builder(COOKIE_KEY)
					.value(uid).path("/")
					.comment("sessionid")
					.maxAge(MAX_COOKIE_AGE)
					.secure(false) //ideally it should be true to only work for https requests
					.httpOnly(true)
					.build();
			
            if(RedisCache.isEnabled()){
                try (Jedis jedis = RedisCache.getCachePool().getResource()) {
                    jedis.setex(cookie.getValue(), MAX_COOKIE_AGE, JSON.encode(cookie));
                }
            }
            /**
             * A logica de manter e cookie e guardada no cliente. So tenho de manter o mapping de uid/userId na cache com o record.
             * A cookie e mantida durante o pedido na thread local
             */

			FakeRedisLayer.getInstance().putSession( new Session( uid, user));	
			
            return Response.seeOther(URI.create( REDIRECT_TO_AFTER_LOGIN ))
                    .cookie(cookie) 
                    .build();
		} else
			throw new NotAuthorizedException("Incorrect login");
	}
}
