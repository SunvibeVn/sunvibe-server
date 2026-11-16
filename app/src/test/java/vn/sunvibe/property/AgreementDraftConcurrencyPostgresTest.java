package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The races of the draft edit on PostgreSQL (brokerage-agreement BR-5; E05-S3 spec S3-D3, S3-D4, ED-2): a stale data
 * version names its entry; two saves of one data version end in one 204 and one 409; a change committed between the
 * edit's read and its write fails the guarded update and nothing of the edit is written. A forced order: a held lead
 * lock keeps the edit out, and it is proven to wait in {@code pg_stat_activity} before the test changes the row. Needs
 * Docker.
 */
class AgreementDraftConcurrencyPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<String> created = new CopyOnWriteArrayList<>();

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	String phonePrefix() {
		return "0947";
	}

	@AfterAll
	void stopExecutorAndDeleteAgreements() {
		executor.shutdownNow();
		AgreementFixture.delete(jdbc, created);
	}

	private Result put(TestUser caller, String agreementId, Map<String, Object> body) throws Exception {
		return api.put(caller, AGREEMENTS + "/" + agreementId + "/draft", body);
	}

	/** The example body of spec section 4 with this rent. */
	private static Map<String, Object> withRent(long dataVersion, long rent) {
		Map<String, Object> body = AgreementDraftApiPostgresTest.example(dataVersion);
		@SuppressWarnings("unchecked") // the example body's terms are a map of the API test
		Map<String, Object> terms = (Map<String, Object>) body.get("terms");
		terms.put("monthlyRent", AgreementDraftApiPostgresTest.rent(rent));
		return body;
	}

	private record Done(String leadId, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's draft on its unit. */
	private Done done() throws Exception {
		Passed passed = house(unique("Race Street"));
		checked(passed, owner());
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
		return new Done(passed.leadId(), id);
	}

	private int edits(String agreementId) {
		return count("select count(*) from brokerage_agreement_events where agreement_id = ? and event_type ="
				+ " 'DRAFT_EDITED'", agreementId);
	}

	private BigDecimal storedRent(String agreementId) {
		return jdbc.queryForObject("select monthly_rent_amount from brokerage_agreement_versions where agreement_id = ?",
				BigDecimal.class, agreementId);
	}

	private Map<String, Object> versionRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ?", agreementId);
	}

	private static void assertConcurrent(Result answer, String errors) {
		assertEquals(409, answer.status(), answer.rawBody());
		assertEquals("CONCURRENT_MODIFICATION", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("Something changed at the same time. Try again.", answer.body().path("message").asString());
		if (errors != null) {
			assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
		}
	}

	/** A row lock a test transaction holds until it is closed (as AgreementCreateConcurrencyPostgresTest's). */
	private final class Hold implements AutoCloseable {

		private final CountDownLatch held = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final Future<?> owner;

		Hold(String sql, Object... arguments) throws Exception {
			owner = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				jdbc.query(sql, (RowCallbackHandler) row -> { }, arguments);
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

	/** A lock that keeps the edit's FOR NO KEY UPDATE of its basis lead waiting. */
	private Hold lead(String leadId) throws Exception {
		return new Hold("select id from owner_leads where id = ? for update", leadId);
	}

	private void awaitWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (count("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()") >= count) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(count + " commands never waited on a lock");
	}

	/** Both calls start at once behind one latch. */
	private List<Result> together(Callable<Result> first, Callable<Result> second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Result>> answers = new ArrayList<>();
		for (Callable<Result> call : List.of(first, second)) {
			answers.add(executor.submit(() -> {
				assertTrue(start.await(30, TimeUnit.SECONDS));
				return call.call();
			}));
		}
		start.countDown();
		List<Result> results = new ArrayList<>();
		for (Future<Result> answer : answers) {
			results.add(answer.get(60, TimeUnit.SECONDS));
		}
		return results;
	}

	// ==================== ED-2 ====================

	@Test
	void draft_staleDataVersion_concurrentWithEntryNothingWritten() throws Exception {
		Done done = done();
		assertEquals(204, put(sale, done.agreementId(), withRent(0, 8_000_000L)).status());
		Map<String, Object> before = versionRow(done.agreementId());

		Result stale = put(sale, done.agreementId(), withRent(0, 9_000_000L));

		assertConcurrent(stale, "[{\"field\":\"expectedDataVersion\",\"message\":\"This value is out of date.\"}]");
		assertEquals(before, versionRow(done.agreementId()));
		assertEquals(1, edits(done.agreementId()));
		assertEquals(1L, jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class,
				done.agreementId()));
	}

	@Test
	void draft_twoSavesSameDataVersion_oneSavedOneConcurrent() throws Exception {
		Done done = done();

		List<Result> answers = together(() -> put(sale, done.agreementId(), withRent(0, 8_000_000L)),
				() -> put(sale, done.agreementId(), withRent(0, 9_000_000L)));

		Result saved = answers.get(0).status() == 204 ? answers.get(0) : answers.get(1);
		Result refused = saved == answers.get(0) ? answers.get(1) : answers.get(0);
		assertEquals(204, saved.status(), saved.rawBody());
		assertConcurrent(refused, null);
		assertEquals(1, edits(done.agreementId()));
		BigDecimal savedRent = BigDecimal.valueOf(saved == answers.get(0) ? 8_000_000L : 9_000_000L);
		assertEquals(0, savedRent.compareTo(storedRent(done.agreementId())));
	}

	/** The guarded update: a change committed after the edit read the agreement fails it, with no entry. */
	@Test
	void draft_overtakenBetweenReadAndWrite_concurrentNothingWritten() throws Exception {
		Done done = done();
		Map<String, Object> before = versionRow(done.agreementId());
		Future<Result> edit;
		try (Hold hold = lead(done.leadId())) {
			edit = executor.submit(() -> put(sale, done.agreementId(), withRent(0, 8_000_000L)));
			awaitWaiters(1);
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.update(
					"update brokerage_agreements set version = version + 1, updated_at = ? where id = ?",
					Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)), done.agreementId()));
		}

		Result answer = edit.get(60, TimeUnit.SECONDS);

		assertConcurrent(answer, "[]");
		assertEquals(before, versionRow(done.agreementId()));
		assertEquals(0, edits(done.agreementId()));
		assertEquals(1L, jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class,
				done.agreementId()));
	}
}
