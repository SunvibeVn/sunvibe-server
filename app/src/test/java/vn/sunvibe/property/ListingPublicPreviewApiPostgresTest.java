package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.listing.core.application.usecase.ListingPublicQueryUseCase;
import vn.sunvibe.listing.core.presentation.converter.ListingPublicResponseConverter;

/**
 * The staff preview of a revision's public data over HTTP on PostgreSQL (listing-publish E06-S7 spec section 4.2,
 * E06S7-D9; criterion PB-19; Review Focus 4): the BR-14 groups as the revision would be published, a Draft without its
 * unit fields and names, a Pending review revision with the snapshot's, the live revision equal to the public answer in
 * every common field; data only, so a suspended agreement changes nothing; the five roles in scope; 401 without a token;
 * 403 for any other role on every id; one 404 for a SALE out of scope, a malformed or unknown listing and a malformed or
 * unknown revision number. The listing API tests' context (its limiter on), so the public answer is taken through the use
 * case on the test thread, never over HTTP. Needs Docker.
 */
class ListingPublicPreviewApiPostgresTest extends ListingPublicationApiTestBase {

	private static final String NOT_FOUND = "NOT_FOUND|The resource was not found.|[]";
	private static final List<String> PREVIEW_FIELDS = List.of("areaSqm", "bathrooms", "bedrooms", "code",
			"description", "features", "lastConfirmedOn", "liveOn", "propertyType", "provinceCode", "provinceName",
			"revisionNo", "revisionStatus", "terms", "title", "transactionType", "wardCode", "wardName");
	private static final List<String> COMMON_FIELDS = List.of("code", "transactionType", "propertyType",
			"provinceCode", "wardCode", "bedrooms", "bathrooms", "areaSqm", "terms", "title", "description", "features",
			"liveOn", "lastConfirmedOn");

	@Autowired
	ListingPublicQueryUseCase publicReads;

	@Autowired
	ListingPublicResponseConverter publicResponses;

	@Override
	String phonePrefix() {
		return "0989";
	}

