package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.shared.RefreshTokenPolicy;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Starts a sign-in session: a new token family whose absolute lifetime is
 * fixed now. Runs in the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenGenerateService implements RefreshTokenGenerateUseCase {

    private final RefreshTokenDomainRepository refreshTokenDomainRepository;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final RefreshTokenPolicy refreshTokenPolicy;

    @Override
    public String generateRefreshToken(UserId userId, ClientInfo clientInfo) {
        String secret = RefreshTokenSecrets.generate();

        RefreshToken refreshToken = RefreshToken.startSession(
                snowflakeIdGenerator.generateId(),
                userId,
                RefreshTokenSecrets.hash(secret),
                AppClock.currentUTC(),
                refreshTokenPolicy.getIdleLifetime(),
                refreshTokenPolicy.getAbsoluteLifetime(),
                clientInfo.userAgent(),
                clientInfo.ipAddress());
        refreshTokenDomainRepository.save(refreshToken);

        return secret;
    }
}
