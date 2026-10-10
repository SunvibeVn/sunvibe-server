package vn.sunvibe.auth.core.presentation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.auth.ExpiredRefreshTokenPurgeUseCase;

/**
 * Every refresh adds a row, so expired refresh tokens are deleted daily
 * ({@code app.jwt.refreshCleanupCron}, default 03:30 server time).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final ExpiredRefreshTokenPurgeUseCase expiredRefreshTokenPurgeUseCase;

    @Scheduled(cron = "${app.jwt.refreshCleanupCron:0 30 3 * * *}")
    public void purgeExpiredRefreshTokens() {
        int deleted = expiredRefreshTokenPurgeUseCase.execute();
        log.info("Purged {} expired refresh tokens", deleted);
    }
}
