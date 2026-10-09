package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.command.auth.TokenRefreshCommand;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;

public interface TokenRefreshUseCase {

    /**
     * Rotates the refresh token: the presented token is used up and a new
     * access token plus a new refresh token of the same session are returned.
     *
     * @throws InvalidRefreshTokenException when the token cannot be exchanged;
     *                                      the client must sign in again
     */
    AuthResult execute(TokenRefreshCommand command);

}
