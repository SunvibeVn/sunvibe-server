package vn.sunvibe.auth.core.infrastructure.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.audit.AuthAuditEventEntity;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The audit queue of auth-email-access spec 5.4: a request only enqueues, so it never takes a second connection, and
 * one thread writes. The store is a fake here; {@link AuthAuditLogAdapterTest} runs it against a database.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuthAuditWriterTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private final List<AuthAuditEventEntity> stored = new CopyOnWriteArrayList<>();
    private AuthAuditWriter writer;

    @AfterEach
    void stop() {
        if (writer != null) {
            writer.close();
        }
    }

    private static AuthAuditEventEntity event() {
        AuthAuditEventEntity entity = new AuthAuditEventEntity();
        entity.setEventType(AuditEventType.LOGIN_FAILED);
        return entity;
    }

    @Test
    void audit_record_enqueues_noSecondConnection() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> storingThread = new AtomicReference<>();
        writer = new AuthAuditWriter(batch -> {
            storingThread.set(Thread.currentThread());
            await(release);
            stored.addAll(batch);
        }, 10);

        writer.enqueue(event());

        assertTrue(stored.isEmpty(), "the caller did not wait for the store");
        release.countDown();
        assertTrue(writer.awaitIdle(WAIT));
        assertEquals(1, stored.size());
        assertNotEquals(Thread.currentThread(), storingThread.get(), "the writer thread stored it");
    }

    @Test
    void audit_overflow_logged(CapturedOutput output) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        writer = new AuthAuditWriter(batch -> {
            entered.countDown();
            await(release);
            stored.addAll(batch);
        }, 1);
        writer.enqueue(event());
        assertTrue(entered.await(10, TimeUnit.SECONDS), "the first event reached the store");
        writer.enqueue(event());

        writer.enqueue(event());

        assertTrue(output.getOut().contains("auth.audit.overflow"), "the lost event is logged");
        release.countDown();
        assertTrue(writer.awaitIdle(WAIT));
        assertEquals(2, stored.size());
    }

    @Test
    void audit_failedWrite_logged_loopContinues(CapturedOutput output) {
        AtomicBoolean failed = new AtomicBoolean();
        writer = new AuthAuditWriter(batch -> {
            if (failed.compareAndSet(false, true)) {
                throw new IllegalStateException("database down");
            }
            stored.addAll(batch);
        }, 10);

        writer.enqueue(event());
        assertTrue(writer.awaitIdle(WAIT));
        writer.enqueue(event());
        assertTrue(writer.awaitIdle(WAIT));

        assertEquals(1, stored.size(), "the second event was written");
        assertTrue(output.getOut().contains("auth.audit.write-failed"), "the failed write is logged");
    }

    @Test
    void audit_drainedAtShutdown() {
        CountDownLatch release = new CountDownLatch(1);
        writer = new AuthAuditWriter(batch -> {
            await(release);
            stored.addAll(batch);
        }, 100);
        for (int i = 0; i < 50; i++) {
            writer.enqueue(event());
        }
        release.countDown();

        writer.close();

        assertEquals(50, stored.size());
    }

    @Test
    void enqueue_afterShutdown_isLoggedNotThrown(CapturedOutput output) {
        writer = new AuthAuditWriter(stored::addAll, 10);
        writer.close();

        assertDoesNotThrow(() -> writer.enqueue(event()));

        assertTrue(output.getOut().contains("auth.audit.overflow"));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
