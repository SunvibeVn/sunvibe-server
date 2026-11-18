package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Whether each unit of a lead's latest completed intake can get a brokerage agreement, and the reason groups when not
 * (brokerage-agreement US-1 AC-1, AC-2; E05-S1 spec 3, EL-1 to EL-7, EL-9, EL-10), through the real filter chain. In the
 * property test package to share the intake fixtures and their Spring context (plan decision E05S1-P1). Needs Docker.
 */
class BrokerageEligibilityApiPostgresTest extends IntakeApiTestBase {

	private static final String WARD_NAME = "Hải Châu";

	@Autowired
	private InventoryIntakeReadinessUseCase readiness;

	@Override
	String phonePrefix() {
		return "0953";
	}

	// ==================== HTTP ====================

	private static String path(String leadId) {
		return LEADS + "/" + leadId + "/brokerage-eligibility";
	}

	/** The eligibility, which must answer 200. */
	private JsonNode answer(TestUser caller, String leadId) throws Exception {
		Result answer = api.get(caller, path(leadId));
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	/** Each unit's id to its group names, in the order of the answer. */
	private static Map<String, List<String>> groups(JsonNode answer) {
		Map<String, List<String>> groups = new LinkedHashMap<>();
		for (JsonNode unit : answer.path("units")) {
			List<String> names = new ArrayList<>();
			unit.path("reasonGroups").forEach(group -> names.add(group.asString()));
			groups.put(unit.path("unitId").asString(), List.copyOf(names));
		}
		return groups;
	}

	/** Every unit of the answer holds exactly these groups, and is eligible exactly when there is none. */
	private static void assertEveryUnit(JsonNode answer, String... groups) {
		assertFalse(answer.path("units").isEmpty(), answer.toString());
		for (JsonNode unit : answer.path("units")) {
			List<String> names = new ArrayList<>();
			unit.path("reasonGroups").forEach(group -> names.add(group.asString()));
			assertEquals(List.of(groups), names, answer.toString());
			assertEquals(groups.length == 0, unit.path("eligible").asBoolean(), answer.toString());
		}
	}

	/** Every unit of the answer holds this group among others, and is not eligible. */
	private static void assertEveryUnitContains(JsonNode answer, String group) {
		assertFalse(answer.path("units").isEmpty(), answer.toString());
		for (JsonNode unit : answer.path("units")) {
			assertTrue(unit.path("reasonGroups").toString().contains("\"" + group + "\""), answer.toString());
			assertFalse(unit.path("eligible").asBoolean(), answer.toString());
		}
	}

	// ==================== fixtures ====================

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

	private void release(String leadId, String suspensionId) throws Exception {
		Result answer = release(manager, leadId, suspensionId, "The request was answered", versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	/** An ownership row as {@link #ownership} inserts it, with the relation and the validity named. */
	private void ownershipRow(String propertyId, String userId, String relation, LocalDate from, LocalDate toOrNull) {
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
						+ " valid_to, created_at) values (?, ?, ?, ?, 'UNVERIFIED', ?, ?, ?)", ids.generateId(), propertyId, userId,
				relation, from, toOrNull, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
	}

	// ==================== EL-1, EL-6: usable ====================

	@Test
	void eligibility_usableBasis_everyUnitEligible() throws Exception {
		Done done = done();

		JsonNode answer = answer(sale, done.leadId());

		assertEquals("USABLE", answer.path("basisState").asString());
		assertTrue(answer.path("viewerIsAssignedSale").asBoolean());
		assertEquals(1, answer.path("units").size(), answer.toString());
		JsonNode block = blockOk(sale, done.leadId()).path("readiness").path("units").path(0);
		JsonNode unit = answer.path("units").path(0);
		assertEquals(block.path("unitId").asString(), unit.path("unitId").asString());
		assertEquals(block.path("unitCode").asString(), unit.path("unitCode").asString());
		assertTrue(unit.path("eligible").asBoolean());
		assertEquals("[]", unit.path("reasonGroups").toString());
	}

	@Test
	void eligibility_availabilityDiffers_staysEligible() throws Exception {
		Passed passed = linkedHouse("OCCUPIED");
		completed(passed.leadId());

		assertEquals("[\"AVAILABILITY_DIFFERS\"]",
				blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("reasons").toString());
		JsonNode answer = answer(sale, passed.leadId());
		assertEquals("USABLE", answer.path("basisState").asString());
		assertEveryUnit(answer);
	}

	// ==================== EL-2: the basis ====================

	@Test
	void eligibility_leadHeldMarkedOrClosed_basis() throws Exception {
		Done held = done();
		hold(held.leadId());
		JsonNode whileHeld = answer(sale, held.leadId());
		assertEquals("SUSPENDED", whileHeld.path("basisState").asString());
		assertEveryUnit(whileHeld, "BASIS");
		jdbc.update("update owner_leads set status = 'QUALIFIED', version = version + 1 where id = ?", held.leadId());
		JsonNode resumed = answer(sale, held.leadId());
		assertEquals("USABLE", resumed.path("basisState").asString());
		assertEveryUnit(resumed);

		Done marked = done();
		fixture.requalify(leader, marked.passed().checklistId(), "OTHER");
		JsonNode afterMark = answer(sale, marked.leadId());
		assertEquals("ENDED", afterMark.path("basisState").asString());
		assertEveryUnit(afterMark, "BASIS");

		Done closed = done();
		close(closed.leadId());
		JsonNode afterClose = answer(leader, closed.leadId());
		assertEquals("ENDED", afterClose.path("basisState").asString());
		assertEveryUnit(afterClose, "BASIS");
	}

	// ==================== EL-3: no basis, legacy units ====================

	@Test
	void eligibility_noCompletedIntake_none() throws Exception {
		Passed passed = house(unique("Pasteur"));

		JsonNode answer = answer(sale, passed.leadId());

		assertEquals("NONE", answer.path("basisState").asString());
		assertEquals("[]", answer.path("units").toString());
		assertTrue(answer.path("viewerIsAssignedSale").asBoolean());
	}

	@Test
	void eligibility_legacyUnit_onNoAnswer() throws Exception {
		Done done = done();
		String street = unique("Legacy Street");
		String legacy = property(done.owner().userId(), WARD, WARD_NAME, street, "HOUSE", AddressNormalizer.normalize(street));
		String legacyUnit = unit(legacy, "L1", "AVAILABLE");

		JsonNode answer = answer(sale, done.leadId());

		List<String> mapped = jdbc.queryForList("select unit_id from inventory_unit_mappings where intake_id = ?",
				String.class, intakeRow(done.leadId()).get("id"));
		List<String> answered = new ArrayList<>(groups(answer).keySet());
		assertEquals(mapped.stream().sorted().toList(), answered.stream().sorted().toList());
		assertFalse(answered.contains(legacyUnit), answer.toString());
	}

	// ==================== EL-4, EL-5: stops from another lead ====================

	@Test
	void eligibility_otherLeadWholeSuspension_property_untilReleased() throws Exception {
		Pair pair = housePair();

		suspendLead(pair.first().leadId(), null, "Data request");

		JsonNode stopped = answer(sale, pair.second().leadId());
		assertEquals("USABLE", stopped.path("basisState").asString());
		assertEveryUnit(stopped, "PROPERTY");

		String suspensionId = jdbc.queryForObject("select id from inventory_suspensions where lead_id = ?", String.class,
				pair.first().leadId());
		release(pair.first().leadId(), suspensionId);

		assertEveryUnit(answer(sale, pair.second().leadId()));
	}

	@Test
	void eligibility_unitSuspension_unitOnly() throws Exception {
		Pair pair = buildingPair("P101", "P102");
		String p101 = pair.unitBySpaceCode().get("P101");
		String p102 = pair.unitBySpaceCode().get("P102");

		suspendLead(pair.first().leadId(), p101, "Room only");

		Map<String, List<String>> groups = groups(answer(sale, pair.second().leadId()));
		assertEquals(List.of("UNIT"), groups.get(p101), groups.toString());
		assertEquals(List.of(), groups.get(p102), groups.toString());

		suspendLead(pair.second().leadId(), p102, "This room too");

		groups = groups(answer(sale, pair.second().leadId()));
		assertEquals(List.of("UNIT"), groups.get(p102), groups.toString());
		assertEquals(List.of("UNIT"), groups.get(p101), groups.toString());
	}

	// ==================== EL-7: the owner, brokerage-agreement BR-4 ====================

	@Test
	void eligibility_ownerFailsBr4_owner() throws Exception {
		Done coOwner = done();
		ownershipRow(coOwner.propertyId(), ids.generateId(), "CO_OWNER", LocalDate.of(2026, 1, 1), null);
		assertEveryUnit(answer(sale, coOwner.leadId()), "OWNER");

		Done representative = done();
		ownershipRow(representative.propertyId(), ids.generateId(), "AUTHORIZED_REPRESENTATIVE", LocalDate.of(2026, 1, 1),
				null);
		assertEveryUnit(answer(sale, representative.leadId()), "OWNER");

		Done rejected = done();
		jdbc.update("update property_ownerships set verification_state = 'REJECTED' where property_id = ? and user_id = ?",
				rejected.propertyId(), rejected.owner().userId());
		assertEveryUnit(answer(sale, rejected.leadId()), "OWNER");

		Done otherOwner = done();
		ownershipRow(otherOwner.propertyId(), ids.generateId(), "OWNER", LocalDate.of(2026, 1, 1), null);
		assertEveryUnitContains(answer(sale, otherOwner.leadId()), "OWNER");

		Done ended = done();
		jdbc.update("update property_ownerships set valid_from = ?, valid_to = ? where property_id = ? and user_id = ?",
				LocalDate.of(2026, 1, 1), today().minusDays(1), ended.propertyId(), ended.owner().userId());
		assertEveryUnitContains(answer(sale, ended.leadId()), "OWNER");
	}

	@Test
	void eligibility_ownerTakesStaffRole_basisPropertyOwner_untilRoleEnds() throws Exception {
		Done done = done();

		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'", done.owner().userId());

		JsonNode staff = answer(sale, done.leadId());
		assertEquals("SUSPENDED", staff.path("basisState").asString());
		assertEveryUnit(staff, "BASIS", "PROPERTY", "OWNER");

		jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
				done.owner().userId());

		JsonNode again = answer(sale, done.leadId());
		assertEquals("USABLE", again.path("basisState").asString());
		assertEveryUnit(again);
	}

	@Test
	void eligibility_otherRowsNotValidToday_stopNothing() throws Exception {
		Done done = done();

		ownershipRow(done.propertyId(), ids.generateId(), "OWNER", today().plusDays(1), null);
		ownershipRow(done.propertyId(), ids.generateId(), "CO_OWNER", LocalDate.of(2026, 1, 1), today().minusDays(1));

		assertEveryUnit(answer(sale, done.leadId()));
	}

	// ==================== EL-9: nothing of another lead ====================

	@Test
	void eligibility_otherLeadStops_tellNothingOfIt() throws Exception {
		Pair pair = buildingPair("P101", "P102");
		String first = pair.first().leadId();
		suspendLead(first, null, "Tenant asked to stop processing");
		suspendLead(first, pair.unitBySpaceCode().get("P102"), "Room only, private");
		fixture.requalify(leader, pair.first().checklistId(), "WRONG_ASSET");

		Result answer = api.get(sale, path(pair.second().leadId()));

		assertEquals(200, answer.status(), answer.rawBody());
		String body = answer.response().body();
		List<String> secrets = new ArrayList<>(List.of(first, pair.owner().userId(), pair.first().checklistId(),
				"Tenant asked", "Room only",
				jdbc.queryForObject("select request_code from owner_leads where id = ?", String.class, first)));
		secrets.addAll(jdbc.queryForList("select id from inventory_suspensions where lead_id = ?", String.class, first));
		for (String secret : secrets) {
			assertFalse(body.contains(secret), secret + " in " + body);
		}
		assertEquals(List.of("basisState", "units", "viewerIsAssignedSale"), sortedKeys(answer.body()));
		assertFalse(answer.body().path("units").isEmpty(), body);
		for (JsonNode unit : answer.body().path("units")) {
			assertEquals(List.of("agreementId", "eligible", "reasonGroups", "unfinishedAgreement", "unitCode", "unitId"),
					sortedKeys(unit));
		}
	}

	private static List<String> sortedKeys(JsonNode node) {
		List<String> keys = new ArrayList<>();
		node.properties().forEach(entry -> keys.add(entry.getKey()));
		return keys.stream().sorted().toList();
	}

	// ==================== EL-10: the read writes nothing ====================

	private record Snapshot(long leadVersion, int activities, int leadAccessLogs, int inventoryAccessLogs, int intakes,
			int suspensions, int intakeEvents, int ownerships, IntakeBlockView.Readiness readiness) {
	}

	private Snapshot snapshot(String leadId, String propertyId) {
		return new Snapshot(
				jdbc.queryForObject("select version from owner_leads where id = ?", Long.class, leadId),
				count("select count(*) from owner_lead_activities where lead_id = ?", leadId),
				count("select count(*) from owner_lead_access_logs where lead_id = ?", leadId),
				count("select count(*) from inventory_access_logs where lead_id = ?", leadId),
				count("select count(*) from inventory_intakes where lead_id = ?", leadId),
				count("select count(*) from inventory_suspensions where lead_id = ?", leadId),
				count("select count(*) from inventory_intake_events where intake_id in"
						+ " (select id from inventory_intakes where lead_id = ?)", leadId),
				count("select count(*) from property_ownerships where property_id = ?", propertyId),
				readiness.readiness(leadId));
	}

	private int historySize(String leadId) throws Exception {
		Result detail = api.get(leader, LEADS + "/" + leadId);
		assertEquals(200, detail.status(), detail.rawBody());
		return detail.body().path("activities").size();
	}

	@Test
	void eligibility_read_writesNothing() throws Exception {
		Done done = done();
		JsonNode blockBefore = blockOk(sale, done.leadId()).path("readiness");
		int historyBefore = historySize(done.leadId());
		Snapshot before = snapshot(done.leadId(), done.propertyId());

		answer(sale, done.leadId());
		answer(sale, done.leadId());

		Snapshot after = snapshot(done.leadId(), done.propertyId());
		assertEquals(before, after);
		assertEquals(blockBefore, blockOk(sale, done.leadId()).path("readiness"));
		assertEquals(historyBefore, historySize(done.leadId()));
	}
}
