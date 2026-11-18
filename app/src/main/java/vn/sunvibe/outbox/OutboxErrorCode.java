package vn.sunvibe.outbox;

/**
 * The closed list of {@code outbox_events.last_error_code} values (ADR-014 decision 4; E05-S5b S5b-D7): a fixed code,
 * never a consumer's message.
 */
public enum OutboxErrorCode {
    /** The consumer threw. */
    CONSUMER_ERROR,
    /** The consumer did not return within the delivery timeout. */
    DELIVERY_TIMEOUT,
    /** No consumer of that name is in this instance's subscription list. */
    NO_CONSUMER
}
