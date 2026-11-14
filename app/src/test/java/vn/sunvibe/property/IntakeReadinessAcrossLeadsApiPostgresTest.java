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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.domain.policy.intake.AddressNormalizer;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The units of a lead's readiness carry the stops of brokerage-agreement BR-3 that come from another lead with a result
 * on the same property or from the property itself (SV-215, BR-18, spec SR-1 to SR-9, SR-12): {@code PROPERTY_STOP} for a
 * whole-asset stop, {@code UNIT_STOP} for a stop of one unit, read from the rows that record them and naming no lead.
 * Needs Docker.
 */
class IntakeReadinessAcrossLeadsApiPostgresTest extends IntakeApiTestBase {

	@Autowired
	private InventoryReadRepository inventory;

	@Override
	String phonePrefix() {
		return "0959";
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

	/**
	 * A building lead with P101 and P102 created and completed, and a second building lead on the same street linked
	 * space by space onto the units of the same codes.
	 */
	private Pair buildingPair(String... secondCodes) throws Exception {
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
		Passed second = fixture.parts(leader, sale, "WHOLE_BUILDING", street, secondCodes);
		Issued secondIssued = checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(),
				Arrays.stream(secondCodes).map(unitBySpaceCode::get).toList()));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(first, firstIssued, owner, propertyId, unitBySpaceCode, second, secondIssued);
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

	/** Every unit of the lead's latest result reads exactly these reasons, and there is at least one unit. */
	private void assertAllStopped(String leadId, String... reasons) throws Exception {
		Map<String, UnitRead> units = units(leader, leadId);
		assertFalse(units.isEmpty());
		units.keySet().forEach(unitId -> assertStopped(units, unitId, reasons));
	}

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

