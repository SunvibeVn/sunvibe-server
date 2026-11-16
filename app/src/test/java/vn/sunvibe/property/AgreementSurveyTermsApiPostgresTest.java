package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.ChecklistApiTestSupport;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;

/**
 * The survey reference of a draft over HTTP on PostgreSQL (brokerage-agreement US-1 AC-3; BR-8, BR-16, BR-17; E05-S3
 * spec 3.3, S3-D11, S3-D12, ED-9, ED-10, ED-3): the G8 values of the revision the draft's intake rests on, for the
 * unit's space, with their sources and dates; the empty proposal when that revision no longer stands or the unit has no
 * surveyed space; nothing written. Needs Docker.
 */
class AgreementSurveyTermsApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String UNKNOWN = "01JUNKNOWN0000000000000000";
	private static final String EMPTY = "{\"terms\":{\"monthlyRent\":null,\"deposit\":null,\"minimumTermMonths\":null,"
			+ "\"paymentPeriodMonths\":null,\"mandatoryFees\":null},\"sources\":[]}";

	private final List<String> created = new ArrayList<>();
	private Surveyed shared;

	@Override
	String phonePrefix() {
		return "0946";
	}

	@AfterAll
	void deleteAgreements() {
		AgreementFixture.delete(jdbc, created);
	}

	private Result get(TestUser caller, String agreementId) throws Exception {
		return api.get(caller, AGREEMENTS + "/" + agreementId + "/survey-terms");
	}

	// ==================== fixtures ====================

	/** A building lead of {@code sale} surveyed with G8 values, completed, and the Sale's draft on P101's unit. */
	private record Surveyed(Passed passed, String intakeId, String unitId, String agreementId, LocalDate visit,
			LocalDate stated) {
	}

	/** The G8 values of ED-9: P101 rent stated by the owner, its deposit, term, period and electricity override. */
	private Surveyed surveyed(boolean withTerms) throws Exception {
		ChecklistApiTestSupport checklists = fixture.checklists();
		LocalDate stated = LocalDate.now(SurveyTimeRules.ZONE).minusDays(2);
		String street = unique("Survey Terms Building");
		Passed passed = withTerms ? fixture.partsWithTerms(leader, sale, "WHOLE_BUILDING", street, c -> {
			ChecklistApiTestSupport.set(c, "spaces.0.monthlyRent",
					checklists.ownerStated(checklists.rent(8_000_000L), stated, sale.userId(), null));
			ChecklistApiTestSupport.set(c, "spaces.0.deposit", seen(deposit()));
			ChecklistApiTestSupport.set(c, "spaces.0.paymentPeriodMonths", seen(1));
			ChecklistApiTestSupport.set(c, "spaces.0.minimumTermMonths", seen(12));
			ChecklistApiTestSupport.set(c, "spaces.0.chargeOverrides.electricity",
					seen(checklists.charge("AMOUNT", 3_500L, "PER_KWH")));
			ChecklistApiTestSupport.set(c, "spaces.1.monthlyRent", seen(checklists.rent(9_000_000L)));
			ChecklistApiTestSupport.set(c, "common.water", seen(checklists.charge("PROVIDER_TARIFF", null, null)));
			ChecklistApiTestSupport.set(c, "common.internet", seen(checklists.charge("INCLUDED", null, null)));
			ChecklistApiTestSupport.set(c, "common.parking", seen(checklists.charge("NOT_OFFERED", null, null)));
			ObjectNode cleaning = checklists.object();
			cleaning.put("name", "Cleaning");
			cleaning.set("amount", checklists.money(100_000));
			cleaning.put("chargeUnit", "PER_MONTH");
			ChecklistApiTestSupport.set(c, "common.otherFees", seen(List.of(cleaning)));
		}, "P101", "P102") : fixture.parts(leader, sale, "WHOLE_BUILDING", street, "P101", "P102");
		checked(passed, owner());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), passed.spaceIds().get(0)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String intakeId = (String) intakeRow(passed.leadId()).get("id");
		String unit = jdbc.queryForObject("select unit_id from inventory_unit_mappings where intake_id = ? and"
				+ " space_code = 'P101'", String.class, intakeId);
		LocalDate visit = SurveyTimeRules.vietnamDate(jdbc.queryForObject("select visit_arrived_at from"
				+ " survey_checklist_submissions where checklist_id = ?", Timestamp.class, passed.checklistId())
				.toInstant());
		return new Surveyed(passed, intakeId, unit, drafted(passed.leadId(), unit), visit, stated);
	}

	/** One surveyed lead read by the tests that change nothing of it. */
	private Surveyed shared() throws Exception {
		if (shared == null) {
			shared = surveyed(true);
		}
		return shared;
	}

	private String drafted(String leadId, String unitId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		return id;
	}

	private ObjectNode seen(Object value) {
		return fixture.checklists().sourced(value, "OBSERVED_ON_SITE");
	}

	private ObjectNode deposit() {
		ObjectNode deposit = fixture.checklists().object();
		deposit.put("basis", "MONTHS");
		deposit.put("months", 2);
		deposit.putNull("amount");
		return deposit;
	}

	private static void assertNotFound(Result answer) {
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
	}

	// ==================== ED-9: the proposal of the unit's space ====================

	@Test
	void surveyTerms_assignedSale_proposalOfTheUnitsSpace() throws Exception {
		Surveyed surveyed = shared();

		Result answer = get(sale, surveyed.agreementId());

		assertEquals(200, answer.status(), answer.rawBody());
		String seen = "\"OBSERVED_ON_SITE\",\"date\":\"" + surveyed.visit() + "\"}";
		assertJson("{\"terms\":{\"monthlyRent\":{\"amount\":8000000,\"currency\":\"VND\",\"unit\":\"MONTH\"},"
				+ "\"deposit\":{\"basis\":\"MONTHS\",\"months\":2,\"amount\":null,\"currency\":null},"
				+ "\"minimumTermMonths\":12,\"paymentPeriodMonths\":1,\"mandatoryFees\":["
				+ "{\"name\":\"Electricity\",\"basis\":\"AMOUNT\",\"amount\":3500,\"currency\":\"VND\",\"chargeUnit\":\"PER_KWH\"},"
				+ "{\"name\":\"Water\",\"basis\":\"PROVIDER_TARIFF\",\"amount\":null,\"currency\":null,\"chargeUnit\":null},"
				+ "{\"name\":\"Internet\",\"basis\":\"INCLUDED\",\"amount\":null,\"currency\":null,\"chargeUnit\":null},"
				+ "{\"name\":\"Cleaning\",\"basis\":\"AMOUNT\",\"amount\":100000,\"currency\":\"VND\",\"chargeUnit\":\"PER_MONTH\"}]},"
				+ "\"sources\":[{\"field\":\"monthlyRent\",\"source\":\"OWNER_STATED\",\"date\":\"" + surveyed.stated() + "\"},"
				+ "{\"field\":\"deposit\",\"source\":" + seen + ","
				+ "{\"field\":\"minimumTermMonths\",\"source\":" + seen + ","
				+ "{\"field\":\"paymentPeriodMonths\",\"source\":" + seen + ","
				+ "{\"field\":\"mandatoryFees[0]\",\"source\":" + seen + ","
				+ "{\"field\":\"mandatoryFees[1]\",\"source\":" + seen + ","
				+ "{\"field\":\"mandatoryFees[2]\",\"source\":" + seen + ","
				+ "{\"field\":\"mandatoryFees[3]\",\"source\":" + seen + "]}", answer.body());
	}

	/** The answer equals this JSON, object keys in any order, arrays in order. */
	private void assertJson(String expectedJson, JsonNode actual) {
		assertEquals(api.json().readTree(expectedJson), actual, actual.toString());
	}

	@Test
	void surveyTerms_read_writesNothing() throws Exception {
		Surveyed surveyed = shared();
		String id = surveyed.agreementId();
		int securityEvents = fixture.checklists().securityEvents(surveyed.passed().leadId()).size();
		Map<String, Object> agreement = jdbc.queryForMap("select * from brokerage_agreements where id = ?", id);
		List<String> rows = List.of("brokerage_agreement_versions", "brokerage_agreement_events",
				"brokerage_agreement_access_logs");
		List<Integer> before = rows.stream().map(table -> AgreementFixture.rows(jdbc, table, id)).toList();

		assertEquals(200, get(sale, id).status());
		assertEquals(200, get(manager, id).status());

		assertEquals(before, rows.stream().map(table -> AgreementFixture.rows(jdbc, table, id)).toList());
		assertEquals(0, AgreementFixture.rows(jdbc, "brokerage_agreement_access_logs", id));
		assertEquals(agreement, jdbc.queryForMap("select * from brokerage_agreements where id = ?", id));
		assertEquals(securityEvents, fixture.checklists().securityEvents(surveyed.passed().leadId()).size());
	}

	// ==================== ED-10: the empty proposal, and who reads ====================

	@Test
	void surveyTerms_noG8Values_empty() throws Exception {
		Surveyed bare = surveyed(false);

		Result answer = get(sale, bare.agreementId());

		assertEquals(200, answer.status(), answer.rawBody());
		assertJson(EMPTY, answer.body());
	}

	@Test
	void surveyTerms_markedRevision_empty() throws Exception {
		Surveyed marked = surveyed(true);
		fixture.requalify(leader, marked.passed().checklistId(), "OTHER");

		Result answer = get(sale, marked.agreementId());

		assertEquals(200, answer.status(), answer.rawBody());
		assertJson(EMPTY, answer.body());
	}

	@Test
	void surveyTerms_unitWithoutMappedSpace_empty() throws Exception {
		Surveyed unmapped = surveyed(true);
		jdbc.update("delete from inventory_unit_mappings where intake_id = ? and unit_id = ?", unmapped.intakeId(),
				unmapped.unitId());

		Result answer = get(sale, unmapped.agreementId());

		assertEquals(200, answer.status(), answer.rawBody());
		assertJson(EMPTY, answer.body());
	}

	@Test
	void surveyTerms_managerWhoSeesTheLead_ok() throws Exception {
		Result answer = get(manager, shared().agreementId());

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(8_000_000L, answer.body().path("terms").path("monthlyRent").path("amount").asLong());
	}

	@Test
	void surveyTerms_otherSaleUnknownOrMalformed_notFound() throws Exception {
		String id = shared().agreementId();

		assertNotFound(get(otherSale, id));
		for (String other : List.of(UNKNOWN, "not-an-id", id.toLowerCase(Locale.ROOT))) {
			assertNotFound(get(sale, other));
		}
	}

	@Test
	void surveyTerms_customer_forbidden() throws Exception {
		TestUser customer = survey.user("Survey Terms Customer", "CUSTOMER");

		Result answer = get(customer, shared().agreementId());

		assertEquals(403, answer.status(), answer.rawBody());
		assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
	}

	@Test
	void surveyTerms_noToken_unauthorized() throws Exception {
		Result answer = get(null, shared().agreementId());

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
	}

	/** ED-3: only a draft has a survey reference. */
	@Test
	void surveyTerms_awaitingOwnerOrCancelled_notDraft() throws Exception {
		Passed passed = house(unique("Survey Terms Not Draft"));
		for (String[] state : List.of(new String[] {"DRAFTING", "AWAITING_OWNER"}, new String[] {"CANCELLED", "CANCELLED"})) {
			String id = AgreementFixture.insert(jdbc, ids, ids.generateId(), passed.leadId(), state[0], state[1]);
			created.add(id);

			Result answer = get(sale, id);

			assertEquals(409, answer.status(), answer.rawBody());
			JsonNode body = answer.body();
			assertEquals("AGREEMENT_NOT_DRAFT", body.path("code").asString(), answer.rawBody());
			assertEquals("[]", body.path("errors").toString(), answer.rawBody());
		}
	}
}
