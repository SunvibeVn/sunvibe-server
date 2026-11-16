package vn.sunvibe.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
