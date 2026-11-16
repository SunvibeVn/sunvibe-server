package vn.sunvibe.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled trigger: one clean-up run of {@link OutboxWorker#cleanUp()} every clean-up interval, on the outbox's own
 * scheduler (ADR-014 decision 9). A failed run is logged by class name and never cancels the schedule.
 */
public final class OutboxCleanupJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxWorker worker;

    public OutboxCleanupJob(OutboxWorker worker) {
        this.worker = worker;
    }

    @Override
    public void run() {
        try {
            worker.cleanUp();
        } catch (RuntimeException e) {
            log.error("OUTBOX_RUN_FAILED job=OutboxCleanupJob exception={}", e.getClass().getName());
        }
    }
}
