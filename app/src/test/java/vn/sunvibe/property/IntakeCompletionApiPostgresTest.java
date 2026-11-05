package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.domain.policy.AddressNormalizer;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Completion (property-create intake spec 5.3, S-1, S-11..S-18, S-24, S-30) through the real filter chain, with the rows of earlier
 * tasks that need a real result. Needs Docker.
 */
class IntakeCompletionApiPostgresTest extends IntakeApiTestBase {

	private static final String WARD_NAME = "Hải Châu";

	@Override
	String phonePrefix() {
		return "0968";
	}

	private record Ready(Passed passed, TestUser owner, String address) {
		String leadId() {
			return passed.leadId();
		}

		List<String> spaces() {
			return passed.spaceIds();
		}
	}

	/** A surveyed house with a checked proof, a create target and today's availability. */
	private Ready ready() throws Exception {
		return ready(unique("Bạch Đằng"));
	}

	private Ready ready(String address) throws Exception {
		Passed passed = house(address);
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return new Ready(passed, owner, address);
	}

	private int propertiesAt(String address) {
		return count("select count(*) from properties where street = ?", address);
	}

	private String propertyAt(String address) {
		return jdbc.queryForObject("select id from properties where street = ?", String.class, address);
	}

	private String seeded(String address, String ownerId) {
		String id = property(ownerId, WARD, WARD_NAME, address, "HOUSE", AddressNormalizer.normalize(address));
		ownership(id, ownerId);
		return id;
	}

	private Result completeNow(Ready ready) throws Exception {
		return complete(sale, ready.leadId(), versionOf(sale, ready.leadId()), previewOk(ready.leadId()).path("previewHash").asString());
	}

	private String caseOf(String leadId) {
		return jdbc.queryForObject("select c.id from inventory_duplicate_cases c join inventory_intakes i on"
				+ " i.id in (c.intake_id, c.candidate_intake_id) where i.lead_id = ? order by c.created_at desc, c.id desc limit 1",
				String.class, leadId);
	}

	private void decided(String caseId, String outcome, String proceedingOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("outcome", outcome);
		body.put("proceedingIntakeId", proceedingOrNull);
		body.put("basis", "Checked both files");
		body.put("expectedVersion", jdbc.queryForObject("select version from inventory_duplicate_cases where id = ?", Long.class,
				caseId));
		SurveyApiTestSupport.ok(api.post(manager, CASES + "/" + caseId + "/decision", body));
	}

	// ==================== create (S-12, S-13) ====================

	@Test
	void create_whole_oneOfEach_nullLegacyColumns_leadStaysQualified() throws Exception {
		Ready ready = ready();
		String before = survey.leadStatus(ready.leadId());

		JsonNode result = completed(ready.leadId());

		assertEquals(Set.of("propertyId", "propertyCode", "units", "mode", "completedAt"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(result)));
		assertEquals("CREATE", result.path("mode").asString());
		assertTrue(result.path("propertyCode").asString().matches("SV-HOU-\\d{6}"));
		String id = result.path("propertyId").asString();
		Map<String, Object> property = jdbc.queryForMap("select * from properties where id = ?", id);
		assertEquals(ready.address(), property.get("street"));
		assertEquals(WARD_NAME, property.get("ward"));
		assertEquals("Whole house, " + WARD_NAME, property.get("title"));
		assertEquals("HOUSE", property.get("asset_type"));
		assertEquals("WHOLE", property.get("rental_layout"));
		assertEquals(WARD, property.get("ward_code"));
		assertEquals(ready.owner().userId(), property.get("owner_id"));
		for (String legacy : List.of("property_type", "bedrooms", "bathrooms", "square_meters", "monthly_price", "deposit_price",
				"min_rent_months", "status", "district")) {
			assertNull(property.get(legacy), legacy);
		}
		Map<String, Object> unit = jdbc.queryForMap("select * from units where property_id = ?", id);
		assertEquals(result.path("propertyCode").asString(), unit.get("unit_code"));
		assertEquals("AVAILABLE", unit.get("availability_state"));
		assertNull(unit.get("unit_type"));
		assertEquals(1, count("select count(*) from property_ownerships where property_id = ? and user_id = ? and"
				+ " relation_type = 'OWNER' and verification_state = 'UNVERIFIED'", id, ready.owner().userId()));
		assertEquals(1, count("select count(*) from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = ?"
				+ " and r.name = 'OWNER'", ready.owner().userId()));
		assertEquals(before, survey.leadStatus(ready.leadId()));
		JsonNode block = blockOk(sale, ready.leadId());
		assertEquals("COMPLETED", block.path("status").asString());
		assertEquals(result.path("propertyCode").asString(), block.path("result").path("propertyCode").asString());
		assertEquals(1, block.path("result").path("unitCount").asInt());
	}

