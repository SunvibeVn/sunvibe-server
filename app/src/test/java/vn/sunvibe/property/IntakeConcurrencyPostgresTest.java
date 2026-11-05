package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The races of property-create intake S-19, S-20 and S-22 on PostgreSQL. A forced order: a held row lock keeps every command out,
 * each one is proven to wait in {@code pg_stat_activity} before the next starts, and the lock queue then runs them in that order.
 * Whatever the order, a completion is refused whole or finished first. Needs Docker.
 */
class IntakeConcurrencyPostgresTest extends IntakeApiTestBase {

	/** The namespace of PostgresWardCreateLockAdapter. */
	private static final int WARD_LOCK_NAMESPACE = 2021;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	String phonePrefix() {
		return "0971";
	}

	@AfterAll
	void stopExecutor() {
		executor.shutdownNow();
	}

	private record Ready(Passed passed, TestUser owner, Issued issued, String address) {
		String leadId() {
			return passed.leadId();
		}
	}

	/** A surveyed house with a create target and today's availability: one call away from a result. */
	private Ready ready() throws Exception {
		return ready(unique("Bạch Đằng"));
	}

	private Ready ready(String address) throws Exception {
		Passed passed = house(address);
		TestUser owner = owner();
		Issued issued = checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return new Ready(passed, owner, issued, address);
	}

	private int propertiesAt(String address) {
		return count("select count(*) from properties where street = ?", address);
	}

	/** A row or advisory lock a test transaction holds until it is closed. */
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

	private Hold lead(String leadId) throws Exception {
		return new Hold("select id from owner_leads where id = ? for no key update", leadId);
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

	private Callable<Result> completion(Ready ready) throws Exception {
		long version = versionOf(sale, ready.leadId());
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		return () -> complete(sale, ready.leadId(), version, hash);
	}

	/** The completion goes first: it finishes, and the other command runs on the finished result. */
	private void completionFirst(Ready ready, Callable<Integer> other) throws Exception {
		Callable<Result> completion = completion(ready);
		Future<Result> first;
		Future<Integer> second;
		try (Hold hold = lead(ready.leadId())) {
			first = executor.submit(completion);
			awaitWaiters(1);
			second = executor.submit(other);
			awaitWaiters(2);
		}
		Result answer = first.get(60, TimeUnit.SECONDS);
		assertEquals(200, answer.status(), answer.rawBody());
		assertTrue(second.get(60, TimeUnit.SECONDS) < 300);
		assertEquals(1, propertiesAt(ready.address()));
	}

	/** The other command goes first: the completion reads it under its locks and stores nothing. */
	private void otherFirst(Ready ready, Callable<Integer> other) throws Exception {
		Callable<Result> completion = completion(ready);
		Future<Integer> first;
		Future<Result> second;
		try (Hold hold = lead(ready.leadId())) {
			first = executor.submit(other);
			awaitWaiters(1);
			second = executor.submit(completion);
			awaitWaiters(2);
		}
		assertTrue(first.get(60, TimeUnit.SECONDS) < 300);
		Result refused = second.get(60, TimeUnit.SECONDS);
		assertNotEquals(200, refused.status(), refused.rawBody());
		assertNotEquals(500, refused.status(), refused.rawBody());
		assertEquals(0, propertiesAt(ready.address()));
	}

	private Callable<Integer> reassign(Ready ready) {
		long version = survey.version(ready.leadId());
		return () -> api.post(leader, LEADS + "/" + ready.leadId() + "/assign",
				Map.of("assigneeUserId", otherSale.userId(), "expectedVersion", version)).status();
	}

	private Callable<Integer> statusChange(Ready ready, String toStatus, String textField) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", toStatus);
		body.put("expectedVersion", survey.version(ready.leadId()));
		body.put(textField, "Told by the owner");
		return () -> api.post(leader, LEADS + "/" + ready.leadId() + "/status-changes", body).status();
	}

	private Callable<Integer> photoHold(Ready ready) {
		return () -> api.post(admin, "/api/v1/staff/survey-media/" + ready.passed().photoIds().getFirst() + "/hold",
				Map.of("reason", "SECURITY_INCIDENT")).status();
	}

	private Callable<Integer> mark(Ready ready) {
		return () -> {
			fixture.requalify(leader, ready.passed().checklistId(), "OTHER");
			return 200;
		};
	}

	private Callable<Integer> withdrawal(Ready ready) {
		return () -> withdraw(ready.owner(), ready.issued().requestId(), passwordOf(ready.owner())).status();
	}

	// ==================== completion against each move (S-19) ====================

	@Test
	void complete_vsReassign() throws Exception {
		Ready first = ready();
		completionFirst(first, reassign(first));
		Ready second = ready();
		otherFirst(second, reassign(second));
	}

