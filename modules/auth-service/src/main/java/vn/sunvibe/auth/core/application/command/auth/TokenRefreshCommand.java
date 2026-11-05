package vn.sunvibe.auth.core.application.command.auth;

/**
 * Exchange a refresh token for a new access/refresh token pair.
 *
 * @param refreshToken the secret from the refresh-token cookie; may be null or blank
 *                     (rejected by the use case with a 401, not by validation)
 * @param clientInfo   the device asking for the refresh
 */
public record TokenRefreshCommand(String refreshToken, ClientInfo clientInfo) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "TokenRefreshCommand[hidden]";
    }
}
