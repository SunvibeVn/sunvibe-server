package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.listing.core.application.command.FreshnessRecordCommand;
import vn.sunvibe.listing.core.application.usecase.ListingPublicationCommandUseCase;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

/**
 * The put live racing E05's stops, an owner's account lock and the freshness record on PostgreSQL (listing-publish US-5
 * AC-1, AC-3, BR-11; listing BR-3, BR-4; E06-S6 spec E06S6-D8, D18, D12-13; PL-8 to PL-10; plan E06S6-P2): a stop
 * committed first refuses the put live; a held put live makes a stop wait until it commits; a stop held before the put
 * live's share read makes it wait, then refuse with the stop's group; a held record makes the put live wait at its row
 * lock and answer CONCURRENT_MODIFICATION, and the other way round. Each interleaving is forced with the held
 * transactions of {@link AgreementStopApiTestBase} and seen in {@code pg_locks} of this database; a sleep proves no
 * order (testing.md). A Linux run is required before the issue closes (SV-068). Needs Docker.
 */
class ListingPutLiveConcurrencyPostgresTest extends ListingPublicationApiTestBase {

	@Autowired
	ListingPublicationCommandUseCase publications;

	@Override
	String phonePrefix() {
		return "0978";
	}

	/** PL-8: a suspension committed first: 409 with the advertising right and E05's SUSPENDED; still Approved. */
	@Test
	void putLive_suspensionCommittedFirst_refusedAdvertisingRightSuspended() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		suspended(manager, drafting.chain().agreementId());

		Result answer = putLive(sale, listingId, "1", dataVersion(listingId));

