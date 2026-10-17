package vn.sunvibe.acquisition.core.application.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.usecase.IdempotencyKeyCleanupUseCase;
import vn.sunvibe.acquisition.core.domain.repository.IdempotencyKeyDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;

/**
 * Deletes the owner intake idempotency keys once they can no longer replay a request. They are technical data, not
 * lead data (acquisition-owner-intake spec 5.1).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyKeyCleanupService implements IdempotencyKeyCleanupUseCase {

    // acquisition-owner-intake BR-10: a key replays its first result for 24 hours, so an older key is of no use
    static final Duration KEY_LIFETIME = Duration.ofHours(24);

    private final IdempotencyKeyDomainRepository idempotencyKeys;

    @Override
    @Transactional
    public int purgeExpired() {
        return idempotencyKeys.deleteCreatedBefore(AppClock.currentUTC().minus(KEY_LIFETIME));
    }
}
