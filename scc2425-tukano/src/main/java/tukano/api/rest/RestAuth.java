package tukano.api.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(RestAuth.PATH)
public interface RestAuth {
    String PATH = "/login";
	String USER_ID = "userId";
	String PWD = "pwd";

    @POST
    @Path("/{" + USER_ID + "}")
    Response login( @PathParam(USER_ID) String userId, @QueryParam(PWD) String password );

    @GET
	@Produces(MediaType.TEXT_HTML)
	String login();
}
