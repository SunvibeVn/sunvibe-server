package vn.sunvibe.auth.core.infrastructure.audit;

import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.audit.AuthAuditEventEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.audit.AuthAuditEventJpaRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Writes audit events on one thread and one connection of its own (auth-email-access spec 5.4). A request only puts its
 * event in a bounded queue, so it never takes a second connection and the event survives the rollback of the request.
 * What is queued when the process crashes is lost (spec 2.4 item 7); {@link #close()} drains the queue on a normal stop.
 */
@Log4j2
@Component
public class AuthAuditWriter {

    private static final int QUEUE_CAPACITY = 1000;
    private static final int MAX_BATCH = 100;
    private static final long POLL_MILLIS = 200;
    private static final long IDLE_CHECK_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private static final Duration DRAIN_LIMIT = Duration.ofSeconds(5);

    private final BlockingQueue<AuthAuditEventEntity> queue;
    private final Consumer<List<AuthAuditEventEntity>> store;
    private final Thread worker;
    /** Events queued or in the batch being written. */
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean closing;

    @Autowired
    public AuthAuditWriter(AuthAuditEventJpaRepository repository, PlatformTransactionManager transactionManager) {
        this(inOwnTransaction(repository, transactionManager), QUEUE_CAPACITY);
    }

    AuthAuditWriter(Consumer<List<AuthAuditEventEntity>> store, int capacity) {
        this.store = store;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = new Thread(this::run, "auth-audit-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    private static Consumer<List<AuthAuditEventEntity>> inOwnTransaction(AuthAuditEventJpaRepository repository,
                                                                         PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return batch -> transaction.executeWithoutResult(status -> repository.saveAll(batch));
    }

    /** Never waits and never throws: a full queue loses the event, with a warn line. */
    public void enqueue(AuthAuditEventEntity event) {
        pending.incrementAndGet();
        if (closing || !queue.offer(event)) {
            pending.decrementAndGet();
            log.warn("auth.audit.overflow type={}", event.getEventType());
        }
    }

    /**
     * Waits until every event accepted so far is written or given up, for tests that read the table.
     *
     * @return false if the limit passed first
     */
    public boolean awaitIdle(Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (pending.get() > 0) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            LockSupport.parkNanos(IDLE_CHECK_NANOS);
        }
        return true;
    }

    /** Stops taking events and writes what is queued, within a limit, so a shutdown does not hang on a dead database. */
    @PreDestroy
    public void close() {
        closing = true;
        try {
            worker.join(DRAIN_LIMIT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (worker.isAlive()) {
            worker.interrupt();
            log.warn("auth.audit.drain-timeout remaining={}", pending.get());
        }
    }

    private void run() {
        List<AuthAuditEventEntity> batch = new ArrayList<>(MAX_BATCH);
        while (true) {
            AuthAuditEventEntity first;
            try {
                first = queue.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (first == null) {
                if (closing) {
                    return;
                }
                continue;
            }
            batch.add(first);
            queue.drainTo(batch, MAX_BATCH - 1);
            write(batch);
            batch.clear();
        }
    }

    /** The class only: a database message can quote row values. */
    private void write(List<AuthAuditEventEntity> batch) {
        try {
            store.accept(List.copyOf(batch));
        } catch (RuntimeException e) {
            log.error("auth.audit.write-failed events={} class={}", batch.size(), e.getClass().getName());
        } finally {
            pending.addAndGet(-batch.size());
        }
    }
}
