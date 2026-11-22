package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * Pin P-2 of listing-publish E06-S4 (spec section 8, U-6, U-20, U-24): the revision commands and reads of E06-S2 over
 * HTTP on a revision a review decided. Revision 1 is completed and submitted through the API, then set by SQL to each
 * of the statuses a decision makes reachable (no decision row: no code of the pinned commit reads one). A decided
 * revision is locked as a Pending review one is, is read with its content, is not the listing's open revision, and lets
 * a new Draft start beside it. Needs Docker.
 */
class ListingDecidedRevisionApiPostgresTest extends ListingRevisionApiTestBase {

	private static final List<String> DECIDED = List.of("NEEDS_CHANGES", "REJECTED", "APPROVED");

	@Override
	String phonePrefix() {
		return "0909";
	}

	/** Save, terms capture and submission answer REVISION_NOT_DRAFT, withdrawal REVISION_NOT_PENDING; nothing written. */
	@Test
	void commands_decidedRevision_refusedNothingWritten() throws Exception {
		for (String status : DECIDED) {
			String listingId = decided(status);
			long version = dataVersion(listingId);
			String row = revisionRow(listingId);
			int history = count("select count(*) from listing_history where listing_id = ?", listingId);
			Map<String, Result> answers = new LinkedHashMap<>();
			answers.put("save", saveContent(sale, listingId, "1", completeContentBody(version)));
			answers.put("terms", captureTerms(sale, listingId, "1", version, "IN_EFFECT"));
			answers.put("submit", submit(sale, listingId, "1", version));
			answers.put("withdraw", withdraw(sale, listingId, "1", version));

			for (Map.Entry<String, Result> answer : answers.entrySet()) {
				assertCode(409, "withdraw".equals(answer.getKey()) ? "REVISION_NOT_PENDING" : "REVISION_NOT_DRAFT",
						answer.getValue());
			}
			assertEquals(version, dataVersion(listingId), status);
			assertEquals(row, revisionRow(listingId), status);
			assertEquals(history, count("select count(*) from listing_history where listing_id = ?", listingId), status);
		}
	}

	/** The revision read answers its content and no problem; the listing read lists it and has no open revision. */
	@Test
	void reads_decidedRevision_contentAndNoOpenRevision() throws Exception {
		for (String status : DECIDED) {
			String listingId = decided(status);

			Result read = getRevision(sale, listingId, "1");

			assertEquals(200, read.status(), read.rawBody());
			JsonNode revision = read.body();
			assertEquals(status, revision.path("status").asString());
			assertEquals(TITLE, revision.path("title").asString());
			assertEquals(DESCRIPTION, revision.path("description").asString());
			assertFalse(revision.path("termsSnapshot").isNull(), revision.toString());
			assertFalse(revision.path("termsSnapshot").isMissingNode(), revision.toString());
			assertFalse(revision.path("unitSnapshot").isNull(), revision.toString());
			assertFalse(revision.path("unitSnapshot").isMissingNode(), revision.toString());
			assertFalse(revision.path("submittedAt").isNull(), revision.toString());
			assertTrue(revision.path("problems").isArray(), revision.toString());
			assertEquals(0, revision.path("problems").size(), revision.toString());
			Result listingRead = getListing(sale, listingId);
			assertEquals(200, listingRead.status(), listingRead.rawBody());
			JsonNode listing = listingRead.body();
			assertTrue(listing.path("openRevisionNo").isNull(), listing.toString());
			assertEquals(1, listing.path("revisions").size(), listing.toString());
			assertEquals(1, listing.path("revisions").path(0).path("revisionNo").asInt());
			assertEquals(status, listing.path("revisions").path(0).path("status").asString());
		}
	}

	/** listing-publish BR-6: a decided revision is not open, so the next Draft starts beside it. */
	@Test
	void start_besideApprovedOrNeedsChanges_draftTwo() throws Exception {
		for (String status : List.of("APPROVED", "NEEDS_CHANGES")) {
			String listingId = decided(status);

			Result started = startRevision(sale, listingId, dataVersion(listingId));

			assertEquals(201, started.status(), started.rawBody());
			assertEquals(2, started.body().path("revisionNo").asInt(), status);
			assertEquals("DRAFT", started.body().path("status").asString(), status);
			assertEquals(status, jdbc.queryForObject("select status from listing_revisions where listing_id = ?"
					+ " and revision_no = 1", String.class, listingId));
		}
	}

