package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.outbox.OutboxEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * Append in the caller's transaction (E05-S5b spec 4.4; S5b-AC-1 to S5b-AC-5): one row per subscribed consumer, none
 * without a subscriber, refused outside a read-write transaction; due times truncated to microseconds and bounded at
 * 800 days. The business write is an insert into a table this class owns. Needs Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventOutboxAdapterPostgresTest extends OutboxPostgresTestBase {

	private EventOutboxAdapter twoConsumers;

	@BeforeAll
	void createBusinessTable() {
		jdbc.execute("CREATE TABLE outbox_test_business_writes (id BIGINT PRIMARY KEY)");
	}

	@AfterAll
	void dropBusinessTable() {
		jdbc.execute("DROP TABLE IF EXISTS outbox_test_business_writes");
	}

	@BeforeEach
	void setUp() {
		jdbc.update("DELETE FROM outbox_test_business_writes");
		clock.set(START);
		twoConsumers = adapter(subscriptions("test-a", "test-b"));
	}

	@Test
	void append_businessTransactionRollsBack_writesNoRow() {
		assertThatThrownBy(() -> inTransaction(status -> {
			jdbc.update("INSERT INTO outbox_test_business_writes (id) VALUES (1)");
			twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID));
			throw new IllegalStateException("the business change fails");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(count("outbox_test_business_writes")).isZero();
		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_businessWriteCommits_rowCommitsWithIt() {
		inTransaction(status -> {
			jdbc.update("INSERT INTO outbox_test_business_writes (id) VALUES (1)");
			twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID));
		});

		assertThat(count("outbox_test_business_writes")).isEqualTo(1);
		assertThat(count("outbox_events")).isEqualTo(2);
	}

	@Test
	void append_twoSubscribers_oneRowPerConsumerOneEventId() {
		Instant withNanos = START.plusNanos(789);
		clock.set(withNanos);
		MDC.put(CorrelationId.MDC_KEY, "corr-00000001");
		try {
			inTransaction(status -> twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID)));
		} finally {
			MDC.remove(CorrelationId.MDC_KEY);
		}

		List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM outbox_events ORDER BY consumer");
		assertThat(rows).hasSize(2);
		assertThat(rows).extracting(row -> row.get("consumer")).containsExactly("test-a", "test-b");
		assertThat(rows).extracting(row -> row.get("event_id")).containsOnly(rows.get(0).get("event_id"));
		assertThat(rows.get(0).get("id")).isNotEqualTo(rows.get(1).get("id"));
		for (Map<String, Object> row : rows) {
			assertThat(row.get("status")).isEqualTo("PENDING");
			assertThat(row.get("attempts")).isEqualTo(0);
			assertThat(row.get("lease_token")).isNull();
			assertThat(row.get("published_at")).isNull();
			assertThat(row.get("last_error_code")).isNull();
			assertThat(instant(row, "occurred_at")).isEqualTo(withNanos.truncatedTo(ChronoUnit.MICROS));
			assertThat(instant(row, "next_attempt_at")).isEqualTo(withNanos.truncatedTo(ChronoUnit.MICROS));
			assertThat(row.get("correlation_id")).isEqualTo("corr-00000001");
		}

		jdbc.update("DELETE FROM outbox_events");
		MDC.put(CorrelationId.MDC_KEY, "bad id");
		try {
			inTransaction(status -> twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID)));
		} finally {
			MDC.remove(CorrelationId.MDC_KEY);
		}
		assertThat(jdbc.queryForList("SELECT correlation_id FROM outbox_events", String.class))
				.hasSize(2).containsOnlyNulls();
	}

	@Test
	void append_noSubscriber_writesNothing() {
		inTransaction(status -> {
			twoConsumers.append(new OutboxEvent(SAMPLE_TYPE, 1, "test", "unit", UNIT_ID, Map.of("unitId", UNIT_ID),
					null));
			// listing-publish E06-S7 spec S-3: E05's event has a production consumer now; the listing event has none
			adapter(OutboxSubscriptions.production()).append(ListingChangedEvent.now(UNIT_ID));
		});

		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_noActiveTransaction_isRefused() {
		assertThatThrownBy(() -> twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID)))
				.isInstanceOf(IllegalStateException.class);

		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_readOnlyTransaction_isRefused() {
		TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
		readOnly.setReadOnly(true);

		assertThatThrownBy(() -> readOnly.executeWithoutResult(
				status -> twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID))))
				.isInstanceOf(IllegalStateException.class);

		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_dueLater_nextAttemptAtIsTheDueTime() {
		Instant due = START.plus(Duration.ofDays(30)).plusNanos(456_789);

		inTransaction(status -> twoConsumers.append(UnitAdvertisingChangedEvent.dueAt(UNIT_ID, due)));

		assertThat(dueTimes()).containsExactly(due.truncatedTo(ChronoUnit.MICROS), due.truncatedTo(ChronoUnit.MICROS));
		assertThat(jdbc.queryForList("SELECT occurred_at FROM outbox_events", Timestamp.class))
				.extracting(Timestamp::toInstant).containsOnly(START);
	}

	@Test
	void append_dueInThePast_isDueNow() {
		inTransaction(status -> {
			twoConsumers.append(UnitAdvertisingChangedEvent.dueAt(UNIT_ID, START.minus(Duration.ofHours(1))));
			twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID));
		});

		assertThat(dueTimes()).hasSize(4).containsOnly(START);
	}

	@Test
	void append_dueAtTheLimit_isAccepted() {
		Instant limit = START.plus(EventOutboxAdapter.MAX_DUE_AHEAD);

		inTransaction(status -> twoConsumers.append(UnitAdvertisingChangedEvent.dueAt(UNIT_ID, limit)));

		assertThat(dueTimes()).containsExactly(limit, limit);
	}

	@Test
	void append_dueBeyond800Days_isRefused() {
		Instant beyond = START.plus(Duration.ofDays(800)).plus(1, ChronoUnit.MICROS);

		assertThatThrownBy(() -> inTransaction(
				status -> twoConsumers.append(UnitAdvertisingChangedEvent.dueAt(UNIT_ID, beyond))))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_unitAdvertisingChanged_storesTheUnitIdOnly() {
		inTransaction(status -> adapter(subscriptions("test-a")).append(UnitAdvertisingChangedEvent.now(UNIT_ID)));

		Map<String, Object> row = jdbc.queryForMap("SELECT event_type, event_version, source_module, aggregate_type,"
				+ " aggregate_id, payload = CAST(? AS jsonb) AS exact_payload FROM outbox_events",
				"{\"unitId\":\"" + UNIT_ID + "\"}");
		assertThat(row.get("event_type")).isEqualTo("brokerage.unit-advertising-changed");
		assertThat(row.get("event_version")).isEqualTo(1);
		assertThat(row.get("source_module")).isEqualTo("brokerage");
		assertThat(row.get("aggregate_type")).isEqualTo("unit");
		assertThat(row.get("aggregate_id")).isEqualTo(UNIT_ID);
		assertThat(row.get("exact_payload")).isEqualTo(true);
	}

	@Test
	void append_undeclaredOrMissingKey_isRefused() {
		for (EventOutboxAdapter adapter : List.of(twoConsumers, adapter(OutboxSubscriptions.production()))) {
			for (Map<String, String> payload : List.of(Map.of("unitId", UNIT_ID, "phone", "0905123456"),
					Map.<String, String>of())) {
				assertThatThrownBy(() -> inTransaction(status -> adapter.append(e05Event(payload))))
						.isInstanceOf(IllegalArgumentException.class);
			}
		}

		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_malformedValue_isRefused() {
		for (EventOutboxAdapter adapter : List.of(twoConsumers, adapter(OutboxSubscriptions.production()))) {
			for (String value : List.of("a b", "x".repeat(65), "", "nguyễn")) {
				assertThatThrownBy(() -> inTransaction(status -> adapter.append(e05Event(Map.of("unitId", value)))))
						.as(value)
						.isInstanceOf(IllegalArgumentException.class);
			}
		}

		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_unknownType_isRefused() {
		OutboxEvent unknown = new OutboxEvent("test.unknown-event", 1, "test", "unit", UNIT_ID,
				Map.of("unitId", UNIT_ID), null);

		for (EventOutboxAdapter adapter : List.of(twoConsumers, adapter(OutboxSubscriptions.production()))) {
			assertThatThrownBy(() -> inTransaction(status -> adapter.append(unknown)))
					.isInstanceOf(IllegalArgumentException.class);
		}

		assertThat(count("outbox_events")).isZero();
	}

	@Test
	void append_twiceInOneTransaction_twoEvents() {
		inTransaction(status -> {
			twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID));
			twoConsumers.append(UnitAdvertisingChangedEvent.now(UNIT_ID));
		});

		assertThat(jdbc.queryForList("SELECT id FROM outbox_events", Long.class)).hasSize(4).doesNotHaveDuplicates();
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT event_id) FROM outbox_events", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForList("SELECT consumer FROM outbox_events WHERE event_id = (SELECT min(event_id)"
				+ " FROM outbox_events) ORDER BY consumer", String.class)).containsExactly("test-a", "test-b");
	}

	private static OutboxEvent e05Event(Map<String, String> payload) {
		return new OutboxEvent(UnitAdvertisingChangedEvent.TYPE, 1, "brokerage", "unit", UNIT_ID, payload, null);
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private List<Instant> dueTimes() {
		return jdbc.queryForList("SELECT next_attempt_at FROM outbox_events ORDER BY id", Timestamp.class).stream()
				.map(Timestamp::toInstant).toList();
	}

	private static Instant instant(Map<String, Object> row, String column) {
		return ((Timestamp) row.get(column)).toInstant();
	}
}
