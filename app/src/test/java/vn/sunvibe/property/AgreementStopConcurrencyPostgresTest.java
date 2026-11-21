package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowCallbackHandler;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;

/**
 * The stops racing the other writers of an agreement on PostgreSQL (brokerage-agreement US-5 AC-6, BR-5, E-8; E05-S8 spec
 * S8-D4, S8-D8, S8-D18; RC-1 to RC-8, EX-4 forced; plan E05S8-P9). "X first" runs X to completion, then Y with the data
 * version Y's caller read before X. "X held" makes X wait at its history insert after its guarded update (the history
 * table held in SHARE mode), starts Y, waits until this database shows both waiting, then lets X commit. A stop decides
 * on the state read under both row locks, so it ends the agreement in both orders; every other command that read the
 * state before a stop is refused and writes nothing. Needs Docker; a Linux run is the evidence of a race test
 * (testing.md).
 */
class AgreementStopConcurrencyPostgresTest extends AgreementStopApiTestBase {

	private static final String RELEASE_REASON = "The price was checked with the owner.";

	@Override
	String phonePrefix() {
		return "0921";
	}

	// ==================== RC-1 to RC-4: the termination against the writers before the agreement took effect ====================

	/** RC-1: the termination first refuses the activation read before it; the activation held first is then ended. */
	@Test
	void terminateAndActivate_eitherOrder_terminatedVersionCancelled() throws Exception {
		Chain first = confirmed(today().plusDays(10), today().plusMonths(6));
		String id = first.agreementId();
		long readBefore = rowVersion(id);
		assertEquals(204, terminate(sale, id, ownerWithdrew()).status());
		WriteSnapshot terminated = snapshot(id);
		int notices = notices(first.owner());

		Result activation = activate(admin, id, readBefore, 1);

		assertRefused(activation, "AGREEMENT_NOT_AWAITING_ACTIVATION", "CONCURRENT_MODIFICATION");
		assertNothingWritten(terminated);
		assertEquals(0, eventCount(id, "ACTIVATED"));
		assertEquals(notices, notices(first.owner()));
		assertEquals("TERMINATED", status(id));
		assertEquals("CANCELLED", versionStatus(id, 1));

		Chain second = confirmed(today().plusDays(10), today().plusMonths(6));
		List<Result> answers = heldThenStop(() -> activate(admin, second.agreementId()),
				() -> terminate(sale, second.agreementId(), ownerWithdrew()));

		assertEquals(204, answers.get(0).status(), answers.get(0).rawBody());
		assertEquals(204, answers.get(1).status(), answers.get(1).rawBody());
		assertEquals("TERMINATED", status(second.agreementId()));
		assertEquals("CANCELLED", versionStatus(second.agreementId(), 1));
		assertEquals(List.of("ACTIVATED 1 " + admin.userId(), "TERMINATED 1 " + sale.userId()),
				lastTwo(second.agreementId()));
		AdvertisingRightView answer = answer(second);
		assertTrue(answer.ended(), answer.toString());
		assertTrue(answer.reasonGroups().contains("ENDED"), answer.toString());
		assertFalse(answer.allowed(), answer.toString());
	}

	/** RC-2: the same with v1 rejected and v2 confirmed: both orders end TERMINATED with v2 CANCELLED. */
	@Test
	void terminateAndActivateRedraftedVersion_eitherOrder_terminated() throws Exception {
		Chain first = redraftedAndConfirmed();
		String id = first.agreementId();
		long readBefore = rowVersion(id);
		assertEquals(204, terminate(sale, id, ownerWithdrew()).status());
		WriteSnapshot terminated = snapshot(id);

		assertRefused(activate(admin, id, readBefore, 2), "AGREEMENT_NOT_AWAITING_ACTIVATION", "CONCURRENT_MODIFICATION");
		assertNothingWritten(terminated);
		assertEquals("TERMINATED", status(id));
		assertEquals("CANCELLED", versionStatus(id, 2));
		assertEquals(0, eventCount(id, "ACTIVATED"));

		Chain second = redraftedAndConfirmed();
		String other = second.agreementId();
		List<Result> answers = heldThenStop(() -> activate(admin, other, rowVersion(other), 2),
				() -> terminate(sale, other, ownerWithdrew()));

		assertEquals(204, answers.get(0).status(), answers.get(0).rawBody());
		assertEquals(204, answers.get(1).status(), answers.get(1).rawBody());
		assertEquals("TERMINATED", status(other));
		assertEquals("CANCELLED", versionStatus(other, 2));
		assertEquals(List.of("ACTIVATED 2 " + admin.userId(), "TERMINATED 2 " + sale.userId()), lastTwo(other));
	}

