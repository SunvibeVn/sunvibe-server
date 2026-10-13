package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationChallengePurgeUseCase;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationPhoneChallengeRepository;
import vn.sunvibe.common.time.AppClock;

import java.time.Duration;

/**
 * Deletes registration phone challenges a day after they expired.
 */
@Service
@RequiredArgsConstructor
public class RegistrationChallengePurgeService implements RegistrationChallengePurgeUseCase {

    /** Kept a little longer so recent failures stay traceable next to the audit log. */
    static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);

    private final RegistrationPhoneChallengeRepository challengeRepository;

    @Override
    @Transactional
    public int execute() {
        return challengeRepository.deleteExpiredBefore(AppClock.currentUTC().minus(RETENTION_AFTER_EXPIRY));
    }
}
