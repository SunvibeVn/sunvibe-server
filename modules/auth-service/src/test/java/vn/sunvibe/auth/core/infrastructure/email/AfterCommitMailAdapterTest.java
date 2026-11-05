package vn.sunvibe.auth.core.infrastructure.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access PD-7: recovery mail leaves only after the commit, off the request thread, on a bounded queue. */
@ExtendWith(OutputCaptureExtension.class)
class AfterCommitMailAdapterTest {

    /** Commits and rolls back nothing, but runs the synchronizations as a real manager does. */
    private static final class NoDatabaseTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private final TransactionTemplate transaction = new TransactionTemplate(new NoDatabaseTransactions());
    private final AuthMailExecutor executor = new AuthMailExecutor(1, 1);
    private final AfterCommitMailAdapter adapter = new AfterCommitMailAdapter(executor);

    @AfterEach
    void stop() throws Exception {
        executor.destroy();
    }

    @Test
    void afterCommit_runsOnlyAfterCommit() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        AtomicBoolean committed = new AtomicBoolean();

        transaction.executeWithoutResult(status -> {
            adapter.dispatch(sent::countDown);
            assertEquals(1, sent.getCount(), "nothing leaves before the commit");
            committed.set(true);
        });

        assertTrue(sent.await(10, TimeUnit.SECONDS));
        assertTrue(committed.get());
    }

    @Test
    void afterCommit_rollback_sendsNothing() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);

        transaction.executeWithoutResult(status -> {
            adapter.dispatch(sent::countDown);
            status.setRollbackOnly();
        });

        assertFalse(sent.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void dispatch_withoutTransaction_sendsAtOnce() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);

        adapter.dispatch(sent::countDown);

        assertTrue(sent.await(10, TimeUnit.SECONDS));
    }

    /** The caller records it: the warn line alone leaves no trace of whose mail was lost (auth-email-access spec 5.6). */
    @Test
    void dispatch_queueFull_runsTheRefusal() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        adapter.dispatch(() -> {
            running.countDown();
            await(release);
        });
        assertTrue(running.await(10, TimeUnit.SECONDS));
        adapter.dispatch(() -> { });
        AtomicInteger refused = new AtomicInteger();

        adapter.dispatch(() -> { }, refused::incrementAndGet);
        transaction.executeWithoutResult(status -> adapter.dispatch(() -> { }, refused::incrementAndGet));

        release.countDown();
        assertEquals(2, refused.get());
    }

    /** The response is already neutral: a full queue costs a warn line and nothing else. */
    @Test
    void afterCommit_queueFull_logsAndContinues(CapturedOutput output) throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        adapter.dispatch(() -> {
            running.countDown();
            await(release);
        });
        assertTrue(running.await(10, TimeUnit.SECONDS));
        adapter.dispatch(() -> { });

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> adapter.dispatch(() -> { })));

        release.countDown();
        assertTrue(output.getOut().contains("auth.mail.queue-full"));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
