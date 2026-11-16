package vn.sunvibe.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled trigger: one status run of {@link OutboxWorker#report()} every status interval, on the outbox's own
 * scheduler (ADR-014 decision 8). A failed run is logged by class name and never cancels the schedule.
 */
public final class OutboxStatusJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OutboxStatusJob.class);

    private final OutboxWorker worker;

    public OutboxStatusJob(OutboxWorker worker) {
        this.worker = worker;
    }

    @Override
    public void run() {
        try {
            worker.report();
        } catch (RuntimeException e) {
            log.error("OUTBOX_RUN_FAILED job=OutboxStatusJob exception={}", e.getClass().getName());
        }
    }
}
