package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.listing.core.application.command.PutLiveCommand;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;

/**
 * What the publication API tests of listing-publish E06-S6 share (plan E06S6-P6, Task A11): {@link ListingReviewApiTestBase}
 * (unchanged, with its context, its agreements and its cleanup, the outbox rows of the listing event included), an
 * Approved revision that waits while every display condition holds, the unit's availability moved by SQL, the
 * freshness record, the put live and the two publication reads over HTTP, the put live as the use case's command, and
 * the listing's event rows. It adds no annotation, so its subclasses keep the context of the listing API tests. Needs
 * Docker.
 */
abstract class ListingPublicationApiTestBase extends ListingReviewApiTestBase {

	/**
	 * E06S6-P6: a listing of {@code sale} under an agreement in effect, its revision 1 submitted with the terms in effect;
	 * the unit set OCCUPIED by SQL; a valid freshness confirmation recorded over HTTP (nothing waits yet); the approval
	 * over HTTP, whose try is refused by the unit; then the unit AVAILABLE again: an Approved revision that waits while
	 * every condition holds, which only the Sale's command puts live.
	 */
	Drafting waitingApproval() throws Exception {
		Drafting drafting = pendingDrafting();
		String listingId = drafting.listingId();
		unitAvailability(drafting.chain().unitId(), "OCCUPIED");
		Result recorded = recordFreshness(sale, listingId, validRecord(dataVersion(listingId)));
		assertEquals(201, recorded.status(), recorded.rawBody());
		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));
		assertEquals("APPROVED", revisionStatus(listingId, 1));
		unitAvailability(drafting.chain().unitId(), "AVAILABLE");
		return drafting;
	}

	/** The unit's availability in property's data (PLAN2-D4: tests set it through fixtures). */
	void unitAvailability(String unitId, String code) {
		assertEquals(1, jdbc.update("update units set availability_state = ? where id = ?", code, unitId));
	}

	/** The listing's state as stored. */
	String state(String listingId) {
		return jdbc.queryForObject("select state from listings where id = ?", String.class, listingId);
	}

	// ==================== the calls over HTTP ====================

	Result putLive(TestUser caller, String listingId, String revisionNo, Object dataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		if (dataVersion != null) {
			body.put("dataVersion", dataVersion);
		}
		return api.post(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/publication", body);
	}

	/** The put live by the assigned Sale of revision 1 on the stored data version, which must answer 200 LIVE. */
	void putLiveOk(String listingId) throws Exception {
		Result answer = putLive(sale, listingId, "1", dataVersion(listingId));
		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("LIVE", answer.body().path("status").asString(), answer.rawBody());
	}

	Result publication(TestUser caller, String listingId) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/publication");
	}

	Result liveDifference(TestUser caller, String listingId, String revisionNo) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/live-difference");
	}

	Result recordFreshness(TestUser caller, String listingId, Map<String, Object> body) throws Exception {
		return api.post(caller, LISTINGS + "/" + listingId + "/freshness-records", body);
	}

	/** A valid confirmation: by call, still available, the terms still apply and the unit unchanged. */
	static Map<String, Object> validRecord(long dataVersion) {
		return recordBody(dataVersion, "AVAILABLE", true, true);
	}

	/** A contact that is no valid confirmation: the unit no longer available. */
	static Map<String, Object> invalidRecord(long dataVersion) {
		return recordBody(dataVersion, "NOT_AVAILABLE", null, null);
	}

	private static Map<String, Object> recordBody(long dataVersion, String outcome, Boolean termsStillApply,
			Boolean unitUnchanged) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dataVersion", dataVersion);
		body.put("channel", "CALL");
		body.put("outcome", outcome);
		body.put("termsStillApply", termsStillApply);
		body.put("unitUnchanged", unitUnchanged);
		body.put("note", null);
		return body;
	}

	// ==================== the use case's command, for a held transaction ====================

	static PutLiveCommand putLiveCommand(String listingId, int revisionNo, long dataVersion) {
		return new PutLiveCommand(listingId, revisionNo, dataVersion);
	}

	/** The assigned Sale as the use case sees it. */
	ListingViewer saleViewer() {
		return new ListingViewer(sale.userId(), Set.of("ROLE_SALE"));
	}

	// ==================== what a call wrote ====================

	/**
	 * The outbox rows of the listing's event (E06S6-D21), each as "type version module aggregate id payload consumer
	 * status dueAtOccurrence".
	 */
	List<String> listingEventRows(String listingId) {
		return jdbc.query("select event_type, event_version, source_module, aggregate_type, aggregate_id, payload::text,"
				+ " consumer, status, next_attempt_at = occurred_at from outbox_events where aggregate_type = 'listing'"
				+ " and aggregate_id = ? order by id", (row, rowNum) -> row.getString(1) + " " + row.getInt(2) + " "
				+ row.getString(3) + " " + row.getString(4) + " " + row.getString(5) + " " + row.getString(6) + " "
				+ row.getString(7) + " " + row.getString(8) + " " + row.getBoolean(9), listingId);
	}

	/** The one event row a put live of the listing leaves, as {@link #listingEventRows} reads it. */
	static String eventRow(String listingId) {
		return "listing.listing-changed 1 listing listing " + listingId + " {\"listingId\": \"" + listingId + "\"}"
				+ " test-listing-changed PENDING true";
	}

	/** A revision's status and its two times' presence, as "status wentLive replaced". */
	String revisionTimes(String listingId, int revisionNo) {
		return jdbc.queryForObject("select status || ' ' || (went_live_at is not null) || ' ' || (replaced_at is not null)"
				+ " from listing_revisions where listing_id = ? and revision_no = ?", String.class, listingId, revisionNo);
	}

	/** The listing's freshness records as "row version valid", in row-version order. */
	List<String> freshnessRecords(String listingId) {
		return jdbc.query("select listing_row_version, valid from listing_freshness_records where listing_id = ? order by"
				+ " listing_row_version", (row, rowNum) -> row.getLong(1) + " " + row.getBoolean(2), listingId);
	}
}
