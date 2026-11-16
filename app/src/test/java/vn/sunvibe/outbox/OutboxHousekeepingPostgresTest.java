package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.shared.outbox.OutboxEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The status run and the clean-up (E05-S5b spec 4.7, 4.8; S5b-D7, S5b-D8, S5b-AC-5, S5b-AC-8, S5b-AC-10, S5b-AC-11,
 * S5b-AC-18 (d)). Fixture rows are inserted with times from the test clock, never SQL {@code now()}. Needs Docker.
 */
class OutboxHousekeepingPostgresTest extends OutboxPostgresTestBase {

	private static final long BOUND_SECONDS = 10;
	private static final Duration ONE_SECOND = Duration.ofSeconds(1);

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger workerLog = (Logger) LoggerFactory.getLogger(OutboxWorker.class);
	private final List<OutboxScheduling> schedulings = new ArrayList<>();
	private final List<CountDownLatch> latches = new ArrayList<>();

	@BeforeEach
	void captureLogs() {
		clock.set(START);
		logs.start();
		workerLog.addAppender(logs);
	}

	@AfterEach
	void releaseEverything() {
		workerLog.detachAppender(logs);
		latches.forEach(CountDownLatch::countDown);
		schedulings.forEach(OutboxScheduling::destroy);
	}

	@Test
	void cleanUp_oldDone_deleted_othersKept() {
		long oldDone = insertRow("x", "DONE", START.minus(Duration.ofDays(8)), START.minus(Duration.ofDays(8)), null);
		long recentDone = insertRow("x", "DONE", START.minus(Duration.ofDays(6)), START.minus(Duration.ofDays(6)), null);
		long dueLater = insertRow("x", "PENDING", START.plus(Duration.ofDays(700)), null, null);
		long dead = insertRow("x", "DEAD", START.minus(Duration.ofDays(30)), null, "CONSUMER_ERROR");

		worker("x").cleanUp();

		assertThat(jdbc.queryForList("SELECT id FROM outbox_events", Long.class))
				.containsExactlyInAnyOrder(recentDone, dueLater, dead)
				.doesNotContain(oldDone);
	}

	@Test
	void cleanUp_manyRows_boundedPerRun() {
		OffsetDateTime old = time(START.minus(Duration.ofDays(8)));
		jdbc.update("INSERT INTO outbox_events (id, event_id, event_type, event_version, consumer, source_module,"
				+ " aggregate_type, aggregate_id, payload, occurred_at, status, attempts, next_attempt_at, published_at)"
				+ " SELECT g, g, 'brokerage.unit-advertising-changed', 1, 'x', 'brokerage', 'unit', 'u' || g,"
				+ " '{\"unitId\":\"u\"}'::jsonb, ?, 'DONE', 1, ?, ? FROM generate_series(1, 21500) AS g", old, old, old);
		OutboxWorker worker = worker("x");

		worker.cleanUp();
		assertThat(count()).as("one run deletes 20 statements of 1,000").isEqualTo(1_500);

		worker.cleanUp();
		assertThat(count()).isZero();
	}

	@Test
	void report_queueAge_dueRowsOnly() {
		insertRow("x", "PENDING", START.plus(Duration.ofDays(30)), null, null);
		insertLeasedRow("x", START.plus(Duration.ofSeconds(130)));
		insertRow("x", "PENDING", START.minus(Duration.ofMinutes(2)), null, null);

		worker("x").report();

		assertThat(statusLine()).contains(" due=1 ", " oldestDueAgeSeconds=120 ");
		assertThat(lines("OUTBOX_QUEUE_STALE")).isEmpty();
	}

	@Test
	void report_deadRows_alertLine() {
		OutboxWorker worker = worker("x");
		worker.report();
		assertThat(lines("OUTBOX_DEAD_ROWS")).isEmpty();
		assertThat(statusLine()).contains(" dead=0 ");

		insertRow("x", "DEAD", START.minus(Duration.ofDays(1)), null, "CONSUMER_ERROR");
		logs.list.clear();
		worker.report();

		assertThat(statusLine()).contains(" dead=1 ");
		assertThat(lines("OUTBOX_DEAD_ROWS")).singleElement().satisfies(line -> {
			assertThat(line.getLevel()).isEqualTo(Level.ERROR);
			assertThat(line.getFormattedMessage()).isEqualTo("OUTBOX_DEAD_ROWS dead=1");
		});
	}

