package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The anonymous public read over HTTP on PostgreSQL (listing-publish E06-S7 spec section 4.1, E06S7-D5 to D8; criteria
 * PB-1 to PB-6, PB-8; Review Focus 2): a visible listing answers exactly the public fields with the live revision's
 * values and {@code no-store}; every hidden case answers one 404 body with the same telling headers, with or without a
 * token of any kind; no log line holds the code; another method answers 401; each display condition hides the listing
 * at the next read and its return shows it again; a copy shows only once it is live. Dates are set by SQL from the
 * JVM's clock in Vietnam time (testing.md). Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class ListingPublicReadApiPostgresTest extends ListingPublicReadApiTestBase {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final String NOT_FOUND = "NOT_FOUND|The resource was not found.|[]";
	private static final String COPY_TITLE = "Phòng trọ mới sửa, sạch sẽ gần chợ Hàn";
	private static final List<String> PUBLIC_FIELDS = List.of("areaSqm", "bathrooms", "bedrooms", "code",
			"description", "features", "lastConfirmedOn", "liveOn", "propertyType", "provinceCode", "terms", "title",
			"transactionType", "wardCode");
	/** The terms of AgreementDraftApiPostgresTest.example in the staff schema ListingTerms, the fees in E05's order. */
	private static final String TERMS = "{\"monthlyRent\":{\"amount\":8000000,\"currency\":\"VND\",\"unit\":\"MONTH\"},"
			+ "\"deposit\":{\"basis\":\"MONTHS\",\"months\":2,\"amount\":null,\"currency\":null},"
			+ "\"minimumTermMonths\":12,\"paymentPeriodMonths\":1,\"mandatoryFees\":["
			+ "{\"name\":\"Electricity\",\"basis\":\"AMOUNT\",\"amount\":3500,\"currency\":\"VND\",\"chargeUnit\":\"PER_KWH\"},"
			+ "{\"name\":\"Water\",\"basis\":\"PROVIDER_TARIFF\",\"amount\":null,\"currency\":null,\"chargeUnit\":null}]}";

	/** The test profile's public, committed signing key (application-test.yml), for the expired token only. */
	@Value("${app.jwt.secret}")
	private String testSigningKey;

	@Override
	String phonePrefix() {
		return "0984";
	}

	/**
	 * PB-1: exactly the fields of 4.1 with the live revision's values, the two fees in fee_no order, liveOn the Vietnam
	 * date of the put live and lastConfirmedOn the latest valid confirmation's date (set to yesterday, so they differ),
	 * and Cache-Control: no-store.
	 */
	@Test
	void publicRead_visibleListing_publicFieldsLiveValuesNoStore() throws Exception {
		String listingId = liveListing().listingId();
		confirmedDaysAgo(listingId, 1);
		String code = publicCodeOf(listingId);

		Result answer = publicRead(code);

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode body = answer.body();
		Map<String, Object> revision = jdbc.queryForMap("select unit_asset_type, unit_province_code, unit_ward_code,"
				+ " unit_bedrooms, unit_bathrooms, unit_area_sqm, went_live_at from listing_revisions"
				+ " where listing_id = ? and revision_no = 1", listingId);
		LocalDate liveOn = ((Timestamp) revision.get("went_live_at")).toInstant().atZone(VIETNAM).toLocalDate();
		JsonNode terms = api.json().readTree(TERMS);
		JsonNode features = api.json().valueToTree(everyFeature());
		// one comparison, so a red names every part that differs
		assertEquals(List.of("Cache-Control no-store", "fields " + PUBLIC_FIELDS, code, "LONG_TERM_RENT",
						String.valueOf(revision.get("unit_asset_type")), String.valueOf(revision.get("unit_province_code")),
						String.valueOf(revision.get("unit_ward_code")), String.valueOf(revision.get("unit_bedrooms")),
						String.valueOf(revision.get("unit_bathrooms")),
						String.valueOf(((Number) revision.get("unit_area_sqm")).doubleValue()), TITLE, DESCRIPTION,
						"terms " + terms, "features as saved", "liveOn " + liveOn,
						"lastConfirmedOn " + today().minusDays(1)),
				List.of("Cache-Control " + answer.header("Cache-Control"), "fields " + names(body),
						body.path("code").asString(), body.path("transactionType").asString(),
						text(body.path("propertyType")), text(body.path("provinceCode")), text(body.path("wardCode")),
						body.path("bedrooms").asString(), body.path("bathrooms").asString(),
						String.valueOf(body.path("areaSqm").asDouble()), body.path("title").asString(),
						body.path("description").asString(),
						"terms " + (terms.equals(body.path("terms")) ? terms : body.path("terms")),
						features.equals(body.path("features")) ? "features as saved" : "features " + body.path("features"),
						"liveOn " + body.path("liveOn").asString(),
						"lastConfirmedOn " + body.path("lastConfirmedOn").asString()));
	}

	/**
	 * PB-2 (i): hidden, unknown, malformed, never put live, stored PAUSED and stored ARCHIVED answer one 404 body and the
	 * same values of the telling headers; the hidden listing and the unknown code, both from the handler, the same
	 * Content-Type too.
	 */
	@Test
	void publicRead_everyHiddenCase_oneNotFoundBodyAndHeaders() throws Exception {
		Drafting live = liveListing();
		String listingId = live.listingId();
		String code = publicCodeOf(listingId);
		String neverLive = publicCodeOf(listingInEffect().listingId());

		unitAvailability(live.chain().unitId(), "OCCUPIED");
		Result hidden = publicRead(code);
		unitAvailability(live.chain().unitId(), "AVAILABLE");
		Result unknown = publicRead(UNKNOWN_CODE);
		Result malformed = publicRead("TD-ABCD-EFG");
		Result notLive = publicRead(neverLive);
		assertEquals(1, jdbc.update("update listings set state = 'PAUSED' where id = ?", listingId));
		Result paused = publicRead(code);
		assertEquals(1, jdbc.update("update listings set state = 'ARCHIVED' where id = ?", listingId));
		Result archived = publicRead(code);

		// the filter's own 404 (malformed) gives the header values every other answer must have; one comparison, so a red
		// names each answer that differs, in body or headers
		List<String> expected = new ArrayList<>();
		List<String> found = new ArrayList<>();
		Map<String, Result> answers = new java.util.LinkedHashMap<>();
		answers.put("hidden", hidden);
		answers.put("unknown", unknown);
		answers.put("malformed", malformed);
		answers.put("never live", notLive);
		answers.put("PAUSED", paused);
		answers.put("ARCHIVED", archived);
		for (Map.Entry<String, Result> answer : answers.entrySet()) {
			expected.add(answer.getKey() + " 404 " + NOT_FOUND + " " + tellingHeaders(malformed));
			found.add(answer.getKey() + " " + answer.getValue().status() + " " + bodyWithoutCorrelation(answer.getValue())
					+ " " + tellingHeaders(answer.getValue()));
		}
		assertEquals(expected, found);
		assertEquals("no-store", malformed.header("Cache-Control"));
		assertEquals(hidden.header("Content-Type"), unknown.header("Content-Type"));
	}

	/**
	 * PB-2 (ii): the same answers, 200 for a visible listing and 404 for a hidden one, with a valid staff token, an
	 * expired one (signed over the test profile's key) and a malformed one.
	 */
	@Test
	void publicRead_anyToken_sameAnswers() throws Exception {
		Drafting live = liveListing();
		String code = publicCodeOf(live.listingId());
		String expired = expiredToken(sale.userId());

		List<String> answers = new ArrayList<>();
		answers.add(statusAndCode(publicRead(sale, code)));
		answers.add(statusAndCode(publicReadWithToken(expired, code)));
		answers.add(statusAndCode(publicReadWithToken("not-a-token-0000", code)));
		unitAvailability(live.chain().unitId(), "OCCUPIED");
		answers.add(statusAndCode(publicRead(sale, code)));
		answers.add(statusAndCode(publicReadWithToken(expired, code)));
		answers.add(statusAndCode(publicReadWithToken("not-a-token-0000", code)));

		assertEquals(List.of("200 " + code, "200 " + code, "200 " + code, "404 NOT_FOUND", "404 NOT_FOUND",
				"404 NOT_FOUND"), answers);
	}

	/** PB-2 (iii): no log line captured during the reads holds a code read, whatever the answer. */
	@Test
	void publicRead_logs_holdNoCode(CapturedOutput output) throws Exception {
		Drafting live = liveListing();
		String code = publicCodeOf(live.listingId());
		int before = output.getAll().length();

		List<Integer> statuses = new ArrayList<>();
		statuses.add(publicRead(code).status());
		statuses.add(publicReadWithToken(expiredToken(sale.userId()), code).status());
		statuses.add(publicRead(UNKNOWN_CODE).status());
		statuses.add(publicRead("TD-ABCD-EFG").status());
		unitAvailability(live.chain().unitId(), "OCCUPIED");
		statuses.add(publicRead(code).status());

		assertEquals(List.of(200, 200, 404, 404, 404), statuses);
		String logged = output.getAll().substring(before);
		List<String> held = new ArrayList<>();
		for (String read : List.of(code, UNKNOWN_CODE, "TD-ABCD-EFG")) {
			if (logged.contains(read)) {
				held.add(read);
			}
		}
		assertEquals(List.of(), held);
	}

	/** Review Focus 2: HEAD, POST and PUT of the public path anonymously: 401, no listing data. */
	@Test
	void publicPath_otherMethods_unauthorizedNoListingData() throws Exception {
		String listingId = liveListing().listingId();
		String code = publicCodeOf(listingId);

		List<String> answers = new ArrayList<>();
		for (String method : List.of("HEAD", "POST", "PUT")) {
			Result answer = publicRequest(method, code);
			answers.add(method + " " + answer.status() + " " + (answer.rawBody().contains(code)
					|| answer.rawBody().contains(TITLE) ? "with listing data" : "without listing data"));
		}

		assertEquals(List.of("HEAD 401 without listing data", "POST 401 without listing data",
				"PUT 401 without listing data"), answers);
	}

	/**
	 * PB-3: after a record "No longer available", "Unreachable" or "Still available" without both answers yes, the next
	 * read answers 404; each later valid confirmation answers 200 again.
	 */
	@Test
	void publicRead_invalidRecordThenValid_hiddenThenShown() throws Exception {
		String listingId = liveListing().listingId();
		String code = publicCodeOf(listingId);

		List<String> answers = new ArrayList<>();
		for (Map<String, Object> invalid : List.of(record(0, "NOT_AVAILABLE", null, null),
				record(0, "UNREACHABLE", null, null), record(0, "AVAILABLE", true, false))) {
			invalid.put("dataVersion", dataVersion(listingId));
			assertEquals(201, recordFreshness(sale, listingId, invalid).status());
			answers.add(invalid.get("outcome") + " " + publicRead(code).status());
			assertEquals(201, recordFreshness(sale, listingId, validRecord(dataVersion(listingId))).status());
			answers.add("valid " + publicRead(code).status());
		}

		assertEquals(List.of("NOT_AVAILABLE 404", "valid 200", "UNREACHABLE 404", "valid 200", "AVAILABLE 404",
				"valid 200"), answers);
	}

	/** PB-4: a latest valid confirmation 14 days old answers 200, one 15 days old 404 (dates from the JVM clock). */
	@Test
	void publicRead_confirmationAge_fourteenShownFifteenHidden() throws Exception {
		String listingId = liveListing().listingId();
		String code = publicCodeOf(listingId);

		confirmedDaysAgo(listingId, 14);
		int fourteen = publicRead(code).status();
		confirmedDaysAgo(listingId, 15);
		int fifteen = publicRead(code).status();

		assertEquals(List.of("14 days 200", "15 days 404"), List.of("14 days " + fourteen, "15 days " + fifteen));
	}

	/** PB-5: the unit OCCUPIED answers 404, AVAILABLE again 200. */
	@Test
	void publicRead_unitOccupiedThenAvailable_hiddenThenShown() throws Exception {
		Drafting live = liveListing();
		String code = publicCodeOf(live.listingId());

		unitAvailability(live.chain().unitId(), "OCCUPIED");
		int occupied = publicRead(code).status();
		unitAvailability(live.chain().unitId(), "AVAILABLE");
		int available = publicRead(code).status();

		assertEquals(List.of("OCCUPIED 404", "AVAILABLE 200"), List.of("OCCUPIED " + occupied,
				"AVAILABLE " + available));
	}

	/**
	 * PB-6: a suspension answers 404 from its commit and 200 after its release; the end date moved to yesterday 404 and to
	 * today 200, the stored status still ACTIVE (the answer follows the date); a termination 404.
	 */
	@Test
	void publicRead_suspensionEndDateTermination_followE05() throws Exception {
		Drafting live = liveListing();
		String agreementId = live.chain().agreementId();
		String code = publicCodeOf(live.listingId());

		List<String> answers = new ArrayList<>();
		suspended(manager, agreementId);
		answers.add("suspended " + publicRead(code).status());
		Result released = releaseSuspension(manager, agreementId);
		assertEquals(204, released.status(), released.rawBody());
		answers.add("released " + publicRead(code).status());
		movePeriod(agreementId, 1, today().minusDays(1).minusMonths(1), today().minusDays(1));
		answers.add("ended yesterday " + publicRead(code).status() + " " + status(agreementId));
		movePeriod(agreementId, 1, today().minusMonths(1), today());
		answers.add("ends today " + publicRead(code).status() + " " + status(agreementId));
		Result terminated = terminate(sale, agreementId, ownerWithdrew());
		assertEquals(204, terminated.status(), terminated.rawBody());
		answers.add("terminated " + publicRead(code).status());

		assertEquals(List.of("suspended 404", "released 200", "ended yesterday 404 ACTIVE", "ends today 200 ACTIVE",
				"terminated 404"), answers);
	}

	/** PB-8: while a copy with another title is a Draft, Pending review or Approved, the live title is answered. */
	@Test
	void publicRead_copyNotYetLive_liveTitleKept() throws Exception {
		Drafting live = liveListing();
		String listingId = live.listingId();
		String code = publicCodeOf(listingId);
		long rowVersion = publicationRowVersion(listingId);

		List<String> titles = new ArrayList<>();
		assertEquals(201, copy(sale, listingId, "1", dataVersion(listingId)).status());
		Result saved = saveContent(sale, listingId, "2", contentBody(dataVersion(listingId), COPY_TITLE, DESCRIPTION,
				everyFeature()));
		assertEquals(200, saved.status(), saved.rawBody());
		titles.add("DRAFT " + publicRead(code).body().path("title").asString());
		assertEquals(200, submit(sale, listingId, "2", dataVersion(listingId)).status());
		titles.add("PENDING_REVIEW " + publicRead(code).body().path("title").asString());
		unitAvailability(live.chain().unitId(), "OCCUPIED");
		decided(reviewer, listingId, "2", approval(dataVersion(listingId)));
		unitAvailability(live.chain().unitId(), "AVAILABLE");
		titles.add(revisionStatus(listingId, 2) + " " + publicRead(code).body().path("title").asString());

		assertEquals(List.of("DRAFT " + TITLE, "PENDING_REVIEW " + TITLE, "APPROVED " + TITLE), titles);
		assertEquals(rowVersion, publicationRowVersion(listingId));
	}

	/** PB-8: the copy approved while every condition holds goes live: its title, the row's row_version plus one. */
	@Test
	void publicRead_copyLive_itsTitleAndRowVersionPlusOne() throws Exception {
		String listingId = liveListing().listingId();
		String code = publicCodeOf(listingId);
		long rowVersion = publicationRowVersion(listingId);
		assertEquals(201, copy(sale, listingId, "1", dataVersion(listingId)).status());
		Result saved = saveContent(sale, listingId, "2", contentBody(dataVersion(listingId), COPY_TITLE, DESCRIPTION,
				everyFeature()));
		assertEquals(200, saved.status(), saved.rawBody());
		assertEquals(TITLE, publicRead(code).body().path("title").asString());
		assertEquals(200, submit(sale, listingId, "2", dataVersion(listingId)).status());

		decided(reviewer, listingId, "2", approval(dataVersion(listingId)));

		assertEquals("LIVE", revisionStatus(listingId, 2));
		Result answer = publicRead(code);
		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(List.of(COPY_TITLE, String.valueOf(rowVersion + 1)), List.of(answer.body().path("title")
				.asString(), String.valueOf(publicationRowVersion(listingId))));
	}

	// ==================== helpers ====================

	/** A token of the user, signed HS256 over the test profile's key, expired an hour ago. */
	private String expiredToken(String userId) throws Exception {
		Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
		long expiredAt = java.time.Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond();
		String header = url.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payload = url.encodeToString(("{\"sub\":\"" + userId + "\",\"uid\":\"" + userId + "\",\"iat\":"
				+ (expiredAt - 900) + ",\"exp\":" + expiredAt + "}").getBytes(StandardCharsets.UTF_8));
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(Base64.getDecoder().decode(testSigningKey), "HmacSHA256"));
		String signature = url.encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
		return header + "." + payload + "." + signature;
	}

	/** The headers that tell a reader something, the media type of Content-Type without its parameters. */
	private static String tellingHeaders(Result answer) {
		String contentType = answer.header("Content-Type");
		return answer.header("Cache-Control") + "|" + String.join(",", answer.response().headers().allValues("Vary"))
				+ "|" + answer.header("X-Content-Type-Options") + "|" + answer.header("X-Frame-Options") + "|"
				+ (contentType == null ? null : contentType.split(";")[0].trim());
	}

	/** The status and, for a 200, the listing's code, else the error code. */
	private static String statusAndCode(Result answer) {
		return answer.status() + " " + answer.body().path("code").asString();
	}

	private static String text(JsonNode value) {
		return value.isNull() ? "null" : value.asString();
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names.stream().sorted().toList();
	}
}
