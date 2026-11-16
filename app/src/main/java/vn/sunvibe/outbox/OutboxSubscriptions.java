package vn.sunvibe.outbox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import vn.sunvibe.shared.outbox.OutboxEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The static subscription list of the outbox (ADR-014 decisions 3, 11; E05-S5b S5b-D3): per event type, the exact
 * payload keys it declares and the consumers that receive it. An append is checked against it whether or not the type
 * has a consumer. Payload keys come from a closed list of identifiers of aggregates that name no person (ADR-014
 * decision 4); values are identifiers or codes, never free text. A refusal names the type and key, never the value.
 */
public final class OutboxSubscriptions {

    /** Every key any type may declare; a new key needs a review that it names no person (S5b-D3). */
    public static final Set<String> ALLOWED_PAYLOAD_KEYS = Set.of(UnitAdvertisingChangedEvent.UNIT_ID);

    private static final Pattern CONSUMER_NAME = Pattern.compile("^[a-z][a-z0-9-]{0,99}$");
    private static final Pattern PAYLOAD_VALUE = Pattern.compile("^[0-9A-Za-z-]{1,64}$");

    private final Map<String, Subscription> byType;
    private final List<String> consumers;

    private OutboxSubscriptions(Map<String, Subscription> byType, List<String> consumers) {
        this.byType = byType;
        this.consumers = consumers;
    }

    /**
     * One event type's declared payload keys and its consumers, in delivery order of the list.
     *
     * @param consumers fixed codes matching {@code ^[a-z][a-z0-9-]{0,99}$}: they name threads, log values and rows
     */
    public record Subscription(Set<String> payloadKeys, List<String> consumers) {

        public Subscription {
            payloadKeys = Set.copyOf(payloadKeys);
            consumers = List.copyOf(consumers);
        }
    }

    /** A subscription list; refuses an undeclared key, a malformed consumer name or a consumer listed twice for a type. */
    public static OutboxSubscriptions of(Map<String, Subscription> byType) {
        Map<String, Subscription> copy = new TreeMap<>(byType);
        Set<String> all = new TreeSet<>();
        for (Map.Entry<String, Subscription> entry : copy.entrySet()) {
            for (String key : entry.getValue().payloadKeys()) {
                if (!ALLOWED_PAYLOAD_KEYS.contains(key)) {
                    throw new IllegalArgumentException(
                            "outbox type " + entry.getKey() + " declares the payload key " + key + ", which is not allowed");
                }
            }
            Set<String> seen = new HashSet<>();
            for (String consumer : entry.getValue().consumers()) {
                if (!CONSUMER_NAME.matcher(consumer).matches()) {
                    throw new IllegalArgumentException("outbox type " + entry.getKey() + " lists a malformed consumer name");
                }
                if (!seen.add(consumer)) {
                    throw new IllegalArgumentException(
                            "outbox type " + entry.getKey() + " lists the consumer " + consumer + " twice");
                }
            }
            all.addAll(entry.getValue().consumers());
        }
        return new OutboxSubscriptions(Map.copyOf(copy), List.copyOf(new ArrayList<>(all)));
    }

    /** The list of this release: E05's event with its unit id and no consumer yet (E06-S7 subscribes the listing). */
    public static OutboxSubscriptions production() {
        return of(Map.of(UnitAdvertisingChangedEvent.TYPE,
                new Subscription(Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of())));
    }

    /** Refuses an unknown type, a missing or undeclared payload key, or a value outside {@code [0-9A-Za-z-]{1,64}}. */
    public void check(OutboxEvent event) {
        Subscription subscription = byType.get(event.type());
        if (subscription == null) {
            throw new IllegalArgumentException("outbox type " + event.type() + " is not in the subscription list");
        }
        for (String key : event.payload().keySet()) {
            if (!subscription.payloadKeys().contains(key)) {
                throw new IllegalArgumentException(
                        "outbox type " + event.type() + " does not declare the payload key " + safeKey(key));
            }
        }
        for (String key : subscription.payloadKeys()) {
            String value = event.payload().get(key);
            if (value == null) {
                throw new IllegalArgumentException("outbox type " + event.type() + " needs the payload key " + key);
            }
            if (!PAYLOAD_VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException(
                        "outbox type " + event.type() + " has a malformed value for the payload key " + key);
            }
        }
    }

    /** The consumers of a type, in list order; empty when none or the type is unknown. */
    public List<String> consumersOf(String type) {
        Subscription subscription = byType.get(type);
        return subscription == null ? List.of() : subscription.consumers();
    }

    /** Every consumer name once, sorted. */
    public List<String> consumers() {
        return consumers;
    }

    /** An undeclared key is caller input: named only when it has the shape of a key, never echoed as free text. */
    private static String safeKey(String key) {
        return key.matches("^[A-Za-z][A-Za-z0-9]{0,39}$") ? key : "(malformed)";
    }
}
