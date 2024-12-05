package tukano.impl.rest;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import jakarta.ws.rs.core.Application;
import main.java.utils.Props;
import tukano.impl.Token;
import tukano.impl.auth.RequestCookiesCleanupFilter;
import tukano.impl.auth.RequestCookiesFilter;
import utils.Args;

public class TukanoRestServer extends Application {
    final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

    static final String INETADDR_ANY = "0.0.0.0";
    static String SERVER_BASE_URI = "http://" + INETADDR_ANY + ":%s/rest";  // <-- Changed here

    public static final int PORT = 8080;

    public static String serverURI;
    private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

    static final String PROPS_FILE = "azurekeys-northeurope.props"; // Change to kubernetes secrets

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }

    public TukanoRestServer() {
        serverURI = String.format(SERVER_BASE_URI, PORT);
        resources.add(RestBlobsResource.class);
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);
        resources.add(RestAuthResource.class);
        resources.add(RequestCookiesFilter.class);
        resources.add(RequestCookiesCleanupFilter.class);

        Props.load(PROPS_FILE);                          // Change to kubernetes secrets
        Token.setSecret(Props.get("secret", "lol123")); // Change to kubernetes secrets
    }

    protected void start() throws Exception {
        ResourceConfig config = new ResourceConfig();

        config.register(RestBlobsResource.class);
        config.register(RestUsersResource.class);
        config.register(RestShortsResource.class);
        config.register(RestAuthResource.class);
        config.register(RequestCookiesFilter.class);

        JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config); 
        Log.info(String.format("Tukano Server ready @ %s\n", serverURI));
    }

    @Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}

//     public static void main(String[] args) throws Exception {
//         Args.use(args);

//         Token.setSecret(Args.valueOf("-secret", ""));
// //        Props.load( Args.valueOf("-props", "").split(","));

//         new TukanoRestServer().start();
//     }

    public static void main(String[] args) throws Exception {
		return;
	}
}