	/** RC-3: the termination first refuses the new draft; the new draft held first is cancelled by the termination. */
	@Test
	void terminateAndNewDraft_eitherOrder_terminated() throws Exception {
		Chain first = rejected();
		String id = first.agreementId();
		long readBefore = rowVersion(id);
		assertEquals(204, terminate(sale, id, ownerWithdrew()).status());
		WriteSnapshot terminated = snapshot(id);

		assertRefused(openDraft(id, readBefore), "AGREEMENT_NEW_DRAFT_NOT_ALLOWED", "CONCURRENT_MODIFICATION");
		assertNothingWritten(terminated);
		assertEquals("TERMINATED", status(id));
		assertEquals(1, count("select count(*) from brokerage_agreement_versions where agreement_id = ?", id));

		Chain second = rejected();
		String other = second.agreementId();
		List<Result> answers = heldThenStop(() -> openDraft(other, rowVersion(other)),
				() -> terminate(sale, other, ownerWithdrew()));

		assertEquals(204, answers.get(0).status(), answers.get(0).rawBody());
		assertEquals(204, answers.get(1).status(), answers.get(1).rawBody());
		assertEquals("TERMINATED", status(other));
		assertEquals("CANCELLED", versionStatus(other, 2));
		assertEquals(List.of("DRAFT_OPENED 2 " + sale.userId(), "TERMINATED 2 " + sale.userId()), lastTwo(other));
	}

	/** RC-4: the termination first refuses the owner's confirmation; the confirmation first is cancelled with v1. */
	@Test
	void terminateAndOwnerConfirm_eitherOrder_terminated() throws Exception {
		Chain first = sent(today().plusDays(10), today().plusMonths(6));
		String id = first.agreementId();
		assertEquals(204, terminate(sale, id, ownerWithdrew()).status());
		WriteSnapshot terminated = snapshot(id);

		Result confirmation = confirmResult(first.owner(), id, 1);

		assertEquals(409, confirmation.status(), confirmation.rawBody());
		assertNothingWritten(terminated);
		assertEquals("TERMINATED", status(id));
		assertEquals("CANCELLED", versionStatus(id, 1));
		assertEquals(0, eventCount(id, "OWNER_CONFIRMED"));

		Chain second = sent(today().plusDays(10), today().plusMonths(6));
		confirm(second.owner(), second.agreementId(), 1);

		assertEquals(204, terminate(sale, second.agreementId(), ownerWithdrew()).status());

		assertEquals("TERMINATED", status(second.agreementId()));
		Map<String, Object> version = versionRow(second.agreementId(), 1);
		assertEquals("CANCELLED", version.get("status"));
		assertEquals("CONFIRMED", version.get("owner_answer"));
	}

	// ==================== RC-5: the cancellation keeps BR-5's check ====================

	/** RC-5: with the activation and with the owner's confirmation, the first to commit wins; a refused one writes nothing. */
	@Test
	void cancelAndActivateOrConfirm_firstCommitWins() throws Exception {
		Chain cancelledFirst = confirmed(today().plusDays(10), today().plusMonths(6));
		String id = cancelledFirst.agreementId();
		long readBefore = rowVersion(id);
		assertEquals(204, cancel(sale, id, readBefore).status());
		WriteSnapshot cancelled = snapshot(id);
		assertRefused(activate(admin, id, readBefore, 1), "AGREEMENT_NOT_AWAITING_ACTIVATION", "CONCURRENT_MODIFICATION");
		assertNothingWritten(cancelled);
		assertEquals("CANCELLED", status(id));

		Chain activatedFirst = confirmed(today().plusDays(10), today().plusMonths(6));
		id = activatedFirst.agreementId();
		readBefore = rowVersion(id);
		assertEquals(204, activate(admin, id, readBefore, 1).status());
		WriteSnapshot active = snapshot(id);
		assertCode(409, "AGREEMENT_TOOK_EFFECT", cancel(sale, id, readBefore));
		assertNothingWritten(active);
		assertEquals("ACTIVE", status(id));

		Chain cancelledBeforeAnswer = sent(today().plusDays(10), today().plusMonths(6));
		id = cancelledBeforeAnswer.agreementId();
		assertEquals(204, cancel(sale, id, rowVersion(id)).status());
		cancelled = snapshot(id);
		Result confirmation = confirmResult(cancelledBeforeAnswer.owner(), id, 1);
		assertEquals(409, confirmation.status(), confirmation.rawBody());
		assertNothingWritten(cancelled);
		assertEquals("CANCELLED", status(id));

		Chain answeredFirst = sent(today().plusDays(10), today().plusMonths(6));
		id = answeredFirst.agreementId();
		readBefore = rowVersion(id);
		confirm(answeredFirst.owner(), id, 1);
		WriteSnapshot confirmed = snapshot(id);
		Result stale = cancel(sale, id, readBefore);
		assertCode(409, "CONCURRENT_MODIFICATION", stale);
		assertEquals("expectedDataVersion", stale.body().path("errors").path(0).path("field").asString());
		assertNothingWritten(confirmed);
		assertEquals("AWAITING_CONFIRMATION", status(id));
	}

