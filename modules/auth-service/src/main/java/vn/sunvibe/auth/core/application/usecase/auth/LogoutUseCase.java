package vn.sunvibe.auth.core.application.usecase.auth;

/**
 * Ends sign-in sessions on the user's request.
 */
public interface LogoutUseCase {

    /**
     * Ends the sign-in session the refresh token belongs to. Idempotent: a
     * missing, unknown or already revoked token is silently ignored.
     */
    void execute(String refreshToken);

    /** Ends every sign-in session of the authenticated user, on all devices. */
    void executeAllDevices();

}