	@Test
	void create_parts2of10_twoUnits_canonicalCode_ownG3() throws Exception {
		String address = unique("Tòa Mười");
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", address, "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9",
				"P10");
		List<String> two = passed.spaceIds().subList(0, 2);
		TestUser owner = owner();
		selected(passed.leadId(), two);
		Issued issued = issued(passed.leadId(), address(owner));
		confirmed(owner, issued);
		assertEquals(204, enter(owner, issued.requestId(), started(manager, passed.leadId())).status());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), two.get(1)));
		available(passed.leadId(), two, "VACANT");

		JsonNode result = completed(passed.leadId());

		assertEquals(2, result.path("units").size());
		String code = result.path("propertyCode").asString();
		assertTrue(code.startsWith("SV-BLD-"));
		List<Map<String, Object>> units = jdbc.queryForList("select unit_code, location_label from units where property_id = ?"
				+ " order by location_label", result.path("propertyId").asString());
		assertEquals("P1", units.get(0).get("unit_code"));
		assertEquals(code, units.get(1).get("unit_code"));
		assertTrue(units.get(0).get("location_label").toString().startsWith("P1 · "));
		assertTrue(units.get(1).get("location_label").toString().startsWith("P2 · "));
		assertEquals(2, count("select count(*) from inventory_unit_mappings where intake_id = ?", intakeRow(passed.leadId()).get("id")));
		assertEquals(1, count("select count(*) from inventory_unit_mappings where intake_id = ? and canonical",
				intakeRow(passed.leadId()).get("id")));
	}

	/** D5-20: condition and amenities stay in the revision, so no unit row carries a balcony. */
	@Test
	void create_p101BalconyNeverOnP102() throws Exception {
		String address = unique("Tòa Ban Công");
		Passed passed = fixture.partsWithAmenity(leader, sale, "WHOLE_BUILDING", address, "BALCONY", 0, "P101", "P102");
		checked(passed, owner());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), passed.spaceIds().getFirst()));
		available(passed.leadId(), passed.spaceIds(), "VACANT");

		JsonNode preview = previewOk(passed.leadId());
		assertTrue(preview.path("units").path(0).path("amenities").toString().contains("BALCONY"));
		assertFalse(preview.path("units").path(1).path("amenities").toString().contains("BALCONY"));

		JsonNode result = completed(passed.leadId());

		assertEquals(0, count("select count(*) from property_amenity where property_id = ?", result.path("propertyId").asString()));
	}

	/** S-15: a code that differs only by case or spaces cannot make a second unit. */
	@Test
	void unitCodes_uniqueIgnoringCase() throws Exception {
		Ready ready = ready();
		String id = completed(ready.leadId()).path("propertyId").asString();
		String code = jdbc.queryForObject("select unit_code from units where property_id = ?", String.class, id);

		assertRefusedByDatabase(() -> unit(id, " " + code.toLowerCase(java.util.Locale.ROOT) + " ", "AVAILABLE"));
	}

	private static void assertRefusedByDatabase(Runnable write) {
		try {
			write.run();
		} catch (org.springframework.dao.DataIntegrityViolationException expected) {
			return;
		}
		throw new AssertionError("The database took a second unit with the same code.");
	}

	// ==================== checks before any write (S-1, S-16, S-18) ====================

	@Test
	void gate_insideCompletion_eachReason() throws Exception {
		Ready held = ready();
		String hash = previewOk(held.leadId()).path("previewHash").asString();
		long version = versionOf(sale, held.leadId());
		hold(held.leadId());
		Result refused = complete(sale, held.leadId(), version, hash);
		assertRefused(409, "INTAKE_GATE_FAILED", refused);
		assertEquals("gate.NOT_QUALIFIED", refused.body().path("errors").path(0).path("field").asString());

		Ready restricted = ready();
		hash = previewOk(restricted.leadId()).path("previewHash").asString();
		suspend(manager, restricted.leadId(), null, "A reason", versionOf(manager, restricted.leadId()));
		refused = complete(sale, restricted.leadId(), versionOf(sale, restricted.leadId()), hash);
		assertRefused(409, "INTAKE_GATE_FAILED", refused);
		assertEquals("gate.RESTRICTED", refused.body().path("errors").path(0).path("field").asString());

		assertEquals(0, propertiesAt(held.address()) + propertiesAt(restricted.address()));
	}

	@Test
	void complete_yesterdayContact_409() throws Exception {
		Passed passed = house(unique("Lê Duẩn"));
		checked(passed, owner());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		SurveyApiTestSupport.ok(availability(passed.leadId(), List.of(availabilityItem(passed.spaceIds().getFirst(), "VACANT",
				today().minusDays(1), "Owner said so"))));

		assertRefused(409, "AVAILABILITY_NOT_CONFIRMED", complete(sale, passed.leadId(), versionOf(sale, passed.leadId()),
				previewOk(passed.leadId()).path("previewHash").asString()));
	}

	@Test
	void complete_noTarget_409() throws Exception {
		Passed passed = house(unique("Hùng Vương"));
		checked(passed, owner());

		assertRefused(409, "INTAKE_STATE", complete(sale, passed.leadId(), versionOf(sale, passed.leadId()), "x".repeat(64)));
	}

	@Test
	void complete_noProof_409() throws Exception {
		Passed passed = house(unique("Pasteur"));
		selected(passed.leadId(), passed.spaceIds());

		assertRefused(409, "OWNER_PROOF_REQUIRED", complete(sale, passed.leadId(), versionOf(sale, passed.leadId()), "x".repeat(64)));
	}

	@Test
	void complete_clearedTarget_409() throws Exception {
		String address = unique("Cleared Street");
		Ready ready = readyWithLinkPlaceCase(address);
		decided(caseOf(ready.leadId()), "DIFFERENT_ASSET", null);

		assertRefused(409, "INTAKE_STATE", complete(sale, ready.leadId(), versionOf(sale, ready.leadId()), "x".repeat(64)));
	}

	private Ready readyWithLinkPlaceCase(String address) throws Exception {
		Passed passed = house(address);
		TestUser owner = owner();
		checked(passed, owner);
		String id = seeded(address + " other", owner.userId());
		String unit = unit(id, "U1", "AVAILABLE");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), id, passed.spaceIds(), List.of(unit)));
		assertEquals("CASE_OPEN", blockOk(sale, passed.leadId()).path("status").asString());
		return new Ready(passed, owner, address);
	}

	@Test
	void complete_stalePreview_409_availability() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();

		available(ready.leadId(), ready.spaces(), "OCCUPIED");

		assertRefused(409, "INTAKE_PREVIEW_STALE", complete(sale, ready.leadId(), versionOf(sale, ready.leadId()), hash));
	}

	@Test
	void complete_stalePreview_409_assignee() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		long version = versionOf(sale, ready.leadId());

		fixture.checklists().reassign(leader, ready.leadId(), otherSale);

		assertRefused(409, "INTAKE_PREVIEW_STALE", complete(otherSale, ready.leadId(), version, hash));
	}

	@Test
	void complete_stalePreview_409_unitVersion() throws Exception {
		Passed passed = house(unique("Unit Version"));
		TestUser owner = owner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class, passed.leadId());
		String id = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(id, owner.userId());
		String unit = unit(id, "U1", "AVAILABLE");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), id, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String hash = previewOk(passed.leadId()).path("previewHash").asString();

		jdbc.update("update units set version = version + 1 where id = ?", unit);

		assertRefused(409, "INTAKE_PREVIEW_STALE", complete(sale, passed.leadId(), versionOf(sale, passed.leadId()), hash));
	}

	/** Review focus 5: a suspension moves the row, so the old version and preview are stale. */
	@Test
	void complete_stalePreview_409_suspensionMovesTheVersion() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		long version = versionOf(sale, ready.leadId());
		Result held = suspend(manager, ready.leadId(), null, "A reason", version);
		SurveyApiTestSupport.ok(release(manager, ready.leadId(), held.body().path("suspensions").path(0).path("id").asString(), "Fine",
				versionOf(manager, ready.leadId())));

		assertRefused(409, "CONCURRENT_MODIFICATION", complete(sale, ready.leadId(), version, hash));
	}

	@Test
	void complete_staleVersion_409() throws Exception {
		Ready ready = ready();

		assertRefused(409, "CONCURRENT_MODIFICATION", complete(sale, ready.leadId(), versionOf(sale, ready.leadId()) - 1,
				previewOk(ready.leadId()).path("previewHash").asString()));
	}

	@Test
	void complete_notAssignee_403() throws Exception {
		Ready ready = ready();

		assertRefused(403, "INTAKE_NOT_ASSIGNEE", complete(leader, ready.leadId(), versionOf(sale, ready.leadId()), "x".repeat(64)));
	}

	@Test
	void complete_ownerNoLongerEligible_409() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'", ready.owner().userId());

		assertRefused(409, "OWNER_PROOF_REQUIRED", complete(sale, ready.leadId(), versionOf(sale, ready.leadId()), hash));
	}

	// ==================== repeat (S-18) ====================

	@Test
	void complete_repeat_storedResult() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		long version = versionOf(sale, ready.leadId());
		Result first = complete(sale, ready.leadId(), version, hash);
		assertEquals(200, first.status(), first.rawBody());

		Result second = complete(sale, ready.leadId(), version, hash);

		assertEquals(200, second.status(), second.rawBody());
		assertEquals(first.body(), second.body());
		assertEquals(1, propertiesAt(ready.address()));
	}

	@Test
	void complete_repeatByFormerAssignee_403() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		long version = versionOf(sale, ready.leadId());
		complete(sale, ready.leadId(), version, hash);

		fixture.checklists().reassign(leader, ready.leadId(), otherSale);

		assertEquals(404, complete(sale, ready.leadId(), version, hash).status(), "a Sale no longer sees the lead");
		TestUser boss = survey.user("Former Boss", "SALE_LEADER");
		fixture.checklists().reassign(leader, ready.leadId(), boss);
		fixture.checklists().reassign(leader, ready.leadId(), otherSale);
		assertRefused(403, "INTAKE_NOT_ASSIGNEE", complete(boss, ready.leadId(), version, hash));
	}

	@Test
	void complete_otherHash_409() throws Exception {
		Ready ready = ready();
		long version = versionOf(sale, ready.leadId());
		complete(sale, ready.leadId(), version, previewOk(ready.leadId()).path("previewHash").asString());

		assertRefused(409, "INTAKE_ALREADY_COMPLETED", complete(sale, ready.leadId(), version, "y".repeat(64)));
	}

	@Test
	void usedVersions_stored() throws Exception {
		Passed passed = house(unique("Used Versions"));
		TestUser owner = owner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class, passed.leadId());
		String id = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(id, owner.userId());
		String unit = unit(id, "U1", "AVAILABLE");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), id, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");

		completed(passed.leadId());

		String used = (String) intakeRow(passed.leadId()).get("used_versions");
		assertTrue(used.contains(id));
		assertTrue(used.contains(unit));
	}

	// ==================== link (S-11, S-16) ====================

	private record Linked(Passed passed, TestUser owner, String propertyId, String unitId) {
	}

	private Linked linkedHouse(String unitState) throws Exception {
		Passed passed = house(unique("Link Street"));
		TestUser owner = owner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class, passed.leadId());
		String id = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(id, owner.userId());
		String unit = unit(id, "U1", unitState);
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), id, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return new Linked(passed, owner, id, unit);
	}

	@Test
	void link_writesNoUnitOwnershipOrRole() throws Exception {
		Linked linked = linkedHouse("AVAILABLE");
		int ownerships = count("select count(*) from property_ownerships where property_id = ?", linked.propertyId());

		JsonNode result = completed(linked.passed().leadId());

		assertEquals("LINK", result.path("mode").asString());
		assertEquals(linked.propertyId(), result.path("propertyId").asString());
		assertEquals(linked.unitId(), result.path("units").path(0).path("unitId").asString());
		assertEquals(1, count("select count(*) from units where property_id = ?", linked.propertyId()));
		assertEquals(ownerships, count("select count(*) from property_ownerships where property_id = ?", linked.propertyId()));
		assertEquals(0, count("select count(*) from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = ?"
				+ " and r.name = 'OWNER'", linked.owner().userId()));
		assertEquals(1, count("select count(*) from inventory_unit_mappings where unit_id = ?", linked.unitId()));
	}

	/** BR-11: on a link nothing reaches the unit; a difference only reads on it. */
	@Test
	void link_marksDifference_writesNothing() throws Exception {
		Linked linked = linkedHouse("OCCUPIED");

		completed(linked.passed().leadId());

		assertEquals("OCCUPIED", jdbc.queryForObject("select availability_state from units where id = ?", String.class, linked.unitId()));
		JsonNode unit = blockOk(sale, linked.passed().leadId()).path("readiness").path("units").path(0);
		assertFalse(unit.path("usable").asBoolean());
		assertTrue(unit.path("reasons").toString().contains("AVAILABILITY_DIFFERS"));
	}

	@Test
	void complete_link_reChecksDispute() throws Exception {
		Linked linked = linkedHouse("AVAILABLE");
		String hash = previewOk(linked.passed().leadId()).path("previewHash").asString();
		String street = jdbc.queryForObject("select street from properties where id = ?", String.class, linked.propertyId());
		Passed rival = house(street);
		checked(rival, owner());
		SurveyApiTestSupport.ok(createTarget(rival.leadId(), null));

		assertRefused(409, "INTAKE_DISPUTED", complete(sale, linked.passed().leadId(), versionOf(sale, linked.passed().leadId()), hash));
	}

	// ==================== the signal and the code (S-17, S-20) ====================

	@Test
	void complete_lateSignal_commitsCaseAnd409() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		seeded(ready.address(), owner().userId());

		assertRefused(409, "INTAKE_DUPLICATE_CHECK", complete(sale, ready.leadId(), versionOf(sale, ready.leadId()), hash));

		assertEquals("CASE_OPEN", blockOk(sale, ready.leadId()).path("status").asString());
		assertEquals(1, count("select count(*) from inventory_duplicate_cases where intake_id = ?", intakeRow(ready.leadId()).get("id")));
		assertEquals(1, propertiesAt(ready.address()), "only the seeded row");
	}

	@Test
	void complete_forcedProperty_createRefused() throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		seeded(ready.address(), ready.owner().userId());
		assertRefused(409, "INTAKE_DUPLICATE_CHECK", complete(sale, ready.leadId(), versionOf(sale, ready.leadId()), hash));
		decided(caseOf(ready.leadId()), "SAME_ASSET", null);

		assertRefused(409, "INTAKE_STATE", complete(sale, ready.leadId(), versionOf(sale, ready.leadId()), hash));
		assertRefused(409, "INTAKE_CREATE_NOT_ALLOWED", createTarget(ready.leadId(), null));
	}

	@Test
	void complete_takenCode_409() throws Exception {
		Ready ready = ready();
		long next = jdbc.queryForObject("select nextval('property_intake_code_seq')", Long.class) + 1;
		String legacy = property(owner().userId(), "99999", "Elsewhere", unique("Taken"), "HOUSE", "x");
		jdbc.update("update properties set property_code = ? where id = ?", String.format("SV-HOU-%06d", next), legacy);

		assertRefused(409, "CONCURRENT_MODIFICATION", completeNow(ready));
		assertEquals(0, propertiesAt(ready.address()));

		JsonNode retry = completed(ready.leadId());
		assertNotEquals(String.format("SV-HOU-%06d", next), retry.path("propertyCode").asString());
	}

	/** S-17: a failure at each write leaves nothing, the role included. A trigger stands for the failing write. */
	@ParameterizedTest
	@ValueSource(strings = { "properties", "units", "property_ownerships", "user_roles", "inventory_unit_mappings",
			"inventory_intake_events", "owner_lead_activities" })
	void complete_failureAtEachWrite_leavesNothing(String table) throws Exception {
		Ready ready = ready();
		String hash = previewOk(ready.leadId()).path("previewHash").asString();
		long version = versionOf(sale, ready.leadId());
		jdbc.execute("create or replace function fail_the_write() returns trigger language plpgsql as"
				+ " $$ begin raise exception 'injected'; end $$");
		jdbc.execute("create trigger fail_the_write before insert on " + table + " for each row execute function fail_the_write()");
		try {
			assertEquals(500, complete(sale, ready.leadId(), version, hash).status());
		} finally {
			jdbc.execute("drop trigger fail_the_write on " + table);
		}

		assertEquals(0, propertiesAt(ready.address()));
		assertEquals("OWNER_CONFIRMED", intakeRow(ready.leadId()).get("status"));
		assertEquals(0, count("select count(*) from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = ?"
				+ " and r.name = 'OWNER'", ready.owner().userId()));
		assertEquals(0, count("select count(*) from owner_lead_activities where lead_id = ? and type = 'INVENTORY_INTAKE_COMPLETED'",
				ready.leadId()));
		assertEquals(200, complete(sale, ready.leadId(), version, hash).status(), "nothing stood in the way of a retry");
	}

	// ==================== History and logs (S-24) ====================

	@Test
	void history_holdsNoPropertyOrOwner() throws Exception {
		Ready ready = ready();
		String code = completed(ready.leadId()).path("propertyCode").asString();

		List<Map<String, Object>> rows = jdbc.queryForList("select * from owner_lead_activities where lead_id = ? and"
				+ " type = 'INVENTORY_INTAKE_COMPLETED'", ready.leadId());

		assertEquals(1, rows.size());
		assertEquals(intakeRow(ready.leadId()).get("id"), rows.getFirst().get("reference_id"));
		String text = rows.getFirst().toString();
		assertFalse(text.contains(code));
		assertFalse(text.contains(ready.owner().userId()));
		assertFalse(text.contains(ready.address()));
		assertEquals(1, count("select count(*) from inventory_intake_events where intake_id = ? and action = 'COMPLETION'",
				intakeRow(ready.leadId()).get("id")));
	}

	// ==================== a result meets the signal by its space code (D5-14) ====================

	/** The unit of a WHOLE result carries the property's code, so the surveyed code must reach the signal another way. */
	@ParameterizedTest
	@ValueSource(strings = { "APARTMENT", "CONDOTEL" })
	void signal_sameFlatAfterAResult_opensCase(String assetType) throws Exception {
		String address = unique("Sky Tower");
		Passed first = fixture.whole(leader, sale, assetType, address);
		createdHouse(first, owner());

		Passed second = fixture.whole(leader, sale, assetType, address);
		checked(second, owner());

		assertEquals("CASE_OPEN", createTarget(second.leadId(), null).body().path("status").asString());
		assertEquals(1, propertiesAt(address));
	}

	@Test
	void signal_otherFlatOfTheBuilding_opensNoCase() throws Exception {
		String address = unique("Sea Tower");
		Passed first = fixture.whole(leader, sale, "APARTMENT", address);
		createdHouse(first, owner());
		jdbc.update("update inventory_unit_mappings set space_code = 'A1206' where intake_id = ?", intakeRow(first.leadId()).get("id"));

		Passed second = fixture.whole(leader, sale, "APARTMENT", address);
		checked(second, owner());

		assertEquals("OWNER_CONFIRMED", createTarget(second.leadId(), null).body().path("status").asString());
	}

	// ==================== the other inputs of the hash (S-18) ====================

	@Test
	void complete_stalePreview_409_target() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Tòa Mục Tiêu"), "P1", "P2");
		checked(passed, owner());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), passed.spaceIds().get(0)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String hash = previewOk(passed.leadId()).path("previewHash").asString();

		SurveyApiTestSupport.ok(createTarget(passed.leadId(), passed.spaceIds().get(1)));

		assertRefused(409, "INTAKE_PREVIEW_STALE", complete(sale, passed.leadId(), versionOf(sale, passed.leadId()), hash));
	}

	@Test
	void complete_stalePreview_409_proof() throws Exception {
		Passed passed = house(unique("Proof Street"));
		TestUser owner = owner();
		Issued issued = checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String hash = previewOk(passed.leadId()).path("previewHash").asString();
		assertEquals(204, withdraw(owner, issued.requestId(), passwordOf(owner)).status());

		Issued again = issued(passed.leadId(), address(owner));
		confirmed(owner, again);
		assertEquals(204, enter(owner, again.requestId(), started(manager, passed.leadId())).status());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");

		assertRefused(409, "INTAKE_PREVIEW_STALE", complete(sale, passed.leadId(), versionOf(sale, passed.leadId()), hash));
	}

	@Test
	void complete_stalePreview_409_selection() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Tòa Chọn"), "P1", "P2");
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), passed.spaceIds().get(0)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String hash = previewOk(passed.leadId()).path("previewHash").asString();

		List<String> one = passed.spaceIds().subList(0, 1);
		selected(passed.leadId(), one);
		Issued issued = issued(passed.leadId(), address(owner));
		confirmed(owner, issued);
		assertEquals(204, enter(owner, issued.requestId(), started(manager, passed.leadId())).status());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), one.getFirst()));
		available(passed.leadId(), one, "VACANT");

		assertRefused(409, "INTAKE_PREVIEW_STALE", complete(sale, passed.leadId(), versionOf(sale, passed.leadId()), hash));
	}

	/** spec 4.6 step 1: an expired request drops its address at the next command on its intake, a result's too. */
	@Test
	void snapshot_expiredOnAResult_clearedByTheNextCommand() throws Exception {
		Ready ready = ready();
		completed(ready.leadId());
		jdbc.update("update users set user_status = 'LOCK' where id = ?", ready.owner().userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", ready.owner().userId());
		Issued issued = issued(ready.leadId(), address(ready.owner()));
		jdbc.update("update inventory_owner_requests set expires_at = ? where id = ?",
				java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), issued.requestId());

		SurveyApiTestSupport.ok(suspend(manager, ready.leadId(), null, "A reason", versionOf(manager, ready.leadId())));

		assertNull(jdbc.queryForObject("select display_snapshot from inventory_owner_requests where id = ?", String.class,
				issued.requestId()));
	}

	// ==================== rows of earlier tasks that need a result ====================

	/** S-9: a request on a result to another account binds nothing. */
	@Test
	void requestOnCompleted_otherAccount_bindsNothing() throws Exception {
		Ready ready = ready();
		completed(ready.leadId());
		jdbc.update("update users set user_status = 'LOCK' where id = ?", ready.owner().userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", ready.owner().userId());
		assertTrue(blockOk(sale, ready.leadId()).path("readiness").path("reasons").toString().contains("OWNER_NOT_CONFIRMED"));
		TestUser stranger = owner();

		Issued issued = issued(ready.leadId(), address(stranger));

		assertEquals(1, count("select count(*) from inventory_owner_requests where id = ? and target_user_id is null",
				issued.requestId()));
		assertRefused(404, "NOT_FOUND", details(stranger, issued.requestId(), issued.code()));
		assertEquals("COMPLETED", blockOk(sale, ready.leadId()).path("status").asString());
	}

	/** spec 4.2: only the owner of the result binds, so a result whose property cannot be read binds nobody. */
	@Test
	void requestOnCompleted_resultUnreadable_bindsNothing() throws Exception {
		Ready ready = ready();
		String property = completed(ready.leadId()).path("propertyId").asString();
		jdbc.update("update users set user_status = 'LOCK' where id = ?", ready.owner().userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", ready.owner().userId());
		jdbc.update("delete from property_ownerships where property_id = ?", property);
		jdbc.update("delete from units where property_id = ?", property);
		jdbc.update("delete from properties where id = ?", property);

		Issued issued = issued(ready.leadId(), address(ready.owner()));

		assertEquals(1, count("select count(*) from inventory_owner_requests where id = ? and target_user_id is null",
				issued.requestId()));
	}

	@Test
	void requestOnCompleted_healthyResult_refused() throws Exception {
		Ready ready = ready();
		completed(ready.leadId());

		assertRefused(409, "INTAKE_STATE", requestOwner(sale, ready.leadId(), "x@example.com", versionOf(sale, ready.leadId())));
	}

	/** spec 4.6 step 6: a refusal on a result leaves the row, and History says nothing of it. */
	@Test
	void refusal_completed_statusStays_noHistory() throws Exception {
		Ready ready = ready();
		completed(ready.leadId());
		String proofId = proofIdOf(ready.leadId());
		int rows = count("select count(*) from owner_lead_activities where lead_id = ?", ready.leadId());
		jdbc.update("update inventory_owner_proofs set owner_check = 'REQUIRED' where id = ?", proofId);

		SurveyApiTestSupport.ok(refuse(admin, ready.leadId(), proofId, "Reached a stranger", versionOf(admin, ready.leadId())));

		assertEquals("COMPLETED", blockOk(sale, ready.leadId()).path("status").asString());
		assertEquals(rows, count("select count(*) from owner_lead_activities where lead_id = ?", ready.leadId()));
		assertEquals(1, count("select count(*) from auth_email_notifications where user_id = ? and kind = 'OWNER_CHECK_REFUSED'",
				ready.owner().userId()));
	}

	@Test
	void suspend_unitOfEarlierCompletedIntake_ok() throws Exception {
		Ready ready = ready();
		String unit = completed(ready.leadId()).path("units").path(0).path("unitId").asString();

		Result answer = suspend(manager, ready.leadId(), unit, "This room only", versionOf(manager, ready.leadId()));

		assertEquals(200, answer.status(), answer.rawBody());
		assertNotNull(answer.body().path("suspensions").path(0).path("unitCode").asString());
		JsonNode readiness = answer.body().path("readiness");
		assertEquals("USABLE", readiness.path("state").asString(), "a one-unit reason leaves the state and marks the unit");
		assertFalse(readiness.path("units").path(0).path("usable").asBoolean());
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type = 'INVENTORY_INTAKE_SUSPENDED'"
				+ " and reference_id = ?", ready.leadId(), intakeRow(ready.leadId()).get("id")));
	}

	/** spec 4.10: a winner that COMPLETES stays live, and the case reads as SAME_ASSET on its result. */
	@Test
	void winnerCompleted_appliesItsProperty() throws Exception {
		String address = unique("Winner Street");
		Passed first = house(address);
		TestUser owner = owner();
		checked(first, owner);
		Passed second = house(address);
		checked(second, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		String caseId = caseOf(first.leadId());
		decided(caseId, "SAME_ASSET", (String) intakeRow(first.leadId()).get("id"));
		available(first.leadId(), first.spaceIds(), "VACANT");

		String code = completed(first.leadId()).path("propertyCode").asString();

		JsonNode loser = blockOk(sale, second.leadId());
		assertEquals("OWNER_CONFIRMED", loser.path("status").asString());
		assertEquals(code, loser.path("forcedPropertyCode").asString());
		assertRefused(409, "INTAKE_CREATE_NOT_ALLOWED", createTarget(second.leadId(), null));
	}

	@Test
	void completedWinner_staysLive_resultDisputed() throws Exception {
		String address = unique("Disputed Winner");
		Passed first = house(address);
		TestUser owner = owner();
		checked(first, owner);
		Passed second = house(address);
		checked(second, owner());
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		decided(caseOf(first.leadId()), "SAME_ASSET", (String) intakeRow(first.leadId()).get("id"));
		available(first.leadId(), first.spaceIds(), "VACANT");

		completed(first.leadId());

		assertEquals("CASE_OPEN", blockOk(sale, second.leadId()).path("status").asString(), "another user's checked proof holds");
		assertEquals("WAITING", blockOk(sale, second.leadId()).path("caseState").asString());
		Passed third = house(address);
		checked(third, owner);
		String property = propertyAt(address);
		assertRefused(409, "INTAKE_DISPUTED", linkTarget(third.leadId(), property, third.spaceIds(),
				List.of(jdbc.queryForObject("select id from units where property_id = ?", String.class, property))));
	}

	@Test
	void availability_todayRule_usesVietnam() {
		assertEquals(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")), today());
	}
}
