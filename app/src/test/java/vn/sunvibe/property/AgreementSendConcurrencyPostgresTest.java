package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * The races of sending a draft on PostgreSQL (brokerage-agreement BR-5; E05-S4 spec S4-D6 steps 3 and 10, SD-6; plan
 * E05S4-P7). Every interleaving is forced on the basis lead's row: the test holds the row, waits until
 * {@code pg_stat_activity} shows the commands waiting on it (each has read the agreement by then, since the command reads
 * the agreement before it locks the lead), and then releases the row or changes the agreement first. Two sends, or a
 * send and an edit, end in one 204 and one 409 and never in a sent version with the edit's content; a change committed
 * between the send's read and its write fails the guarded update with nothing written. Needs Docker; a Linux run is the
 * evidence of a race test (testing.md).
 */
class AgreementSendConcurrencyPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final long COMPLETE_RENT = 8_000_000L;
	private static final long EDITED_RENT = 9_000_000L;

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<String> created = new CopyOnWriteArrayList<>();
	private final List<String> owners = new CopyOnWriteArrayList<>();

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	String phonePrefix() {
		return "0940";
	}

	@AfterAll
	void stopExecutorAndDeleteRows() {
		executor.shutdownNow();
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ? and kind = 'AGREEMENT_PENDING'", owner);
		}
	}

	// ==================== HTTP and fixtures ====================

	private Result send(String agreementId, long expectedDataVersion) throws Exception {
		return api.post(sale, AGREEMENTS + "/" + agreementId + "/draft/submission",
				Map.of("expectedDataVersion", expectedDataVersion));
	}

	private Result editRent(String agreementId, long expectedDataVersion, long rent) throws Exception {
		Map<String, Object> body = AgreementFeePolicyApiPostgresTest.complete(expectedDataVersion);
		@SuppressWarnings("unchecked") // the example body's terms are a map of the API test
		Map<String, Object> terms = (Map<String, Object>) body.get("terms");
		terms.put("monthlyRent", AgreementDraftApiPostgresTest.rent(rent));
		return api.put(sale, AGREEMENTS + "/" + agreementId + "/draft", body);
	}

	private record Done(String leadId, TestUser owner, String agreementId) {
	}

	/**
	 * A house lead of {@code sale} with a completed intake, the Sale's draft on its unit and the complete draft saved
	 * once: the agreement's row version is 1.
	 */
	private Done done() throws Exception {
		Passed passed = house(unique("Send Race Street"));
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
		Result saved = editRent(id, 0, COMPLETE_RENT);
		assertEquals(204, saved.status(), saved.rawBody());
		assertEquals(1L, rowVersion(id));
		return new Done(passed.leadId(), owner, id);
	}

	// ==================== reading rows ====================

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private String agreementStatus(String agreementId) {
		return jdbc.queryForObject("select status from brokerage_agreements where id = ?", String.class, agreementId);
	}

	private Map<String, Object> versionRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ?", agreementId);
	}

	private int events(String agreementId, String type) {
		return count("select count(*) from brokerage_agreement_events where agreement_id = ? and event_type = ?",
				agreementId, type);
	}

	private int notices(TestUser owner) {
		return count("select count(*) from auth_email_notifications where user_id = ? and kind = 'AGREEMENT_PENDING'",
				owner.userId());
	}

	// ==================== the forced order ====================

	/** A row lock a test transaction holds until it is closed (as AgreementDraftConcurrencyPostgresTest's). */
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

	/** A lock that keeps the send's FOR NO KEY UPDATE of its basis lead (S4-D6 step 3) waiting. */
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

	private static void assertRefused(Result answer) {
		assertEquals(409, answer.status(), answer.rawBody());
		String code = answer.body().path("code").asString();
		assertTrue(List.of("CONCURRENT_MODIFICATION", "AGREEMENT_NOT_DRAFT").contains(code), answer.rawBody());
	}

	// ==================== SD-6 ====================

	@Test
	void twoSends_released_oneSentOneConflict() throws Exception {
		Done done = done();
		Future<Result> first;
		Future<Result> second;
		try (Hold hold = lead(done.leadId())) {
			first = executor.submit(() -> send(done.agreementId(), 1));
			second = executor.submit(() -> send(done.agreementId(), 1));
			awaitWaiters(2);
		}

		List<Result> answers = List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));

		Result sent = answers.get(0).status() == 204 ? answers.get(0) : answers.get(1);
		Result refused = sent == answers.get(0) ? answers.get(1) : answers.get(0);
		assertEquals(204, sent.status(), sent.rawBody());
		assertRefused(refused);
		assertEquals(1, events(done.agreementId(), "VERSION_SENT"));
		assertEquals(1, notices(done.owner()));
		assertEquals(2L, rowVersion(done.agreementId()));
		assertEquals("AWAITING_CONFIRMATION", agreementStatus(done.agreementId()));
	}

	@Test
	void sendAndEdit_released_oneWinsNeverMixed() throws Exception {
		Done done = done();
		Future<Result> send;
		Future<Result> edit;
		try (Hold hold = lead(done.leadId())) {
			send = executor.submit(() -> send(done.agreementId(), 1));
			edit = executor.submit(() -> editRent(done.agreementId(), 1, EDITED_RENT));
			awaitWaiters(2);
		}

		Result sendAnswer = send.get(60, TimeUnit.SECONDS);
		Result editAnswer = edit.get(60, TimeUnit.SECONDS);

		Map<String, Object> version = versionRow(done.agreementId());
		BigDecimal rent = (BigDecimal) version.get("monthly_rent_amount");
		if (sendAnswer.status() == 204) {
			assertRefused(editAnswer);
			assertEquals("AWAITING_OWNER", version.get("status"));
			assertEquals(0, BigDecimal.valueOf(COMPLETE_RENT).compareTo(rent), "sent with the content read");
			assertEquals(1, events(done.agreementId(), "VERSION_SENT"));
			assertEquals(1, notices(done.owner()));
			assertEquals(1, events(done.agreementId(), "DRAFT_EDITED"));
		} else {
			assertEquals(204, editAnswer.status(), editAnswer.rawBody());
			assertRefused(sendAnswer);
			assertEquals("DRAFT", version.get("status"));
			assertEquals("DRAFTING", agreementStatus(done.agreementId()));
			assertEquals(0, BigDecimal.valueOf(EDITED_RENT).compareTo(rent), "edited, still a draft");
			assertEquals(2, events(done.agreementId(), "DRAFT_EDITED"));
			assertEquals(0, events(done.agreementId(), "VERSION_SENT"));
			assertEquals(0, notices(done.owner()));
		}
		assertFalse("AWAITING_OWNER".equals(version.get("status"))
				&& BigDecimal.valueOf(EDITED_RENT).compareTo(rent) == 0, "never a sent version with the edit's rent");
		assertEquals(2L, rowVersion(done.agreementId()));
	}

	/** The guarded update: a change committed after the send read the agreement fails it, with no entry. */
	@Test
	void send_rowVersionOvertaken_conflictNothingWritten() throws Exception {
		Done done = done();
		Map<String, Object> before = versionRow(done.agreementId());
		Future<Result> send;
		try (Hold hold = lead(done.leadId())) {
			send = executor.submit(() -> send(done.agreementId(), 1));
			awaitWaiters(1);
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.update(
					"update brokerage_agreements set version = version + 1, updated_at = ? where id = ?",
					Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)), done.agreementId()));
		}

		Result answer = send.get(60, TimeUnit.SECONDS);

		assertEquals(409, answer.status(), answer.rawBody());
		assertEquals("CONCURRENT_MODIFICATION", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
		assertEquals(before, versionRow(done.agreementId()));
		assertEquals("DRAFTING", agreementStatus(done.agreementId()));
		assertEquals(2L, rowVersion(done.agreementId()));
		assertEquals(0, events(done.agreementId(), "VERSION_SENT"));
		assertEquals(0, notices(done.owner()));
	}

	/** S4-D6 step 3: the send holds the basis lead's row lock, so it waits while another transaction holds the row. */
	@Test
	void send_leadRowHeld_waitsUntilReleased() throws Exception {
		Done done = done();
		Future<Result> send;
		try (Hold hold = lead(done.leadId())) {
			send = executor.submit(() -> send(done.agreementId(), 1));
			awaitWaiters(1);
			Thread.sleep(300);
			assertFalse(send.isDone(), "the send finished while the lead's row was held");
			assertEquals(0, events(done.agreementId(), "VERSION_SENT"));
		}

		Result answer = send.get(60, TimeUnit.SECONDS);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(1, events(done.agreementId(), "VERSION_SENT"));
		assertEquals(1, notices(done.owner()));
	}
}
