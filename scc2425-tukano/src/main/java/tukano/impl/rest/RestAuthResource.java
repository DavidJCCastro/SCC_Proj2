package tukano.impl.rest;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import tukano.api.Auth;
import tukano.api.rest.RestAuth;
import tukano.impl.JavaAuth;

@Path(RestAuthResource.PATH)
public class RestAuthResource extends RestResource implements RestAuth {
	static final Auth impl = JavaAuth.getInstance();

	@Override
	public Response login(String userId, String password) {
		return super.resultOrThrow(impl.login(userId, password));
	}

	@Override
	public String login() {
		return resultOrThrow(impl.login());
	}
}