	/** PB-19: a Draft answers its content and terms, no unit field, no name, no dates. */
	@Test
	void publicPreview_draft_noUnitFieldsNoNames() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);

		Result answer = preview(sale, listingId, "1");

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode body = answer.body();
		assertEquals(PREVIEW_FIELDS, names(body));
		assertEquals(List.of("1", "DRAFT", code(listingId), "LONG_TERM_RENT", TITLE, DESCRIPTION, "8000000"),
				List.of(body.path("revisionNo").asString(), body.path("revisionStatus").asString(),
						body.path("code").asString(), body.path("transactionType").asString(),
						body.path("title").asString(), body.path("description").asString(),
						body.path("terms").path("monthlyRent").path("amount").asString()));
		List<String> nulls = new ArrayList<>();
		for (String field : List.of("propertyType", "provinceCode", "provinceName", "wardCode", "wardName", "bedrooms",
				"bathrooms", "areaSqm", "liveOn", "lastConfirmedOn")) {
			nulls.add(field + " " + body.path(field).isNull());
		}
		assertEquals(List.of("propertyType true", "provinceCode true", "provinceName true", "wardCode true",
				"wardName true", "bedrooms true", "bathrooms true", "areaSqm true", "liveOn true",
				"lastConfirmedOn true"), nulls);
		assertEquals(api.json().valueToTree(everyFeature()), body.path("features"));
	}

	/** PB-19: a Pending review revision answers the unit fields and the names of its snapshot. */
	@Test
	void publicPreview_pendingReview_unitFieldsAndSnapshotNames() throws Exception {
		String listingId = pending();
		Map<String, Object> snapshot = jdbc.queryForMap("select unit_asset_type, unit_province_code, unit_province_name,"
				+ " unit_ward_code, unit_ward_name, unit_bedrooms, unit_bathrooms, unit_area_sqm from listing_revisions"
				+ " where listing_id = ? and revision_no = 1", listingId);

		Result answer = preview(sale, listingId, "1");

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode body = answer.body();
		assertEquals(List.of("PENDING_REVIEW", String.valueOf(snapshot.get("unit_asset_type")),
						String.valueOf(snapshot.get("unit_province_code")), String.valueOf(snapshot.get("unit_province_name")),
						String.valueOf(snapshot.get("unit_ward_code")), String.valueOf(snapshot.get("unit_ward_name")),
						String.valueOf(snapshot.get("unit_bedrooms")), String.valueOf(snapshot.get("unit_bathrooms")),
						String.valueOf(((Number) snapshot.get("unit_area_sqm")).doubleValue())),
				List.of(body.path("revisionStatus").asString(), body.path("propertyType").asString(),
						body.path("provinceCode").asString(), body.path("provinceName").asString(),
						body.path("wardCode").asString(), body.path("wardName").asString(),
						body.path("bedrooms").asString(), body.path("bathrooms").asString(),
						String.valueOf(body.path("areaSqm").asDouble())));
	}

	/**
	 * PB-19: the live revision of a visible listing: every field common with the public answer equal to it, the public
	 * answer taken through the use case on the test thread.
	 */
	@Test
	void publicPreview_liveRevision_commonFieldsEqualPublicAnswer() throws Exception {
		String listingId = waitingApproval().listingId();
		putLiveOk(listingId);
		JsonNode shown = api.json().readTree(api.json().writeValueAsString(
				publicResponses.toResponse(publicReads.publicListing(code(listingId)))));

		Result answer = preview(sale, listingId, "1");

		assertEquals(200, answer.status(), answer.rawBody());
		List<String> differing = new ArrayList<>();
		for (String field : COMMON_FIELDS) {
			if (!shown.path(field).equals(answer.body().path(field))) {
				differing.add(field + " " + shown.path(field) + " / " + answer.body().path(field));
			}
		}
		assertEquals(List.of(), differing);
		assertEquals(List.of("1", "LIVE"), List.of(answer.body().path("revisionNo").asString(),
				answer.body().path("revisionStatus").asString()));
	}

	/** PB-19: the preview computes no condition, so with the agreement suspended it still answers its data. */
	@Test
	void publicPreview_agreementSuspended_stillAnswersData() throws Exception {
		Drafting live = waitingApproval();
		putLiveOk(live.listingId());
		suspended(manager, live.chain().agreementId());

		Result answer = preview(sale, live.listingId(), "1");

		assertEquals(List.of("200", "LIVE", TITLE), List.of(String.valueOf(answer.status()),
				answer.body().path("revisionStatus").asString(), answer.body().path("title").asString()));
	}

	/** PB-19: 200 for each of the five roles in scope: the assigned SALE, SALE_LEADER, SALES_MANAGER, ADMIN, reviewer. */
	@Test
	void publicPreview_fiveRolesInScope_ok() throws Exception {
		String listingId = pending();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(sale, leader, manager, admin, reviewer)) {
			Result answer = preview(caller, listingId, "1");
			answers.add(answer.status() + " " + answer.body().path("code").asString());
		}

		assertEquals(Collections.nCopies(5, "200 " + code(listingId)), answers);
	}

	/** PB-19: 401 without a token; 403 FORBIDDEN for CUSTOMER, LEADER and MANAGER, the same for every id. */
	@Test
	void publicPreview_noTokenOrOtherRole_unauthorizedOrForbiddenEveryId() throws Exception {
		String listingId = pending();
		TestUser oldLeader = survey.user("Preview Old Leader", "LEADER");
		TestUser oldManager = survey.user("Preview Old Manager", "MANAGER");

		List<String> answers = new ArrayList<>();
		answers.add("anonymous " + codeOf(preview(null, listingId, "1")));
		for (TestUser caller : List.of(customer, oldLeader, oldManager)) {
			answers.add(codeOf(preview(caller, listingId, "1")) + " / " + codeOf(preview(caller, UNKNOWN, "1")));
		}

		assertEquals(List.of("anonymous 401 UNAUTHORIZED", "403 FORBIDDEN / 403 FORBIDDEN",
				"403 FORBIDDEN / 403 FORBIDDEN", "403 FORBIDDEN / 403 FORBIDDEN"), answers);
	}

	/**
	 * PB-19, Review Focus 4: one 404 body for a SALE who is not the assignee (IDOR), a malformed listing id, an unknown
	 * listing, the revision numbers 01, 0, x, 1234567890 and +1, and an unknown revision.
	 */
	@Test
	void publicPreview_outOfScopeOrUnknown_oneNotFound() throws Exception {
		String listingId = pending();

		List<String> answers = new ArrayList<>();
		answers.add("other sale " + body(preview(otherSale, listingId, "1")));
		answers.add("malformed id " + body(preview(sale, "not-an-id", "1")));
		answers.add("unknown listing " + body(preview(sale, UNKNOWN, "1")));
		for (String revisionNo : List.of("01", "0", "x", "1234567890", "+1", "9")) {
			answers.add("revision " + revisionNo + " " + body(preview(sale, listingId, revisionNo)));
		}

		assertEquals(List.of("other sale 404 " + NOT_FOUND, "malformed id 404 " + NOT_FOUND,
				"unknown listing 404 " + NOT_FOUND, "revision 01 404 " + NOT_FOUND, "revision 0 404 " + NOT_FOUND,
				"revision x 404 " + NOT_FOUND, "revision 1234567890 404 " + NOT_FOUND, "revision +1 404 " + NOT_FOUND,
				"revision 9 404 " + NOT_FOUND), answers);
	}

	private Result preview(TestUser caller, String listingId, String revisionNo) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/public-preview");
	}

	private String code(String listingId) {
		return jdbc.queryForObject("select code from listings where id = ?", String.class, listingId);
	}

	private static String body(Result answer) {
		return answer.status() + " " + bodyWithoutCorrelation(answer);
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names.stream().sorted().toList();
	}
}
