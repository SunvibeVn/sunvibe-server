package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.InventoryIntakeReadinessUseCase;
import vn.sunvibe.property.core.domain.policy.intake.AddressNormalizer;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * What readiness must keep answering when two leads have results on one property (SV-215, brokerage-agreement BR-3
 * "Không chặn" and BR-18): another lead's hold, closure or held photo and an OPEN case stop no unit, a SAME_ASSET decision
 * of the property's own owner stops none, the lead-level readiness, the gate and the link stay as coded, and the published
 * readiness equals the block's. Pins written before the change; they pass on the code before it. Needs Docker.
 */
class IntakeReadinessUnchangedApiPostgresTest extends IntakeApiTestBase {

	private static final String WARD_NAME = "Hải Châu";

	@Autowired
	private InventoryIntakeReadinessUseCase readiness;

	@Override
	String phonePrefix() {
		return "0958";
	}

	// ==================== fixtures ====================

	private record Pair(Passed first, Issued firstIssued, TestUser owner, String propertyId,
			Map<String, String> unitBySpaceCode, Passed second, Issued secondIssued) {
	}

	private record UnitRead(List<String> reasons, boolean usable) {
	}

	/** Two houses on one street: the first creates the property, the second links onto its one unit; both completed. */
	private Pair housePair() throws Exception {
		String street = unique("Two Leads");
		Passed first = house(street);
		TestUser owner = owner();
		Issued firstIssued = checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		available(first.leadId(), first.spaceIds(), "VACANT");
		JsonNode result = completed(first.leadId());
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		Passed second = house(street);
		Issued secondIssued = checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(first, firstIssued, owner, propertyId, Map.of("A1", unit), second, secondIssued);
	}

	/** A building lead created and completed, with its units by space code. */
	private record Building(Passed first, Issued firstIssued, TestUser owner, String street, String propertyId,
			Map<String, String> unitBySpaceCode) {
	}

