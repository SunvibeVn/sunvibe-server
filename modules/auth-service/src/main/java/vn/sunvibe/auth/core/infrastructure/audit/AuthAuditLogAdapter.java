package vn.sunvibe.auth.core.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.audit.AuthAuditEventEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.audit.AuthAuditEventJpaRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;

/**
 * Records audit events in {@code auth_audit_events}.
 * <p>
 * {@link #record} only hands the event to {@link AuthAuditWriter}: it is kept when the request that produced it fails
 * and rolls back, which is exactly when security events matter most, and the request takes no second connection.
 * {@link #recordAfterCommit} inserts in the caller's transaction, so a role event and its change commit together or
 * not at all. The client IP and User-Agent come from the current HTTP request, if any, read on the request's thread.
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
    private final AuthAuditWriter writer;
    private final TransactionTemplate inNewTransaction;

    public AuthAuditLogAdapter(AuthAuditEventJpaRepository repository, IdGenerator<Long> snowflakeIdGenerator,
                               AuthAuditWriter writer, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.writer = writer;
        this.inNewTransaction = new TransactionTemplate(transactionManager);
        this.inNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(AuthAuditEvent event) {
        try {
            writer.enqueue(toEntity(event));
        } catch (RuntimeException e) {
            log.error("Could not record audit event {} for user {}", event.type(), event.userId(), e);
        }
    }

    @Override
    public void recordAfterCommit(AuthAuditEvent event) {
        repository.save(toEntity(event));
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