	/**
	 * Pin P-8 of listing-publish E06-S6 (spec section 8, S-1, S-9): a Live or Replaced revision is locked as a decided
	 * one is; save, terms capture and submission answer REVISION_NOT_DRAFT, withdrawal REVISION_NOT_PENDING; nothing
	 * written.
	 */
	@Test
	void commands_liveOrReplacedRevision_refusedNothingWritten() throws Exception {
		for (String status : List.of("LIVE", "REPLACED")) {
			String listingId = decided(status);
			long version = dataVersion(listingId);
			String row = revisionRow(listingId);
			int history = count("select count(*) from listing_history where listing_id = ?", listingId);
			Map<String, Result> answers = new LinkedHashMap<>();
			answers.put("save", saveContent(sale, listingId, "1", completeContentBody(version)));
			answers.put("terms", captureTerms(sale, listingId, "1", version, "IN_EFFECT"));
			answers.put("submit", submit(sale, listingId, "1", version));
			answers.put("withdraw", withdraw(sale, listingId, "1", version));

			for (Map.Entry<String, Result> answer : answers.entrySet()) {
				assertCode(409, "withdraw".equals(answer.getKey()) ? "REVISION_NOT_PENDING" : "REVISION_NOT_DRAFT",
						answer.getValue());
			}
			assertEquals(version, dataVersion(listingId), status);
			assertEquals(row, revisionRow(listingId), status);
			assertEquals(history, count("select count(*) from listing_history where listing_id = ?", listingId), status);
		}
	}

	/**
	 * Pin P-8 of listing-publish E06-S6: the revision read answers a Live or Replaced revision with its content and no
	 * problem; the listing read lists it and has no open revision.
	 */
	@Test
	void reads_liveOrReplacedRevision_contentAndNoOpenRevision() throws Exception {
		for (String status : List.of("LIVE", "REPLACED")) {
			String listingId = decided(status);

			Result read = getRevision(sale, listingId, "1");

			assertEquals(200, read.status(), read.rawBody());
			JsonNode revision = read.body();
			assertEquals(status, revision.path("status").asString());
			assertEquals(TITLE, revision.path("title").asString());
			assertEquals(DESCRIPTION, revision.path("description").asString());
			assertFalse(revision.path("termsSnapshot").isNull(), revision.toString());
			assertFalse(revision.path("termsSnapshot").isMissingNode(), revision.toString());
			assertFalse(revision.path("unitSnapshot").isNull(), revision.toString());
			assertFalse(revision.path("unitSnapshot").isMissingNode(), revision.toString());
			assertFalse(revision.path("submittedAt").isNull(), revision.toString());
			assertTrue(revision.path("problems").isArray(), revision.toString());
			assertEquals(0, revision.path("problems").size(), revision.toString());
			Result listingRead = getListing(sale, listingId);
			assertEquals(200, listingRead.status(), listingRead.rawBody());
			JsonNode listing = listingRead.body();
			assertTrue(listing.path("openRevisionNo").isNull(), listing.toString());
			assertEquals(1, listing.path("revisions").size(), listing.toString());
			assertEquals(1, listing.path("revisions").path(0).path("revisionNo").asInt());
			assertEquals(status, listing.path("revisions").path(0).path("status").asString());
		}
	}

	/** A listing whose revision 1 was completed and submitted over HTTP, then set by SQL to the decided status. */
	private String decided(String status) throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);
		Result submitted = submit(sale, listingId, "1", dataVersion(listingId));
		assertEquals(200, submitted.status(), submitted.rawBody());
		// listing-publish E06-S6 spec E06S6-D6: a Live or Replaced row holds the time it went live, a Replaced one also
		// the time it was replaced (JVM clock, testing.md)
		java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
		jdbc.update("update listing_revisions set status = ?, went_live_at = ?, replaced_at = ? where listing_id = ?"
				+ " and revision_no = 1", status, "LIVE".equals(status) || "REPLACED".equals(status) ? now : null,
				"REPLACED".equals(status) ? now : null, listingId);
		return listingId;
	}

	/** Revision 1 of the listing read whole as text. */
	private String revisionRow(String listingId) {
		return jdbc.queryForObject("select r::text from listing_revisions r where listing_id = ? and revision_no = 1",
				String.class, listingId);
	}
}
