package tukano.impl.rest;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import tukano.impl.Token;
import utils.Args;
import utils.Props;

public class TukanoRestServer {
    final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

    static final String INETADDR_ANY = "0.0.0.0";
    static String SERVER_BASE_URI = "http://" + INETADDR_ANY + ":%s/rest";  // <-- Changed here

    public static final int PORT = 8080;

    static final String PROPS_FILE = "tukano.props";

    public static String serverURI;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }

    protected TukanoRestServer() {
        serverURI = String.format(SERVER_BASE_URI, PORT);

    }
    
    protected void start() throws Exception {
        ResourceConfig config = new ResourceConfig();
        
        config.register(RestBlobsResource.class);
        config.register(RestUsersResource.class);
        config.register(RestShortsResource.class);

        Props.load(PROPS_FILE);
        Token.setSecret(Props.get("secret", "lol123"));
        Log.log(Level.INFO, "secret: {0}", Props.get("secret", "lol123"));
        
        JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config); 
        Log.info(String.format("Tukano Server ready @ %s\n", serverURI));
    }

    public static void main(String[] args) throws Exception {
        Args.use(args);
        
        // Token.setSecret(Args.valueOf("-secret", ""));
        //        Props.load( Args.valueOf("-props", "").split(","));

        
        new TukanoRestServer().start();
    }
}
