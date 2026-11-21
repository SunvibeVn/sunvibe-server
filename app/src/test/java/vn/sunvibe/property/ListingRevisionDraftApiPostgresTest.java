package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;

/**
 * Drafting a revision over HTTP (listing-publish US-2 AC-1 to AC-3, BR-2 to BR-4; E06-S2 spec 3.2, 3.3, 3.7, section 4;
 * RD-1 to RD-3, RD-15): partial saves read back with their problems, the text and body rules answered before the role,
 * the stored text form, the terms snapshot of either of E05's versions in E05's own forms, and the terms sources. Needs
 * Docker.
 */
class ListingRevisionDraftApiPostgresTest extends ListingRevisionApiTestBase {

	@Override
	String phonePrefix() {
		return "0910";
	}

	/** RD-1: a title only saves; the read answers it and the problems a submission would answer now. */
	@Test
	void save_titleOnly_problemsAndFeaturesRoundTrip() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();

		Result saved = saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), "a".repeat(14), null,
				everyFeature()));

		assertEquals(200, saved.status(), saved.rawBody());
		assertEquals(listingId, saved.body().path("listingId").asString());
		assertEquals(1, saved.body().path("revisionNo").asInt());
		assertEquals("DRAFT", saved.body().path("status").asString());
		assertEquals(dataVersion(listingId), saved.body().path("dataVersion").asLong());
		JsonNode revision = read(listingId, "1");
		assertEquals("a".repeat(14), revision.path("title").asString());
		assertTrue(revision.path("description").isNull(), revision.toString());
		assertEquals(everyFeature(), features(revision));
		assertEquals(List.of("title.TOO_SHORT", "description.REQUIRED", "terms.REQUIRED"), problems(revision));

		Result fifteen = saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), "a".repeat(15), null,
				everyFeature()));
		assertEquals(200, fifteen.status(), fifteen.rawBody());
		assertEquals(List.of("description.REQUIRED", "terms.REQUIRED"), problems(read(listingId, "1")));
	}

	/** RD-1: a save replaces every feature. */
	@Test
	void save_allFeaturesThenSecond_replacedAll() throws Exception {
		String listingId = listingInEffect().listingId();
		assertEquals(200, saveContent(sale, listingId, "1", completeContentBody(dataVersion(listingId))).status());
		assertEquals(everyFeature(), features(read(listingId, "1")));
		Map<String, Object> second = noFeature();
		second.put("kitchen", "PRIVATE");
		second.put("fridge", true);

		Result saved = saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), TITLE, DESCRIPTION, second));

		assertEquals(200, saved.status(), saved.rawBody());
		assertEquals(second, features(read(listingId, "1")));
	}

	/** RD-2: what the revision cannot store answers 400 with its field, and nothing is stored. */
	@Test
	void save_textBounds_validationFailedNothingStored() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		RevisionRows before = revisionRows(listingId);
		Map<String, Object> rentedFurniture = everyFeature();
		rentedFurniture.put("furnishing", "RENTED");
		Map<String, Object> noFridge = everyFeature();
		noFridge.remove("fridge");
		Map<String, Map<String, Object>> bodies = new LinkedHashMap<>();
		bodies.put("title", contentBody(version, "a".repeat(91), null, everyFeature()));
		bodies.put("title (line feed)", contentBody(version, "Phòng\nmới", null, everyFeature()));
		bodies.put("description", contentBody(version, null, "a".repeat(3001), everyFeature()));
		bodies.put("description (NUL)", contentBody(version, null, "a\u0000b", everyFeature()));
		bodies.put("title (BEL)", contentBody(version, "a\u0007b", null, everyFeature()));
		bodies.put("title (zero width)", contentBody(version, "a​b", null, everyFeature()));
		bodies.put("features.furnishing", contentBody(version, null, null, rentedFurniture));
		bodies.put("features.fridge", contentBody(version, null, null, noFridge));

		for (Map.Entry<String, Map<String, Object>> body : bodies.entrySet()) {
			Result answer = saveContent(sale, listingId, "1", body.getValue());
			assertCode(400, "VALIDATION_FAILED", answer);
			assertEquals(List.of(body.getKey().split(" ")[0]), fields(answer), body.getKey());
			assertFalse(answer.rawBody().contains("RENTED"), answer.rawBody());
		}
		assertEquals(before, revisionRows(listingId));
		assertTrue(read(listingId, "1").path("title").isNull());
	}

	/** RD-2: the text is stored as normalized: NFC, LF, trimmed; 90 characters typed in NFD fit; spaces are empty. */
	@Test
	void save_nfdNinetyCrLfAndSpaces_storedNormalized() throws Exception {
		String listingId = listingInEffect().listingId();
		String ninety = "ộ".repeat(90);
		String typed = "  " + Normalizer.normalize(ninety, Normalizer.Form.NFD) + " ";

		Result saved = saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), typed,
				"Dòng một\r\nDòng hai\r\n", everyFeature()));

		assertEquals(200, saved.status(), saved.rawBody());
		JsonNode revision = read(listingId, "1");
		assertEquals(ninety, revision.path("title").asString());
		assertEquals("Dòng một\nDòng hai", revision.path("description").asString());
		assertEquals(ninety, jdbc.queryForObject("select title from listing_revisions where listing_id = ?", String.class,
				listingId));

		assertEquals(200, saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), "     ", "",
				everyFeature())).status());
		JsonNode spaces = read(listingId, "1");
		assertTrue(spaces.path("title").isNull(), spaces.toString());
		assertTrue(spaces.path("description").isNull(), spaces.toString());
	}

	/** RD-2: each command without a data version, with a negative one or with an unreadable body writes nothing. */
	@Test
	void commands_badBody_validationOrMalformedNothingWritten() throws Exception {
		String listingId = listingInEffect().listingId();
		RevisionRows before = revisionRows(listingId);
		String revision = LISTINGS + "/" + listingId + "/revisions";

		for (Object dataVersion : java.util.Arrays.asList(null, -1)) {
			List<Result> answers = List.of(
					startRevision(sale, listingId, dataVersion),
					saveContent(sale, listingId, "1", contentBody(dataVersion, TITLE, null, everyFeature())),
					captureTerms(sale, listingId, "1", dataVersion, "IN_EFFECT"),
					submit(sale, listingId, "1", dataVersion),
					withdraw(sale, listingId, "1", dataVersion));
			for (Result answer : answers) {
				assertCode(400, "VALIDATION_FAILED", answer);
				assertEquals(List.of("dataVersion"), fields(answer), answer.rawBody());
			}
		}
		for (Result answer : List.of(postRaw(sale, revision, "{"), putRaw(sale, revision + "/1/content", "{"),
				putRaw(sale, revision + "/1/terms", "{"), postRaw(sale, revision + "/1/submission", "{"),
				postRaw(sale, revision + "/1/withdrawal", "{"))) {
			assertMalformed(answer);
		}
		Result later = captureTerms(sale, listingId, "1", dataVersion(listingId), "LATER");
		assertCode(400, "VALIDATION_FAILED", later);
		assertEquals(List.of("source"), fields(later));
		assertEquals(before, revisionRows(listingId));
	}

	/** RD-2, A-6: the body rules answer before the role and the scope, so a non-assignee and a manager get 400. */
	@Test
	void save_bodyRuleBeforeRole_badRequestForNonAssigneeAndManager() throws Exception {
		String listingId = listingInEffect().listingId();
		RevisionRows before = revisionRows(listingId);

		for (TestUser caller : List.of(otherSale, manager)) {
			Result answer = saveContent(caller, listingId, "1", contentBody(dataVersion(listingId), "a".repeat(91), null,
					everyFeature()));
			assertCode(400, "VALIDATION_FAILED", answer);
			assertEquals(List.of("title"), fields(answer));
		}
		assertCode(404, "NOT_FOUND", saveContent(otherSale, listingId, "1", completeContentBody(dataVersion(listingId))));
		assertCode(403, "FORBIDDEN", saveContent(manager, listingId, "1", completeContentBody(dataVersion(listingId))));
		assertEquals(before, revisionRows(listingId));
	}

	/** RD-3: the terms in effect copied field for field, the rent in ADR-006's shape, the fees in E05's order. */
	@Test
	void terms_inEffect_copiedAndAnsweredInMoneyShape() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		AdvertisingRightView.Terms expected = answer(drafting.chain()).termsInEffectOrNull();

		Result captured = captureTerms(sale, listingId, "1", dataVersion(listingId), "IN_EFFECT");

		assertEquals(200, captured.status(), captured.rawBody());
		JsonNode snapshot = read(listingId, "1").path("termsSnapshot");
		assertEquals("IN_EFFECT", snapshot.path("source").asString());
		assertTrue(snapshot.path("pendingStartDate").isNull(), snapshot.toString());
		assertFalse(snapshot.path("capturedAt").asString().isEmpty());
		assertTerms(expected, snapshot.path("terms"));
		assertEquals(expected.mandatoryFees().size(), revisionRows(listingId).fees());
	}

	/** RD-3: the version waiting to start copied with its start date. */
	@Test
	void terms_pendingStart_pendingTermsAndStartDate() throws Exception {
		Drafting drafting = listingPendingStart();
		String listingId = drafting.listingId();
		AdvertisingRightView answer = answer(drafting.chain());

		Result captured = captureTerms(sale, listingId, "1", dataVersion(listingId), "PENDING_START");

		assertEquals(200, captured.status(), captured.rawBody());
		JsonNode snapshot = read(listingId, "1").path("termsSnapshot");
		assertEquals("PENDING_START", snapshot.path("source").asString());
		assertEquals(today().plusDays(10).toString(), snapshot.path("pendingStartDate").asString());
		assertEquals(answer.pendingStartDateOrNull().toString(), snapshot.path("pendingStartDate").asString());
		assertTerms(answer.pendingTermsOrNull(), snapshot.path("terms"));
	}

	/** RD-3: a source E05 does not have answers 409 TERMS_SOURCE_ABSENT and writes nothing. */
	@Test
	void terms_sourceMissing_termsSourceAbsent() throws Exception {
		String listingId = listingInEffect().listingId();
		RevisionRows before = revisionRows(listingId);

		assertCode(409, "TERMS_SOURCE_ABSENT", captureTerms(sale, listingId, "1", dataVersion(listingId),
				"PENDING_START"));

		assertEquals(before, revisionRows(listingId));
		assertTrue(read(listingId, "1").path("termsSnapshot").isNull());
	}

	/** RD-3: a second snapshot replaces the first one and all its fee rows. */
	@Test
	void terms_secondSnapshot_replacesFirstAndFees() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		AdvertisingRightView.Terms expected = answer(drafting.chain()).termsInEffectOrNull();
		assertEquals(200, captureTerms(sale, listingId, "1", dataVersion(listingId), "IN_EFFECT").status());
		String firstCapture = read(listingId, "1").path("termsSnapshot").path("capturedAt").asString();
		// a fee row the first snapshot did not have: the second one must replace every row
		jdbc.update("insert into listing_revision_fees (revision_id, fee_no, name, basis) select id, 20, 'Stale',"
				+ " 'INCLUDED' from listing_revisions where listing_id = ?", listingId);

		Result second = captureTerms(sale, listingId, "1", dataVersion(listingId), "IN_EFFECT");

		assertEquals(200, second.status(), second.rawBody());
		JsonNode snapshot = read(listingId, "1").path("termsSnapshot");
		assertTerms(expected, snapshot.path("terms"));
		assertFalse(firstCapture.equals(snapshot.path("capturedAt").asString()), snapshot.toString());
		assertEquals(expected.mandatoryFees().size(), revisionRows(listingId).fees());
		assertEquals(2, count("select count(*) from listing_history where listing_id = ? and action = 'TERMS_CAPTURED'",
				listingId));
	}

	/** RD-3, plan E06S2-P15: the version in effect as a source, and none once the agreement ended. */
	@Test
	void termsSources_inEffectOnly_thenEndedAfterTermination() throws Exception {
		Drafting drafting = listingInEffect();
		AdvertisingRightView.Terms expected = answer(drafting.chain()).termsInEffectOrNull();

		Result sources = getTermsSources(sale, drafting.listingId());

		assertEquals(200, sources.status(), sources.rawBody());
		assertFalse(sources.body().path("ended").asBoolean());
		assertTerms(expected, sources.body().path("inEffect"));
		assertTrue(sources.body().path("pending").isNull(), sources.rawBody());

		Result termination = terminate(sale, drafting.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		Result ended = getTermsSources(sale, drafting.listingId());
		assertEquals(200, ended.status(), ended.rawBody());
		assertTrue(ended.body().path("ended").asBoolean(), ended.rawBody());
		assertTrue(ended.body().path("inEffect").isNull(), ended.rawBody());
		assertTrue(ended.body().path("pending").isNull(), ended.rawBody());
	}

	/** RD-3, plan E06S2-P15: the version waiting to start as a source, with its start date. */
	@Test
	void termsSources_pendingStartOnly_startDateAndTerms() throws Exception {
		Drafting drafting = listingPendingStart();
		AdvertisingRightView answer = answer(drafting.chain());

		Result sources = getTermsSources(sale, drafting.listingId());

		assertEquals(200, sources.status(), sources.rawBody());
		assertFalse(sources.body().path("ended").asBoolean());
		assertTrue(sources.body().path("inEffect").isNull(), sources.rawBody());
		assertEquals(today().plusDays(10).toString(), sources.body().path("pending").path("startDate").asString());
		assertTerms(answer.pendingTermsOrNull(), sources.body().path("pending").path("terms"));
	}

	/** RD-15: a Draft's problems are exactly the entries its submission answers, in order. */
	@Test
	void revisionRead_draftProblemsEqualSubmissionEntries() throws Exception {
		String listingId = listingInEffect().listingId();
		assertEquals(200, saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), "0905.123.456",
				"Mô tả ngắn, zalo nhé.", everyFeature())).status());

		List<String> problems = problems(read(listingId, "1"));
		Result refused = submit(sale, listingId, "1", dataVersion(listingId));

		assertCode(422, "REVISION_NOT_SUBMITTABLE", refused);
		assertEquals(fields(refused), problems);
		assertEquals(List.of("title.TOO_SHORT", "description.TOO_SHORT", "terms.REQUIRED", "title.PHONE_NUMBER",
				"description.SOCIAL_HANDLE"), problems);
	}

	private JsonNode read(String listingId, String revisionNo) throws Exception {
		Result answer = getRevision(sale, listingId, revisionNo);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	private static Map<String, Object> features(JsonNode revision) {
		Map<String, Object> features = new LinkedHashMap<>();
		revision.path("features").properties().forEach(feature -> features.put(feature.getKey(),
				feature.getValue().isNull() ? null
						: feature.getValue().isBoolean() ? (Object) feature.getValue().asBoolean()
						: feature.getValue().asString()));
		return features;
	}

	/** E05's terms as listing answers them: the rent in ADR-006's shape, the deposit and the fees flat, in order. */
	private static void assertTerms(AdvertisingRightView.Terms expected, JsonNode terms) {
		assertEquals(expected.monthlyRentOrNull().longValue(), terms.path("monthlyRent").path("amount").asLong(),
				terms.toString());
		assertEquals("VND", terms.path("monthlyRent").path("currency").asString());
		assertEquals("MONTH", terms.path("monthlyRent").path("unit").asString());
		AdvertisingRightView.Deposit deposit = expected.depositOrNull();
		JsonNode depositAnswer = terms.path("deposit");
		assertEquals(deposit.basis(), depositAnswer.path("basis").asString());
		assertEquals(deposit.monthsOrNull() == null, depositAnswer.path("months").isNull(), depositAnswer.toString());
		if (deposit.monthsOrNull() != null) {
			assertEquals(deposit.monthsOrNull().intValue(), depositAnswer.path("months").asInt());
		}
		assertEquals(deposit.amountOrNull() == null, depositAnswer.path("amount").isNull(), depositAnswer.toString());
		assertEquals(deposit.amountOrNull() == null, depositAnswer.path("currency").isNull(), depositAnswer.toString());
		assertEquals(expected.minimumTermMonthsOrNull().intValue(), terms.path("minimumTermMonths").asInt());
		assertEquals(expected.paymentPeriodMonthsOrNull().intValue(), terms.path("paymentPeriodMonths").asInt());
		JsonNode fees = terms.path("mandatoryFees");
		assertEquals(expected.mandatoryFees().size(), fees.size(), terms.toString());
		for (int index = 0; index < fees.size(); index++) {
			AdvertisingRightView.MandatoryFee fee = expected.mandatoryFees().get(index);
			JsonNode answer = fees.path(index);
			assertEquals(fee.name(), answer.path("name").asString());
			assertEquals(fee.basis(), answer.path("basis").asString());
			assertEquals(fee.amountOrNull() == null ? "null" : fee.amountOrNull().toString(),
					answer.path("amount").isNull() ? "null" : Long.toString(answer.path("amount").asLong()));
			assertEquals(fee.amountOrNull() == null, answer.path("currency").isNull());
			assertEquals(fee.chargeUnitOrNull() == null ? "null" : fee.chargeUnitOrNull(),
					answer.path("chargeUnit").isNull() ? "null" : answer.path("chargeUnit").asString());
		}
	}
}
