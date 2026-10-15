package vn.sunvibe.auth.core.infrastructure.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.audit.AuthAuditEventEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.audit.AuthAuditEventJpaRepository;
import vn.sunvibe.shared.crypto.AesGcmCryptoService;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs without a test-managed transaction so that commits and rollbacks are real.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuthAuditLogAdapter.class, AesGcmCryptoService.class, AuthAuditLogAdapterTest.Ids.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.crypto.id-card-key=bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=",
        "app.crypto.id-card-index-key=HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw="
})
class AuthAuditLogAdapterTest {

    @TestConfiguration
    static class Ids {
        @Bean
        IdGenerator<Long> snowflakeIdGenerator() {
            AtomicLong ids = new AtomicLong();
            return ids::incrementAndGet;
        }
    }

    @Autowired
    private AuthAuditLogAdapter adapter;

    @Autowired
    private AuthAuditEventJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        repository.deleteAll();
    }

    /** Security events usually end in an exception that rolls the request back; the event must survive it. */
    @Test
    void record_survivesARollbackOfTheCallersTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adapter.record(AuthAuditEvent.of(AuditEventType.LOGIN_FAILED, "user-1", "0912345678", "BAD_CREDENTIALS"));
            status.setRollbackOnly();
        });

        List<AuthAuditEventEntity> events = repository.findAll();
        assertEquals(1, events.size());
        AuthAuditEventEntity event = events.getFirst();
        assertEquals(AuditEventType.LOGIN_FAILED, event.getEventType());
        assertEquals("user-1", event.getUserId());
        assertEquals("0912345678", event.getSubject());
        assertEquals("BAD_CREDENTIALS", event.getDetails());
        assertNotNull(event.getOccurredAt());
    }

    /** Success events (a role granted) must not outlive a rolled-back change (auth-roles US-4 AC-4, BR-8). */
    @Test
    void recordAfterCommit_callersTransactionRollsBack_writesNothing() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adapter.recordAfterCommit(AuthAuditEvent.of(AuditEventType.ROLE_GRANTED, "user-1", null, "role=OWNER"));
            status.setRollbackOnly();
        });

        assertEquals(0, repository.count());
    }

    @Test
    void recordAfterCommit_callersTransactionCommits_writesTheEventWithTheRequestData() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/staff/users/u/roles/SALE");
        request.setRemoteAddr("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adapter.recordAfterCommit(AuthAuditEvent.of(AuditEventType.ROLE_GRANTED, "user-1", null, "role=SALE"));
            assertEquals(0, repository.count(), "not written before the commit");
        });

        AuthAuditEventEntity event = repository.findAll().getFirst();
        assertEquals(AuditEventType.ROLE_GRANTED, event.getEventType());
        assertEquals("role=SALE", event.getDetails());
        assertEquals("203.0.113.9", event.getIpAddress());
    }

    @Test
    void recordAfterCommit_withoutATransaction_writesAtOnce() {
        adapter.recordAfterCommit(AuthAuditEvent.of(AuditEventType.ROLE_REVOKED, "user-1", null, "role=SALE"));

        assertEquals(1, repository.count());
    }

    @Test
    void record_takesClientIpAndUserAgentFromTheCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("203.0.113.7");
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        adapter.record(AuthAuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, "user-1", "0912345678"));

        AuthAuditEventEntity event = repository.findAll().getFirst();
        assertEquals("203.0.113.7", event.getIpAddress());
        assertEquals("Mozilla/5.0", event.getUserAgent());
    }

    @Test
    void record_withoutARequestStillWorks() {
        adapter.record(AuthAuditEvent.of(AuditEventType.LOGOUT, "user-1", null));

        AuthAuditEventEntity event = repository.findAll().getFirst();
        assertNull(event.getIpAddress());
        assertNull(event.getUserAgent());
    }

    @Test
    void record_truncatesOverlongValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "x".repeat(1000));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        adapter.record(AuthAuditEvent.of(AuditEventType.LOGIN_FAILED, null, "s".repeat(500), "d".repeat(500)));

        AuthAuditEventEntity event = repository.findAll().getFirst();
        assertEquals(100, event.getSubject().length());
        assertEquals(255, event.getDetails().length());
        assertEquals(255, event.getUserAgent().length());
    }

    /** A broken audit log must never break sign-in. */
    @Test
    void record_neverThrows() {
        AuthAuditLogAdapter broken = new AuthAuditLogAdapter(repository, () -> {
            throw new IllegalStateException("id generator down");
        }, transactionManager);

        assertDoesNotThrow(() -> broken.record(AuthAuditEvent.of(AuditEventType.LOGIN_FAILED, null, null)));
        assertEquals(0, repository.count());
    }

    @Test
    void purgeOlderThan_deletesOnlyOldEvents() {
        AuthAuditEventEntity old = new AuthAuditEventEntity();
        old.setId(1000L);
        old.setEventType(AuditEventType.LOGIN_SUCCEEDED);
        old.setOccurredAt(Instant.now().minus(Duration.ofDays(200)));
        repository.save(old);
        adapter.record(AuthAuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, "user-1", null));

        int deleted = adapter.purgeOlderThan(Instant.now().minus(Duration.ofDays(180)));

        assertEquals(1, deleted);
        assertEquals(1, repository.count());
    }
}
