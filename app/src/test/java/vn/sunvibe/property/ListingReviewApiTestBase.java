package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * What the review API tests of listing-publish E06-S4 share (plan E06S4-P9): {@link ListingRevisionApiTestBase}
 * (unchanged, with its context, its agreements and its cleanup; decision rows go with their revisions by the cascade),
 * a Pending review revision made over HTTP as pin P-2 makes it, the six review calls, the decision bodies, what a refused
 * call must leave as it is (the listing tables, the outbox, the decisions and the access log), and the cleanup of the
 * access log rows of every listing of an agreement a test created (the table has no foreign key). Needs Docker.
 */
abstract class ListingReviewApiTestBase extends ListingRevisionApiTestBase {

	static final List<String> FIVE_CHECKS = List.of("CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES",
			"TERMS_MATCH_AGREEMENT", "NO_FRAUD_SIGNS", "AGREEMENT_END_AND_RIGHT");
	static final String NOTE = "Mô tả thiếu diện tích phòng tắm.";

	/** The access log rows of every listing of an agreement this test created; runs before the listings go. */
	@AfterEach
	void deleteAccessLogRows() {
		for (String agreementId : created) {
			jdbc.update("delete from listing_access_logs where listing_id in (select id from listings"
					+ " where agreement_id = ?)", agreementId);
		}
	}

	/** A listing of {@code sale} whose revision 1 was completed and submitted over HTTP: Pending review. */
	Drafting pendingDrafting() throws Exception {
		Drafting drafting = listingInEffect();
		submitted(drafting);
		return drafting;
	}

	String pending() throws Exception {
		return pendingDrafting().listingId();
	}

	/** Revision 1 of the listing completed and submitted by {@code sale}. */
	void submitted(Drafting drafting) throws Exception {
		completeDraft(drafting);
		Result answer = submit(sale, drafting.listingId(), "1", dataVersion(drafting.listingId()));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	// ==================== the six review calls over HTTP ====================

	Result queue(TestUser caller) throws Exception {
		return api.get(caller, LISTINGS + "/review-queue");
	}

	Result review(TestUser caller, String listingId, String revisionNo) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/review");
	}

	Result decide(TestUser caller, String listingId, String revisionNo, Object body) throws Exception {
		return api.post(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/decision", body);
	}

	Result decisionRead(TestUser caller, String listingId, String revisionNo) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/decision");
	}

	Result openLocation(TestUser caller, String listingId, String revisionNo) throws Exception {
		return api.post(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/unit-location", Map.of());
	}

	Result copy(TestUser caller, String listingId, String revisionNo, Object dataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		if (dataVersion != null) {
			body.put("dataVersion", dataVersion);
		}
		return api.post(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/copies", body);
	}

	/** The decision by the caller, which must answer 200. */
	void decided(TestUser caller, String listingId, String revisionNo, Map<String, Object> body) throws Exception {
		Result answer = decide(caller, listingId, revisionNo, body);
		assertEquals(200, answer.status(), answer.rawBody());
	}

	// ==================== the decision bodies ====================

	static Map<String, Object> approval(Object dataVersion) {
		return decisionBody(dataVersion, "APPROVED", FIVE_CHECKS, null, null, null);
	}

	static Map<String, Object> returnBody(Object dataVersion, List<String> reasons, Object note) {
		return decisionBody(dataVersion, "NEEDS_CHANGES", null, reasons, null, note);
	}

	static Map<String, Object> rejection(Object dataVersion, Object group, Object note) {
		return decisionBody(dataVersion, "REJECTED", null, null, group, note);
	}

	/** Every property present, null where the caller sends none, except a null data version, which is left out. */
	static Map<String, Object> decisionBody(Object dataVersion, Object outcome, Object checks, Object changeReasons,
			Object rejectionGroup, Object note) {
		Map<String, Object> body = new LinkedHashMap<>();
		if (dataVersion != null) {
			body.put("dataVersion", dataVersion);
		}
		body.put("outcome", outcome);
		body.put("checks", checks);
		body.put("changeReasons", changeReasons);
		body.put("rejectionGroup", rejectionGroup);
		body.put("note", note);
		return body;
	}

	// ==================== what a call wrote ====================

	/** The listing tables and the outbox, the decisions and the access log: what a refused call leaves as it is. */
	record Written(ListingRows rows, int decisions, int accessLogs) {
	}

	Written written() {
		return new Written(listingRows(), count("select count(*) from listing_revision_reviews"),
				count("select count(*) from listing_access_logs"));
	}

	int decisionRows(String listingId) {
		return count("select count(*) from listing_revision_reviews where listing_id = ?", listingId);
	}

	int accessLogRows(String listingId) {
		return count("select count(*) from listing_access_logs where listing_id = ?", listingId);
	}

	String revisionStatus(String listingId, int revisionNo) {
		return jdbc.queryForObject("select status from listing_revisions where listing_id = ? and revision_no = ?",
				String.class, listingId, revisionNo);
	}

	/** The listing's history as "action revision actor", oldest first. */
	List<String> history(String listingId) {
		return jdbc.queryForList("select action || ' ' || coalesce(revision_no::text, '-') || ' '"
				+ " || coalesce(actor_user_id, '-') from listing_history where listing_id = ? order by occurred_at, id",
				String.class, listingId);
	}

	static String codeOf(Result answer) {
		return answer.status() + " " + answer.body().path("code").asString();
	}

	static String codeAndFields(Result answer) {
		return codeOf(answer) + " " + fields(answer);
	}

	static String bodyWithoutCorrelation(Result answer) {
		return answer.body().path("code").asString() + "|" + answer.body().path("message").asString() + "|"
				+ answer.body().path("errors");
	}
}
