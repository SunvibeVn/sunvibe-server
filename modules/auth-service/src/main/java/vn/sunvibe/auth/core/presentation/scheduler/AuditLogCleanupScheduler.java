package vn.sunvibe.auth.core.presentation.scheduler;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.common.time.AppClock;

import java.time.Duration;

/**
 * Deletes audit events older than {@code app.security.audit.retention}
 * (default 180 days), daily at {@code app.security.audit.cleanup-cron} (04:00).
 */
@Log4j2
@Component
public class AuditLogCleanupScheduler {

    private final AuditLogPort auditLog;
    private final Duration retention;

    public AuditLogCleanupScheduler(AuditLogPort auditLog,
                                    @Value("${app.security.audit.retention:180d}") Duration retention) {
        this.auditLog = auditLog;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.security.audit.cleanup-cron:0 0 4 * * *}")
    public void purgeOldAuditEvents() {
        int deleted = auditLog.purgeOlderThan(AppClock.currentUTC().minus(retention));
        log.info("Purged {} audit events older than {}", deleted, retention);
    }
}
