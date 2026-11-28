package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.outbox.OutboxEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/** The subscription list and its payload rule (E05-S5b S5b-D3, ADR-014 decisions 3, 4). */
class OutboxSubscriptionsTest {

	private static final String UNIT_ID = "01HZX3M5Q8R2T4V6W8Y0A2C4E6";

	@Test
	void production_unitAdvertisingChanged_unitIdOnlyAndNoConsumer() {
		OutboxSubscriptions production = OutboxSubscriptions.production();

		assertThat(production.consumersOf(UnitAdvertisingChangedEvent.TYPE)).isEmpty();
		assertThat(production.consumers()).isEmpty();
		assertThatCode(() -> production.check(UnitAdvertisingChangedEvent.now(UNIT_ID))).doesNotThrowAnyException();

		assertThatThrownBy(() -> production.check(unitEvent(Map.of("unitId", UNIT_ID, "name", "x"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name");
		assertThatThrownBy(() -> production.check(unitEvent(Map.of())))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> production.check(unitEvent(Map.of("unitId", "01HZX3M5Q8 R2T4V6W8Y0A2C4E6"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageNotContaining("01HZX3M5Q8");
		assertThatThrownBy(() -> production.check(new OutboxEvent("brokerage.unknown", 1, "brokerage", "unit",
				UNIT_ID, Map.of("unitId", UNIT_ID), null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void declaredPayloadKeys_closedListNamesNoPerson() {
		// A new key fails this test until a review shows it names an aggregate that names no person (S5b-D3).
		assertThat(OutboxSubscriptions.ALLOWED_PAYLOAD_KEYS).isEqualTo(Set.of("unitId", "listingId"));

		assertThatThrownBy(() -> OutboxSubscriptions.of(Map.of("test.owner-changed",
				new OutboxSubscriptions.Subscription(Set.of("ownerId"), List.of("test-a")))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ownerId");
	}

	/**
	 * listing-publish E06-S6 spec E06S6-D9: the listing event is declared with its listing id and no consumer, so an
	 * append writes no row until E06-S7 subscribes one.
	 */
	@Test
	void production_listingChanged_acceptedWithoutConsumer() {
		OutboxSubscriptions production = OutboxSubscriptions.production();

		assertThatCode(() -> production.check(ListingChangedEvent.now(UNIT_ID))).doesNotThrowAnyException();
		assertThat(production.consumersOf(ListingChangedEvent.TYPE)).isEmpty();
		assertThat(production.consumers()).isEmpty();
		assertThatThrownBy(() -> production.check(new OutboxEvent(ListingChangedEvent.TYPE, 1, "listing", "listing",
				UNIT_ID, Map.of("unitId", UNIT_ID), null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void of_malformedConsumerName_isRefused() {
		for (String name : new String[] { "Listing", "a b", "a" + "b".repeat(100), "1abc", "" }) {
			assertThatThrownBy(() -> OutboxSubscriptions.of(Map.of("test.sample-event",
					new OutboxSubscriptions.Subscription(Set.of("unitId"), List.of(name)))))
					.as(name)
					.isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> OutboxSubscriptions.of(Map.of("test.sample-event",
				new OutboxSubscriptions.Subscription(Set.of("unitId"), List.of("test-a", "test-a")))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(OutboxSubscriptions.of(Map.of("test.sample-event",
				new OutboxSubscriptions.Subscription(Set.of("unitId"), List.of("a" + "b".repeat(99), "test-a"))))
				.consumers()).containsExactly("a" + "b".repeat(99), "test-a");
	}

	/**
	 * listing-publish E06-S7 spec E06S7-D14, criterion PB-17 (register SV-321 (1)): the production list binds E05's event
	 * to the aggregate (unit, unitId) and the listing event to (listing, listingId); an event whose aggregate type or id
	 * does not match is refused, the message naming the type and the field, never a value.
	 */
	@Test
	void production_aggregateNotMatchingItsBinding_refusedNamingTypeAndFieldOnly() {
		OutboxSubscriptions production = OutboxSubscriptions.production();
		String otherId = "01HZX3M5Q8R2T4V6W8Y0A2C4E7";

		assertThatThrownBy(() -> production.check(new OutboxEvent(UnitAdvertisingChangedEvent.TYPE, 1, "brokerage",
				"listing", UNIT_ID, Map.of("unitId", UNIT_ID), null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(UnitAdvertisingChangedEvent.TYPE)
				.hasMessageContaining("aggregateType")
				.hasMessageNotContaining(UNIT_ID)
				.hasMessageNotContaining(" listing");
		assertThatThrownBy(() -> production.check(new OutboxEvent(UnitAdvertisingChangedEvent.TYPE, 1, "brokerage",
				"unit", otherId, Map.of("unitId", UNIT_ID), null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(UnitAdvertisingChangedEvent.TYPE)
				.hasMessageContaining("aggregateId")
				.hasMessageNotContaining(UNIT_ID)
				.hasMessageNotContaining(otherId);
		assertThatThrownBy(() -> production.check(new OutboxEvent(ListingChangedEvent.TYPE, 1, "listing", "unit",
				UNIT_ID, Map.of("listingId", UNIT_ID), null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(ListingChangedEvent.TYPE)
				.hasMessageContaining("aggregateType")
				.hasMessageNotContaining(UNIT_ID)
				.hasMessageNotContaining(" unit");
		assertThatThrownBy(() -> production.check(new OutboxEvent(ListingChangedEvent.TYPE, 1, "listing", "listing",
				otherId, Map.of("listingId", UNIT_ID), null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(ListingChangedEvent.TYPE)
				.hasMessageContaining("aggregateId")
				.hasMessageNotContaining(UNIT_ID)
				.hasMessageNotContaining(otherId);
	}

	/** E06S7-D14: both factories' events match their binding, the due forms included. */
	@Test
	void production_factoryEvents_matchTheirBinding() {
		OutboxSubscriptions production = OutboxSubscriptions.production();
		java.time.Instant due = java.time.Instant.parse("2026-12-12T17:00:00Z");

		assertThatCode(() -> {
			production.check(UnitAdvertisingChangedEvent.now(UNIT_ID));
			production.check(UnitAdvertisingChangedEvent.dueAt(UNIT_ID, due));
			production.check(ListingChangedEvent.now(UNIT_ID));
			production.check(ListingChangedEvent.dueAt(UNIT_ID, due));
		}).doesNotThrowAnyException();
	}

	/** E06S7-D14: a list built with the two-argument constructor keeps no binding and accepts any aggregate. */
	@Test
	void of_subscriptionWithoutBinding_acceptsAnyAggregate() {
		OutboxSubscriptions list = OutboxSubscriptions.of(Map.of(UnitAdvertisingChangedEvent.TYPE,
				new OutboxSubscriptions.Subscription(Set.of("unitId"), List.of("test-a"))));

		assertThatCode(() -> list.check(new OutboxEvent(UnitAdvertisingChangedEvent.TYPE, 1, "brokerage", "listing",
				"01HZX3M5Q8R2T4V6W8Y0A2C4E7", Map.of("unitId", UNIT_ID), null))).doesNotThrowAnyException();
	}

	private static OutboxEvent unitEvent(Map<String, String> payload) {
		return new OutboxEvent(UnitAdvertisingChangedEvent.TYPE, 1, "brokerage", "unit", UNIT_ID, payload, null);
	}
}
