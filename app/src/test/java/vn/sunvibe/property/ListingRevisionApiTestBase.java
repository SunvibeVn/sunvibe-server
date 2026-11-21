package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * What the revision API tests of listing-publish E06-S2 share (plan decision E06S2-P12): {@link ListingApiTestBase}
 * (unchanged, with its context, its agreements and its cleanup, which removes the revisions of every listing of an
 * agreement a test created, and their fee rows by the cascade), the seven revision calls over HTTP with the revision
 * number as text, a listing under an agreement in effect from today and one under an agreement activated to start in
 * ten days (dates from today in Vietnam), a complete content body, and the counts of a listing's revision rows. Needs
 * Docker.
 */
abstract class ListingRevisionApiTestBase extends ListingApiTestBase {

	/** 34 characters that break no rule. */
	static final String TITLE = "Phòng trọ mới, sạch sẽ gần chợ Hàn";
	/** About 140 characters that break no rule. */
	static final String DESCRIPTION = "Phòng rộng 25 mét vuông, có cửa sổ lớn và ban công thoáng mát. Khu vực yên"
			+ " tĩnh, gần trường học, chợ và siêu thị. Giờ giấc tự do, có chỗ để xe máy.";

	/** A listing of {@code sale} and the chain of its agreement. */
	record Drafting(Chain chain, String listingId) {
	}

	/** A listing, created by its assigned Sale, under an agreement in effect from today: E05 answers its terms in effect. */
	Drafting listingInEffect() throws Exception {
		Chain chain = inEffect();
		return new Drafting(chain, newListing(sale, chain.agreementId()));
	}

	/**
	 * A listing under an agreement activated to start in ten days (as ListingCreateApiPostgresTest:87 builds it): E05
	 * answers only the version waiting to start, with its start date (plan E06S2-P15).
	 */
	Drafting listingPendingStart() throws Exception {
		Chain chain = activated(today().plusDays(10), today().plusMonths(6));
		return new Drafting(chain, newListing(sale, chain.agreementId()));
	}

	/** The listing's data version as stored. */
	long dataVersion(String listingId) {
		return jdbc.queryForObject("select row_version from listings where id = ?", Long.class, listingId);
	}

	/** A save of a title of 34 and a description of about 140 Vietnamese characters, every feature chosen. */
	static Map<String, Object> completeContentBody(long dataVersion) {
		return contentBody(dataVersion, TITLE, DESCRIPTION, everyFeature());
	}

	static Map<String, Object> contentBody(Object dataVersion, Object title, Object description,
			Map<String, Object> features) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dataVersion", dataVersion);
		body.put("title", title);
		body.put("description", description);
		body.put("features", features);
		return body;
	}

	/** Every feature chosen: each option and each yes-feature set. */
	static Map<String, Object> everyFeature() {
		Map<String, Object> features = new LinkedHashMap<>();
		features.put("furnishing", "FULL");
		features.put("airConditioner", true);
		features.put("waterHeater", true);
		features.put("fridge", true);
		features.put("washingMachine", "PRIVATE");
		features.put("kitchen", "SHARED");
		features.put("balcony", true);
		features.put("elevator", true);
		features.put("motorbikeParking", true);
		features.put("carParking", true);
		features.put("internet", true);
		features.put("pets", "NOT_ALLOWED");
		features.put("noSharedLandlord", true);
		return features;
	}

	/** No feature chosen. */
	static Map<String, Object> noFeature() {
		Map<String, Object> features = new LinkedHashMap<>();
		for (String name : List.of("furnishing", "washingMachine", "kitchen", "pets")) {
			features.put(name, null);
		}
		for (String name : List.of("airConditioner", "waterHeater", "fridge", "balcony", "elevator", "motorbikeParking",
				"carParking", "internet", "noSharedLandlord")) {
			features.put(name, false);
		}
		return features;
	}

	/** The content saved and the terms in effect captured by the assigned Sale: a Draft ready to submit. */
	void completeDraft(Drafting drafting) throws Exception {
		Result saved = saveContent(sale, drafting.listingId(), "1", completeContentBody(dataVersion(drafting.listingId())));
		assertEquals(200, saved.status(), saved.rawBody());
		Result captured = captureTerms(sale, drafting.listingId(), "1", dataVersion(drafting.listingId()), "IN_EFFECT");
		assertEquals(200, captured.status(), captured.rawBody());
	}

	// ==================== the seven revision calls over HTTP ====================

	Result getRevision(TestUser caller, String listingId, String revisionNo) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo);
	}

	Result startRevision(TestUser caller, String listingId, Object dataVersion) throws Exception {
		return api.post(caller, LISTINGS + "/" + listingId + "/revisions", command(dataVersion));
	}

	Result saveContent(TestUser caller, String listingId, String revisionNo, Map<String, Object> body) throws Exception {
		return api.put(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/content", body);
	}

	Result captureTerms(TestUser caller, String listingId, String revisionNo, Object dataVersion, Object source)
			throws Exception {
		Map<String, Object> body = command(dataVersion);
		body.put("source", source);
		return api.put(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/terms", body);
	}

	Result submit(TestUser caller, String listingId, String revisionNo, Object dataVersion) throws Exception {
		return api.post(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/submission",
				command(dataVersion));
	}

	Result withdraw(TestUser caller, String listingId, String revisionNo, Object dataVersion) throws Exception {
		return api.post(caller, LISTINGS + "/" + listingId + "/revisions/" + revisionNo + "/withdrawal",
				command(dataVersion));
	}

	Result getTermsSources(TestUser caller, String listingId) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/terms-sources");
	}

	/** A PUT whose body is the raw text, sent as JSON. */
	Result putRaw(TestUser caller, String path, String raw) throws Exception {
		return api.send(api.request(caller, path).header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(raw, StandardCharsets.UTF_8)).build());
	}

	/** {dataVersion}, or no property at all for a null. */
	private static Map<String, Object> command(Object dataVersion) {
		Map<String, Object> body = new LinkedHashMap<>();
		if (dataVersion != null) {
			body.put("dataVersion", dataVersion);
		}
		return body;
	}

	// ==================== what a call wrote ====================

	/** A listing's revisions, their fee rows, its history rows and its row version. */
	record RevisionRows(int revisions, int fees, int history, long rowVersion) {
	}

	RevisionRows revisionRows(String listingId) {
		return new RevisionRows(count("select count(*) from listing_revisions where listing_id = ?", listingId),
				count("select count(*) from listing_revision_fees f join listing_revisions r on r.id = f.revision_id"
						+ " where r.listing_id = ?", listingId),
				count("select count(*) from listing_history where listing_id = ?", listingId), dataVersion(listingId));
	}

	/** The entries' fields of an error body, in order. */
	static List<String> fields(Result answer) {
		List<String> fields = new ArrayList<>();
		answer.body().path("errors").forEach(entry -> fields.add(entry.path("field").asString()));
		return fields;
	}

	/** The problems of a revision read as {@code <part>.<RULE>} in lower-case part, in order. */
	static List<String> problems(JsonNode revision) {
		List<String> problems = new ArrayList<>();
		revision.path("problems").forEach(problem -> problems.add(
				problem.path("part").asString().toLowerCase(java.util.Locale.ROOT) + "." + problem.path("rule").asString()));
		return problems;
	}
}
