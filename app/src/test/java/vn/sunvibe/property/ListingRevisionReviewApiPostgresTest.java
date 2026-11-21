package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationView;

/**
 * The review read over HTTP on PostgreSQL (listing-publish US-4 AC-2, AC-4, AC-5; E06-S4 spec 3.2, 4.2, E06S4-D11; RV-4):
 * the listing's code and data version, the reviewer's eligibility, E05's answer, the terms check, the unit as property
 * answers it, the freshness summary and the checklist; never the real location; the reasons beyond the queue for a
 * decided, withdrawn or archived revision, in contract order. Needs Docker.
 */
class ListingRevisionReviewApiPostgresTest extends ListingReviewApiTestBase {

	@Autowired
	UnitIntakeUseCase unitIntakes;

	@Autowired
	UnitLocationUseCase unitLocations;

	@Override
	String phonePrefix() {
		return "0907";
	}

	/** RV-4: every field of a pending revision after a recorded contact; no street, label or ward name in the body. */
	@Test
	void review_pendingRevision_everyFieldNoLocation() throws Exception {
		Drafting drafting = pendingDrafting();
		String listingId = drafting.listingId();
		LocalDate dayBefore = today();
		Result recorded = api.post(sale, LISTINGS + "/" + listingId + "/freshness-records", available(dataVersion(listingId)));
		LocalDate dayAfter = today();
		assertEquals(201, recorded.status(), recorded.rawBody());
		AdvertisingRightView e05 = answer(drafting.chain());
		UnitIntakeView unit = unitIntakes.forUnits(List.of(drafting.chain().unitId())).getFirst();

		Result answer = review(reviewer, listingId, "1");

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode body = answer.body();
		assertEquals(List.of(listingId, jdbc.queryForObject("select code from listings where id = ?", String.class,
				listingId), "1", "PENDING_REVIEW", String.valueOf(dataVersion(listingId)), "true", "[]", "IN_EFFECT"),
				List.of(body.path("listingId").asString(), body.path("code").asString(),
						body.path("revisionNo").asString(), body.path("status").asString(),
						body.path("dataVersion").asString(), body.path("viewerMayReview").asString(),
						body.path("notEligibleReasons").toString(), body.path("termsMatch").asString()));
		JsonNode advertising = body.path("advertising");
		List<String> groups = new ArrayList<>();
		advertising.path("reasonGroups").forEach(group -> groups.add(group.asString()));
		assertEquals(List.of(e05.allowed(), e05.ended(), e05.reasonGroups(), String.valueOf(e05.endDateOrNull()),
				e05.interiorPhotos(), e05.exteriorPhotos(), e05.termsInEffectOrNull().monthlyRentOrNull()),
				List.of(advertising.path("allowed").asBoolean(), advertising.path("ended").asBoolean(), groups,
						advertising.path("endDate").asString(), advertising.path("interiorPhotos").asBoolean(),
						advertising.path("exteriorPhotos").asBoolean(),
						advertising.path("termsInEffect").path("monthlyRent").path("amount").asLong()));
		assertTrue(advertising.path("pending").isNull(), advertising.toString());
		boolean differs = unit.intakes().stream().anyMatch(entry -> entry.unitReasons().contains("AVAILABILITY_DIFFERS"));
		assertEquals(List.of(String.valueOf(unit.unitCodeOrNull()), unit.availabilityState(), String.valueOf(differs)),
				List.of(body.path("unit").path("unitCode").asString(), body.path("unit").path("availability").asString(),
						body.path("unit").path("availabilityDiffers").asString()));
		JsonNode freshness = body.path("freshness");
		LocalDate confirmedOn = LocalDate.parse(freshness.path("confirmedOn").asString());
		assertTrue(List.of(dayBefore, dayAfter).contains(confirmedOn), freshness.toString());
		assertEquals(List.of(confirmedOn.plusDays(14).toString(), "false", "true"),
				List.of(freshness.path("validThrough").asString(), freshness.path("deadlinePassed").asString(),
						freshness.path("latestValid").asString()));
		List<String> checklist = new ArrayList<>();
		body.path("checklist").forEach(check -> checklist.add(check.asString()));
		assertEquals(FIVE_CHECKS, checklist);
		UnitLocationView location = unitLocations.locate(List.of(drafting.chain().unitId())).getFirst();
		assertNotNull(location.streetOrNull());
		List<String> shown = Stream.of(location.streetOrNull(), location.locationLabelOrNull(), location.wardNameOrNull())
				.filter(value -> value != null && answer.rawBody().contains(value))
				.toList();
		assertEquals(List.of(), shown);
	}

