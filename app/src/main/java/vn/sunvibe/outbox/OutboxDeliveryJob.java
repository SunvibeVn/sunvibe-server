package vn.sunvibe.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled trigger: one delivery run of {@link OutboxWorker#deliverDue()} every poll interval, on the outbox's own
 * scheduler (ADR-014 decision 5). A failed run is logged by class name and never cancels the schedule.
 */
public final class OutboxDeliveryJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryJob.class);

    private final OutboxWorker worker;

    public OutboxDeliveryJob(OutboxWorker worker) {
        this.worker = worker;
    }

    @Override
    public void run() {
        try {
            worker.deliverDue();
        } catch (RuntimeException e) {
            log.error("OUTBOX_RUN_FAILED job=OutboxDeliveryJob exception={}", e.getClass().getName());
        }
    }
}
