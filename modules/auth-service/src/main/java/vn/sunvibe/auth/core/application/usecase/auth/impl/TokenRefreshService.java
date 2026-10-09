package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.TokenRefreshCommand;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.RefreshTokenPolicy;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.auth.AccessTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.TokenRefreshUseCase;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
import java.util.Optional;

/**
 * Refresh-token rotation with reuse detection.
 * <p>
 * The presented token is locked for the duration of the transaction, so two
 * concurrent refreshes with the same token are decided one after the other.
 * A token reused after the grace period means it was most likely copied: the
 * whole session is revoked, and that revocation is committed even though the
 * request fails ({@code noRollbackFor}).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TokenRefreshService implements TokenRefreshUseCase {

    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final UserDomainRepository userRepository;
    private final AccessTokenGenerateUseCase accessTokenGenerateUseCase;
    private final AuthResultAssembler authResultAssembler;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final RefreshTokenPolicy refreshTokenPolicy;

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult execute(TokenRefreshCommand command) {
        String presentedSecret = command.refreshToken();
        if (presentedSecret == null || presentedSecret.isBlank()) {
            throw rejected("no refresh token was sent");
        }

        Instant now = AppClock.currentUTC();
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenSecrets.hash(presentedSecret))
                .orElseThrow(() -> rejected("unknown refresh token"));

        if (current.isRevoked()) {
            throw rejected("revoked token of session " + current.getFamilyId());
        }
        if (current.isExpired(now)) {
            throw rejected("expired token of session " + current.getFamilyId());
        }
        if (current.isUsed() && !current.isWithinReuseGrace(now, refreshTokenPolicy.getReuseGracePeriod())) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), RevokeReason.REUSE_DETECTED, now);
            log.warn("Refresh token reuse detected for user {}: session {} revoked",
                    current.getUserId().getValue(), current.getFamilyId());
            throw new InvalidRefreshTokenException(AuthMessage.SESSION_EXPIRED);
        }

        Optional<AppUser> owner = userRepository.findById(current.getUserId()).filter(AppUser::canAuthenticate);
        if (owner.isEmpty()) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), RevokeReason.ACCOUNT_INACTIVE, now);
            throw rejected("user " + current.getUserId().getValue() + " is missing, locked or disabled");
        }
        AppUser user = owner.get();

        String nextSecret = RefreshTokenSecrets.generate();
        ClientInfo clientInfo = command.clientInfo() != null ? command.clientInfo() : ClientInfo.unknown();
        RefreshToken next = current.rotate(snowflakeIdGenerator.generateId(), RefreshTokenSecrets.hash(nextSecret), now,
                refreshTokenPolicy.getIdleLifetime(), clientInfo.userAgent(), clientInfo.ipAddress());
        refreshTokenRepository.save(current);
        refreshTokenRepository.save(next);

        String accessToken = accessTokenGenerateUseCase.generateAccessToken(user.getUsername().getValue());
        return authResultAssembler.assemble(user, accessToken, nextSecret, AuthMessage.TOKEN_REFRESH_SUCCESS);
    }

    /** Every failure looks the same to the client; the reason is only logged. */
    private static InvalidRefreshTokenException rejected(String reason) {
        log.info("Refresh rejected: {}", reason);
        return new InvalidRefreshTokenException(AuthMessage.SESSION_EXPIRED);
    }
}