	/** RV-4: a listing under an agreement waiting to start: E05's waiting version with its start, PENDING_START. */
	@Test
	void review_pendingStartListing_pendingTermsAndMatch() throws Exception {
		Drafting drafting = listingPendingStart();
		String listingId = drafting.listingId();
		assertEquals(200, saveContent(sale, listingId, "1", completeContentBody(dataVersion(listingId))).status());
		assertEquals(200, captureTerms(sale, listingId, "1", dataVersion(listingId), "PENDING_START").status());
		assertEquals(200, submit(sale, listingId, "1", dataVersion(listingId)).status());
		AdvertisingRightView e05 = answer(drafting.chain());

		Result answer = review(reviewer, listingId, "1");

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode advertising = answer.body().path("advertising");
		assertTrue(advertising.path("termsInEffect").isNull(), advertising.toString());
		assertEquals(List.of(today().plusDays(10).toString(), String.valueOf(e05.pendingTermsOrNull().monthlyRentOrNull())),
				List.of(advertising.path("pending").path("startDate").asString(),
						advertising.path("pending").path("terms").path("monthlyRent").path("amount").asString()));
		assertEquals("PENDING_START", answer.body().path("termsMatch").asString());
	}

	/** RV-4: a returned and a withdrawn revision read with NOT_PENDING alone. */
	@Test
	void review_decidedOrWithdrawn_notPendingReason() throws Exception {
		String returned = pending();
		decided(reviewer, returned, "1", returnBody(dataVersion(returned), List.of("OTHER"), NOTE));
		String withdrawn = pending();
		assertEquals(200, withdraw(sale, withdrawn, "1", dataVersion(withdrawn)).status());

		Map<String, String> reasons = new LinkedHashMap<>();
		for (String listingId : List.of(returned, withdrawn)) {
			JsonNode body = okReview(reviewer, listingId);
			reasons.put(body.path("status").asString(), body.path("viewerMayReview").asBoolean() + " "
					+ body.path("notEligibleReasons"));
		}

		assertEquals(Map.of("NEEDS_CHANGES", "false [\"NOT_PENDING\"]", "CANCELLED", "false [\"NOT_PENDING\"]"),
				reasons);
	}

	/** RV-4, RV-12: a pending revision of an ended agreement reads LISTING_ARCHIVED. */
	@Test
	void review_endedAgreement_listingArchivedReason() throws Exception {
		Drafting drafting = pendingDrafting();
		Result termination = terminate(sale, drafting.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());

		JsonNode body = okReview(reviewer, drafting.listingId());

		assertEquals("false [\"LISTING_ARCHIVED\"]", body.path("viewerMayReview").asBoolean() + " "
				+ body.path("notEligibleReasons"));
	}

	/**
	 * RV-4: a decided revision of an ended agreement read by its author who is the assignee:
	 * [AUTHOR, ASSIGNED_SALE, NOT_PENDING, LISTING_ARCHIVED].
	 */
	@Test
	void review_decidedOnEndedAgreement_reasonsInContractOrder() throws Exception {
		TestUser author = survey.user("Review Author Reviewer", "SALE", "LISTING_REVIEWER");
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), author);
		String listingId = newListing(author, chain.agreementId());
		assertEquals(200, saveContent(author, listingId, "1", completeContentBody(dataVersion(listingId))).status());
		assertEquals(200, captureTerms(author, listingId, "1", dataVersion(listingId), "IN_EFFECT").status());
		assertEquals(200, submit(author, listingId, "1", dataVersion(listingId)).status());
		decided(reviewer, listingId, "1", rejection(dataVersion(listingId), "WRONG_UNIT", NOTE));
		Result termination = terminate(author, chain.agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());

		JsonNode body = okReview(author, listingId);

		assertEquals("[\"AUTHOR\",\"ASSIGNED_SALE\",\"NOT_PENDING\",\"LISTING_ARCHIVED\"]",
				body.path("notEligibleReasons").toString());
		assertFalse(body.path("viewerMayReview").asBoolean());
	}

	private JsonNode okReview(TestUser caller, String listingId) throws Exception {
		Result answer = review(caller, listingId, "1");
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	private static Map<String, Object> available(long dataVersion) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dataVersion", dataVersion);
		body.put("channel", "CALL");
		body.put("outcome", "AVAILABLE");
		body.put("termsStillApply", true);
		body.put("unitUnchanged", true);
		body.put("note", null);
		return body;
	}
}
