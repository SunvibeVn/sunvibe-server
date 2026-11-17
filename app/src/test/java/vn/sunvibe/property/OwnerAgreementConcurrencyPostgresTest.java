package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The races of the owner's answer, the new draft and the lapse job on PostgreSQL (brokerage-agreement BR-5; E05-S5 spec
 * S5-D6, S5-D8, OC-7, LP-3; plan E05S5-P6). Every interleaving is forced on the agreement row, which the owner's
 * commands and the job write only through the guarded update: the test holds the row FOR UPDATE, starts the sides one
 * after another and waits after each until that many ungranted locks of this database show in {@code pg_locks} (each
 * side has read the agreement by then), then releases. PostgreSQL queues the waiters of a row in arrival order, so the
 * side started first commits first and the other's guarded update matches no row. The one wait for a time, not for an
 * order, is the clock passing a deadline 10 s away (spec S5-D19 (2)). The context of
 * {@link OwnerAgreementLapseApiPostgresTest} (the job held off). Needs Docker; a Linux run is the evidence of a race test
 * (testing.md).
 */
@TestPropertySource(properties = "app.brokerage.lapse-delay=1h")
class OwnerAgreementConcurrencyPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<String> created = new CopyOnWriteArrayList<>();
	private final List<String> owners = new CopyOnWriteArrayList<>();

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private AgreementCommandUseCase commands;

	@Override
	String phonePrefix() {
		return "0936";
	}

	/** A run stores every due row of this context's database, so every agreement goes after its test. */
	@AfterEach
	void deleteRows() {
		AgreementFixture.delete(jdbc, created);
		created.clear();
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
		owners.clear();
	}

	@AfterAll
	void stopExecutor() {
		executor.shutdownNow();
	}

	// ==================== HTTP and fixtures ====================

	private record Done(String leadId, TestUser owner, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's complete draft sent to its owner. */
	private Done sent() throws Exception {
		Passed passed = house(unique("Owner Race Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String unit = completed(passed.leadId()).path("units").path(0).path("unitId").asString();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", passed.leadId());
		body.put("unitId", unit);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		Result saved = api.put(sale, AGREEMENTS + "/" + id + "/draft", AgreementFeePolicyApiPostgresTest.complete(rowVersion(id)));
		assertEquals(204, saved.status(), saved.rawBody());
		Result sentAnswer = api.post(sale, AGREEMENTS + "/" + id + "/draft/submission",
				Map.of("expectedDataVersion", rowVersion(id)));
		assertEquals(204, sentAnswer.status(), sentAnswer.rawBody());
		return new Done(passed.leadId(), owner, id);
	}

	/** Moves the sending time of version 1 back by {@code ago}; answers the version's deadline. */
	private Instant sentAgo(String agreementId, Duration ago) {
		Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(ago);
		jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ? and version_no = 1",
				Timestamp.from(sentAt), agreementId);
		return sentAt.plus(Duration.ofDays(7));
	}

	private Result confirm(Done done) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", passwordOf(done.owner()));
		body.put("statementVersion", "brokerage-agreement-2026-11");
		body.put("versions", List.of(Map.of("agreementId", done.agreementId(), "versionNo", 1)));
		return api.post(done.owner(), ME + "/confirmations", body);
	}

	private Result openDraft(String agreementId, long expectedDataVersion) throws Exception {
		return api.post(sale, AGREEMENTS + "/" + agreementId + "/draft", Map.of("expectedDataVersion", expectedDataVersion));
	}

	// ==================== reading rows ====================

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private Map<String, Object> versionRow(String agreementId, int versionNo) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ? and version_no = ?",
				agreementId, versionNo);
	}

	private int events(String agreementId, String type) {
		return count("select count(*) from brokerage_agreement_events where agreement_id = ? and event_type = ?",
				agreementId, type);
	}

	// ==================== the forced order ====================

	/** A row lock a test transaction holds until it is closed (as AgreementSendConcurrencyPostgresTest's). */
	private final class Hold implements AutoCloseable {

		private final CountDownLatch held = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final Future<?> owner;

		Hold(String agreementId) throws Exception {
			owner = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				jdbc.query("select id from brokerage_agreements where id = ? for update", (RowCallbackHandler) row -> { },
						agreementId);
				held.countDown();
				try {
					release.await(120, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertTrue(held.await(30, TimeUnit.SECONDS), "the test never took its lock");
		}

		@Override
		public void close() throws Exception {
			release.countDown();
			owner.get(30, TimeUnit.SECONDS);
		}
	}

	/** Waits until this database shows {@code count} ungranted locks: that many sides wait on the held row. */
	private void awaitWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (count("select count(*) from pg_locks l join pg_stat_activity a on a.pid = l.pid where not l.granted"
					+ " and a.datname = current_database()") >= count) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(count + " sides never waited on a lock");
	}

	/** Waits for the JVM clock to pass the deadline: a time, never an order (spec S5-D19 (2)); bounded at 20 s. */
	private static void awaitPast(Instant deadline) throws InterruptedException {
		long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (!Instant.now().isAfter(deadline.plusMillis(50))) {
			if (System.nanoTime() > limit) {
				throw new AssertionError("the clock never passed " + deadline);
			}
			Thread.sleep(50);
		}
	}

	private static void assertConflict(Result answer, String... codes) {
		assertEquals(409, answer.status(), answer.rawBody());
		assertTrue(List.of(codes).contains(answer.body().path("code").asString()), answer.rawBody());
	}

	// ==================== OC-7 ====================

	@Test
	void twoConfirmations_released_oneRecordedOneConflict() throws Exception {
		Done done = sent();
		List<Future<Result>> sides = new ArrayList<>();
		try (Hold hold = new Hold(done.agreementId())) {
			sides.add(executor.submit(() -> confirm(done)));
			awaitWaiters(1);
			sides.add(executor.submit(() -> confirm(done)));
			awaitWaiters(2);
		}

		Result first = sides.get(0).get(60, TimeUnit.SECONDS);
		Result second = sides.get(1).get(60, TimeUnit.SECONDS);

		assertEquals(204, first.status(), first.rawBody());
		assertConflict(second, "CONCURRENT_MODIFICATION", "AGREEMENT_NOT_AWAITING_OWNER");
		assertEquals("OWNER_CONFIRMED", versionRow(done.agreementId(), 1).get("status"));
		assertEquals(1, events(done.agreementId(), "OWNER_CONFIRMED"));
	}

	/** OC-7: a confirmation and the Sale's new draft of an agreement lapsing at that moment; exactly one commits. */
	@Test
	void confirmAndNewDraft_acrossTheDeadline_exactlyOneCommits() throws Exception {
		Done done = sent();
		long dataVersion = api.get(sale, AGREEMENTS + "/" + done.agreementId()).body().path("dataVersion").asLong();
		Instant deadline = sentAgo(done.agreementId(), Duration.ofDays(7).minusSeconds(10));
		Future<Result> confirmation;
		Future<Result> draft;
		try (Hold hold = new Hold(done.agreementId())) {
			confirmation = executor.submit(() -> confirm(done));
			awaitWaiters(1);
			awaitPast(deadline);
			draft = executor.submit(() -> openDraft(done.agreementId(), dataVersion));
			awaitWaiters(2);
		}

		Result confirmed = confirmation.get(60, TimeUnit.SECONDS);
		Result opened = draft.get(60, TimeUnit.SECONDS);

		assertEquals(204, confirmed.status(), confirmed.rawBody());
		assertConflict(opened, "CONCURRENT_MODIFICATION");
		assertEquals("OWNER_CONFIRMED", versionRow(done.agreementId(), 1).get("status"));
		assertEquals(0, events(done.agreementId(), "VERSION_LAPSED"));
		assertEquals(0, events(done.agreementId(), "DRAFT_OPENED"));
		assertEquals(1, AgreementFixture.rows(jdbc, "brokerage_agreement_versions", done.agreementId()));
	}

	/** OC-7 third case: the confirmation passes its checks before the deadline; the run after it writes nothing. */
	@Test
	void confirmAndJobRun_acrossTheDeadline_confirmationWinsRunWritesNothing() throws Exception {
		Done done = sent();
		Instant deadline = sentAgo(done.agreementId(), Duration.ofDays(7).minusSeconds(10));
		Future<Result> confirmation;
		Future<Integer> run;
		try (Hold hold = new Hold(done.agreementId())) {
			confirmation = executor.submit(() -> confirm(done));
			awaitWaiters(1);
			awaitPast(deadline);
			run = executor.submit(() -> commands.storeDueLapses());
			awaitWaiters(2);
		}

		Result confirmed = confirmation.get(60, TimeUnit.SECONDS);

		assertEquals(204, confirmed.status(), confirmed.rawBody());
		assertEquals(0, run.get(60, TimeUnit.SECONDS));
		Map<String, Object> version = versionRow(done.agreementId(), 1);
		assertEquals("OWNER_CONFIRMED", version.get("status"));
		assertEquals("CONFIRMED", version.get("owner_answer"));
		assertEquals(0, events(done.agreementId(), "VERSION_LAPSED"));
		assertEquals(1, events(done.agreementId(), "OWNER_CONFIRMED"));
	}

	// ==================== LP-3 ====================

	/** LP-3: two runs on one due row store it once; the loser goes on to its next agreement and stores that one. */
	@Test
	void twoRuns_released_oneStoresOtherGoesOn() throws Exception {
		Done first = sent();
		Done second = sent();
		sentAgo(first.agreementId(), Duration.ofDays(7).plusSeconds(5));
		Future<Integer> runX;
		Future<Integer> runY;
		try (Hold hold = new Hold(first.agreementId())) {
			runX = executor.submit(() -> commands.storeDueLapses());
			awaitWaiters(1);
			sentAgo(second.agreementId(), Duration.ofDays(7).plusSeconds(1));
			runY = executor.submit(() -> commands.storeDueLapses());
			awaitWaiters(2);
		}

		assertEquals(1, runX.get(60, TimeUnit.SECONDS));
		assertEquals(1, runY.get(60, TimeUnit.SECONDS));
		for (Done done : List.of(first, second)) {
			assertEquals("LAPSED", versionRow(done.agreementId(), 1).get("status"), done.agreementId());
			assertNull(versionRow(done.agreementId(), 1).get("owner_answer"));
			assertEquals(1, events(done.agreementId(), "VERSION_LAPSED"), done.agreementId());
		}
	}
}
