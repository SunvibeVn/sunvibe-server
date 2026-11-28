package vn.sunvibe.integration;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import vn.sunvibe.listing.core.application.usecase.ListingSignalUseCase;
import vn.sunvibe.outbox.OutboxConsumer;
import vn.sunvibe.outbox.OutboxRetryPolicy;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * Listing's consumer of E05's event (listing-publish E06-S7 spec E06S7-D13; ADR-012 decision 6, ADR-014 decisions 6 to
 * 8): a row of {@code brokerage.unit-advertising-changed} for a unit calls listing's published use case, which signals
 * every published listing of that unit to be read again. A row that is not E05's (its type, its aggregate type
 * {@code unit}, its aggregate id a ULID equal to the payload's {@code unitId}, its payload that one key) throws, naming
 * no value, so the outbox retries it and then marks it {@code DEAD} with its alert. Not a removal consumer: the
 * default retry policy. Called on the outbox's delivery thread with no transaction open; the use case opens its own.
 */
@Component
public class ListingUnitAdvertisingAdapter implements OutboxConsumer {

    /** The fixed name of this consumer, as the production subscription list writes it. */
    public static final String NAME = "listing-unit-advertising";

    private static final Pattern ULID = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    private final ListingSignalUseCase signals;

    public ListingUnitAdvertisingAdapter(ListingSignalUseCase signals) {
        this.signals = signals;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public OutboxRetryPolicy retryPolicy() {
        return OutboxRetryPolicy.DEFAULT;
    }

    @Override
    public void deliver(Delivery delivery) {
        if (!UnitAdvertisingChangedEvent.TYPE.equals(delivery.eventType())) {
            throw new IllegalArgumentException(NAME + " receives only the unit-advertising event");
        }
        if (!UnitAdvertisingChangedEvent.AGGREGATE_TYPE.equals(delivery.aggregateType())) {
            throw new IllegalArgumentException(NAME + " received a row whose aggregate is not a unit");
        }
        Map<String, String> payload = delivery.payload();
        if (payload == null || payload.size() != 1 || !payload.containsKey(UnitAdvertisingChangedEvent.UNIT_ID)) {
            throw new IllegalArgumentException(NAME + " received a payload other than the unit id");
        }
        String unitId = payload.get(UnitAdvertisingChangedEvent.UNIT_ID);
        String aggregateId = delivery.aggregateId();
        if (aggregateId == null || !ULID.matcher(aggregateId).matches() || !aggregateId.equals(unitId)) {
            throw new IllegalArgumentException(NAME + " received an aggregate id that is not the payload's unit id");
        }
        signals.signalUnit(unitId);
    }
}