	private void assertGateReady(String leadId) throws Exception {
		JsonNode gate = blockOk(leader, leadId).path("gate");
		assertTrue(gate.path("ready").asBoolean(), gate.toString());
		assertEquals("[]", gate.path("reasons").toString());
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	private void release(String leadId, String suspensionId) throws Exception {
		Result answer = release(manager, leadId, suspensionId, "The request was answered", versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	private void restore(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", versionOf(leader, leadId));
		Result answer = api.post(sale, base(leadId) + "/restoration", body);
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

	/** A new confirmation and check of the lead's selection by this account. */
	private void reproved(String leadId, TestUser owner) throws Exception {
		Issued again = issued(leadId, address(owner));
		confirmed(owner, again);
		assertEquals(204, enter(owner, again.requestId(), started(manager, leadId)).status());
	}

	private void withdrawn(Pair pair) throws Exception {
		Result answer = withdraw(pair.owner(), pair.firstIssued().requestId(), passwordOf(pair.owner()));
		assertEquals(204, answer.status(), answer.rawBody());
	}

	// ==================== BR-3 (b) a whole-lead suspension of another lead ====================

	@Test
	void otherLeadWholeSuspension_stopsEveryUnit_untilReleased() throws Exception {
		Pair pair = housePair();

		suspendLead(pair.first().leadId(), null, "Data request");

		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP");
		assertState(pair.second().leadId(), "USABLE");
		assertGateReady(pair.second().leadId());
		String suspensionId = jdbc.queryForObject("select id from inventory_suspensions where lead_id = ? and released_at is null",
				String.class, pair.first().leadId());
		release(pair.first().leadId(), suspensionId);
		assertAllUsable(pair.second().leadId());
	}

	// ==================== BR-3 (a) a mark that needs reconciliation, a ruling ====================

	@Test
	void otherLeadWrongAssetOrOwnerMark_stopsEveryUnit() throws Exception {
		for (String kind : List.of("WRONG_ASSET", "WRONG_OWNER")) {
			Pair pair = housePair();

			fixture.requalify(leader, pair.first().checklistId(), kind);

			assertAllStopped(pair.second().leadId(), "PROPERTY_STOP");
			close(pair.first().leadId());
			assertAllStopped(pair.second().leadId(), "PROPERTY_STOP");
		}
	}

	@Test
	void otherLeadRuledOutResult_stopsEveryUnit() throws Exception {
		Pair pair = housePair();
		String first = pair.first().leadId();

		fixture.requalify(leader, pair.first().checklistId(), "OTHER");

		assertAllStopped(pair.second().leadId(), "UNIT_STOP");
		Passed again = fixture.revisit(leader, sale, first, "WHOLE", "HOUSE", unique("A Moved House"), "A1");
		checked(again, pair.owner());
		SurveyApiTestSupport.ok(linkTarget(first, pair.propertyId(), again.spaceIds(),
				List.of(pair.unitBySpaceCode().get("A1"))));
		assertEquals("CASE_OPEN", blockOk(sale, first).path("status").asString());
		String caseId = jdbc.queryForObject("select id from inventory_duplicate_cases where candidate_property_id = ?",
				String.class, pair.propertyId());
		decide(caseId, "DIFFERENT_ASSET");
		assertTrue(reasons(blockOk(sale, first)).contains("RECONCILIATION_REQUIRED"));
		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP", "UNIT_STOP");
		close(first);
		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP", "UNIT_STOP");
	}

	// ==================== BR-3 (c) a SAME_ASSET decision that shows another owner (E-15) ====================

	@Test
	void sameAssetWithAnotherOwner_stopsEveryUnit() throws Exception {
		String street = unique("Another Owner");
		Passed first = house(street);
		String propertyId = createdHouse(first, owner()).path("propertyId").asString();
		Passed rival = house(street);
		checked(rival, owner());
		SurveyApiTestSupport.ok(createTarget(rival.leadId(), null));
		assertAllUsable(first.leadId());
		String caseId = jdbc.queryForObject("select id from inventory_duplicate_cases where candidate_property_id = ?",
				String.class, propertyId);

		decide(caseId, "SAME_ASSET");

		assertEquals("WAITING", blockOk(sale, rival.leadId()).path("caseState").asString());
		assertAllStopped(first.leadId(), "PROPERTY_STOP");
		assertState(first.leadId(), "USABLE");
		close(rival.leadId());
		assertAllStopped(first.leadId(), "PROPERTY_STOP");
	}

	/** Spec SV215-D3 (c): a checked proof of another account confirmed after the decision counts. */
	@Test
	void sameAssetCheckedForAnotherOwnerAfterTheDecision_stopsEveryUnit() throws Exception {
		String street = unique("Later Owner");
		Passed first = house(street);
		TestUser owner = owner();
		String propertyId = createdHouse(first, owner).path("propertyId").asString();
		Passed rival = house(street);
		Issued rivalIssued = checked(rival, owner);
		SurveyApiTestSupport.ok(createTarget(rival.leadId(), null));
		assertEquals("CASE_OPEN", blockOk(sale, rival.leadId()).path("status").asString());
		String caseId = jdbc.queryForObject("select id from inventory_duplicate_cases where candidate_property_id = ?"
				+ " and status = 'OPEN'", String.class, propertyId);
		decide(caseId, "SAME_ASSET");
		assertFalse(blockOk(sale, rival.leadId()).path("forcedPropertyCode").isNull());
		assertAllUsable(first.leadId());

		Result withdrawal = withdraw(owner, rivalIssued.requestId(), passwordOf(owner));
		assertEquals(204, withdrawal.status(), withdrawal.rawBody());
		reproved(rival.leadId(), owner());

		assertEquals("WAITING", blockOk(sale, rival.leadId()).path("caseState").asString());
		assertAllStopped(first.leadId(), "PROPERTY_STOP");
		assertState(first.leadId(), "USABLE");
		close(rival.leadId());
		assertAllStopped(first.leadId(), "PROPERTY_STOP");
	}

	// ==================== BR-3 (d) the owner's standing, the owner's withdrawal ====================

	@Test
	void ownerNotEligible_stopsEveryUnit_untilEligibleAgain() throws Exception {
		Passed first = house(unique("Staff Owner"));
		TestUser owner = owner();
		createdHouse(first, owner);

		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'", owner.userId());

		assertState(first.leadId(), "SUSPENDED", "OWNER_NOT_ELIGIBLE");
		assertAllStopped(first.leadId(), "PROPERTY_STOP");
		jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
				owner.userId());
		assertState(first.leadId(), "USABLE");
		assertAllUsable(first.leadId());
	}

	@Test
	void otherLeadOwnerWithdrew_stopsEveryUnit_untilRestored() throws Exception {
		Pair pair = housePair();

		withdrawn(pair);

		assertState(pair.first().leadId(), "SUSPENDED", "OWNER_NOT_CONFIRMED");
		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP");
		reproved(pair.first().leadId(), pair.owner());
		restore(pair.first().leadId());
		assertAllUsable(pair.second().leadId());
	}

	/** Spec SV215-D3 (d) revision 4: a closure is no confirmation again; the owner's later confirmed result of P is. */
	@Test
	void otherLeadClosedAfterWithdrawal_endsOnTheOwnersLaterConfirmedResult() throws Exception {
		Pair pair = housePair();
		withdrawn(pair);

		close(pair.first().leadId());

		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP");
		assertState(pair.second().leadId(), "USABLE");
		String street = jdbc.queryForObject("select street from properties where id = ?", String.class, pair.propertyId());
		Passed third = house(street);
		checked(third, pair.owner());
		SurveyApiTestSupport.ok(linkTarget(third.leadId(), pair.propertyId(), third.spaceIds(),
				List.of(pair.unitBySpaceCode().get("A1"))));
		available(third.leadId(), third.spaceIds(), "VACANT");
		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP");
		completed(third.leadId());
		assertAllUsable(pair.second().leadId());
		assertAllUsable(third.leadId());
		assertState(third.leadId(), "USABLE");
	}

	@Test
	void otherLeadOwnerWithdrew_staysAfterItsResultIsMarked() throws Exception {
		Pair pair = housePair();
		withdrawn(pair);

		fixture.requalify(leader, pair.first().checklistId(), "NEED_CHANGED");

		assertEquals("ENDED", blockOk(leader, pair.first().leadId()).path("readiness").path("state").asString());
		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP", "UNIT_STOP");
		close(pair.first().leadId());
		assertAllStopped(pair.second().leadId(), "PROPERTY_STOP", "UNIT_STOP");
	}

	// ==================== BR-3 (e) the OWNER holders ====================

	@Test
	void ownerHoldersDiffer_stopsEveryUnit() throws Exception {
		Passed first = house(unique("Two Holders"));
		String propertyId = createdHouse(first, owner()).path("propertyId").asString();

		ownership(propertyId, owner().userId());

		assertAllStopped(first.leadId(), "PROPERTY_STOP");
		assertState(first.leadId(), "USABLE");

		Passed another = house(unique("Other Holder"));
		String anotherProperty = createdHouse(another, owner()).path("propertyId").asString();
		jdbc.update("update properties set owner_id = ? where id = ?", owner().userId(), anotherProperty);
		assertAllStopped(another.leadId(), "PROPERTY_STOP");
	}

	/** BR-3 (e): the distinct users with an OWNER row valid on the day, sorted; the day is the caller's. */
	@Test
	void currentOwnerIds_countsOwnerRowsValidOnTheDayOnly() {
		LocalDate day = LocalDate.of(2026, 11, 12);
		String street = unique("Holder Rows");
		String a = ids.generateId();
		String id = property(a, WARD, "Hải Châu", street, "HOUSE", AddressNormalizer.normalize(street));
		ownership(id, a);
		ownershipRow(id, ids.generateId(), "OWNER", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
		ownershipRow(id, ids.generateId(), "OWNER", day.plusDays(30), null);
		ownershipRow(id, ids.generateId(), "CO_OWNER", LocalDate.of(2026, 1, 1), null);
		ownership(id, a);

		assertEquals(List.of(a), inventory.currentOwnerIds(id, day));

		String e = ids.generateId();
		ownership(id, e);
		assertEquals(Stream.of(a, e).sorted().toList(), inventory.currentOwnerIds(id, day));
	}

	private void ownershipRow(String propertyId, String userId, String relation, LocalDate from, LocalDate toOrNull) {
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
						+ " valid_to, created_at) values (?, ?, ?, ?, 'UNVERIFIED', ?, ?, ?)", ids.generateId(), propertyId, userId,
				relation, from, toOrNull, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
	}

	// ==================== BR-3 (f) a unit suspension, (g) a mark of another kind ====================

	@Test
	void otherLeadUnitSuspension_stopsThatUnitOnly() throws Exception {
		Pair pair = buildingPair("P101", "P102");
		String p101 = pair.unitBySpaceCode().get("P101");
		String p102 = pair.unitBySpaceCode().get("P102");

		suspendLead(pair.first().leadId(), p101, "Room only");

		Map<String, UnitRead> second = units(leader, pair.second().leadId());
		assertStopped(second, p101, "UNIT_STOP");
		assertUsable(second, p102);
		assertStopped(units(leader, pair.first().leadId()), p101, "RESTRICTED");
		suspendLead(pair.second().leadId(), p101, "Room only too");
		assertStopped(units(leader, pair.second().leadId()), p101, "RESTRICTED", "UNIT_STOP");
	}

	@Test
	void otherLeadMarkOfAnotherKind_stopsItsUnitsOnly() throws Exception {
		Pair pair = buildingPair("P101");

		fixture.requalify(leader, pair.second().checklistId(), "PHOTO_PROBLEM");

		Map<String, UnitRead> first = units(leader, pair.first().leadId());
		assertStopped(first, pair.unitBySpaceCode().get("P101"), "UNIT_STOP");
		assertUsable(first, pair.unitBySpaceCode().get("P102"));
		assertState(pair.first().leadId(), "USABLE");
	}

	@Test
	void resultCompletedAfterTheMark_liftsTheUnitStop() throws Exception {
		String street = unique("Marked First");
		Passed first = house(street);
		TestUser owner = owner();
		JsonNode result = createdHouse(first, owner);
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		fixture.requalify(leader, first.checklistId(), "PHOTO_PROBLEM");
		Passed second = house(street);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");

		completed(second.leadId());

		assertUsable(units(leader, second.leadId()), unit);
		hold(second.leadId());
		assertState(second.leadId(), "SUSPENDED", "LEAD_ON_HOLD");
		assertUsable(units(leader, second.leadId()), unit);
	}

	// ==================== BR-17, BR-18: the stops name no lead ====================

	@Test
	void stops_tellNothingOfTheOtherLead() throws Exception {
		Pair pair = buildingPair("P101", "P102");
		String first = pair.first().leadId();
		String second = pair.second().leadId();
		fixture.checklists().reassign(leader, second, otherSale);

		suspendLead(first, null, "Tenant asked to stop processing");
		suspendLead(first, pair.unitBySpaceCode().get("P102"), "Room only, private");

		assertEquals(404, block(otherSale, first).status());
		Result read = block(otherSale, second);
		assertEquals(200, read.status(), read.rawBody());
		String body = read.rawBody();
		assertTrue(body.contains("PROPERTY_STOP"), body);
		List<String> hidden = new ArrayList<>(List.of(first, pair.first().checklistId(), "Tenant asked", "Room only",
				jdbc.queryForObject("select request_code from owner_leads where id = ?", String.class, first)));
		hidden.addAll(jdbc.queryForList("select id from inventory_suspensions where lead_id = ?", String.class, first));
		hidden.forEach(text -> assertFalse(body.contains(text), text));
		assertEquals("[]", read.body().path("suspensions").toString());
		assertEquals("[]", blockOk(manager, second).path("suspensions").toString());
	}
}