	// ==================== RC-6: the stops among themselves and against the expiry store ====================

	/** RC-6, the first two pairs: suspension or release and termination; both orders end TERMINATED. */
	@Test
	void suspendOrReleaseAndTerminate_eitherOrder_terminated() throws Exception {
		Chain suspendedFirst = inEffect();
		String id = suspendedFirst.agreementId();
		List<Result> answers = heldThenStop(() -> suspendAgreement(manager, id, "COMPLAINT", null),
				() -> terminate(sale, id, ownerWithdrew()));
		assertEquals(204, answers.get(0).status(), answers.get(0).rawBody());
		assertEquals(204, answers.get(1).status(), answers.get(1).rawBody());
		assertEquals("TERMINATED", status(id));
		assertEquals(1, openSuspensions(id));
		assertEquals(List.of("SUSPENDED 1 " + manager.userId(), "TERMINATED 1 " + sale.userId()), lastTwo(id));

		Chain terminatedFirst = inEffect();
		String second = terminatedFirst.agreementId();
		assertEquals(204, terminate(sale, second, ownerWithdrew()).status());
		WriteSnapshot terminated = snapshot(second);
		assertCode(409, "AGREEMENT_ENDED", suspendAgreement(manager, second, "COMPLAINT", null));
		assertNothingWritten(terminated);

		Chain releasedFirst = inEffect();
		String third = releasedFirst.agreementId();
		suspended(manager, third);
		long releaseRead = rowVersion(third);
		answers = heldThenStop(() -> releaseSuspension(admin, third, releaseRead, RELEASE_REASON),
				() -> terminate(sale, third, ownerWithdrew()));
		assertEquals(204, answers.get(0).status(), answers.get(0).rawBody());
		assertEquals(204, answers.get(1).status(), answers.get(1).rawBody());
		assertEquals("TERMINATED", status(third));
		assertEquals(0, openSuspensions(third));
		assertEquals(List.of("SUSPENSION_RELEASED 1 " + admin.userId(), "TERMINATED 1 " + sale.userId()),
				lastTwo(third));

		Chain terminatedBeforeRelease = inEffect();
		String fourth = terminatedBeforeRelease.agreementId();
		suspended(manager, fourth);
		long readBefore = rowVersion(fourth);
		assertEquals(204, terminate(sale, fourth, ownerWithdrew()).status());
		terminated = snapshot(fourth);
		assertCode(409, "AGREEMENT_ENDED", releaseSuspension(admin, fourth, readBefore, RELEASE_REASON));
		assertNothingWritten(terminated);
		assertEquals("TERMINATED", status(fourth));
		assertEquals(1, openSuspensions(fourth));
	}

