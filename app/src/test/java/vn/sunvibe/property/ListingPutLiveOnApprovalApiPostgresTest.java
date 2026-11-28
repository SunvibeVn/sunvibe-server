package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The approval's try over HTTP on PostgreSQL (listing-publish US-5 AC-3 moment 1; E06-S6 spec 3.2, E06S6-D7; PL-3): an
 * approval while every condition holds answers LIVE, the listing Published, the version moved once, the put live's
 * history row without actor and one event row; an approval while the freshness fails answers APPROVED with no event.
 * Needs Docker.
 */
class ListingPutLiveOnApprovalApiPostgresTest extends ListingPublicationApiTestBase {

	@Override
	String phonePrefix() {
		return "0976";
	}

	/** PL-3: a valid confirmation recorded before the submission; the approval puts the revision live. */
	@Test
	void approve_allHold_liveAnswerPublishedOneEvent() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		Result recorded = recordFreshness(sale, listingId, validRecord(dataVersion(listingId)));
		assertEquals(201, recorded.status(), recorded.rawBody());
		// listing-publish E06-S7 (S-14): the valid record's two event rows, so the one row below is the approval's
		clearListingEventRows(listingId);
		submitted(drafting);
		long version = dataVersion(listingId);
		Set<String> history = new HashSet<>(history(listingId));

		Result answer = decide(reviewer, listingId, "1", approval(version));

		assertEquals(200, answer.status(), answer.rawBody());
		history.add("REVISION_APPROVED 1 " + reviewer.userId());
		history.add("REVISION_PUT_LIVE_ON_APPROVAL 1 -");
		assertAll(
				() -> assertEquals(List.of("LIVE", String.valueOf(version + 1)), List.of(
						answer.body().path("status").asString(), answer.body().path("dataVersion").asString())),
				() -> assertEquals(version + 1, dataVersion(listingId)),
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals("LIVE true false", revisionTimes(listingId, 1)),
				() -> assertEquals(history, new HashSet<>(history(listingId))),
				() -> assertEquals(1, decisionRows(listingId)),
				() -> assertEquals(List.of(eventRow(listingId)), listingEventRows(listingId)));
	}

	/** PL-3: no valid confirmation, every other condition holding: APPROVED, Unpublished, the version moved once. */
	@Test
	void approve_freshnessFails_approvedNoEventVersionPlusOne() throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);

		Result answer = decide(reviewer, listingId, "1", approval(version));

		assertEquals(200, answer.status(), answer.rawBody());
		assertAll(
				() -> assertEquals("APPROVED", answer.body().path("status").asString()),
				() -> assertEquals("APPROVED", revisionStatus(listingId, 1)),
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				() -> assertEquals(version + 1, dataVersion(listingId)),
				() -> assertEquals(List.of(), listingEventRows(listingId)));
	}
}