	@Test
	void complete_vsHold() throws Exception {
		Ready first = ready();
		completionFirst(first, statusChange(first, "ON_HOLD", "note"));
		Ready second = ready();
		otherFirst(second, statusChange(second, "ON_HOLD", "note"));
	}

	@Test
	void complete_vsClose() throws Exception {
		Ready first = ready();
		completionFirst(first, statusChange(first, "WITHDRAWN", "reason"));
		Ready second = ready();
		otherFirst(second, statusChange(second, "WITHDRAWN", "reason"));
	}

	@Test
	void complete_vsPhotoHold() throws Exception {
		Ready first = ready();
		completionFirst(first, photoHold(first));
		Ready second = ready();
		otherFirst(second, photoHold(second));
	}

	@Test
	void complete_vsMark() throws Exception {
		Ready first = ready();
		completionFirst(first, mark(first));
		Ready second = ready();
		otherFirst(second, mark(second));
	}

	@Test
	void complete_vsWithdrawal() throws Exception {
		Ready first = ready();
		completionFirst(first, withdrawal(first));
		Ready second = ready();
		otherFirst(second, withdrawal(second));
	}

	/** An administrator's lock takes no lead lock, so it lands while the completion waits; the completion reads it after. */
	@Test
	void complete_vsAccountLock() throws Exception {
		Ready ready = ready();
		Callable<Result> completion = completion(ready);
		Future<Result> answer;
		try (Hold hold = lead(ready.leadId())) {
			answer = executor.submit(completion);
			awaitWaiters(1);
			jdbc.update("update users set user_status = 'LOCK' where id = ?", ready.owner().userId());
		}

		assertRefused(409, "OWNER_PROOF_REQUIRED", answer.get(60, TimeUnit.SECONDS));
		assertEquals(0, propertiesAt(ready.address()));
	}

	@Test
	void twoCompletions_oneOwner_twoWards_ok() throws Exception {
		Passed first = house(unique("Lê Duẩn"));
		Passed second = house(unique("Lê Lợi"));
		TestUser owner = owner();
		for (Passed passed : List.of(first, second)) {
			checked(passed, owner);
			SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
			available(passed.leadId(), passed.spaceIds(), "VACANT");
		}
		jdbc.update("update inventory_intakes set ward_code = ? where lead_id = ?", SurveyApiTestSupport.OTHER_WARD, second.leadId());
		Callable<Result> one = completion(new Ready(first, owner, null, null));
		Callable<Result> two = completion(new Ready(second, owner, null, null));

		List<Future<Result>> answers = together(List.of(one, two));

		for (Future<Result> answer : answers) {
			Result result = answer.get(60, TimeUnit.SECONDS);
			assertEquals(200, result.status(), result.rawBody());
		}
	}

