package tukano.api;

public interface Auth {

    String NAME = "auth";

    /**
     * Logs a user in
     * @param userId - Id of the user logging in
     * @param password - Password for that user
     * @return redirect response
     */
    Result<Void> login(String userId, String password );

    /**
     * Returns the login page
     * @return the login page
     */
    Result<String> login();
}
