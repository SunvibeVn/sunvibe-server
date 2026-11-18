package vn.sunvibe.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The outbox's PostgreSQL tests (E05-S5b plan E05S5b-P1). The class annotations are FlywayPostgresMigrationTest's as
 * written, so these classes share its context and its database; every {@code outbox_events} row is deleted before and
 * after each test, so that test's "no row" assertion holds in any class order. Time comes from a {@link MutableClock}.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
abstract class OutboxPostgresTestBase {

	static final Instant START = Instant.parse("2026-11-16T08:00:00.123456Z");
	static final String UNIT_ID = "01HZX3M5Q8R2T4V6W8Y0A2C4E6";
	static final String SAMPLE_TYPE = "test.sample-event";

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected EntityManager entityManager;

	@Autowired
	@Qualifier("Snowflake")
	protected IdGenerator<Long> snowflake;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected PlatformTransactionManager transactionManager;

	protected final MutableClock clock = new MutableClock(START);

	@BeforeEach
	void deleteRowsBefore() {
		jdbc.update("DELETE FROM outbox_events");
	}

	@AfterEach
	void deleteRowsAfter() {
		jdbc.update("DELETE FROM outbox_events");
	}

	/** E05's type delivered to {@code consumers}, and {@link #SAMPLE_TYPE} with the same key and no consumer. */
	static OutboxSubscriptions subscriptions(String... consumers) {
		return OutboxSubscriptions.of(Map.of(
				UnitAdvertisingChangedEvent.TYPE,
				new OutboxSubscriptions.Subscription(Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of(consumers)),
				SAMPLE_TYPE,
				new OutboxSubscriptions.Subscription(Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of())));
	}

	EventOutboxAdapter adapter(OutboxSubscriptions subscriptions) {
		return new EventOutboxAdapter(entityManager, snowflake, subscriptions, objectMapper, clock);
	}

	/** Batch 10, the given delivery timeout, margin 5 s, DONE kept 7 days, queue-age alert 5 minutes. */
	static OutboxWorker.Limits limits(Duration timeout) {
		return new OutboxWorker.Limits(10, timeout, Duration.ofSeconds(5), Duration.ofDays(7), Duration.ofMinutes(5));
	}

	OutboxWorker worker(OutboxSubscriptions subscriptions, OutboxScheduling scheduling, OutboxWorker.Limits limits,
			OutboxConsumer... consumers) {
		Map<String, OutboxConsumer> byName = new TreeMap<>();
		for (OutboxConsumer consumer : consumers) {
			byName.put(consumer.name(), consumer);
		}
		return new OutboxWorker(jdbc, objectMapper, subscriptions, byName, scheduling, clock, limits);
	}

	/** Appends {@code count} E05 events, one unit id each, in one transaction; returns the unit ids in order. */
	List<String> appendEvents(EventOutboxAdapter adapter, int count) {
		List<String> unitIds = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			unitIds.add(unitId(i));
		}
		inTransaction(status -> unitIds.forEach(id -> adapter.append(UnitAdvertisingChangedEvent.now(id))));
		return unitIds;
	}

	/** A ULID-shaped unit id, distinct per {@code n} below 32^4. */
	static String unitId(int n) {
		String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
		StringBuilder suffix = new StringBuilder();
		int rest = n;
		for (int i = 0; i < 4; i++) {
			suffix.insert(0, alphabet.charAt(rest % 32));
			rest /= 32;
		}
		return "01HZX3M5Q8R2T4V6W8Y0A2" + suffix;
	}

	/** A test consumer: records each delivery, its thread and whether a transaction was open, then runs its behaviour. */
	static final class TestConsumer implements OutboxConsumer {

		final String name;
		final OutboxRetryPolicy policy;
		final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
		final List<String> threads = new CopyOnWriteArrayList<>();
		final List<Boolean> transactionActive = new CopyOnWriteArrayList<>();
		volatile Consumer<Delivery> behaviour = delivery -> { };

		TestConsumer(String name, OutboxRetryPolicy policy) {
			this.name = name;
			this.policy = policy;
		}

		TestConsumer(String name) {
			this(name, OutboxRetryPolicy.DEFAULT);
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public OutboxRetryPolicy retryPolicy() {
			return policy;
		}

		@Override
		public void deliver(Delivery delivery) {
			deliveries.add(delivery);
			threads.add(Thread.currentThread().getName());
			transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
			behaviour.accept(delivery);
		}
	}

	void inTransaction(Consumer<TransactionStatus> work) {
		new TransactionTemplate(transactionManager).executeWithoutResult(work);
	}

	/** A clock the test moves; UTC. */
	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> now;

		MutableClock(Instant start) {
			this.now = new AtomicReference<>(start);
		}

		void set(Instant instant) {
			now.set(instant);
		}

		void advance(Duration duration) {
			now.updateAndGet(current -> current.plus(duration));
		}

		@Override
		public Instant instant() {
			return now.get();
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException("the outbox clock is UTC");
		}
	}
}
