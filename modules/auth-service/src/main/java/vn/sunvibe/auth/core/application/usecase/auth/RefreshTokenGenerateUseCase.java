package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

public interface RefreshTokenGenerateUseCase {

    /**
     * Starts a new sign-in session for the user and returns its first refresh
     * token (the secret; only its hash is stored).
     */
    String generateRefreshToken(UserId userId, ClientInfo clientInfo);

}
