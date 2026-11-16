package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Who may edit a draft (brokerage-agreement BR-16, E-10; E05-S3 spec 3.1, ED-5): only the basis lead's current
 * assignee holding SALE or SALE_LEADER; others who see the lead are refused, others answer as an unknown id; no body
 * field sets a status, owner, basis or scope. Needs Docker.
 */
class AgreementDraftAccessApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String UNKNOWN = "01JUNKNOWN0000000000000000";

	private final List<String> created = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0944";
	}

	@AfterAll
	void deleteAgreements() {
		AgreementFixture.delete(jdbc, created);
	}

	private Result put(TestUser caller, String agreementId, Map<String, Object> body) throws Exception {
		return api.put(caller, AGREEMENTS + "/" + agreementId + "/draft", body);
	}

	private record Done(String leadId, TestUser owner, String unitId, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's draft on its unit. */
	private Done done() throws Exception {
		Passed passed = house(unique("Access Street"));
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("leadId", passed.leadId());
		body.put("unitId", unit);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		return new Done(passed.leadId(), owner, unit, id);
	}

	private List<String> edits(String agreementId) {
		return jdbc.queryForList("select actor_user_id from brokerage_agreement_events where agreement_id = ?"
				+ " and event_type = 'DRAFT_EDITED' order by occurred_at, id", String.class, agreementId);
	}

	private static void assertNotFound(Result answer) {
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("The resource was not found.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
	}

	// ==================== ED-5: who edits ====================

	@Test
	void draft_assignedSaleOrSaleLeaderAssignee_saved() throws Exception {
		Done bySale = done();
		assertEquals(204, put(sale, bySale.agreementId(), AgreementDraftApiPostgresTest.example(0)).status());
		assertEquals(List.of(sale.userId()), edits(bySale.agreementId()));

		Done byLeader = done();
		fixture.checklists().reassign(leader, byLeader.leadId(), leader);
		Result answer = put(leader, byLeader.agreementId(), AgreementDraftApiPostgresTest.example(0));

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(List.of(leader.userId()), edits(byLeader.agreementId()));
	}

	@Test
	void draft_managersAdminsSuperAdmin_notAssignee() throws Exception {
		Done done = done();
		TestUser superAdmin = survey.user("Draft Super Admin", "SUPER_ADMIN");

		for (TestUser caller : List.of(leader, manager, admin, superAdmin)) {
			Result answer = put(caller, done.agreementId(), AgreementDraftApiPostgresTest.example(0));

			assertEquals(403, answer.status(), answer.rawBody());
			assertEquals("AGREEMENT_NOT_ASSIGNEE", answer.body().path("code").asString(), answer.rawBody());
			assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
		}
		assertEquals(List.of(), edits(done.agreementId()));
	}

	@Test
	void draft_otherSale_notFoundLikeUnknown() throws Exception {
		Done done = done();

		assertNotFound(put(otherSale, done.agreementId(), AgreementDraftApiPostgresTest.example(0)));
		for (String other : List.of(UNKNOWN, "not-an-id", done.agreementId().toLowerCase(Locale.ROOT))) {
			assertNotFound(put(sale, other, AgreementDraftApiPostgresTest.example(0)));
		}
		assertEquals(List.of(), edits(done.agreementId()));
	}

	@Test
	void draft_customerOrListingReviewer_forbidden() throws Exception {
		Done done = done();
		TestUser customer = survey.user("Draft Customer", "CUSTOMER");
		TestUser reviewer = survey.user("Draft Reviewer", "LISTING_REVIEWER");

		for (TestUser caller : List.of(customer, reviewer)) {
			Result answer = put(caller, done.agreementId(), AgreementDraftApiPostgresTest.example(0));
			assertEquals(403, answer.status(), answer.rawBody());
			assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
		}
		assertEquals(List.of(), edits(done.agreementId()));
	}

	@Test
	void draft_noToken_unauthorized() throws Exception {
		Done done = done();

		Result answer = put(null, done.agreementId(), AgreementDraftApiPostgresTest.example(0));

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
		assertEquals(List.of(), edits(done.agreementId()));
	}

	/** brokerage-agreement E-10: a reassignment moves the right at once. */
	@Test
	void draft_afterReassignment_formerSaleNotFound_newSaleSaves() throws Exception {
		Done done = done();
		fixture.checklists().reassign(leader, done.leadId(), otherSale);

		assertNotFound(put(sale, done.agreementId(), AgreementDraftApiPostgresTest.example(0)));
		Result answer = put(otherSale, done.agreementId(), AgreementDraftApiPostgresTest.example(0));

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(List.of(otherSale.userId()), edits(done.agreementId()));
	}

	/** E05-S3 spec S3-D5: no body field sets a status, owner, basis, version number or scope. */
	@Test
	void draft_bodyFieldsForStatusOwnerBasisScope_ignored() throws Exception {
		Done done = done();
		Map<String, Object> body = AgreementDraftApiPostgresTest.example(0);
		body.put("status", "ACTIVE");
		body.put("ownerUserId", "01JXOWNER00000000000000000");
		body.put("basisLeadId", "01JXLEAD000000000000000000");
		body.put("advertisingScope", "SOCIAL_NETWORKS");
		body.put("versionNo", 7);

		Result answer = put(sale, done.agreementId(), body);

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> agreement = jdbc.queryForMap("select status, owner_user_id, basis_lead_id from"
				+ " brokerage_agreements where id = ?", done.agreementId());
		assertEquals("DRAFTING", agreement.get("status"));
		assertEquals(done.owner().userId(), agreement.get("owner_user_id"));
		assertEquals(done.leadId(), agreement.get("basis_lead_id"));
		Map<String, Object> version = jdbc.queryForMap("select version_no, status, basis_lead_id from"
				+ " brokerage_agreement_versions where agreement_id = ?", done.agreementId());
		assertEquals(1, version.get("version_no"));
		assertEquals("DRAFT", version.get("status"));
		assertEquals(done.leadId(), version.get("basis_lead_id"));
	}
}
