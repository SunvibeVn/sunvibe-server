package vn.sunvibe.outbox;

import java.time.Instant;
import java.util.Map;

/**
 * Receives the outbox rows of one subscription (ADR-014 decisions 6, 7). Implemented by an adapter in
 * {@code vn.sunvibe.integration} that calls the consuming module's published use case; its name is listed in
 * {@link OutboxSubscriptions}.
 *
 * <p>Delivery is at least once and may come out of order: a consumer is idempotent and reads the current state of the
 * aggregate again rather than trusting the event. {@link #deliver(Delivery)} runs on the outbox's delivery thread with
 * no transaction open; the consumer's use case opens its own. Returning normally marks the row done; throwing, or
 * running past the delivery timeout, retries it under {@link #retryPolicy()}.
 */
public interface OutboxConsumer {

    /** The fixed code of this consumer, as the subscription list names it. */
    String name();

    OutboxRetryPolicy retryPolicy();

    void deliver(Delivery delivery);

    /**
     * One row as the consumer receives it.
     *
     * @param attempt 1 for the first attempt
     */
    record Delivery(long eventId, String eventType, int eventVersion, String aggregateType, String aggregateId,
                    Map<String, String> payload, Instant occurredAt, String correlationId, int attempt) {
    }
}