	/** RC-6, the third pair: past its end the termination answers AGREEMENT_ENDED in both orders; one EXPIRED row. */
	@Test
	void terminateAndExpiryStore_eitherOrder_expiredOnce() throws Exception {
		Chain terminationFirst = inEffect();
		String id = terminationFirst.agreementId();
		movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));
		WriteSnapshot before = snapshot(id);
		assertCode(409, "AGREEMENT_ENDED", terminate(sale, id, ownerWithdrew()));
		assertNothingWritten(before);
		commands.storeDueExpiries();
		assertEquals("EXPIRED", status(id));
		assertEquals(1, eventCount(id, "EXPIRED"));
		assertEquals(0, eventCount(id, "TERMINATED"));

		Chain storeFirst = inEffect();
		String second = storeFirst.agreementId();
		movePeriod(second, 1, today().minusMonths(3), today().minusDays(1));
		Future<Integer> run;
		Future<Result> termination;
		try (Hold history = holdHistory()) {
			run = executor.submit(() -> commands.storeDueExpiries());
			awaitWaiters(1);
			termination = executor.submit(() -> terminate(sale, second, ownerWithdrew()));
			awaitWaiters(2);
		}

		assertEquals(1, run.get(60, TimeUnit.SECONDS));
		assertCode(409, "AGREEMENT_ENDED", termination.get(60, TimeUnit.SECONDS));
		assertEquals("EXPIRED", status(second));
		assertEquals(1, eventCount(second, "EXPIRED"));
		assertEquals(0, eventCount(second, "TERMINATED"));
	}

	// ==================== RC-7, RC-8: a write held between its flush and its commit ====================

	/**
	 * RC-7: the start store holds its guarded update uncommitted while the manager's suspension issues its locking read;
	 * the suspension then reads v1 EFFECTIVE: exactly one VERSION_STARTED row, then one SUSPENDED row.
	 */
	@Test
	void startStoreHeldUncommitted_suspension_oneStartedRow() throws Exception {
		Chain chain = activated(today().plusDays(2), today().plusMonths(6));
		String id = chain.agreementId();
		movePeriod(id, 1, today().minusDays(1), today().plusMonths(6));
		detail(manager, id);
		int outbox = outboxRows(chain.unitId());
		Future<Integer> run;
		Future<Result> suspension;
		try (Hold history = holdHistory()) {
			run = executor.submit(() -> commands.storeDueStarts());
			awaitWaiters(1);
			suspension = executor.submit(() -> suspendAgreement(manager, id, "COMPLAINT", "Tenant reported a different"
					+ " price."));
			awaitWaiters(2);
		}

		assertEquals(1, run.get(60, TimeUnit.SECONDS));
		Result suspended = suspension.get(60, TimeUnit.SECONDS);
		assertEquals(204, suspended.status(), suspended.rawBody());
		assertEquals("SUSPENDED", status(id));
		assertEquals("EFFECTIVE", versionStatus(id, 1));
		assertEquals(1, eventCount(id, "VERSION_STARTED"));
		assertEquals(startOf(today().minusDays(1)), eventAt(id, "VERSION_STARTED"));
		assertEquals(List.of("VERSION_STARTED 1 -", "SUSPENDED 1 " + manager.userId()), lastTwo(id));
		assertEquals(1, openSuspensions(id));
		assertEquals(outbox + 1, outboxRows(chain.unitId()));
	}

	/**
	 * RC-8: the owner's confirmation holds its guarded update uncommitted while the Sale's termination issues its locking
	 * read; the termination then cancels v1 with the owner's answer kept.
	 */
	@Test
	void ownerConfirmHeldUncommitted_termination_ownerAnswerKept() throws Exception {
		Chain chain = sent(today().plusDays(10), today().plusMonths(6));
		String id = chain.agreementId();
		detail(sale, id);

		List<Result> answers = heldThenStop(() -> confirmResult(chain.owner(), id, 1),
				() -> terminate(sale, id, ownerWithdrew()));

		assertEquals(204, answers.get(0).status(), answers.get(0).rawBody());
		assertEquals(204, answers.get(1).status(), answers.get(1).rawBody());
		assertEquals("TERMINATED", status(id));
		Map<String, Object> version = versionRow(id, 1);
		assertEquals("CANCELLED", version.get("status"));
		assertEquals("CONFIRMED", version.get("owner_answer"));
		assertEquals(chain.owner().userId(), version.get("owner_answered_by"));
		assertNotNull(version.get("owner_answered_at"));
		assertNotNull(version.get("owner_statement_version"));
		assertEquals(List.of("OWNER_CONFIRMED 1 " + chain.owner().userId(), "TERMINATED 1 " + sale.userId()),
				lastTwo(id));
	}

	// ==================== the expiry store against itself and the create's step ====================

	/** Review Focus 2: two runs of the store on the same due agreement: one stores it, the other is skipped. */
	@Test
	void storeDueExpiries_twoConcurrentRuns_oneExpiredRow() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));
		long rowVersion = rowVersion(id);
		Future<Integer> first;
		Future<Integer> second;
		try (Hold history = holdHistory()) {
			first = executor.submit(() -> commands.storeDueExpiries());
			awaitWaiters(1);
			second = executor.submit(() -> commands.storeDueExpiries());
			awaitWaiters(2);
		}

		assertEquals(1, first.get(60, TimeUnit.SECONDS) + second.get(60, TimeUnit.SECONDS));
		assertEquals("EXPIRED", status(id));
		assertEquals(rowVersion + 1, rowVersion(id));
		assertEquals(1, eventCount(id, "EXPIRED"));
	}

	/**
	 * EX-4 forced: the earlier agreement past its end is terminated, cancelled or deleted by a transaction that holds its
	 * row while the create waits past its lookup; the create decides on what committed: 201, the earlier one kept as it
	 * was stored, no EXPIRED row written for it.
	 */
	@Test
	void create_earlierEndedOrDeletedBetweenLookupAndLock_created() throws Exception {
		for (String change : List.of("TERMINATED", "CANCELLED", "DELETED")) {
			Chain chain = inEffect();
			String id = chain.agreementId();
			movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));
			long rowVersion = rowVersion(id);
			int events = events(id).size();
			Future<Result> creation;
			try (Hold earlier = new Hold(() -> endInSql(id, change))) {
				creation = executor.submit(() -> createResult(sale, chain.leadId(), chain.unitId()));
				awaitWaiters(1);
				assertFalse(creation.isDone(), change);
			}

			Result created = creation.get(60, TimeUnit.SECONDS);

			assertEquals(201, created.status(), change + " " + created.rawBody());
			assertEquals("DRAFTING", status(created.body().path("agreementId").asString()));
			if ("DELETED".equals(change)) {
				assertEquals(0, count("select count(*) from brokerage_agreements where id = ?", id));
			} else {
				assertEquals(change, status(id));
				assertEquals(rowVersion + 1, rowVersion(id), change);
				assertEquals(events, events(id).size(), change);
				assertEquals(0, eventCount(id, "EXPIRED"), change);
			}
		}
	}

	// ==================== helpers ====================

	/**
	 * Runs {@code first} until it waits at its history insert after its guarded update, starts {@code stop}, waits until
	 * both wait on a lock, then lets {@code first} commit; answers both results, {@code first}'s first.
	 */
	private List<Result> heldThenStop(Callable<Result> first, Callable<Result> stop) throws Exception {
		Future<Result> held;
		Future<Result> stopping;
		try (Hold history = holdHistory()) {
			held = executor.submit(first);
			awaitWaiters(1);
			stopping = executor.submit(stop);
			awaitWaiters(2);
			assertFalse(stopping.isDone(), "the stop finished while the other writer held its rows");
		}
		return List.of(held.get(60, TimeUnit.SECONDS), stopping.get(60, TimeUnit.SECONDS));
	}

	/** The earlier agreement's row held FOR UPDATE while it is ended or deleted in SQL, uncommitted. */
	private void endInSql(String agreementId, String change) {
		jdbc.query("select id from brokerage_agreements where id = ? for update", (RowCallbackHandler) row -> { },
				agreementId);
		Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
		switch (change) {
			case "TERMINATED" -> jdbc.update("update brokerage_agreements set status = 'TERMINATED', ended_at = ?,"
					+ " termination_type = 'OWNER_WITHDREW', termination_reported_on = ?, termination_channel = 'PHONE_CALL',"
					+ " version = version + 1 where id = ?", now, Date.valueOf(today()), agreementId);
			case "CANCELLED" -> jdbc.update("update brokerage_agreements set status = 'CANCELLED', ended_at = ?,"
					+ " version = version + 1 where id = ?", now, agreementId);
			default -> {
				jdbc.update("delete from brokerage_agreement_access_logs where agreement_id = ?", agreementId);
				jdbc.update("delete from brokerage_agreement_events where agreement_id = ?", agreementId);
				jdbc.update("delete from brokerage_agreement_versions where agreement_id = ?", agreementId);
				jdbc.update("delete from brokerage_agreements where id = ?", agreementId);
			}
		}
	}

	/** v1 rejected by its owner: the agreement DRAFTING with no pending version. */
	private Chain rejected() throws Exception {
		Chain chain = sent(today().plusDays(10), today().plusMonths(6));
		Result rejection = reject(chain.owner(), chain.agreementId(), 1);
		assertEquals(204, rejection.status(), rejection.rawBody());
		return chain;
	}

	/** v1 rejected, v2 drafted again, sent and confirmed: v2 awaits activation. */
	private Chain redraftedAndConfirmed() throws Exception {
		Chain chain = rejected();
		String id = chain.agreementId();
		Result draft = openDraft(id, rowVersion(id));
		assertEquals(204, draft.status(), draft.rawBody());
		saveComplete(id, today().plusDays(10), today().plusMonths(6));
		send(id);
		confirm(chain.owner(), id, 2);
		return chain;
	}

	private List<String> lastTwo(String agreementId) {
		List<String> events = events(agreementId);
		return events.subList(events.size() - 2, events.size());
	}

	private static void assertRefused(Result answer, String... codes) {
		assertEquals(409, answer.status(), answer.rawBody());
		assertTrue(List.of(codes).contains(answer.body().path("code").asString()), answer.rawBody());
	}
}
