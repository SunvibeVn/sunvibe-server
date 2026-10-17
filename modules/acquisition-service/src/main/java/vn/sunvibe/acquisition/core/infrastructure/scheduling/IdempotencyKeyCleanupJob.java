package vn.sunvibe.acquisition.core.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.usecase.IdempotencyKeyCleanupUseCase;

/**
 * Every request with an Idempotency-Key adds a row, so keys older than 24 hours are deleted daily
 * ({@code app.acquisition.idempotency-cleanup-cron}, default 04:30 server time).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupJob {

    private final IdempotencyKeyCleanupUseCase idempotencyKeyCleanupUseCase;

    @Scheduled(cron = "${app.acquisition.idempotency-cleanup-cron:0 30 4 * * *}")
    public void purgeExpiredIdempotencyKeys() {
        int deleted = idempotencyKeyCleanupUseCase.purgeExpired();
        log.info("Purged {} expired owner intake idempotency keys", deleted);
    }
}
