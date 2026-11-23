package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.usecase.ListingReviewCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;

/**
 * A decision racing another command on PostgreSQL (listing-publish US-4 AC-3; listing BR-3; E06-S4 spec 3.3, E06S4-D5
 * step 5; RV-11): the first decision holds its transaction open through the use case; a later decision or a withdrawal on
 * the same data version over HTTP waits on the listing row at its guarded update and, once the first commits, answers
 * CONCURRENT_MODIFICATION; one decision row. Interleavings are forced with the held locks of
 * {@link AgreementStopApiTestBase}; a sleep proves no order (testing.md). A Linux run is required before the issue
 * closes (SV-068). Needs Docker.
 */
class ListingReviewConcurrencyPostgresTest extends ListingReviewApiTestBase {

	@Autowired
	ListingReviewCommandUseCase reviewCommands;

	@Override
	String phonePrefix() {
		return "0901";
	}

	/** RV-11: two reviewers decide the same revision at once: the later waits, then 409; one decision row. */
	@Test
	void decide_heldFirstDecision_laterWaitsThenConcurrentModification() throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);
		Future<Result> later;
		try (Hold hold = new Hold(() -> reviewCommands.decide(approvalCommand(listingId, version), reviewerViewer()))) {
			later = executor.submit(() -> decide(admin, listingId, "1", returnBody(version, List.of("OTHER"), NOTE)));
			awaitWaiters(1);
			assertFalse(later.isDone());
		}

		Result answer = later.get(60, TimeUnit.SECONDS);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals(1, decisionRows(listingId));
		assertEquals("APPROVED", revisionStatus(listingId, 1));
		assertEquals(version + 1, dataVersion(listingId));
	}

	/** RV-11: a Sale's withdrawal racing a decision: the withdrawal waits, then 409; the revision stays decided. */
	@Test
	void withdraw_heldDecision_withdrawWaitsThenConcurrentModification() throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);
		Future<Result> withdrawal;
		try (Hold hold = new Hold(() -> reviewCommands.decide(approvalCommand(listingId, version), reviewerViewer()))) {
			withdrawal = executor.submit(() -> withdraw(sale, listingId, "1", version));
			awaitWaiters(1);
			assertFalse(withdrawal.isDone());
		}

		Result answer = withdrawal.get(60, TimeUnit.SECONDS);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals("APPROVED", revisionStatus(listingId, 1));
		assertEquals(1, decisionRows(listingId));
		assertEquals(version + 1, dataVersion(listingId));
	}

	private static RevisionDecisionCommand approvalCommand(String listingId, long version) {
		return new RevisionDecisionCommand(listingId, 1, version, ReviewOutcome.APPROVED, ReviewCheck.VERSION_1,
				List.of(), null, null);
	}

	private ListingViewer reviewerViewer() {
		return new ListingViewer(reviewer.userId(), Set.of("ROLE_LISTING_REVIEWER"));
	}
}
