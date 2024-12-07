package tukano.impl.auth;

import java.util.Map;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Cookie;

public class RequestCookies {

    private static Logger Log = Logger.getLogger(RequestCookies.class.getName());

    private static final ThreadLocal<Map<String, Cookie>> requestCookiesThreadLocal = new ThreadLocal<>();

    public static void set(Map<String, Cookie> cookies) {
    	requestCookiesThreadLocal.set(cookies);
    }

    public static  Map<String, Cookie> get() {
        Map<String, Cookie> cookies = requestCookiesThreadLocal.get();
        if (cookies == null) {
            throw new IllegalStateException("RequestCookies not set");
        }
        Log.info(() -> "RequestCookies.get() - Cookies retrieved: " + cookies + "\n");
        return requestCookiesThreadLocal.get();
    }

    public static void clear() {
    	requestCookiesThreadLocal.remove();
    }
}
