package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.usecase.auth.ExpiredRefreshTokenPurgeUseCase;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.common.time.AppClock;

import java.time.Duration;

/**
 * Deletes refresh tokens a day after they expired.
 */
@Service
@RequiredArgsConstructor
public class ExpiredRefreshTokenPurgeService implements ExpiredRefreshTokenPurgeUseCase {

    /** Expired tokens are kept a little longer so recent refresh failures stay traceable. */
    static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final RefreshTokenDomainRepository refreshTokenRepository;

    @Override
    @Transactional
    public int execute() {
        return refreshTokenRepository.deleteExpiredBefore(AppClock.currentUTC().minus(RETENTION_AFTER_EXPIRY));
    }
}