	@Test
	void report_staleQueue_alertLine() {
		long row = insertRow("x", "PENDING", START.minus(Duration.ofMinutes(4)), null, null);
		OutboxWorker worker = worker("x");
		worker.report();
		assertThat(lines("OUTBOX_QUEUE_STALE")).isEmpty();

		jdbc.update("UPDATE outbox_events SET next_attempt_at = ? WHERE id = ?", time(START.minus(Duration.ofMinutes(6))),
				row);
		logs.list.clear();
		worker.report();

		assertThat(lines("OUTBOX_QUEUE_STALE")).singleElement().satisfies(line -> {
			assertThat(line.getLevel()).isEqualTo(Level.ERROR);
			assertThat(line.getFormattedMessage()).isEqualTo("OUTBOX_QUEUE_STALE oldestDueAgeSeconds=360 due=1");
		});
	}

	@Test
	void report_removalFailures_counterAndRetryingRemovalCount() {
		TestConsumer x = new TestConsumer("x", OutboxRetryPolicy.REMOVAL);
		TestConsumer y = new TestConsumer("y");
		x.behaviour = delivery -> {
			throw new IllegalStateException("down");
		};
		y.behaviour = x.behaviour;
		appendEvents(adapter(subscriptions("x", "y")), 1);
		OutboxWorker worker = worker(subscriptions("x", "y"), scheduling("x", "y"), limits(ONE_SECOND), x, y);
		worker.deliverConsumer("x");
		worker.deliverConsumer("y");

		worker.report();

		assertThat(statusLine()).contains(" retrying=2 ", " retryingRemoval=1 ", " removalFailures=1 ", " retried=2 ");
	}

	@Test
	void report_unknownConsumerRowDueFiveMinutes_deadWithNoConsumer() {
		long row = insertRow("gone", "PENDING", START.minus(Duration.ofMinutes(5)).minusSeconds(1), null, null);
		OutboxWorker worker = worker(OutboxSubscriptions.production(), scheduling(), limits(ONE_SECOND));

		worker.report();

		Map<String, Object> dead = jdbc.queryForMap("SELECT * FROM outbox_events WHERE id = ?", row);
		assertThat(dead.get("status")).isEqualTo("DEAD");
		assertThat(dead.get("last_error_code")).isEqualTo("NO_CONSUMER");
		assertThat(dead.get("lease_token")).isNull();
		assertThat(lines("OUTBOX_DEAD")).singleElement().satisfies(line -> {
			assertThat(line.getLevel()).isEqualTo(Level.ERROR);
			assertThat(line.getFormattedMessage()).contains("rowId=" + row, "consumer=gone", "errorCode=NO_CONSUMER");
		});
		assertThat(worker.deadCount()).isEqualTo(1);
	}

