package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.sunvibe.outbox.OutboxConsumer.Delivery;
import vn.sunvibe.outbox.OutboxRetryPolicy;
import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * Listing's consumer of E05's event (listing-publish E06-S7 spec E06S7-D13; criterion PB-16): a row of E05's event calls
 * the published use case once with its unit; a row whose type, aggregate type, aggregate id or payload is not E05's
 * throws, naming no value, and calls nothing (retried, then DEAD under the default policy). Each wrong row has exactly
 * one wrong part. A recording use case, no Spring.
 */
class ListingUnitAdvertisingAdapterTest {

	private static final String UNIT = "01J000000000000000000000N1";
	private static final String OTHER_UNIT = "01J000000000000000000000N2";

	private final List<String> signalled = new ArrayList<>();
	private final ListingUnitAdvertisingAdapter adapter = new ListingUnitAdvertisingAdapter(signalled::add);

	@Test
	void deliver_unitAdvertisingRow_signalsItsUnitOnce() {
		adapter.deliver(row(UnitAdvertisingChangedEvent.TYPE, "unit", UNIT, Map.of("unitId", UNIT)));

		assertEquals(List.of(UNIT), signalled);
	}

	@Test
	void deliver_wrongType_throwsSignalsNothing() {
		assertRefused(row(ListingChangedEvent.TYPE, "unit", UNIT, Map.of("unitId", UNIT)));
	}

	@Test
	void deliver_wrongAggregateType_throwsSignalsNothing() {
		assertRefused(row(UnitAdvertisingChangedEvent.TYPE, "listing", UNIT, Map.of("unitId", UNIT)));
	}

	@Test
	void deliver_aggregateIdNotTheUnit_throwsSignalsNothing() {
		assertRefused(row(UnitAdvertisingChangedEvent.TYPE, "unit", OTHER_UNIT, Map.of("unitId", UNIT)));
	}

	@Test
	void deliver_wrongPayload_throwsSignalsNothing() {
		assertRefused(row(UnitAdvertisingChangedEvent.TYPE, "unit", UNIT, Map.of("unitId", UNIT, "listingId", UNIT)));
	}

	@Test
	void nameAndPolicy_ofTheSubscription() {
		assertEquals(List.of("listing-unit-advertising", "listing-unit-advertising", "DEFAULT"),
				List.of(ListingUnitAdvertisingAdapter.NAME, adapter.name(), adapter.retryPolicy().name()));
		assertEquals(OutboxRetryPolicy.DEFAULT, adapter.retryPolicy());
	}

	private void assertRefused(Delivery delivery) {
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> adapter.deliver(delivery));

		assertEquals(List.of(), signalled);
		assertFalse(refused.getMessage().contains(UNIT) || refused.getMessage().contains(OTHER_UNIT),
				refused.getMessage());
	}

	private static Delivery row(String type, String aggregateType, String aggregateId, Map<String, String> payload) {
		return new Delivery(7L, type, 1, aggregateType, aggregateId, payload, Instant.parse("2026-11-28T03:00:00Z"),
				"corr-00000001", 1);
	}
}