		assertEquals("409 PUT_LIVE_REFUSED [reasons.ADVERTISING_RIGHT, reasonGroups.SUSPENDED]", codeAndFields(answer));
		assertEquals("APPROVED", revisionStatus(listingId, 1));
	}

	/** PL-8, E06S6-D8: the put live holds E05's share lock until it commits; the suspension waits, then commits. */
	@Test
	void putLive_heldThenSuspension_suspensionWaitsCommitsAfter() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		String agreementId = drafting.chain().agreementId();
		long version = dataVersion(listingId);
		Future<Result> suspension;
		try (Hold hold = new Hold(() -> publications.putLive(putLiveCommand(listingId, 1, version), saleViewer()))) {
			suspension = executor.submit(() -> suspendAgreement(manager, agreementId, "COMPLAINT",
					"Tenant reported a different price."));
			awaitWaiters(1);
			assertFalse(suspension.isDone(), "the suspension finished while the put live held E05's share lock");
		}

		Result suspended = suspension.get(60, TimeUnit.SECONDS);

		assertEquals(204, suspended.status(), suspended.rawBody());
		assertAll(
				() -> assertEquals("LIVE", revisionStatus(listingId, 1)),
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals(1, openSuspensions(agreementId)));
	}

	/** PL-9: a termination committed first: the listing reads archived, 409 LISTING_ARCHIVED. */
	@Test
	void putLive_terminationCommittedFirst_listingArchived() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		Result ended = terminate(sale, drafting.chain().agreementId(), ownerWithdrew());
		assertEquals(204, ended.status(), ended.rawBody());

		Result answer = putLive(sale, listingId, "1", dataVersion(listingId));

		assertEquals("409 LISTING_ARCHIVED", codeOf(answer));
		assertEquals("APPROVED", revisionStatus(listingId, 1));
	}

	/**
	 * PL-9, E06S6-D8: the termination holds the agreement row, its update done, at its history insert; the put live
	 * passes step (4) on the committed standing and waits at the share lock; released, it reads the ended agreement.
	 */
	@Test
	void putLive_terminationHeldBeforeShareLock_advertisingRightEnded() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		long version = dataVersion(listingId);
		Future<Result> termination;
		Future<Result> putLive;
		try (Hold history = holdHistory()) {
			termination = executor.submit(() -> terminate(sale, drafting.chain().agreementId(), ownerWithdrew()));
			awaitWaiters(1);
			putLive = executor.submit(() -> putLive(sale, listingId, "1", version));
			awaitWaiters(2);
			assertFalse(putLive.isDone(), "the put live finished while the termination held the agreement row");
		}

		Result ended = termination.get(60, TimeUnit.SECONDS);
		Result answer = putLive.get(60, TimeUnit.SECONDS);

		assertEquals(204, ended.status(), ended.rawBody());
		assertEquals("409 PUT_LIVE_REFUSED", codeOf(answer));
		assertEquals(List.of("reasons.ADVERTISING_RIGHT", "reasonGroups.ENDED"), fields(answer).subList(0, 2),
				answer.rawBody());
		assertEquals("APPROVED", revisionStatus(listingId, 1));
	}

	/** PL-9, E06S6-D8: the put live holds E05's share lock; the termination waits, then commits. */
	@Test
	void putLive_heldThenTermination_terminationWaitsCommitsAfter() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		String agreementId = drafting.chain().agreementId();
		long version = dataVersion(listingId);
		Future<Result> termination;
		try (Hold hold = new Hold(() -> publications.putLive(putLiveCommand(listingId, 1, version), saleViewer()))) {
			termination = executor.submit(() -> terminate(sale, agreementId, ownerWithdrew()));
			awaitWaiters(1);
			assertFalse(termination.isDone(), "the termination finished while the put live held E05's share lock");
		}

		Result ended = termination.get(60, TimeUnit.SECONDS);

		assertEquals(204, ended.status(), ended.rawBody());
		assertAll(
				() -> assertEquals("LIVE", revisionStatus(listingId, 1)),
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals("TERMINATED", status(agreementId)));
	}

	/**
	 * D12-13 (Context gap 4): the owner's account locked and committed while the put live, its reads and its write made,
	 * is held: before the release the lock is committed and seen by E05's answer, and the put live is not committed;
	 * released, the put live commits. No break of listing code turns this red; it pins a permission (plan, Task A11).
	 */
	@Test
	void putLive_ownerAccountLockedMeanwhile_commits() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		Chain chain = drafting.chain();
		long version = dataVersion(listingId);
		try (Hold hold = new Hold(() -> publications.putLive(putLiveCommand(listingId, 1, version), saleViewer()))) {
			executor.submit(() -> jdbc.update("update users set user_status = 'LOCK' where id = ?",
					chain.owner().userId())).get(30, TimeUnit.SECONDS);

			TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
			readOnly.setReadOnly(true);
			AdvertisingRightView seen = readOnly.execute(status -> answer(chain));
			assertAll(
					() -> assertEquals("LOCK", jdbc.queryForObject("select user_status from users where id = ?",
							String.class, chain.owner().userId())),
					() -> assertTrue(seen.reasonGroups().contains("OWNER"), seen.reasonGroups().toString()),
					() -> assertEquals("UNPUBLISHED", state(listingId)),
					() -> assertEquals("APPROVED", revisionStatus(listingId, 1)));
		}

		assertAll(
				() -> assertEquals("LIVE", revisionStatus(listingId, 1)),
				() -> assertEquals("PUBLISHED", state(listingId)));
	}

	/** PL-10, E06S6-D8: a held invalid record; the put live waits at its row lock, then 409; nothing live. */
	@Test
	void putLive_invalidRecordHeldFirst_concurrentModificationNothingLive() throws Exception {
		String listingId = waitingApproval().listingId();
		long version = dataVersion(listingId);
		Future<Result> putLive;
		try (Hold hold = new Hold(() -> listingCommands.recordFreshness(new FreshnessRecordCommand(listingId, version,
				FreshnessChannel.CALL, FreshnessOutcome.NOT_AVAILABLE, null, null, null), saleViewer()))) {
			putLive = executor.submit(() -> putLive(sale, listingId, "1", version));
			awaitWaiters(1);
			assertFalse(putLive.isDone(), "the put live finished while the record held the listing row");
		}

		Result answer = putLive.get(60, TimeUnit.SECONDS);

		assertEquals("409 CONCURRENT_MODIFICATION", codeOf(answer));
		assertAll(
				() -> assertEquals("APPROVED", revisionStatus(listingId, 1)),
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				() -> assertEquals(version + 1, dataVersion(listingId)),
				() -> assertEquals(List.of(), listingEventRows(listingId)));
	}

	/** PL-10: the invalid record committed, then the put live on the new version: 409 with the freshness reason. */
	@Test
	void putLive_afterInvalidRecord_refusedFreshness() throws Exception {
		String listingId = waitingApproval().listingId();
		assertEquals(201, recordFreshness(sale, listingId, invalidRecord(dataVersion(listingId))).status());

		Result answer = putLive(sale, listingId, "1", dataVersion(listingId));

		assertEquals("409 PUT_LIVE_REFUSED [reasons.FRESHNESS]", codeAndFields(answer));
		assertEquals("APPROVED", revisionStatus(listingId, 1));
	}

	/**
	 * E06S6-D18: the put live holds the listing row; an invalid record sent on the version read before it waits at its
	 * guarded update, then answers 409 with nothing of it written; sent again on the new version it is stored.
	 */
	@Test
	void record_invalidWhilePutLiveHoldsRow_concurrentThenSavedAgain() throws Exception {
		String listingId = waitingApproval().listingId();
		long version = dataVersion(listingId);
		List<String> records = freshnessRecords(listingId);
		Future<Result> record;
		try (Hold hold = new Hold(() -> publications.putLive(putLiveCommand(listingId, 1, version), saleViewer()))) {
			record = executor.submit(() -> recordFreshness(sale, listingId, invalidRecord(version)));
			awaitWaiters(1);
			assertFalse(record.isDone(), "the record finished while the put live held the listing row");
		}

		Result refused = record.get(60, TimeUnit.SECONDS);

		assertEquals("409 CONCURRENT_MODIFICATION", codeOf(refused));
		assertAll(
				() -> assertEquals(records, freshnessRecords(listingId)),
				() -> assertEquals("LIVE", revisionStatus(listingId, 1)),
				() -> assertEquals("PUBLISHED", state(listingId)));

		Result again = recordFreshness(sale, listingId, invalidRecord(dataVersion(listingId)));

		assertEquals(201, again.status(), again.rawBody());
		assertEquals((version + 2) + " false", freshnessRecords(listingId).getLast());
	}
}
