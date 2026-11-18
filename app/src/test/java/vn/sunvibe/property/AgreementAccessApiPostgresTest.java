package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Who may draft a brokerage agreement and who may open its detail (brokerage-agreement BR-16, BR-17, US-4 AC-3 part;
 * E05-S2 spec 3.1, 3.3, S2-D9, S2-D10, AD-6, AD-7, E-10): the current assignee drafts, staff who may see the basis lead
 * read, every answered read writes one access-log row and a refused one writes none. In the property test package to
 * share the intake fixtures and their Spring context (plan decision E05S2-P1). Needs Docker.
 */
class AgreementAccessApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String UNKNOWN = "01JUNKNOWN0000000000000000";

	private final List<String> created = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0951";
	}

	@AfterAll
	void deleteAgreements() {
		AgreementFixture.delete(jdbc, created);
	}

	// ==================== HTTP ====================

	private Result post(TestUser caller, Map<String, Object> body) throws Exception {
		Result answer = api.post(caller, AGREEMENTS, body);
		if (answer.status() == 201) {
			created.add(answer.body().path("agreementId").asString());
		}
		return answer;
	}

	private Result get(TestUser caller, String agreementId) throws Exception {
		return api.get(caller, AGREEMENTS + "/" + agreementId);
	}

	private static Map<String, Object> body(String leadId, String unitId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		return body;
	}

	/** The access-log rows of the agreement as "user action", oldest first. */
	private List<String> accessRows(String agreementId) {
		return jdbc.queryForList("select user_id || ' ' || action from brokerage_agreement_access_logs"
				+ " where agreement_id = ? order by id", String.class, agreementId);
	}

	private int agreementsOfUnit(String unitId) {
		return count("select count(*) from brokerage_agreements where unit_id = ?", unitId);
	}

	private static void assertNotFound(Result answer) {
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("The resource was not found.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
	}

	private static List<String> sortedKeys(JsonNode node) {
		List<String> keys = new ArrayList<>();
		node.properties().forEach(entry -> keys.add(entry.getKey()));
		return keys.stream().sorted().toList();
	}

	// ==================== fixtures ====================

	private record Done(String leadId, String unitId, String unitCodeOrNull, TestUser owner, String propertyId,
			String intakeId) {
	}

	/** A lead of {@code sale} with a completed intake, and the first unit it maps. */
	private Done done() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		JsonNode unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0);
		JsonNode code = unit.path("unitCode");
		return new Done(passed.leadId(), unit.path("unitId").asString(), code.isNull() ? null : code.asString(), owner,
				propertyId, (String) intakeRow(passed.leadId()).get("id"));
	}

	/** The assigned Sale's draft of the lead's unit; the answer must be 201. */
	private String drafted(Done done) throws Exception {
		Result answer = post(sale, body(done.leadId(), done.unitId()));
		assertEquals(201, answer.status(), answer.rawBody());
		return answer.body().path("agreementId").asString();
	}

	// ==================== AD-6: who drafts ====================

	@Test
	void create_assignedSaleOrSaleLeaderAssignee_created() throws Exception {
		Done bySale = done();
		assertEquals(201, post(sale, body(bySale.leadId(), bySale.unitId())).status());

		Done byLeader = done();
		fixture.checklists().reassign(leader, byLeader.leadId(), leader);
		Result answer = post(leader, body(byLeader.leadId(), byLeader.unitId()));

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(leader.userId(), jdbc.queryForObject("select created_by_user_id from brokerage_agreements where id = ?",
				String.class, answer.body().path("agreementId").asString()));
	}

	@Test
	void create_managersAdminsSuperAdmin_notAssignee() throws Exception {
		Done done = done();
		TestUser superAdmin = survey.user("Agreement Super Admin", "SUPER_ADMIN");

		for (TestUser caller : List.of(leader, manager, admin, superAdmin)) {
			Result answer = post(caller, body(done.leadId(), done.unitId()));

			assertEquals(403, answer.status(), answer.rawBody());
			assertEquals("AGREEMENT_NOT_ASSIGNEE", answer.body().path("code").asString(), answer.rawBody());
			assertEquals("Only the lead's assigned Sale may draft an agreement.", answer.body().path("message").asString());
			assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
		}
		assertEquals(0, agreementsOfUnit(done.unitId()));
	}

	@Test
	void create_otherSale_notFoundLikeUnknownLead() throws Exception {
		Done done = done();

		assertNotFound(post(otherSale, body(done.leadId(), done.unitId())));
		assertNotFound(post(sale, body(UNKNOWN, done.unitId())));
		assertEquals(0, agreementsOfUnit(done.unitId()));
	}

	@Test
	void create_customerOrListingReviewer_forbidden() throws Exception {
		Done done = done();
		TestUser customer = survey.user("Agreement Customer", "CUSTOMER");
		TestUser reviewer = survey.user("Agreement Reviewer", "LISTING_REVIEWER");

		for (TestUser caller : List.of(customer, reviewer)) {
			Result answer = post(caller, body(done.leadId(), done.unitId()));
			assertEquals(403, answer.status(), answer.rawBody());
			assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
		}
		assertEquals(0, agreementsOfUnit(done.unitId()));
	}

	@Test
	void create_noToken_unauthorized() throws Exception {
		Done done = done();

		Result answer = post(null, body(done.leadId(), done.unitId()));

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
		assertEquals(0, agreementsOfUnit(done.unitId()));
	}

	@Test
	void create_afterReassignment_formerSaleNotFound_newSaleCreated() throws Exception {
		Done done = done();
		fixture.checklists().reassign(leader, done.leadId(), otherSale);

		assertNotFound(post(sale, body(done.leadId(), done.unitId())));
		Result answer = post(otherSale, body(done.leadId(), done.unitId()));

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(1, agreementsOfUnit(done.unitId()));
	}

	@Test
	void create_bodyFieldsForStatusOwnerActor_ignored() throws Exception {
		Done done = done();
		Instant start = Instant.now().minusSeconds(1);
		Map<String, Object> body = body(done.leadId(), done.unitId());
		body.put("status", "ACTIVE");
		body.put("ownerUserId", "01JXOWNER00000000000000000");
		body.put("createdByUserId", "01JXCREATOR000000000000000");
		body.put("actorUserId", "01JXACTOR00000000000000000");
		body.put("createdAt", "2020-01-01T00:00:00Z");

		Result answer = post(sale, body);

		assertEquals(201, answer.status(), answer.rawBody());
		Map<String, Object> row = jdbc.queryForMap("select status, owner_user_id, created_by_user_id, created_at"
				+ " from brokerage_agreements where id = ?", answer.body().path("agreementId").asString());
		assertEquals("DRAFTING", row.get("status"));
		assertEquals(done.owner().userId(), row.get("owner_user_id"));
		assertEquals(sale.userId(), row.get("created_by_user_id"));
		assertTrue(((Timestamp) row.get("created_at")).toInstant().isAfter(start), row.toString());
	}

	// ==================== AD-7: who reads, and the access log ====================

	@Test
	void detail_assignedSale_exactFieldsAndOneAccessRowPerRead() throws Exception {
		Done done = done();
		String id = drafted(done);

		Result answer = get(sale, id);

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode detail = answer.body();
		assertEquals(List.of("activationStatement", "advertising", "agreementId", "basisLeadId", "createdAt", "dataVersion",
				"status", "transactionType", "unitCode", "unitId", "versions", "viewerIsAssignedSale",
				"viewerMayDecideActivation"), sortedKeys(detail));
		assertEquals(1, detail.path("versions").size(), answer.rawBody());
		JsonNode version = detail.path("versions").path(0);
		assertEquals(List.of("activateBy", "activation", "advertisingScope", "answerBy", "createdAt", "endDate",
				"exteriorPhotoConsent", "feePolicy", "ownerAnsweredAt", "ownerRejectionReason", "photoConsent", "sentAt",
				"startDate", "status", "terms", "versionNo"), sortedKeys(version));
		assertEquals(id, detail.path("agreementId").asString());
		assertEquals("DRAFTING", detail.path("status").asString());
		assertEquals("LONG_TERM_RENT", detail.path("transactionType").asString());
		assertEquals(done.unitId(), detail.path("unitId").asString());
		assertEquals(done.unitCodeOrNull(), detail.path("unitCode").isNull() ? null : detail.path("unitCode").asString());
		assertEquals(done.leadId(), detail.path("basisLeadId").asString());
		assertEquals(1, version.path("versionNo").asInt());
		assertEquals("DRAFT", version.path("status").asString());
		assertEquals("2027-01-01", version.path("startDate").asString());
		assertEquals("2027-12-31", version.path("endDate").asString());
		for (String secret : List.of(done.owner().userId(), done.intakeId(), done.propertyId())) {
			assertFalse(answer.rawBody().contains(secret), secret + " in " + answer.rawBody());
		}
		assertEquals(List.of(sale.userId() + " DETAIL_READ"), accessRows(id));

		assertEquals(200, get(sale, id).status());

		assertEquals(List.of(sale.userId() + " DETAIL_READ", sale.userId() + " DETAIL_READ"), accessRows(id));
	}

	@Test
	void detail_managersWhoSeeTheLead_readWithARowEach() throws Exception {
		Done done = done();
		String id = drafted(done);
		List<String> expected = new ArrayList<>();

		for (TestUser reader : List.of(leader, manager, admin)) {
			Result answer = get(reader, id);

			assertEquals(200, answer.status(), reader + " " + answer.rawBody());
			expected.add(reader.userId() + " DETAIL_READ");
			assertEquals(expected, accessRows(id));
		}
	}

	@Test
	void detail_otherSaleUnknownOrMalformed_notFoundNoRow() throws Exception {
		Done done = done();
		String id = drafted(done);

		assertNotFound(get(otherSale, id));
		for (String other : List.of(UNKNOWN, "not-an-id", id.toLowerCase(Locale.ROOT))) {
			assertNotFound(get(sale, other));
		}

		assertEquals(List.of(), accessRows(id));
	}

	@Test
	void detail_customerRole_forbidden() throws Exception {
		Done done = done();
		String id = drafted(done);
		TestUser customer = survey.user("Agreement Reader Customer", "CUSTOMER");

		Result answer = get(customer, id);

		assertEquals(403, answer.status(), answer.rawBody());
		assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
		assertEquals(List.of(), accessRows(id));
	}

	@Test
	void detail_noToken_unauthorized() throws Exception {
		Done done = done();
		String id = drafted(done);

		Result answer = get(null, id);

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
		assertEquals(List.of(), accessRows(id));
	}

	@Test
	void detail_afterReassignment_formerSaleNotFound_newSaleReads() throws Exception {
		Done done = done();
		String id = drafted(done);
		fixture.checklists().reassign(leader, done.leadId(), otherSale);

		assertNotFound(get(sale, id));
		assertEquals(List.of(), accessRows(id));

		assertEquals(200, get(otherSale, id).status());
		assertEquals(List.of(otherSale.userId() + " DETAIL_READ"), accessRows(id));
	}
}
