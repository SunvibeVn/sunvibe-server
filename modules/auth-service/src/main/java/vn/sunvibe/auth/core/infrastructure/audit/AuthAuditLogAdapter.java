package vn.sunvibe.auth.core.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.audit.AuthAuditEventEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.audit.AuthAuditEventJpaRepository;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;

/**
 * Writes audit events to {@code auth_audit_events}.
 * <p>
 * Each event is written in its own transaction ({@code REQUIRES_NEW}), so it is
 * kept when the request that produced it fails and rolls back, which is exactly
 * when security events matter most. Any failure is logged and swallowed. The
 * client IP and User-Agent come from the current HTTP request, if any.
 */
@Log4j2
@Component
public class AuthAuditLogAdapter implements AuditLogPort {

    private static final int MAX_SUBJECT = 100;
    private static final int MAX_DETAILS = 255;
    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_IP = 45;

    private final AuthAuditEventJpaRepository repository;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final TransactionTemplate inNewTransaction;

    public AuthAuditLogAdapter(AuthAuditEventJpaRepository repository, IdGenerator<Long> snowflakeIdGenerator,
                               PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.inNewTransaction = new TransactionTemplate(transactionManager);
        this.inNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(AuthAuditEvent event) {
        try {
            AuthAuditEventEntity entity = toEntity(event);
            inNewTransaction.executeWithoutResult(status -> repository.save(entity));
        } catch (RuntimeException e) {
            log.error("Could not write audit event {} for user {}", event.type(), event.userId(), e);
        }
    }

    @Override
    public void recordAfterCommit(AuthAuditEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            record(event);
            return;
        }
        try {
            // Built now, while the request (IP, User-Agent) is still at hand; written after the commit
            AuthAuditEventEntity entity = toEntity(event);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        inNewTransaction.executeWithoutResult(status -> repository.save(entity));
                    } catch (RuntimeException e) {
                        log.error("Could not write audit event {} for user {}", event.type(), event.userId(), e);
                    }
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not write audit event {} for user {}", event.type(), event.userId(), e);
        }
    }

    private AuthAuditEventEntity toEntity(AuthAuditEvent event) {
            AuthAuditEventEntity entity = new AuthAuditEventEntity();
            entity.setId(snowflakeIdGenerator.generateId());
            entity.setEventType(event.type());
            entity.setUserId(event.userId());
            entity.setSubject(truncate(event.subject(), MAX_SUBJECT));
            entity.setDetails(truncate(event.details(), MAX_DETAILS));
            entity.setOccurredAt(AppClock.currentUTC());
            HttpServletRequest request = currentRequest();
            if (request != null) {
                entity.setIpAddress(truncate(request.getRemoteAddr(), MAX_IP));
                entity.setUserAgent(truncate(request.getHeader(HttpHeaders.USER_AGENT), MAX_USER_AGENT));
            }
            return entity;
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        Integer deleted = inNewTransaction.execute(status -> repository.deleteOlderThan(cutoff));
        return deleted != null ? deleted : 0;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
