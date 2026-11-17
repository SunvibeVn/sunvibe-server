package vn.sunvibe.shared.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The envelope a producer appends to the outbox (ADR-014 decision 4). It checks shape only; which payload keys a type
 * may carry is the subscription list's rule. A failure names the field, never its value.
 *
 * @param type          dotted lower-case code, at most 100 characters, such as {@code brokerage.unit-advertising-changed}
 * @param version       1 to 99
 * @param sourceModule  the producing module, lower-case letters
 * @param aggregateType the aggregate the event is about, lower-case letters and underscores
 * @param aggregateId   the aggregate's id: letters, digits and dashes, at most 64
 * @param payload       at most 10 entries, no null key or value; copied unmodifiable
 * @param dueAt         when the event is due; null means now
 */
public record OutboxEvent(String type, int version, String sourceModule, String aggregateType, String aggregateId,
                          Map<String, String> payload, Instant dueAt) {

    private static final Pattern TYPE = Pattern.compile("^[a-z]+(\\.[a-z][a-z-]*)+$");
    private static final Pattern SOURCE_MODULE = Pattern.compile("^[a-z]{1,40}$");
    private static final Pattern AGGREGATE_TYPE = Pattern.compile("^[a-z_]{1,40}$");
    private static final Pattern AGGREGATE_ID = Pattern.compile("^[0-9A-Za-z-]{1,64}$");
    private static final int MAX_TYPE_LENGTH = 100;
    private static final int MAX_PAYLOAD_ENTRIES = 10;

    public OutboxEvent {
        if (type == null || type.length() > MAX_TYPE_LENGTH || !TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("outbox event type is malformed");
        }
        if (version < 1 || version > 99) {
            throw new IllegalArgumentException("outbox event version must be 1 to 99");
        }
        requireShape(sourceModule, SOURCE_MODULE, "sourceModule");
        requireShape(aggregateType, AGGREGATE_TYPE, "aggregateType");
        requireShape(aggregateId, AGGREGATE_ID, "aggregateId");
        if (payload == null) {
            throw new IllegalArgumentException("outbox event payload is required");
        }
        if (payload.size() > MAX_PAYLOAD_ENTRIES) {
            throw new IllegalArgumentException("outbox event payload has more than 10 entries");
        }
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("outbox event payload holds a null key or value");
            }
        }
        payload = Map.copyOf(payload);
    }

    private static void requireShape(String value, Pattern shape, String field) {
        if (value == null || !shape.matcher(value).matches()) {
            throw new IllegalArgumentException("outbox event " + field + " is malformed");
        }
    }
}