	@Test
	void report_unknownConsumerRowRecentlyDue_kept() {
		long row = insertRow("gone", "PENDING", START.minus(Duration.ofMinutes(4)), null, null);

		worker(OutboxSubscriptions.production(), scheduling(), limits(ONE_SECOND)).report();

		assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String.class, row))
				.isEqualTo("PENDING");
		assertThat(lines("OUTBOX_DEAD")).isEmpty();
	}

	@Test
	void report_lines_carryNoPayloadOrAggregateId() {
		insertRow("x", "DEAD", START.minus(Duration.ofDays(1)), null, "CONSUMER_ERROR");
		insertRow("x", "PENDING", START.minus(Duration.ofMinutes(10)), null, "DELIVERY_TIMEOUT");
		insertRow("gone", "PENDING", START.minus(Duration.ofMinutes(10)), null, null);
		OutboxWorker worker = worker("x");

		worker.report();

		assertThat(codes()).contains("OUTBOX_DEAD", "OUTBOX_STATUS", "OUTBOX_DEAD_ROWS", "OUTBOX_QUEUE_STALE");
		for (ILoggingEvent line : logs.list) {
			assertThat(line.getFormattedMessage()).doesNotContain(UNIT_ID).doesNotContain("unitId");
			assertThat(line.getThrowableProxy()).isNull();
		}
	}

	@Test
	void report_deliveryOutlivingItsRun_namesTheConsumer() throws Exception {
		TestConsumer x = new TestConsumer("x");
		CountDownLatch release = new CountDownLatch(1);
		latches.add(release);
		x.behaviour = delivery -> OutboxSchedulingConfigTest.awaitIgnoringInterrupts(release);
		CountDownLatch afterExecute = new CountDownLatch(1);
		OutboxScheduling scheduling = new OutboxScheduling(List.of("x"),
				OutboxSchedulingConfigTest.deliveryOf("x", afterExecute));
		schedulings.add(scheduling);
		OutboxSubscriptions sampleToX = OutboxSubscriptions.of(Map.of(SAMPLE_TYPE,
				new OutboxSubscriptions.Subscription(Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of("x"))));
		inTransaction(status -> adapter(sampleToX).append(
				new OutboxEvent(SAMPLE_TYPE, 1, "test", "unit", UNIT_ID, Map.of("unitId", UNIT_ID), null)));
		OutboxWorker worker = worker(sampleToX, scheduling, limits(ONE_SECOND), x);

		worker.deliverConsumer("x");
		worker.report();

		assertThat(statusLine()).endsWith(" deliveryOutlivedTimeout=[x]");

		release.countDown();
		assertThat(afterExecute.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
		logs.list.clear();
		worker.report();

		assertThat(statusLine()).endsWith(" deliveryOutlivedTimeout=[]");
	}

	private OutboxWorker worker(String... consumers) {
		List<OutboxConsumer> beans = new ArrayList<>();
		for (String name : consumers) {
			beans.add(new TestConsumer(name));
		}
		return worker(subscriptions(consumers), scheduling(consumers), limits(ONE_SECOND),
				beans.toArray(OutboxConsumer[]::new));
	}

	private OutboxScheduling scheduling(String... consumers) {
		OutboxScheduling scheduling = new OutboxScheduling(List.of(consumers));
		schedulings.add(scheduling);
		return scheduling;
	}

	private long insertRow(String consumer, String status, Instant nextAttemptAt, Instant publishedAt,
			String lastErrorCode) {
		long id = snowflake.generateId();
		jdbc.update("INSERT INTO outbox_events (id, event_id, event_type, event_version, consumer, source_module,"
				+ " aggregate_type, aggregate_id, payload, occurred_at, status, attempts, next_attempt_at, published_at,"
				+ " last_error_code) VALUES (?, ?, ?, 1, ?, 'brokerage', 'unit', ?, CAST(? AS jsonb), ?, ?, 1, ?, ?, ?)",
				id, id, UnitAdvertisingChangedEvent.TYPE, consumer, UNIT_ID, "{\"unitId\":\"" + UNIT_ID + "\"}",
				time(START.minus(Duration.ofDays(30))), status, time(nextAttemptAt),
				publishedAt == null ? null : time(publishedAt), lastErrorCode);
		return id;
	}

	private void insertLeasedRow(String consumer, Instant leaseEnd) {
		long id = insertRow(consumer, "PENDING", leaseEnd, null, null);
		jdbc.update("UPDATE outbox_events SET lease_token = 'a-lease-token' WHERE id = ?", id);
	}

	private int count() {
		return jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
	}

	private String statusLine() {
		List<ILoggingEvent> status = lines("OUTBOX_STATUS");
		assertThat(status).hasSize(1);
		assertThat(status.get(0).getLevel()).isEqualTo(Level.INFO);
		return status.get(0).getFormattedMessage();
	}

	private List<ILoggingEvent> lines(String code) {
		return logs.list.stream().filter(line -> line.getFormattedMessage().startsWith(code + " ")).toList();
	}

	private List<String> codes() {
		return logs.list.stream().map(line -> line.getFormattedMessage().split(" ", 2)[0]).toList();
	}

	private static OffsetDateTime time(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
