package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
		assertThat(OutboxSubscriptions.ALLOWED_PAYLOAD_KEYS).isEqualTo(Set.of("unitId"));

		assertThatThrownBy(() -> OutboxSubscriptions.of(Map.of("test.owner-changed",
				new OutboxSubscriptions.Subscription(Set.of("ownerId"), List.of("test-a")))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ownerId");
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

	private static OutboxEvent unitEvent(Map<String, String> payload) {
		return new OutboxEvent(UnitAdvertisingChangedEvent.TYPE, 1, "brokerage", "unit", UNIT_ID, payload, null);
	}
}