	private <T> List<Future<T>> together(List<Callable<T>> calls) {
		CountDownLatch ready = new CountDownLatch(calls.size());
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<T>> futures = new ArrayList<>();
		for (Callable<T> call : calls) {
			futures.add(executor.submit(() -> {
				ready.countDown();
				gate.await();
				return call.call();
			}));
		}
		try {
			assertTrue(ready.await(30, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		gate.countDown();
		return futures;
	}

	@Test
	void complete_usersRowHeld_409After3s_storesNothing() throws Exception {
		Ready ready = ready();
		Callable<Result> completion = completion(ready);

		try (Hold hold = new Hold("select id from users where id = ? for update", ready.owner().userId())) {
			assertRefused(409, "CONCURRENT_MODIFICATION", completion.call());
		}

		assertEquals(0, propertiesAt(ready.address()));
		assertEquals("OWNER_CONFIRMED", intakeRow(ready.leadId()).get("status"));
		assertEquals(200, completion(ready).call().status(), "nothing stood in the way of a retry");
	}

	@Test
	void repeat_stillGetsStoredResult_whileUsersRowHeld() throws Exception {
		Ready ready = ready();
		Callable<Result> completion = completion(ready);
		assertEquals(200, completion.call().status());

		try (Hold hold = new Hold("select id from users where id = ? for update", ready.owner().userId())) {
			assertEquals(200, completion.call().status());
		}
	}

	// ==================== the same room from two leads (S-20) ====================

	/** Review focus 1: the second reaches the signal after the first commit, and its case outlives its 409. */
	@Test
	void create_twoLeads_sameRoom_oneCaseCommitted() throws Exception {
		Ready first = ready(unique("First Room"));
		Ready second = ready(unique("Second Room"));
		Callable<Result> winner = completion(first);
		Callable<Result> loser = completion(second);

		assertEquals(200, winner.call().status());
		jdbc.update("update inventory_intakes set address_norm = (select address_norm from inventory_intakes where lead_id = ?)"
				+ " where lead_id = ?", first.leadId(), second.leadId());
		assertRefused(409, "INTAKE_DUPLICATE_CHECK", loser.call());

		assertEquals(1, propertiesAt(first.address()) + propertiesAt(second.address()));
		assertEquals("CASE_OPEN", intakeRow(second.leadId()).get("status"));
		assertEquals(1, count("select count(*) from inventory_duplicate_cases where intake_id = ?", intakeRow(second.leadId()).get("id")));
	}

	@Test
	void create_lockMiss_409() throws Exception {
		Ready ready = ready();
		Callable<Result> completion = completion(ready);

		try (Hold ward = new Hold("select pg_advisory_xact_lock(?, hashtext(?))", WARD_LOCK_NAMESPACE, WARD)) {
			assertRefused(409, "CONCURRENT_MODIFICATION", completion.call());
		}

		assertEquals(0, propertiesAt(ready.address()));
		assertEquals(200, completion(ready).call().status());
	}

	@Test
	void create_otherWard_doesNotWait() throws Exception {
		Ready ready = ready();
		jdbc.update("update inventory_intakes set ward_code = ? where lead_id = ?", SurveyApiTestSupport.OTHER_WARD, ready.leadId());
		Callable<Result> completion = completion(ready);

		try (Hold ward = new Hold("select pg_advisory_xact_lock(?, hashtext(?))", WARD_LOCK_NAMESPACE, WARD)) {
			assertEquals(200, completion.call().status());
		}
	}

	@Test
	void target_twoSamePair_oneCase() throws Exception {
		String address = unique("Pair Room");
		Passed first = house(address);
		Passed second = house(address);
		checked(first, owner());
		checked(second, owner());

		List<Future<Result>> answers = together(List.of(() -> createTarget(first.leadId(), null), () -> createTarget(second.leadId(), null)));

		for (Future<Result> answer : answers) {
			Result result = answer.get(60, TimeUnit.SECONDS);
			assertEquals(200, result.status(), result.rawBody());
			assertEquals("CASE_OPEN", result.body().path("status").asString());
		}
		assertEquals(1, count("select count(*) from inventory_duplicate_cases where intake_id in (?, ?)", intakeRow(first.leadId()).get("id"),
				intakeRow(second.leadId()).get("id")));
	}

	// ==================== decisions (S-22) ====================

	private Callable<Result> decision(String caseId) {
		long version = jdbc.queryForObject("select version from inventory_duplicate_cases where id = ?", Long.class, caseId);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("outcome", "DIFFERENT_ASSET");
		body.put("basis", "Checked both files");
		body.put("expectedVersion", version);
		return () -> api.post(manager, CASES + "/" + caseId + "/decision", body);
	}

	/** spec 4.10: a decision queued behind a close reads the side as it stands after the lock, so a dead side refuses it. */
	@Test
	void decision_behindClose_readsTheClosedSide() throws Exception {
		String address = unique("Queued Case");
		Passed first = house(address);
		Passed second = house(address);
		checked(first, owner());
		checked(second, owner());
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		String caseId = jdbc.queryForObject("select id from inventory_duplicate_cases where intake_id = ?", String.class,
				intakeRow(first.leadId()).get("id"));
		Ready side = new Ready(second, null, null, address);
		Callable<Integer> close = statusChange(side, "WITHDRAWN", "reason");
		Callable<Result> decision = decision(caseId);
		Future<Integer> closed;
		Future<Result> decided;

		try (Hold hold = lead(second.leadId())) {
			closed = executor.submit(close);
			awaitWaiters(1);
			decided = executor.submit(decision);
			awaitWaiters(2);
		}

		assertEquals(200, closed.get(60, TimeUnit.SECONDS));
		assertRefused(409, "INTAKE_STATE", decided.get(60, TimeUnit.SECONDS));
	}

	/** Two cases that share a lead take their locks in id order, whichever side they name first. */
	@Test
	void decision_twoLeads_noDeadlock() throws Exception {
		String address = unique("Three Rooms");
		Passed first = house(address);
		Passed second = house(address);
		Passed third = house(address);
		for (Passed passed : List.of(first, second, third)) {
			checked(passed, owner());
		}
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		SurveyApiTestSupport.ok(createTarget(second.leadId(), null));
		List<String> cases = jdbc.queryForList("select id from inventory_duplicate_cases where intake_id in (?, ?) order by id",
				String.class, intakeRow(first.leadId()).get("id"), intakeRow(second.leadId()).get("id"));
		assertEquals(3, cases.size());

		List<Future<Result>> answers = together(List.of(decision(cases.get(0)), decision(cases.get(1)), decision(cases.get(2))));

		for (Future<Result> answer : answers) {
			Result result = answer.get(60, TimeUnit.SECONDS);
			assertEquals(200, result.status(), result.rawBody());
		}
	}
}
