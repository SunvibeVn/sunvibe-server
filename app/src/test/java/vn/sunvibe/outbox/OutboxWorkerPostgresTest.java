package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The outbox worker on PostgreSQL (E05-S5b spec 4.3, 4.5, 4.6; S5b-AC-5 to S5b-AC-9, S5b-AC-18). One consumer's run is
 * called with {@code deliverConsumer} on the test thread; its deliveries run on the consumer's delivery thread. Every
 * blocking consumer waits on a latch the test opens in its tear-down, and every wait is bounded. Needs Docker.
 */
class OutboxWorkerPostgresTest extends OutboxPostgresTestBase {

	private static final long BOUND_SECONDS = 10;
	private static final Duration ONE_SECOND = Duration.ofSeconds(1);

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger workerLog = (Logger) LoggerFactory.getLogger(OutboxWorker.class);
	private final List<CountDownLatch> latches = new ArrayList<>();
	private final List<OutboxScheduling> schedulings = new ArrayList<>();
	private final ExecutorService testThreads = Executors.newCachedThreadPool();

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
		testThreads.shutdownNow();
	}

	// One consumer's run

	@Test
	void deliverConsumer_success_marksDone() {
		TestConsumer x = new TestConsumer("x");
		MDC.put(CorrelationId.MDC_KEY, "corr-00000001");
		try {
			inTransaction(status -> adapter(subscriptions("x")).append(UnitAdvertisingChangedEvent.now(UNIT_ID)));
		} finally {
			MDC.remove(CorrelationId.MDC_KEY);
		}
		long eventId = jdbc.queryForObject("SELECT event_id FROM outbox_events", Long.class);
		clock.advance(Duration.ofSeconds(3));
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x);

		worker.deliverConsumer("x");

		Map<String, Object> row = onlyRow();
		assertThat(row.get("status")).isEqualTo("DONE");
		assertThat(instant(row, "published_at")).isEqualTo(START.plusSeconds(3));
		assertThat(row.get("lease_token")).isNull();
		assertThat(row.get("last_error_code")).isNull();
		assertThat(x.deliveries).containsExactly(new OutboxConsumer.Delivery(eventId, UnitAdvertisingChangedEvent.TYPE,
				1, "unit", UNIT_ID, Map.of("unitId", UNIT_ID), START, "corr-00000001", 1));
		assertThat(worker.deliveredCount()).isEqualTo(1);
	}

	@Test
	void deliverConsumer_consumerRunsWithoutTransaction() {
		TestConsumer x = new TestConsumer("x");
		appendEvents(adapter(subscriptions("x")), 1);

		worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x).deliverConsumer("x");

		assertThat(x.transactionActive).containsExactly(false);
		assertThat(x.threads).containsExactly("outbox-delivery-x");
	}

	@Test
	void deliverConsumer_notYetDue_notClaimed() {
		TestConsumer x = new TestConsumer("x");
		Instant due = START.plus(Duration.ofHours(1));
		inTransaction(status -> adapter(subscriptions("x")).append(UnitAdvertisingChangedEvent.dueAt(UNIT_ID, due)));
		clock.set(due.minusSeconds(1));

		worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x).deliverConsumer("x");

		Map<String, Object> row = onlyRow();
		assertThat(x.deliveries).isEmpty();
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("attempts")).isEqualTo(0);
		assertThat(row.get("lease_token")).isNull();
		assertThat(instant(row, "next_attempt_at")).isEqualTo(due);
	}

	@Test
	void deliverConsumer_atDueTime_delivered() {
		TestConsumer x = new TestConsumer("x");
		Instant due = START.plus(Duration.ofHours(1));
		inTransaction(status -> adapter(subscriptions("x")).append(UnitAdvertisingChangedEvent.dueAt(UNIT_ID, due)));
		clock.set(due);

		worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x).deliverConsumer("x");

		assertThat(x.deliveries).hasSize(1);
		assertThat(onlyRow().get("status")).isEqualTo("DONE");
	}

	@Test
	void deliverConsumer_consumerThrows_retriesAtBackoffAndGoesOn() {
		TestConsumer x = new TestConsumer("x");
		List<String> unitIds = appendEvents(adapter(subscriptions("x")), 3);
		String failing = unitIds.get(0);
		x.behaviour = delivery -> {
			if (delivery.aggregateId().equals(failing)) {
				throw new IllegalStateException("cannot read unit " + failing);
			}
		};
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x);

		worker.deliverConsumer("x");

		Map<String, Object> failed = rowOf(failing);
		assertThat(failed.get("status")).isEqualTo("PENDING");
		assertThat(failed.get("lease_token")).isNull();
		assertThat(failed.get("last_error_code")).isEqualTo("CONSUMER_ERROR");
		assertThat(instant(failed, "next_attempt_at")).isEqualTo(START.plus(Duration.ofMinutes(1)));
		assertThat(rowOf(unitIds.get(1)).get("status")).isEqualTo("DONE");
		assertThat(rowOf(unitIds.get(2)).get("status")).isEqualTo("DONE");
		List<ILoggingEvent> retries = lines("OUTBOX_DELIVERY_RETRY");
		assertThat(retries).hasSize(1);
		assertThat(retries.get(0).getLevel()).isEqualTo(Level.WARN);
		assertThat(retries.get(0).getFormattedMessage()).contains("errorCode=CONSUMER_ERROR", "attempt=1",
				"exception=java.lang.IllegalStateException");
		assertNoDataInLogs(failing, "cannot read");
		assertThat(worker.retriedCount()).isEqualTo(1);
		assertThat(worker.deliveredCount()).isEqualTo(2);
	}

	@Test
	void deliverConsumer_tenthFailure_deadAndAlert() {
		TestConsumer x = new TestConsumer("x");
		appendEvents(adapter(subscriptions("x")), 1);
		jdbc.update("UPDATE outbox_events SET attempts = 9, last_error_code = 'CONSUMER_ERROR'");
		x.behaviour = delivery -> {
			throw new IllegalStateException("down");
		};
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x);

		worker.deliverConsumer("x");

		Map<String, Object> row = onlyRow();
		assertThat(row.get("status")).isEqualTo("DEAD");
		assertThat(row.get("last_error_code")).isEqualTo("CONSUMER_ERROR");
		assertThat(row.get("lease_token")).isNull();
		assertThat(row.get("attempts")).isEqualTo(10);
		List<ILoggingEvent> dead = lines("OUTBOX_DEAD");
		assertThat(dead).hasSize(1);
		assertThat(dead.get(0).getLevel()).isEqualTo(Level.ERROR);
		assertThat(dead.get(0).getFormattedMessage()).contains("attempt=10", "errorCode=CONSUMER_ERROR");
		assertThat(lines("OUTBOX_DELIVERY_RETRY")).isEmpty();
		assertThat(worker.deadCount()).isEqualTo(1);
	}

	@Test
	void deliverConsumer_timeout_retriesWithTimeoutCode() {
		TestConsumer x = new TestConsumer("x");
		CountDownLatch release = latch();
		x.behaviour = delivery -> awaitInterruptibly(release);
		appendEvents(adapter(subscriptions("x")), 1);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x);

		worker.deliverConsumer("x");

		Map<String, Object> row = onlyRow();
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("last_error_code")).isEqualTo("DELIVERY_TIMEOUT");
		assertThat(row.get("lease_token")).isNull();
		assertThat(instant(row, "next_attempt_at")).isEqualTo(START.plus(Duration.ofMinutes(1)));
		assertThat(worker.timeoutCount()).isEqualTo(1);
		assertThat(lines("OUTBOX_DELIVERY_RETRY")).singleElement()
				.extracting(ILoggingEvent::getFormattedMessage).asString()
				.contains("errorCode=DELIVERY_TIMEOUT").doesNotContain("exception=");
	}

	@Test
	void deliverConsumer_timeout_releasesTheRestOfItsClaim() {
		TestConsumer x = new TestConsumer("x");
		CountDownLatch release = latch();
		x.behaviour = delivery -> awaitInterruptibly(release);
		appendEvents(adapter(subscriptions("x")), 10);
		Instant runAt = START.plusSeconds(10);
		clock.set(runAt);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x);

		worker.deliverConsumer("x");

		assertThat(x.deliveries).hasSize(1);
		List<Map<String, Object>> rows = rowsById();
		assertThat(rows.get(0).get("last_error_code")).isEqualTo("DELIVERY_TIMEOUT");
		assertThat(rows.get(0).get("attempts")).isEqualTo(1);
		for (Map<String, Object> released : rows.subList(1, 10)) {
			assertThat(released.get("status")).isEqualTo("PENDING");
			assertThat(released.get("lease_token")).isNull();
			assertThat(released.get("attempts")).isEqualTo(0);
			assertThat(released.get("last_error_code")).isNull();
			assertThat(instant(released, "next_attempt_at")).isEqualTo(runAt);
		}
		assertThat(worker.releasedCount()).isEqualTo(9);
	}

	@Test
	void deliverConsumer_releaseOfALostLease_countedAndLogged() {
		TestConsumer x = new TestConsumer("x");
		CountDownLatch release = latch();
		appendEvents(adapter(subscriptions("x")), 10);
		OutboxWorker.Limits limits = limits(ONE_SECOND);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits, x);
		AtomicReference<List<OutboxWorker.ClaimedRow>> reclaimed = new AtomicReference<>();
		x.behaviour = delivery -> {
			clock.advance(limits.lease().plusSeconds(1));
			reclaimed.set(worker.claim("x", clock.instant()));
			awaitInterruptibly(release);
		};

		worker.deliverConsumer("x");

		assertThat(reclaimed.get()).hasSize(10);
		String secondToken = reclaimed.get().get(0).token();
		assertThat(jdbc.queryForList("SELECT lease_token FROM outbox_events", String.class))
				.hasSize(10).containsOnly(secondToken);
		assertThat(lines("OUTBOX_LEASE_LOST")).hasSize(10);
		assertThat(worker.lostLeaseCount()).isEqualTo(10);
		assertThat(worker.releasedCount()).isZero();
		assertThat(worker.timeoutCount()).isEqualTo(1);
	}

	@Test
	void deliverConsumer_removalPolicy_noLimitFirstFailureAlerts() {
		TestConsumer x = new TestConsumer("x", OutboxRetryPolicy.REMOVAL);
		x.behaviour = delivery -> {
			throw new IllegalStateException("down");
		};
		appendEvents(adapter(subscriptions("x")), 1);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x);

		for (int run = 0; run < 12; run++) {
			worker.deliverConsumer("x");

			Map<String, Object> row = onlyRow();
			assertThat(row.get("status")).as("run %d", run).isEqualTo("PENDING");
			assertThat(instant(row, "next_attempt_at")).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
			clock.advance(Duration.ofMinutes(5));
		}

		assertThat(x.deliveries).hasSize(12);
		assertThat(onlyRow().get("attempts")).isEqualTo(12);
		assertThat(lines("OUTBOX_REMOVAL_FAILED")).singleElement()
				.satisfies(line -> assertThat(line.getLevel()).isEqualTo(Level.ERROR));
		assertThat(lines("OUTBOX_DELIVERY_RETRY")).hasSize(11);
		assertThat(lines("OUTBOX_DEAD")).isEmpty();
		assertThat(worker.removalFailureCount()).isEqualTo(1);
	}

	@Test
	void deliverConsumer_removalPolicy_firstLeaseLostThenFailure_alerts() {
		TestConsumer x = new TestConsumer("x", OutboxRetryPolicy.REMOVAL);
		appendEvents(adapter(subscriptions("x")), 1);
		OutboxWorker.Limits limits = limits(ONE_SECOND);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits, x);
		assertThat(worker.claim("x", clock.instant())).as("a first claim never marked").hasSize(1);
		clock.advance(limits.lease().plusSeconds(1));
		x.behaviour = delivery -> {
			throw new IllegalStateException("down");
		};

		worker.deliverConsumer("x");

		Map<String, Object> row = onlyRow();
		assertThat(row.get("attempts")).isEqualTo(2);
		assertThat(row.get("last_error_code")).isEqualTo("CONSUMER_ERROR");
		assertThat(lines("OUTBOX_REMOVAL_FAILED")).singleElement()
				.satisfies(line -> assertThat(line.getLevel()).isEqualTo(Level.ERROR));
		assertThat(lines("OUTBOX_DELIVERY_RETRY")).isEmpty();
		assertThat(worker.removalFailureCount()).isEqualTo(1);

		clock.advance(Duration.ofMinutes(5));
		worker.deliverConsumer("x");

		assertThat(lines("OUTBOX_REMOVAL_FAILED")).hasSize(1);
		assertThat(lines("OUTBOX_DELIVERY_RETRY")).hasSize(1);
		assertThat(worker.removalFailureCount()).isEqualTo(1);
	}

	@Test
	void deliverConsumer_removalPolicy_failureWithLostMark_stillAlerts() {
		TestConsumer x = new TestConsumer("x", OutboxRetryPolicy.REMOVAL);
		appendEvents(adapter(subscriptions("x")), 1);
		OutboxWorker.Limits limits = limits(ONE_SECOND);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits, x);
		x.behaviour = delivery -> {
			clock.advance(limits.lease().plusSeconds(1));
			worker.claim("x", clock.instant());
			throw new IllegalStateException("down");
		};

		worker.deliverConsumer("x");

		assertThat(codes()).containsExactly("OUTBOX_REMOVAL_FAILED", "OUTBOX_LEASE_LOST");
		assertThat(lines("OUTBOX_REMOVAL_FAILED").get(0).getLevel()).isEqualTo(Level.ERROR);
		assertThat(worker.removalFailureCount()).isEqualTo(1);
		assertThat(worker.lostLeaseCount()).isEqualTo(1);
		assertThat(worker.retriedCount()).isZero();
		Map<String, Object> row = onlyRow();
		assertThat(row.get("attempts")).isEqualTo(2);
		assertThat(row.get("lease_token")).isNotNull();
		assertThat(row.get("last_error_code")).isNull();
	}

	@Test
	void deliverConsumer_lostLease_countedAndLogged() {
		TestConsumer x = new TestConsumer("x");
		appendEvents(adapter(subscriptions("x")), 1);
		OutboxWorker.Limits limits = limits(ONE_SECOND);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits, x);
		x.behaviour = delivery -> {
			clock.advance(limits.lease().plusSeconds(1));
			worker.claim("x", clock.instant());
		};

		worker.deliverConsumer("x");

		assertThat(onlyRow().get("status")).isEqualTo("PENDING");
		assertThat(codes()).containsExactly("OUTBOX_LEASE_LOST");
		assertThat(lines("OUTBOX_LEASE_LOST").get(0).getLevel()).isEqualTo(Level.WARN);
		assertThat(worker.lostLeaseCount()).isEqualTo(1);
		assertThat(worker.deliveredCount()).isZero();
	}

	@Test
	void deliverConsumer_deliverySubmitRejected_releasesTheClaim() {
		TestConsumer x = new TestConsumer("x");
		appendEvents(adapter(subscriptions("x")), 3);
		Instant runAt = START.plusSeconds(10);
		clock.set(runAt);
		OutboxScheduling scheduling = scheduling(
				name -> name.equals("outbox-delivery-x") ? rejectingAfterFirst(name)
						: OutboxScheduling.newConsumerExecutor(name), "x");
		OutboxWorker worker = worker(subscriptions("x"), scheduling, limits(ONE_SECOND), x);

		worker.deliverConsumer("x");

		List<Map<String, Object>> rows = rowsById();
		assertThat(rows.get(0).get("status")).isEqualTo("DONE");
		for (Map<String, Object> released : rows.subList(1, 3)) {
			assertThat(released.get("status")).isEqualTo("PENDING");
			assertThat(released.get("lease_token")).isNull();
			assertThat(released.get("attempts")).isEqualTo(0);
			assertThat(released.get("last_error_code")).isNull();
			assertThat(instant(released, "next_attempt_at")).isEqualTo(runAt);
		}
		assertThat(worker.releasedCount()).isEqualTo(2);
		assertThat(logs.list).noneMatch(line -> line.getLevel() == Level.ERROR);
		assertThat(lines("OUTBOX_RUN_FAILED")).isEmpty();
	}

	@Test
	void deliverConsumer_backToBackDeliveries_noneRejected() {
		TestConsumer x = new TestConsumer("x");
		appendEvents(adapter(subscriptions("x")), 200);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(ONE_SECOND), x);

		for (int run = 0; run < 20; run++) {
			worker.deliverConsumer("x");
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status = 'DONE'", Integer.class))
				.isEqualTo(200);
		assertThat(worker.releasedCount()).isZero();
		assertThat(worker.deliveredCount()).isEqualTo(200);
	}

	@Test
	void deliverConsumer_interruptedWhileWaiting_rowStaysLeased() throws Exception {
		TestConsumer x = new TestConsumer("x");
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = latch();
		x.behaviour = delivery -> {
			if (delivery.attempt() == 1) {
				started.countDown();
				awaitIgnoringInterrupts(release);
			}
		};
		CountDownLatch afterExecute = new CountDownLatch(1);
		OutboxScheduling scheduling = scheduling(OutboxSchedulingConfigTest.deliveryOf("x", afterExecute), "x");
		OutboxWorker.Limits limits = limits(Duration.ofSeconds(30));
		OutboxWorker worker = worker(subscriptions("x"), scheduling, limits, x);
		appendEvents(adapter(subscriptions("x")), 1);
		AtomicReference<Thread> runner = new AtomicReference<>();
		Future<Boolean> run = testThreads.submit(() -> {
			runner.set(Thread.currentThread());
			worker.deliverConsumer("x");
			return Thread.currentThread().isInterrupted();
		});
		assertThat(started.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
		Map<String, Object> leased = onlyRow();

		runner.get().interrupt();

		assertThat(run.get(BOUND_SECONDS, TimeUnit.SECONDS)).as("the run keeps the interrupt flag").isTrue();
		Map<String, Object> after = onlyRow();
		assertThat(after.get("lease_token")).isNotNull().isEqualTo(leased.get("lease_token"));
		assertThat(after.get("next_attempt_at")).isEqualTo(leased.get("next_attempt_at"));
		assertThat(after.get("status")).isEqualTo("PENDING");
		assertThat(after.get("attempts")).isEqualTo(1);
		assertThat(after.get("last_error_code")).isNull();

		release.countDown();
		assertThat(afterExecute.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
		clock.advance(limits.lease().plusSeconds(1));
		worker.deliverConsumer("x");

		assertThat(x.deliveries).hasSize(2);
		assertThat(x.deliveries.get(1).attempt()).isEqualTo(2);
		assertThat(onlyRow().get("status")).isEqualTo("DONE");
	}

	// Consumers side by side

	@Test
	void deliverDue_timingOutConsumer_otherConsumersRowsNotDelayed() throws Exception {
		TestConsumer x = new TestConsumer("x");
		TestConsumer y = new TestConsumer("y");
		CountDownLatch xRelease = latch();
		x.behaviour = delivery -> awaitInterruptibly(xRelease);
		appendEvents(adapter(subscriptions("x", "y")), 1);
		OutboxWorker worker = worker(subscriptions("x", "y"), scheduling("x", "y"), limits(Duration.ofSeconds(30)), x,
				y);

		Map<String, Future<?>> runs = worker.deliverDue();

		assertThat(runs).containsOnlyKeys("x", "y");
		runs.get("y").get(BOUND_SECONDS, TimeUnit.SECONDS);
		assertThat(runs.get("x").isDone()).as("x still blocks").isFalse();
		assertThat(rowOfConsumer("y").get("status")).isEqualTo("DONE");
		Map<String, Object> xRow = rowOfConsumer("x");
		assertThat(xRow.get("status")).isEqualTo("PENDING");
		assertThat(xRow.get("lease_token")).isNotNull();
		xRelease.countDown();
		runs.get("x").get(BOUND_SECONDS, TimeUnit.SECONDS);
		assertThat(rowOfConsumer("x").get("status")).isEqualTo("DONE");
	}

	@Test
	void deliverDue_backlogOfOneConsumer_otherConsumerDeliveredInTheSameRun() throws Exception {
		TestConsumer x = new TestConsumer("x");
		TestConsumer y = new TestConsumer("y");
		appendEvents(adapter(subscriptions("x")), 50);
		clock.advance(Duration.ofSeconds(1));
		appendEvents(adapter(subscriptions("y")), 1);
		OutboxWorker worker = worker(subscriptions("x", "y"), scheduling("x", "y"), limits(ONE_SECOND), x, y);

		Map<String, Future<?>> runs = worker.deliverDue();

		runs.get("x").get(BOUND_SECONDS, TimeUnit.SECONDS);
		runs.get("y").get(BOUND_SECONDS, TimeUnit.SECONDS);
		assertThat(x.deliveries).hasSize(10);
		assertThat(statusCount("x", "DONE")).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE consumer = 'x' AND status = 'PENDING'"
				+ " AND lease_token IS NULL AND attempts = 0", Integer.class)).isEqualTo(40);
		assertThat(rowOfConsumer("y").get("status")).isEqualTo("DONE");
	}

	@Test
	void deliverDue_deliveryOutlivedItsTimeout_consumerNotClaimed_othersDelivered() throws Exception {
		TestConsumer x = new TestConsumer("x");
		TestConsumer y = new TestConsumer("y");
		CountDownLatch xRelease = latch();
		x.behaviour = delivery -> awaitIgnoringInterrupts(xRelease);
		CountDownLatch xRunEnded = new CountDownLatch(1);
		OutboxScheduling scheduling = scheduling(name -> name.equals("outbox-run-x")
				? OutboxSchedulingConfigTest.latched(name, xRunEnded)
				: OutboxScheduling.newConsumerExecutor(name), "x", "y");
		appendEvents(adapter(subscriptions("x")), 2);
		inTransaction(status -> adapter(subscriptions("y")).append(UnitAdvertisingChangedEvent.now(unitId(900))));
		OutboxWorker worker = worker(subscriptions("x", "y"), scheduling, limits(ONE_SECOND), x, y);

		worker.deliverDue().get("y").get(BOUND_SECONDS, TimeUnit.SECONDS);
		assertThat(xRunEnded.await(BOUND_SECONDS, TimeUnit.SECONDS)).as("x's run ended at its time-out").isTrue();
		assertThat(scheduling.deliveriesOutlivingTheirRun()).containsExactly("x");
		inTransaction(status -> adapter(subscriptions("y")).append(UnitAdvertisingChangedEvent.now(unitId(901))));

		Map<String, Future<?>> next = worker.deliverDue();

		assertThat(next).containsOnlyKeys("y");
		next.get("y").get(BOUND_SECONDS, TimeUnit.SECONDS);
		assertThat(x.deliveries).hasSize(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE consumer = 'x' AND status = 'PENDING'"
				+ " AND lease_token IS NULL AND attempts = 0", Integer.class)).as("x's released row, not claimed again")
				.isEqualTo(1);
		assertThat(statusCount("y", "DONE")).isEqualTo(2);
		assertThat(scheduling.deliveriesOutlivingTheirRun()).containsExactly("x");
		xRelease.countDown();
	}

	@Test
	void deliverDue_oneConsumerFails_otherConsumerDelivered() throws Exception {
		TestConsumer x = new TestConsumer("x");
		TestConsumer y = new TestConsumer("y");
		x.behaviour = delivery -> {
			throw new IllegalStateException("down");
		};
		appendEvents(adapter(subscriptions("x", "y")), 1);
		OutboxWorker worker = worker(subscriptions("x", "y"), scheduling("x", "y"), limits(ONE_SECOND), x, y);

		Map<String, Future<?>> runs = worker.deliverDue();

		runs.get("x").get(BOUND_SECONDS, TimeUnit.SECONDS);
		runs.get("y").get(BOUND_SECONDS, TimeUnit.SECONDS);
		Map<String, Object> xRow = rowOfConsumer("x");
		assertThat(xRow.get("status")).isEqualTo("PENDING");
		assertThat(xRow.get("last_error_code")).isEqualTo("CONSUMER_ERROR");
		assertThat(rowOfConsumer("y").get("status")).isEqualTo("DONE");
	}

	@Test
	void deliverDue_returnsWithoutWaitingForARun() throws Exception {
		TestConsumer x = new TestConsumer("x");
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = latch();
		x.behaviour = delivery -> {
			started.countDown();
			awaitInterruptibly(release);
		};
		appendEvents(adapter(subscriptions("x")), 1);
		OutboxWorker worker = worker(subscriptions("x"), scheduling("x"), limits(Duration.ofSeconds(30)), x);

		Map<String, Future<?>> first = worker.deliverDue();

		assertThat(first.get("x").isDone()).isFalse();
		assertThat(started.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
		inTransaction(status -> adapter(subscriptions("x")).append(UnitAdvertisingChangedEvent.now(unitId(700))));
		Map<String, Future<?>> second = worker.deliverDue();
		assertThat(second).doesNotContainKey("x");
		assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE aggregate_id = ?", Integer.class,
				unitId(700))).as("no more of x's rows claimed").isZero();
		release.countDown();
		first.get("x").get(BOUND_SECONDS, TimeUnit.SECONDS);
		assertThat(x.deliveries).hasSize(1);
	}

	// Helpers

	private Map<String, Object> rowOfConsumer(String consumer) {
		return jdbc.queryForMap("SELECT * FROM outbox_events WHERE consumer = ?", consumer);
	}

	private int statusCount(String consumer, String status) {
		return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE consumer = ? AND status = ?",
				Integer.class, consumer, status);
	}

	private CountDownLatch latch() {
		CountDownLatch latch = new CountDownLatch(1);
		latches.add(latch);
		return latch;
	}

	private OutboxScheduling scheduling(String... consumers) {
		OutboxScheduling scheduling = new OutboxScheduling(List.of(consumers));
		schedulings.add(scheduling);
		return scheduling;
	}

	private OutboxScheduling scheduling(Function<String, ExecutorService> executors, String... consumers) {
		OutboxScheduling scheduling = new OutboxScheduling(List.of(consumers), executors);
		schedulings.add(scheduling);
		return scheduling;
	}

	/** The production shape, which accepts its first submit and rejects every later one. */
	private static ThreadPoolExecutor rejectingAfterFirst(String threadName) {
		AtomicInteger submits = new AtomicInteger();
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
			Thread thread = new Thread(runnable, threadName);
			thread.setDaemon(true);
			return thread;
		}, new ThreadPoolExecutor.AbortPolicy()) {

			@Override
			public void execute(Runnable command) {
				if (submits.incrementAndGet() > 1) {
					throw new RejectedExecutionException("rejected by the test");
				}
				super.execute(command);
			}
		};
	}

	/** A consumer that waits for {@code release} and ends when interrupted (a timed-out delivery's cancel). */
	static void awaitInterruptibly(CountDownLatch release) {
		try {
			release.await(BOUND_SECONDS * 3, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted");
		}
	}

	static void awaitIgnoringInterrupts(CountDownLatch release) {
		OutboxSchedulingConfigTest.awaitIgnoringInterrupts(release);
	}

	private List<ILoggingEvent> lines(String code) {
		return logs.list.stream().filter(line -> line.getFormattedMessage().startsWith(code + " ")).toList();
	}

	private List<String> codes() {
		return logs.list.stream().map(line -> line.getFormattedMessage().split(" ", 2)[0]).toList();
	}

	private void assertNoDataInLogs(String... fragments) {
		for (ILoggingEvent line : logs.list) {
			assertThat(line.getThrowableProxy()).as("no stack trace").isNull();
			for (String fragment : fragments) {
				assertThat(line.getFormattedMessage()).doesNotContain(fragment);
			}
		}
	}

	private Map<String, Object> onlyRow() {
		return jdbc.queryForMap("SELECT * FROM outbox_events");
	}

	private Map<String, Object> rowOf(String unitId) {
		return jdbc.queryForMap("SELECT * FROM outbox_events WHERE aggregate_id = ?", unitId);
	}

	private List<Map<String, Object>> rowsById() {
		return jdbc.queryForList("SELECT * FROM outbox_events ORDER BY id");
	}

	private static Instant instant(Map<String, Object> row, String column) {
		return ((Timestamp) row.get(column)).toInstant();
	}
}
