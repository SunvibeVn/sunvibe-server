package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The live difference read over HTTP on PostgreSQL (listing-publish US-4 AC-2, BR-7; E06-S6 spec 3.7, 4.3, E06S6-D12;
 * PL-12): a revision compared with the live one by value, its terms taken again from the same version counting as no
 * change; no live revision and the live revision itself answering 200; the one 404. Needs Docker.
 */
class ListingLiveDifferenceApiPostgresTest extends ListingPublicationApiTestBase {

	private static final String NEW_TITLE = "Phòng trọ mới, gần biển Mỹ Khê";

	@Override
	String phonePrefix() {
		return "0980";
	}

	/** PL-12: the live revision copied, retitled, its terms taken again (same values, a later time), submitted. */
	@Test
	void liveDifference_compared_titleOnly() throws Exception {
		String listingId = waitingApproval().listingId();
		putLiveOk(listingId);
		assertEquals(201, copy(sale, listingId, "1", dataVersion(listingId)).status());
		Result saved = saveContent(sale, listingId, "2", contentBody(dataVersion(listingId), NEW_TITLE, DESCRIPTION,
				everyFeature()));
		assertEquals(200, saved.status(), saved.rawBody());
		Result captured = captureTerms(sale, listingId, "2", dataVersion(listingId), "IN_EFFECT");
		assertEquals(200, captured.status(), captured.rawBody());
		assertEquals(200, submit(sale, listingId, "2", dataVersion(listingId)).status());

		Result answer = liveDifference(reviewer, listingId, "2");

		assertEquals(200, answer.status(), answer.rawBody());
		assertAll(
				() -> assertEquals("2 COMPARED [\"TITLE\"]", answer.body().path("revisionNo").asInt() + " "
						+ answer.body().path("comparison").asString() + " " + answer.body().path("changedParts")),
				() -> assertEquals("1 " + TITLE + " IN_EFFECT", answer.body().path("live").path("revisionNo").asInt()
						+ " " + answer.body().path("live").path("title").asString() + " "
						+ answer.body().path("live").path("terms").path("source").asString()));
	}

	/** PL-12: before any put live, NO_LIVE_REVISION; after it, for the live revision, IS_LIVE_REVISION; 200 both. */
	@Test
	void liveDifference_noLiveAndIsLive_200() throws Exception {
		String listingId = waitingApproval().listingId();

		Result before = liveDifference(reviewer, listingId, "1");
		putLiveOk(listingId);
		Result after = liveDifference(reviewer, listingId, "1");

		assertAll(
				() -> assertEquals("{\"revisionNo\":1,\"comparison\":\"NO_LIVE_REVISION\",\"live\":null,"
						+ "\"changedParts\":[]}", before.status() == 200 ? before.body().toString() : before.rawBody()),
				() -> assertEquals("{\"revisionNo\":1,\"comparison\":\"IS_LIVE_REVISION\",\"live\":null,"
						+ "\"changedParts\":[]}", after.status() == 200 ? after.body().toString() : after.rawBody()));
	}

	/** PL-12: an unknown and a malformed revision number of a listing with a live revision, an unknown listing: 404. */
	@Test
	void liveDifference_notFoundCases_404() throws Exception {
		String listingId = waitingApproval().listingId();
		putLiveOk(listingId);

		assertAll(
				() -> assertEquals("404 NOT_FOUND", codeOf(liveDifference(reviewer, listingId, "9"))),
				() -> assertEquals("404 NOT_FOUND", codeOf(liveDifference(reviewer, listingId, "01"))),
				() -> assertEquals("404 NOT_FOUND", codeOf(liveDifference(reviewer, UNKNOWN, "1"))));
	}
}
