package vn.sunvibe.auth.core.presentation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.user.PendingAccountCleanupUseCase;

/**
 * Daily deactivation of accounts that never verified their phone
 * ({@code app.security.verification.pending-cleanup-cron}, default 03:15).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PendingAccountCleanupScheduler {

    private final PendingAccountCleanupUseCase pendingAccountCleanupUseCase;

    @Scheduled(cron = "${app.security.verification.pending-cleanup-cron:0 15 3 * * *}")
    public void deactivateExpiredPendingAccounts() {
        int deactivated = pendingAccountCleanupUseCase.execute();
        log.info("Deactivated {} accounts that never verified their phone", deactivated);
    }
}
