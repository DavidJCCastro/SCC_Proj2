package tukano.api.rest;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(RestAuth.PATH)
public interface RestAuth {
    static final String PATH = "login";
	static final String USER = "username";
	static final String PWD = "password";

    @POST
    void login( @FormParam(USER) String userId, @FormParam(PWD) String password );

    @GET
	@Produces(MediaType.TEXT_HTML)
	String login();
}
