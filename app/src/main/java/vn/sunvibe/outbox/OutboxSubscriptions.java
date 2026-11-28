package vn.sunvibe.outbox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.outbox.OutboxEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The static subscription list of the outbox (ADR-014 decisions 3, 11; E05-S5b S5b-D3): per event type, the exact
 * payload keys it declares and the consumers that receive it. An append is checked against it whether or not the type
 * has a consumer. Payload keys come from a closed list of identifiers of aggregates that name no person (ADR-014
 * decision 4); values are identifiers or codes, never free text. A refusal names the type and key, never the value.
 */
public final class OutboxSubscriptions {

    /**
     * Every key any type may declare; a new key needs a review that it names no person (S5b-D3). A listing names no
     * person (listing-publish E06-S6 spec E06S6-D9).
     */
    public static final Set<String> ALLOWED_PAYLOAD_KEYS = Set.of(UnitAdvertisingChangedEvent.UNIT_ID,
            ListingChangedEvent.LISTING_ID);

    private static final Pattern CONSUMER_NAME = Pattern.compile("^[a-z][a-z0-9-]{0,99}$");
    private static final Pattern PAYLOAD_VALUE = Pattern.compile("^[0-9A-Za-z-]{1,64}$");

    private final Map<String, Subscription> byType;
    private final List<String> consumers;

    private OutboxSubscriptions(Map<String, Subscription> byType, List<String> consumers) {
        this.byType = byType;
        this.consumers = consumers;
    }

    /**
     * One event type's declared payload keys, its consumers, in delivery order of the list, and the aggregate it is bound
     * to, if any.
     *
     * @param consumers     fixed codes matching {@code ^[a-z][a-z0-9-]{0,99}$}: they name threads, log values and rows
     * @param bindingOrNull the aggregate type and the payload key that equals the aggregate id (listing-publish E06-S7
     *                      spec E06S7-D14, register SV-321 (1)); none for a list built without one
     */
    public record Subscription(Set<String> payloadKeys, List<String> consumers, Binding bindingOrNull) {

        public Subscription {
            payloadKeys = Set.copyOf(payloadKeys);
            consumers = List.copyOf(consumers);
            if (bindingOrNull != null && !payloadKeys.contains(bindingOrNull.idPayloadKey())) {
                throw new IllegalArgumentException("a binding names a payload key its type declares");
            }
        }

        /** A subscription bound to no aggregate: any aggregate type and id are accepted. */
        public Subscription(Set<String> payloadKeys, List<String> consumers) {
            this(payloadKeys, consumers, null);
        }
    }

    /**
     * The aggregate an event type belongs to: its type, and the payload key whose value is the aggregate id (E06S7-D14).
     */
    public record Binding(String aggregateType, String idPayloadKey) {

        public Binding {
            if (aggregateType == null || idPayloadKey == null) {
                throw new IllegalArgumentException("a binding names an aggregate type and a payload key");
            }
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

    /**
     * The list of this release: E05's event with its unit id and the listing's event with its listing id (E06-S6 spec
     * E06S6-D9), each declared with no consumer yet, and each bound to its aggregate (listing-publish E06-S7 spec
     * E06S7-D14): E05's event to (unit, unitId), the listing's to (listing, listingId).
     */
    public static OutboxSubscriptions production() {
        return of(Map.of(UnitAdvertisingChangedEvent.TYPE,
                new Subscription(Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of(),
                        new Binding(UnitAdvertisingChangedEvent.AGGREGATE_TYPE, UnitAdvertisingChangedEvent.UNIT_ID)),
                ListingChangedEvent.TYPE,
                new Subscription(Set.of(ListingChangedEvent.LISTING_ID), List.of(),
                        new Binding(ListingChangedEvent.AGGREGATE_TYPE, ListingChangedEvent.LISTING_ID))));
    }

    /**
     * Refuses an unknown type, a missing or undeclared payload key, a value outside {@code [0-9A-Za-z-]{1,64}}, or, for a
     * bound type, an aggregate type or id that does not match its binding; a refusal names the type and the field, never
     * a value.
     */
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
        Binding binding = subscription.bindingOrNull();
        if (binding == null) {
            return;
        }
        if (!binding.aggregateType().equals(event.aggregateType())) {
            throw new IllegalArgumentException("outbox type " + event.type() + " does not match its aggregateType");
        }
        if (!event.payload().get(binding.idPayloadKey()).equals(event.aggregateId())) {
            throw new IllegalArgumentException("outbox type " + event.type()
                    + " has an aggregateId that differs from the payload key " + binding.idPayloadKey());
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
