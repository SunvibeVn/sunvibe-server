package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The races of the draft creation on PostgreSQL (brokerage-agreement BR-1, BR-16; E05-S2 spec S2-D2, S2-D7, section 9,
 * AD-3, AD-6): two creates of one unit meet at the lead's row lock or at the unfinished-unit index, one answering 201
 * and the other 409, never 500; a reassignment and a create serialize on the lead's row lock. A forced order: a held
 * lock keeps every command out, each one is proven to wait in {@code pg_stat_activity} before the next starts. Needs
 * Docker.
 */
class AgreementCreateConcurrencyPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<String> created = new CopyOnWriteArrayList<>();

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	String phonePrefix() {
		return "0949";
	}

	@AfterAll
	void stopExecutorAndDeleteAgreements() {
		executor.shutdownNow();
		AgreementFixture.delete(jdbc, created);
	}

	// ==================== HTTP ====================

	private Result post(TestUser caller, String leadId, String unitId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(caller, AGREEMENTS, body);
		if (answer.status() == 201) {
			created.add(answer.body().path("agreementId").asString());
		}
		return answer;
	}

	private int agreementsOfUnit(String unitId) {
		return count("select count(*) from brokerage_agreements where unit_id = ?", unitId);
	}

	private static void assertExists(Result answer) {
		assertEquals(409, answer.status(), answer.rawBody());
		assertEquals("AGREEMENT_EXISTS", answer.body().path("code").asString(), answer.rawBody());
	}

	// ==================== fixtures ====================

	/** A lead of {@code sale} with a completed intake, and the unit it maps. */
	private record Done(String leadId, String unitId) {
	}

	private Done done() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		checked(passed, owner());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String unit = completed(passed.leadId()).path("units").path(0).path("unitId").asString();
		return new Done(passed.leadId(), unit);
	}

	/** Two houses on one street: the first creates the property, the second links onto its one unit; both completed. */
	private record Pair(String firstLeadId, String secondLeadId, String unitId) {
	}

	private Pair housePair() throws Exception {
		String street = unique("Two Leads");
		Passed first = house(street);
		TestUser owner = owner();
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		available(first.leadId(), first.spaceIds(), "VACANT");
		JsonNode result = completed(first.leadId());
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		Passed second = house(street);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(first.leadId(), second.leadId(), unit);
	}

	/** A row lock a test transaction holds until it is closed (as IntakeConcurrencyPostgresTest's). */
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

	/** The lock the create command takes on its basis lead. */
	private Hold lead(String leadId) throws Exception {
		return new Hold("select id from owner_leads where id = ? for no key update", leadId);
	}

	/**
	 * A DRAFTING agreement of the unit, inserted in a test transaction that stays open, so its entry in the
	 * unfinished-unit index is not committed yet; closing commits it, or rolls it back when asked.
	 */
	private final class AgreementHold implements AutoCloseable {

		private final CountDownLatch held = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicBoolean rollBack = new AtomicBoolean();
		private final Future<String> owner;
		private String agreementId;

		AgreementHold(String unitId, String basisLeadId) throws Exception {
			owner = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
				String agreementId = AgreementFixture.insert(jdbc, ids, unitId, basisLeadId, "DRAFTING", "DRAFT");
				held.countDown();
				try {
					release.await(120, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				if (rollBack.get()) {
					status.setRollbackOnly();
				}
				return agreementId;
			}));
			assertTrue(held.await(30, TimeUnit.SECONDS), "the test never inserted its agreement");
		}

		/** Ends the transaction by a rollback instead of a commit. */
		void rollBack() {
			rollBack.set(true);
		}

		@Override
		public void close() throws Exception {
			release.countDown();
			agreementId = owner.get(30, TimeUnit.SECONDS);
			created.add(agreementId);
		}

		/** The fixture agreement's id, once closed. */
		String agreementId() {
			return agreementId;
		}
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

	private Callable<Integer> reassign(String leadId) {
		long version = survey.version(leadId);
		return () -> api.post(leader, LEADS + "/" + leadId + "/assign",
				Map.of("assigneeUserId", otherSale.userId(), "expectedVersion", version)).status();
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
		results.sort((left, right) -> Integer.compare(left.status(), right.status()));
		return results;
	}

	// ==================== AD-3: one unfinished agreement per unit ====================

	@Test
	void create_twoLeadsSameUnit_oneCreatedOneExists() throws Exception {
		Pair pair = housePair();

		List<Result> answers = together(() -> post(sale, pair.firstLeadId(), pair.unitId()),
				() -> post(sale, pair.secondLeadId(), pair.unitId()));

		assertEquals(201, answers.get(0).status(), answers.get(0).rawBody());
		assertExists(answers.get(1));
		assertEquals(1, agreementsOfUnit(pair.unitId()));
	}

	@Test
	void create_twoPostsSameLead_oneCreatedOneExists() throws Exception {
		Done done = done();

		List<Result> answers = together(() -> post(sale, done.leadId(), done.unitId()),
				() -> post(sale, done.leadId(), done.unitId()));

		assertEquals(201, answers.get(0).status(), answers.get(0).rawBody());
		assertExists(answers.get(1));
		assertEquals(1, agreementsOfUnit(done.unitId()));
	}

	@Test
	void create_uncommittedAgreementOnTheUnit_refusedOnceItCommits() throws Exception {
		Done done = done();
		AgreementHold hold = new AgreementHold(done.unitId(), ids.generateId());
		Future<Result> answer;
		try {
			answer = executor.submit(() -> post(sale, done.leadId(), done.unitId()));
			awaitWaiters(1);
		} finally {
			hold.close();
		}

		assertExists(answer.get(60, TimeUnit.SECONDS));
		assertEquals(List.of(hold.agreementId()), jdbc.queryForList(
				"select id from brokerage_agreements where unit_id = ?", String.class, done.unitId()));
	}

	@Test
	void create_uncommittedAgreementRolledBack_created() throws Exception {
		Done done = done();
		Future<Result> answer;
		try (AgreementHold hold = new AgreementHold(done.unitId(), ids.generateId())) {
			answer = executor.submit(() -> post(sale, done.leadId(), done.unitId()));
			awaitWaiters(1);
			hold.rollBack();
		}

		Result draft = answer.get(60, TimeUnit.SECONDS);
		assertEquals(201, draft.status(), draft.rawBody());
		assertEquals(List.of(draft.body().path("agreementId").asString()), jdbc.queryForList(
				"select id from brokerage_agreements where unit_id = ?", String.class, done.unitId()));
	}

	// ==================== AD-6, E-10: the lead lock against a reassignment ====================

	@Test
	void create_vsReassign_createFirst_bothApply() throws Exception {
		Done done = done();
		Callable<Integer> reassignment = reassign(done.leadId());
		Future<Result> create;
		Future<Integer> reassigned;
		try (Hold hold = lead(done.leadId())) {
			create = executor.submit(() -> post(sale, done.leadId(), done.unitId()));
			awaitWaiters(1);
			reassigned = executor.submit(reassignment);
			awaitWaiters(2);
		}

		Result answer = create.get(60, TimeUnit.SECONDS);
		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(200, reassigned.get(60, TimeUnit.SECONDS));
		assertEquals(sale.userId(), jdbc.queryForObject("select created_by_user_id from brokerage_agreements where id = ?",
				String.class, answer.body().path("agreementId").asString()));
		assertEquals(otherSale.userId(), jdbc.queryForObject("select assigned_user_id from owner_leads where id = ?",
				String.class, done.leadId()));
	}

	@Test
	void create_vsReassign_reassignFirst_formerSaleNotFound() throws Exception {
		Done done = done();
		Callable<Integer> reassignment = reassign(done.leadId());
		Future<Integer> reassigned;
		Future<Result> create;
		try (Hold hold = lead(done.leadId())) {
			reassigned = executor.submit(reassignment);
			awaitWaiters(1);
			create = executor.submit(() -> post(sale, done.leadId(), done.unitId()));
			awaitWaiters(2);
		}

		assertEquals(200, reassigned.get(60, TimeUnit.SECONDS));
		Result answer = create.get(60, TimeUnit.SECONDS);
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals(0, agreementsOfUnit(done.unitId()));
	}
}
