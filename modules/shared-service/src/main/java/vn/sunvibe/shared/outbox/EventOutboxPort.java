package vn.sunvibe.shared.outbox;

/**
 * Appends an event to the transactional outbox of ADR-014, in the caller's transaction: one row per consumer
 * subscribed to the event's type, none when no consumer is subscribed. The rows commit or roll back with the caller's
 * change.
 *
 * <p>A command calls it for a change of material state, after its own checks, inside its {@code @Transactional}
 * boundary (ADR-014 decision 2). A change that removes something public is never given a due time.
 *
 * @throws IllegalStateException without an active read-write transaction; nothing is written
 * @throws IllegalArgumentException for an event the subscription list refuses (an unknown type, a missing or
 *                                  undeclared payload key, a value outside its shape) or a due time more than 800
 *                                  days ahead; nothing is written
 */
public interface EventOutboxPort {

    void append(OutboxEvent event);
}