	private Building building() throws Exception {
		String street = unique("Two Leads Building");
		Passed first = fixture.parts(leader, sale, "WHOLE_BUILDING", street, "P101", "P102");
		TestUser owner = owner();
		Issued firstIssued = checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), first.spaceIds().get(0)));
		available(first.leadId(), first.spaceIds(), "VACANT");
		String propertyId = completed(first.leadId()).path("propertyId").asString();
		Map<String, String> unitBySpaceCode = new LinkedHashMap<>();
		jdbc.query("select space_code, unit_id from inventory_unit_mappings where intake_id = ?",
				row -> {
					unitBySpaceCode.put(row.getString("space_code"), row.getString("unit_id"));
				}, intakeRow(first.leadId()).get("id"));
		return new Building(first, firstIssued, owner, street, propertyId, unitBySpaceCode);
	}

	/** {@link #building()} and a second building lead linked space by space onto the units of the same codes. */
	private Pair buildingPair(String... secondCodes) throws Exception {
		Building building = building();
		Passed second = fixture.parts(leader, sale, "WHOLE_BUILDING", building.street(), secondCodes);
		Issued secondIssued = checked(second, building.owner());
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), building.propertyId(), second.spaceIds(),
				Arrays.stream(secondCodes).map(building.unitBySpaceCode()::get).toList()));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(building.first(), building.firstIssued(), building.owner(), building.propertyId(),
				building.unitBySpaceCode(), second, secondIssued);
	}

	/** The units of the lead's readiness, unitId to its reasons and usable, in the order of the answer. */
	private Map<String, UnitRead> units(TestUser caller, String leadId) throws Exception {
		Map<String, UnitRead> units = new LinkedHashMap<>();
		for (JsonNode unit : blockOk(caller, leadId).path("readiness").path("units")) {
			List<String> reasons = new ArrayList<>();
			unit.path("reasons").forEach(reason -> reasons.add(reason.asString()));
			units.put(unit.path("unitId").asString(), new UnitRead(List.copyOf(reasons), unit.path("usable").asBoolean()));
		}
		return units;
	}

	private static void assertStopped(Map<String, UnitRead> units, String unitId, String... reasons) {
		UnitRead unit = units.get(unitId);
		assertNotNull(unit, units.toString());
		assertEquals(List.of(reasons), unit.reasons(), units.toString());
		assertFalse(unit.usable(), units.toString());
	}

	private static void assertUsable(Map<String, UnitRead> units, String unitId) {
		UnitRead unit = units.get(unitId);
		assertNotNull(unit, units.toString());
		assertEquals(List.of(), unit.reasons(), units.toString());
		assertTrue(unit.usable(), units.toString());
	}

	/** Every unit of the lead's latest result is usable, and there is at least one. */
	private void assertAllUsable(String leadId) throws Exception {
		Map<String, UnitRead> units = units(leader, leadId);
		assertFalse(units.isEmpty());
		units.keySet().forEach(unitId -> assertUsable(units, unitId));
	}

	private void assertState(String leadId, String state, String... reasons) throws Exception {
		JsonNode read = blockOk(leader, leadId).path("readiness");
		assertEquals(state, read.path("state").asString(), read.toString());
		List<String> found = new ArrayList<>();
		read.path("reasons").forEach(reason -> found.add(reason.asString()));
		assertEquals(List.of(reasons), found, read.toString());
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	private long versionOfCase(String caseId) {
		return jdbc.queryForObject("select version from inventory_duplicate_cases where id = ?", Long.class, caseId);
	}

	/** The manager's decision, as InventoryCaseApiPostgresTest.decide makes it; the answer must be 200. */
	private void decide(String caseId, String outcome) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("outcome", outcome);
		body.put("proceedingIntakeId", null);
		body.put("basis", "Checked both files");
		body.put("expectedVersion", versionOfCase(caseId));
		SurveyApiTestSupport.ok(api.post(manager, CASES + "/" + caseId + "/decision", body));
	}

	/** A property at this address, with an OWNER row of the account and one AVAILABLE unit. */
	private String seeded(String address, String ownerId) {
		String id = property(ownerId, WARD, WARD_NAME, address, "HOUSE", AddressNormalizer.normalize(address));
		ownership(id, ownerId);
		unit(id, "U1", "AVAILABLE");
		return id;
	}

	private String unitOf(String propertyId) {
		return jdbc.queryForObject("select id from units where property_id = ? order by unit_code limit 1", String.class,
				propertyId);
	}

	private String propertyCode(String propertyId) {
		return jdbc.queryForObject("select property_code from properties where id = ?", String.class, propertyId);
	}

	// ==================== another lead's hold, closure, held photo (BR-3 "Không chặn") ====================

	@Test
	void otherLeadOnHold_unitsStayUsable() throws Exception {
		Pair pair = housePair();

		hold(pair.first().leadId());

		assertAllUsable(pair.second().leadId());
		assertState(pair.second().leadId(), "USABLE");
	}

	@Test
	void otherLeadClosed_unitsStayUsable() throws Exception {
		Pair pair = housePair();

		close(pair.first().leadId());

		assertAllUsable(pair.second().leadId());
		assertState(pair.second().leadId(), "USABLE");
	}

	@Test
	void otherLeadEvidenceHeld_unitsStayUsable() throws Exception {
		Pair pair = housePair();

		jdbc.update("update survey_media set held_at = now(), held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT' where id = ?",
				admin.userId(), pair.first().photoIds().getFirst());

		assertState(pair.first().leadId(), "SUSPENDED", "EVIDENCE_HELD");
		assertAllUsable(pair.second().leadId());
		assertState(pair.second().leadId(), "USABLE");
	}

	@Test
	void openDuplicateCaseOnTheProperty_unitsStayUsable() throws Exception {
		String street = unique("Open Case");
		Passed first = house(street);
		createdHouse(first, owner());
		Passed rival = house(street);
		checked(rival, owner());

		SurveyApiTestSupport.ok(createTarget(rival.leadId(), null));

		assertEquals("CASE_OPEN", blockOk(sale, rival.leadId()).path("status").asString());
		assertAllUsable(first.leadId());
		assertState(first.leadId(), "USABLE");
	}

	// ==================== a SAME_ASSET decision with no other owner (BR-3 (c), spec SV215-D3 (c)) ====================

	@Test
	void sameAssetWithTheSameOwner_unitsStayUsable() throws Exception {
		String street = unique("Same Owner");
		Passed lead = house(street);
		TestUser owner = owner();
		checked(lead, owner);
		String propertyId = seeded(street, owner.userId());
		SurveyApiTestSupport.ok(createTarget(lead.leadId(), null));
		String caseId = jdbc.queryForObject("select id from inventory_duplicate_cases where candidate_property_id = ?",
				String.class, propertyId);

		decide(caseId, "SAME_ASSET");

		assertFalse(blockOk(sale, lead.leadId()).path("forcedPropertyCode").isNull());
		SurveyApiTestSupport.ok(linkTarget(lead.leadId(), propertyId, lead.spaceIds(), List.of(unitOf(propertyId))));
		available(lead.leadId(), lead.spaceIds(), "VACANT");
		completed(lead.leadId());
		assertAllUsable(lead.leadId());
		assertState(lead.leadId(), "USABLE");
	}

	/** The correction sequence (spec SR-4 last clause, SV215-D3 (c), U-28): a proof that died before the decision shows nothing. */
	@Test
	void sameAssetAfterAnotherAccountsProofWasReplaced_unitsStayUsable() throws Exception {
		String street = unique("Corrected Owner");
		Passed first = house(street);
		TestUser owner = owner();
		JsonNode result = createdHouse(first, owner);
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		Passed rival = house(street);
		TestUser other = owner();
		checked(rival, other);
		SurveyApiTestSupport.ok(createTarget(rival.leadId(), null));
		assertEquals("CASE_OPEN", blockOk(sale, rival.leadId()).path("status").asString());

		assertEquals("DRAFT", selected(rival.leadId(), rival.spaceIds()).path("status").asString());
		Map<String, Object> voided = jdbc.queryForMap("select owner_check, voided_at from inventory_owner_proofs"
				+ " where lead_id = ? and user_id = ?", rival.leadId(), other.userId());
		assertEquals("DONE", voided.get("owner_check"));
		assertNotNull(voided.get("voided_at"));
		Issued again = issued(rival.leadId(), address(owner));
		confirmed(owner, again);
		assertEquals(204, enter(owner, again.requestId(), started(manager, rival.leadId())).status());
		SurveyApiTestSupport.ok(createTarget(rival.leadId(), null));
		assertEquals("CASE_OPEN", blockOk(sale, rival.leadId()).path("status").asString());
		String caseId = jdbc.queryForObject("select id from inventory_duplicate_cases where candidate_property_id = ?"
				+ " and status = 'OPEN'", String.class, propertyId);

		decide(caseId, "SAME_ASSET");

		JsonNode block = blockOk(sale, rival.leadId());
		assertEquals(propertyCode(propertyId), block.path("forcedPropertyCode").asString());
		assertEquals("DECIDED", block.path("caseState").asString());
		Instant voidedAt = ((Timestamp) voided.get("voided_at")).toInstant();
		Instant decidedAt = jdbc.queryForObject("select decided_at from inventory_duplicate_cases where id = ?",
				Timestamp.class, caseId).toInstant();
		assertTrue(voidedAt.isBefore(decidedAt), voidedAt + " " + decidedAt);
		assertAllUsable(first.leadId());
		assertState(first.leadId(), "USABLE");

		SurveyApiTestSupport.ok(linkTarget(rival.leadId(), propertyId, rival.spaceIds(), List.of(unit)));
		available(rival.leadId(), rival.spaceIds(), "VACANT");
		completed(rival.leadId());
		assertAllUsable(first.leadId());
		assertAllUsable(rival.leadId());
	}

	// ==================== lead level, gate and link unchanged (BR-18, SR-11) ====================

	@Test
	void otherLeadSuspended_leadLevelReadinessGateAndLinkUnchanged() throws Exception {
		String street = unique("Suspended First");
		Passed first = house(street);
		TestUser owner = owner();
		JsonNode result = createdHouse(first, owner);
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		suspendLead(first.leadId(), null, "Data request");
		Passed second = house(street);
		checked(second, owner);

		Result linked = linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit));

		assertEquals(200, linked.status(), linked.rawBody());
		assertEquals("OWNER_CONFIRMED", linked.body().path("status").asString());
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		JsonNode block = blockOk(leader, second.leadId());
		JsonNode read = block.path("readiness");
		assertEquals("USABLE", read.path("state").asString(), read.toString());
		assertEquals("[]", read.path("reasons").toString());
		assertEquals(second.checklistId(), read.path("sourceRevisionId").asString());
		assertEquals("[\"AGREEMENT\",\"ADVERTISING_PERMISSION\",\"CONFIRMED_TERMS\",\"PHOTO_RIGHTS\"]",
				read.path("missingForBrokerage").toString());
		assertTrue(block.path("gate").path("ready").asBoolean(), block.path("gate").toString());
		assertEquals("[]", block.path("gate").path("reasons").toString());
	}

	@Test
	void ownUnitSuspension_readsRestrictedOnly() throws Exception {
		Building building = building();
		String p101 = building.unitBySpaceCode().get("P101");
		String p102 = building.unitBySpaceCode().get("P102");

		suspendLead(building.first().leadId(), p101, "This room only");

		Map<String, UnitRead> units = units(leader, building.first().leadId());
		assertStopped(units, p101, "RESTRICTED");
		assertUsable(units, p102);
		assertState(building.first().leadId(), "USABLE");
	}

	@Test
	void linkedUnitInAnotherState_readsAvailabilityDiffersOnly() throws Exception {
		Passed passed = house(unique("Link Street"));
		TestUser owner = owner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class,
				passed.leadId());
		String id = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(id, owner.userId());
		String unit = unit(id, "U1", "OCCUPIED");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), id, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");

		completed(passed.leadId());

		assertStopped(units(leader, passed.leadId()), unit, "AVAILABILITY_DIFFERS");
	}

	/** BR-3 (e) counts only OWNER rows valid today (spec SV215-D3 (e)). */
	@Test
	void expiredOwnerRowOrCoOwner_unitsStayUsable() throws Exception {
		Passed passed = house(unique("Old Owner Rows"));
		String propertyId = createdHouse(passed, owner()).path("propertyId").asString();

		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
						+ " valid_to, created_at) values (?, ?, ?, 'OWNER', 'UNVERIFIED', ?, ?, ?)", ids.generateId(), propertyId,
				ids.generateId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
				Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
						+ " created_at) values (?, ?, ?, 'CO_OWNER', 'UNVERIFIED', ?, ?)", ids.generateId(), propertyId,
				ids.generateId(), LocalDate.of(2026, 1, 1), Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));

		assertAllUsable(passed.leadId());
	}

	// ==================== one readiness (spec SV215-D5, SR-13) ====================

	@Test
	void publishedReadiness_equalsTheBlocksReadiness() throws Exception {
		Pair pair = buildingPair("P101", "P102");
		suspendLead(pair.first().leadId(), null, "Data request");

		IntakeBlockView.Readiness published = readiness.readiness(pair.second().leadId());
		JsonNode block = blockOk(leader, pair.second().leadId()).path("readiness").path("units");

		assertEquals(block.size(), published.units().size());
		assertEquals(2, published.units().size());
		for (int i = 0; i < published.units().size(); i++) {
			IntakeBlockView.ReadinessUnit unit = published.units().get(i);
			JsonNode read = block.path(i);
			assertEquals(read.path("unitId").asString(), unit.unitId());
			assertEquals(read.path("unitCode").asString(), unit.unitCode());
			assertEquals(read.path("usable").asBoolean(), unit.usable());
			List<String> reasons = new ArrayList<>();
			read.path("reasons").forEach(reason -> reasons.add(reason.asString()));
			assertEquals(reasons, unit.reasons());
			assertNotNull(unit.unitCode());
			assertEquals(jdbc.queryForObject("select unit_code from units where id = ?", String.class, unit.unitId()),
					unit.unitCode());
		}
	}
}
