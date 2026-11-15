package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
 * The assigned Sale drafts a brokerage agreement for an eligible unit (brokerage-agreement US-1 AC-1 to AC-4; BR-1 to
 * BR-6, BR-18; E05-S2 spec 3.1, AD-1 to AD-5, AD-9), through the real filter chain on PostgreSQL. In the property test
 * package to share the intake fixtures and their Spring context (plan decision E05S2-P1). Needs Docker.
 */
class AgreementCreateApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String WARD_NAME = "Hải Châu";
	private static final String UNMAPPED_UNIT = "01JUNMAPPED" + "0".repeat(14) + "A";
	private static final String GROUP_MESSAGE = "This group stops the agreement.";
	private static final String FIELD_MESSAGE = "This value is not accepted.";

	private final List<String> created = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0950";
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

	private static Map<String, Object> body(String leadId, String unitId, String start, String end) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", start);
		body.put("endDate", end);
		return body;
	}

	private static Map<String, Object> body(String leadId, String unitId) {
		return body(leadId, unitId, "2027-01-01", "2027-12-31");
	}

	/** The first unit of the lead's readiness, which a completed intake maps. */
	private String unitOf(String leadId) throws Exception {
		return blockOk(sale, leadId).path("readiness").path("units").path(0).path("unitId").asString();
	}

	private int agreementsOfUnit(String unitId) {
		return count("select count(*) from brokerage_agreements where unit_id = ?", unitId);
	}

	/** A refusal: its status, code, message and exact errors, and no agreement of the unit. */
	private void assertRefusedWithNothing(Result answer, int status, String code, String message, String errors,
			String unitId) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(message, answer.body().path("message").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
		assertEquals(0, agreementsOfUnit(unitId), answer.rawBody());
	}

	private void assertNotEligible(Result answer, String unitId, String... groups) {
		String errors = Arrays.stream(groups)
				.map(group -> "{\"field\":\"reasonGroups." + group + "\",\"message\":\"" + GROUP_MESSAGE + "\"}")
				.reduce((left, right) -> left + "," + right).orElse("");
		assertRefusedWithNothing(answer, 409, "AGREEMENT_NOT_ELIGIBLE", "This unit cannot get an agreement now.",
				"[" + errors + "]", unitId);
	}

	private static String fieldError(String field) {
		return "[{\"field\":\"" + field + "\",\"message\":\"" + FIELD_MESSAGE + "\"}]";
	}

	private static String text(JsonNode node) {
		return node.isNull() || node.isMissingNode() ? null : node.asString();
	}

	private static List<String> sortedKeys(JsonNode node) {
		List<String> keys = new ArrayList<>();
		node.properties().forEach(entry -> keys.add(entry.getKey()));
		return keys.stream().sorted().toList();
	}

	// ==================== fixtures (as BrokerageEligibilityApiPostgresTest) ====================

	private record Done(Passed passed, TestUser owner, JsonNode result) {
		String leadId() {
			return passed.leadId();
		}

		String propertyId() {
			return result.path("propertyId").asString();
		}
	}

	private Done done() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return new Done(passed, owner, completed(passed.leadId()));
	}

	private record Pair(Passed first, TestUser owner, String propertyId, Map<String, String> unitBySpaceCode,
			Passed second) {
	}

	/** Two houses on one street: the first creates the property, the second links onto its one unit; both completed. */
	private Pair housePair() throws Exception {
		String street = unique("Two Leads");
		Passed first = house(street);
		TestUser owner = owner();
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		available(first.leadId(), first.spaceIds(), "VACANT");
		JsonNode result = completed(first.leadId());
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		Passed second = house(street);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(first, owner, propertyId, Map.of("A1", unit), second);
	}

	/**
	 * A building lead with P101 and P102 created and completed, and a second building lead on the same street linked
	 * space by space onto the units of the same codes.
	 */
	private Pair buildingPair(String... secondCodes) throws Exception {
		String street = unique("Two Leads Building");
		Passed first = fixture.parts(leader, sale, "WHOLE_BUILDING", street, "P101", "P102");
		TestUser owner = owner();
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), first.spaceIds().get(0)));
		available(first.leadId(), first.spaceIds(), "VACANT");
		String propertyId = completed(first.leadId()).path("propertyId").asString();
		Map<String, String> unitBySpaceCode = new LinkedHashMap<>();
		jdbc.query("select space_code, unit_id from inventory_unit_mappings where intake_id = ?",
				row -> {
					unitBySpaceCode.put(row.getString("space_code"), row.getString("unit_id"));
				}, intakeRow(first.leadId()).get("id"));
		Passed second = fixture.parts(leader, sale, "WHOLE_BUILDING", street, secondCodes);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(),
				Arrays.stream(secondCodes).map(unitBySpaceCode::get).toList()));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(first, owner, propertyId, unitBySpaceCode, second);
	}

	/** A surveyed house linked onto a legacy property of its owner whose one unit is in this state, ready to complete. */
	private Passed linkedHouse(String unitState) throws Exception {
		Passed passed = house(unique("Link Street"));
		TestUser owner = owner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class,
				passed.leadId());
		String id = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(id, owner.userId());
		String unit = unit(id, "U1", unitState);
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), id, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return passed;
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	/** An ownership row as {@link #ownership} inserts it, with the relation and the validity named. */
	private void ownershipRow(String propertyId, String userId, String relation, LocalDate from, LocalDate toOrNull) {
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
						+ " valid_to, created_at) values (?, ?, ?, ?, 'UNVERIFIED', ?, ?, ?)", ids.generateId(), propertyId, userId,
				relation, from, toOrNull, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
	}

	// ==================== AD-1: the draft ====================

	@Test
	void create_assignedSale_writesTheDraftAndNoAccessRow() throws Exception {
		Done done = done();
		JsonNode readinessUnit = blockOk(sale, done.leadId()).path("readiness").path("units").path(0);
		String unit = readinessUnit.path("unitId").asString();

		Result answer = post(sale, body(done.leadId(), unit, "2027-01-01", "2027-12-31"));

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(List.of("agreementId"), sortedKeys(answer.body()));
		String id = answer.body().path("agreementId").asString();
		Map<String, Object> agreement = jdbc.queryForMap("select * from brokerage_agreements where id = ?", id);
		assertEquals("DRAFTING", agreement.get("status"));
		assertEquals(unit, agreement.get("unit_id"));
		assertEquals(text(readinessUnit.path("unitCode")), agreement.get("unit_code"));
		assertEquals(done.propertyId(), agreement.get("property_id"));
		assertEquals("LONG_TERM_RENT", agreement.get("transaction_type"));
		assertEquals(done.owner().userId(), agreement.get("owner_user_id"));
		assertEquals(done.leadId(), agreement.get("basis_lead_id"));
		assertEquals(sale.userId(), agreement.get("created_by_user_id"));
		assertEquals(0L, agreement.get("version"));
		List<Map<String, Object>> versions = jdbc.queryForList(
				"select * from brokerage_agreement_versions where agreement_id = ?", id);
		assertEquals(1, versions.size());
		Map<String, Object> version = versions.get(0);
		assertEquals(1, version.get("version_no"));
		assertEquals("DRAFT", version.get("status"));
		assertEquals(LocalDate.parse("2027-01-01"), ((Date) version.get("start_date")).toLocalDate());
		assertEquals(LocalDate.parse("2027-12-31"), ((Date) version.get("end_date")).toLocalDate());
		assertEquals(done.leadId(), version.get("basis_lead_id"));
		assertEquals(intakeRow(done.leadId()).get("id"), version.get("basis_intake_id"));
		assertEquals(sale.userId(), version.get("created_by_user_id"));
		List<Map<String, Object>> events = jdbc.queryForList(
				"select event_type, version_no, actor_user_id from brokerage_agreement_events where agreement_id = ?", id);
		assertEquals(List.of(Map.of("event_type", "CREATED", "version_no", 1, "actor_user_id", sale.userId())), events);
		assertEquals(0, AgreementFixture.rows(jdbc, "brokerage_agreement_access_logs", id));
	}

	// ==================== AD-2: the E05-S1 check reruns ====================

	@Test
	void create_leadHeld_notEligibleBasis() throws Exception {
		Done done = done();
		String unit = unitOf(done.leadId());
		hold(done.leadId());

		assertNotEligible(post(sale, body(done.leadId(), unit)), unit, "BASIS");
	}

	@Test
	void create_otherLeadWholeSuspension_notEligibleProperty() throws Exception {
		Pair pair = housePair();
		String unit = pair.unitBySpaceCode().get("A1");
		suspendLead(pair.first().leadId(), null, "Data request");

		assertNotEligible(post(sale, body(pair.second().leadId(), unit)), unit, "PROPERTY");
	}

	@Test
	void create_unitSuspension_notEligibleUnit() throws Exception {
		Pair pair = buildingPair("P101", "P102");
		String p101 = pair.unitBySpaceCode().get("P101");
		suspendLead(pair.first().leadId(), p101, "Room only");

		assertNotEligible(post(sale, body(pair.second().leadId(), p101)), p101, "UNIT");
	}

	@Test
	void create_ownerTakesStaffRole_notEligibleBasisPropertyOwner() throws Exception {
		Done done = done();
		String unit = unitOf(done.leadId());
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'", done.owner().userId());

		assertNotEligible(post(sale, body(done.leadId(), unit)), unit, "BASIS", "PROPERTY", "OWNER");
	}

	@Test
	void create_coOwnerRow_notEligibleOwner() throws Exception {
		Done done = done();
		String unit = unitOf(done.leadId());
		ownershipRow(done.propertyId(), ids.generateId(), "CO_OWNER", LocalDate.of(2026, 1, 1), null);

		assertNotEligible(post(sale, body(done.leadId(), unit)), unit, "OWNER");
	}

	@Test
	void create_unitNotMappedOrNoCompletedIntake_notEligibleBasis() throws Exception {
		Done done = done();
		assertNotEligible(post(sale, body(done.leadId(), UNMAPPED_UNIT)), UNMAPPED_UNIT, "BASIS");

		Passed noIntake = house(unique("Pasteur"));
		assertNotEligible(post(sale, body(noIntake.leadId(), UNMAPPED_UNIT)), UNMAPPED_UNIT, "BASIS");
	}

	@Test
	void create_availabilityDiffersOnly_created() throws Exception {
		Passed passed = linkedHouse("OCCUPIED");
		completed(passed.leadId());
		String unit = unitOf(passed.leadId());

		Result answer = post(sale, body(passed.leadId(), unit));

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(1, agreementsOfUnit(unit));
	}

	// ==================== AD-3: one unfinished agreement per unit ====================

	@Test
	void create_secondWhileDrafting_exists_namesNothing() throws Exception {
		Pair pair = housePair();
		String unit = pair.unitBySpaceCode().get("A1");
		String first = pair.first().leadId();
		String second = pair.second().leadId();
		Result created = post(sale, body(first, unit));
		assertEquals(201, created.status(), created.rawBody());
		String agreementId = created.body().path("agreementId").asString();

		for (String lead : List.of(first, second)) {
			Result again = post(sale, body(lead, unit));

			assertEquals(409, again.status(), again.rawBody());
			assertEquals("AGREEMENT_EXISTS", again.body().path("code").asString(), again.rawBody());
			assertEquals("This unit already has an agreement in progress.", again.body().path("message").asString());
			assertEquals("[]", again.body().path("errors").toString(), again.rawBody());
			for (String secret : List.of(first, second, agreementId)) {
				assertFalse(again.rawBody().contains(secret), secret + " in " + again.rawBody());
			}
		}
		assertEquals(1, agreementsOfUnit(unit));
	}

	@Test
	void create_cancelledAgreementOnTheUnit_doesNotBlock() throws Exception {
		Done done = done();
		String unit = unitOf(done.leadId());
		created.add(AgreementFixture.insert(jdbc, ids, unit, done.leadId(), "CANCELLED", "CANCELLED"));

		Result answer = post(sale, body(done.leadId(), unit));

		assertEquals(201, answer.status(), answer.rawBody());
	}

	// ==================== AD-4: the period ====================

	@Test
	void create_periodBoundaries_acceptedAndRefused() throws Exception {
		Done done = done();
		String lead = done.leadId();
		String unit = unitOf(lead);

		for (String[] period : List.of(new String[] {"2027-01-01", "2027-01-01"}, new String[] {"2027-01-01", "2028-01-01"},
				new String[] {"2028-02-29", "2029-02-28"})) {
			Result answer = post(sale, body(lead, unit, period[0], period[1]));
			assertEquals(201, answer.status(), period[0] + ".." + period[1] + " " + answer.rawBody());
			AgreementFixture.cancel(jdbc, answer.body().path("agreementId").asString());
		}
		int accepted = agreementsOfUnit(unit);

		for (String[] period : List.of(new String[] {"2027-01-02", "2027-01-01"}, new String[] {"2027-01-01", "2028-01-02"},
				new String[] {"2028-02-29", "2029-03-01"}, new String[] {"2099-06-01", "2100-01-01"})) {
			assertInvalid(post(sale, body(lead, unit, period[0], period[1])), fieldError("endDate"));
		}
		Map<String, Object> noStart = body(lead, unit);
		noStart.remove("startDate");
		assertInvalid(post(sale, noStart), fieldError("startDate"));
		assertInvalid(post(sale, body(lead, unit, "1999-12-31", "2000-06-01")), fieldError("startDate"));
		Result unreadable = post(sale, body(lead, unit, "2027-02-30", "2027-12-31"));
		assertEquals(400, unreadable.status(), unreadable.rawBody());
		assertEquals("MALFORMED_REQUEST", unreadable.body().path("code").asString(), unreadable.rawBody());
		assertEquals("The request could not be read.", unreadable.body().path("message").asString());
		assertEquals("[]", unreadable.body().path("errors").toString(), unreadable.rawBody());

		assertEquals(3, accepted);
		assertEquals(accepted, agreementsOfUnit(unit));
	}

	private static void assertInvalid(Result answer, String errors) {
		assertEquals(400, answer.status(), answer.rawBody());
		assertEquals("VALIDATION_FAILED", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("Validation failed.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	// ==================== AD-5: the type and the ids ====================

	@Test
	void create_transactionTypeOrIdsInvalid_validationFailed() throws Exception {
		Done done = done();
		String lead = done.leadId();
		String unit = unitOf(lead);
		String typeError = "[{\"field\":\"transactionType\",\"message\":\"" + FIELD_MESSAGE
				+ "\",\"allowedValues\":[\"LONG_TERM_RENT\"]}]";

		for (String type : List.of("SALE", "SHORT_TERM_RENT")) {
			Map<String, Object> other = body(lead, unit);
			other.put("transactionType", type);
			assertInvalid(post(sale, other), typeError);
		}
		Map<String, Object> noType = body(lead, unit);
		noType.remove("transactionType");
		assertInvalid(post(sale, noType), typeError);
		assertInvalid(post(sale, body("not-a-ulid", unit)), fieldError("leadId"));
		assertInvalid(post(sale, body(lead, "01jlowercase0000000000000a")), fieldError("unitId"));

		assertEquals(0, agreementsOfUnit(unit));
	}

	// ==================== AD-9: nothing outside brokerage ====================

	private record Snapshot(String leadStatus, long leadVersion, int activities, int leadAccessLogs, int intakes,
			int suspensions, int inventoryAccessLogs, int intakeEvents, int ownerships) {
	}

	private Snapshot snapshot(String leadId, String propertyId) {
		Map<String, Object> lead = jdbc.queryForMap("select status, version from owner_leads where id = ?", leadId);
		return new Snapshot((String) lead.get("status"), ((Number) lead.get("version")).longValue(),
				count("select count(*) from owner_lead_activities where lead_id = ?", leadId),
				count("select count(*) from owner_lead_access_logs where lead_id = ?", leadId),
				count("select count(*) from inventory_intakes where lead_id = ?", leadId),
				count("select count(*) from inventory_suspensions where lead_id = ?", leadId),
				count("select count(*) from inventory_access_logs where lead_id = ?", leadId),
				count("select count(*) from inventory_intake_events where intake_id in"
						+ " (select id from inventory_intakes where lead_id = ?)", leadId),
				count("select count(*) from property_ownerships where property_id = ?", propertyId));
	}

	@Test
	void create_writesNothingOutsideBrokerage() throws Exception {
		Done done = done();
		JsonNode readinessBefore = blockOk(sale, done.leadId()).path("readiness");
		String unit = readinessBefore.path("units").path(0).path("unitId").asString();
		Snapshot before = snapshot(done.leadId(), done.propertyId());

		Result answer = post(sale, body(done.leadId(), unit));

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(before, snapshot(done.leadId(), done.propertyId()));
		assertEquals(readinessBefore, blockOk(sale, done.leadId()).path("readiness"));
	}
}
