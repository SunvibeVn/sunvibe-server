package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.listing.core.application.usecase.ListingPublicationCommandUseCase;

/**
 * The public read beside E05's stops, an owner's account lock and a second agreement on the unit, on PostgreSQL
 * (listing-publish E06-S7 spec PB-7; ADR-012 decision 2, D12-13; US-5 AC-6, E-9 public half): a suspension committed
 * before the put live refuses it and the code stays 404; a held put live makes a suspension wait, and the code answers
 * 404 from the suspension's commit; an owner's account lock hides the listing at the next read and its end shows it
 * again; agreement A ended and B activated on the same unit with A's terms leaves the listing of A at 404. Each
 * interleaving is forced with the held transactions of {@link AgreementStopApiTestBase} and seen in {@code pg_locks};
 * a sleep proves no order (testing.md). A Linux run rests on the pull request's CI (SV-068). Needs Docker.
 */
class ListingPublicRaceApiPostgresTest extends ListingPublicReadApiTestBase {

	@Autowired
	ListingPublicationCommandUseCase publications;

	@Override
	String phonePrefix() {
		return "0985";
	}

	/** PB-7 (a): the suspension commits before the put live reads E05: the put live is refused, 404 throughout. */
	@Test
	void publicRead_suspensionBeforePutLive_refusedNotFoundThroughout() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		String code = publicCodeOf(listingId);

		List<String> answers = new ArrayList<>();
		answers.add("before " + publicRead(code).status());
		suspended(manager, drafting.chain().agreementId());
		answers.add("suspended " + publicRead(code).status());
		answers.add("put live " + codeOf(putLive(sale, listingId, "1", dataVersion(listingId))));
		answers.add("after " + publicRead(code).status());

		assertEquals(List.of("before 404", "suspended 404", "put live 409 PUT_LIVE_REFUSED", "after 404"), answers);
		assertEquals(0, publicationRows(listingId));
	}

	/**
	 * PB-7 (b): the put live holds E05's share lock; the suspension waits; the code is 404 while the put live is not
	 * committed, and 404 again from the suspension's commit.
	 */
	@Test
	void publicRead_putLiveHeldThenSuspension_notFoundFromSuspensionCommit() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		String agreementId = drafting.chain().agreementId();
		String code = publicCodeOf(listingId);
		long version = dataVersion(listingId);
		Future<Result> suspension;
		int whileHeld;
		try (Hold hold = new Hold(() -> publications.putLive(putLiveCommand(listingId, 1, version), saleViewer()))) {
			suspension = executor.submit(() -> suspendAgreement(manager, agreementId, "COMPLAINT",
					"Tenant reported a different price."));
			awaitWaiters(1);
			assertFalse(suspension.isDone(), "the suspension finished while the put live held E05's share lock");
			whileHeld = publicRead(code).status();
		}

		Result suspended = suspension.get(60, TimeUnit.SECONDS);

		assertEquals(204, suspended.status(), suspended.rawBody());
		assertEquals(List.of("LIVE", "PUBLISHED", "1"), List.of(revisionStatus(listingId, 1), state(listingId),
				String.valueOf(openSuspensions(agreementId))));
		assertEquals(List.of("held 404", "suspension committed 404"), List.of("held " + whileHeld,
				"suspension committed " + publicRead(code).status()));
	}

	/**
	 * PB-7 (c): an owner's account lock recorded in auth hides the listing at the next read; eligible again, 200. A lock
	 * followed by an unlock leaves the owner's proof of ownership dead (property-create intake D5-10: the proof is live only
	 * while the account's sign-in loss count equals the one it read), so the unlock alone still answers 404, and the
	 * account is eligible again once its proof is live again (its count aligned by SQL, as a new proof would read it).
	 */
	@Test
	void publicRead_ownerAccountLocked_notFoundThenShownAgain() throws Exception {
		Drafting live = liveListing();
		String code = publicCodeOf(live.listingId());
		String owner = live.chain().owner().userId();
		String status = jdbc.queryForObject("select user_status from users where id = ?", String.class, owner);

		List<String> answers = new ArrayList<>();
		answers.add("eligible " + publicRead(code).status());
		assertEquals(1, jdbc.update("update users set user_status = 'LOCK' where id = ?", owner));
		answers.add("locked " + publicRead(code).status());
		assertEquals(1, jdbc.update("update users set user_status = ? where id = ?", status, owner));
		answers.add("unlocked, proof dead " + publicRead(code).status());
		jdbc.update("update inventory_owner_proofs set sign_in_losses = (select sign_in_losses from users where id = ?)"
				+ " where user_id = ?", owner, owner);
		answers.add("eligible again " + publicRead(code).status());

		assertEquals(List.of("eligible 200", "locked 404", "unlocked, proof dead 404", "eligible again 200"), answers);
	}

	/**
	 * PB-7 (d): agreement A of the listing ends; agreement B is activated on the same unit with A's terms (the chain
	 * can, R-5); the listing of A answers 404 (no stored archive exists before E06-S9).
	 */
	@Test
	void publicRead_agreementEndedSecondActivated_notFound() throws Exception {
		Drafting live = liveListing();
		Chain chain = live.chain();
		String code = publicCodeOf(live.listingId());
		assertEquals(200, publicRead(code).status());
		Result termination = terminate(sale, chain.agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());

		String next = create(sale, chain.leadId(), chain.unitId());
		saveComplete(next, today(), today().plusMonths(6));
		send(next);
		confirm(chain.owner(), next, 1);
		Result activation = activate(admin, next);

		assertEquals(204, activation.status(), activation.rawBody());
		assertEquals("ACTIVE", status(next));
		Result answer = publicRead(code);
		assertEquals("404 NOT_FOUND", codeOf(answer));
	}
}
