package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.domain.policy.intake.AddressNormalizer;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Search, target, availability and preview (property-create intake spec 4.7..4.9, S-10, S-11, S-14, S-16, S-30, S-32) through the
 * real filter chain. The rows that need a manager's decision run in InventoryCaseApiPostgresTest. Needs Docker.
 */
class IntakeTargetApiPostgresTest extends IntakeApiTestBase {

	private static final String WARD_NAME = "Hải Châu";

	@Override
	String phonePrefix() {
		return "0966";
	}

	private record Ready(Passed passed, TestUser owner, String address) {
		String leadId() {
			return passed.leadId();
		}

		String space() {
			return passed.spaceIds().getFirst();
		}
	}

	/** A surveyed house with a checked proof: what search, target and availability need. */
	private Ready readyHouse() throws Exception {
		String address = unique("Bạch Đằng");
		Passed passed = house(address);
		TestUser owner = owner();
		checked(passed, owner);
		return new Ready(passed, owner, address);
	}

	private Ready readyParts(String... codes) throws Exception {
		String address = unique("Nguyễn Tri Phương");
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", address, codes);
		TestUser owner = owner();
		checked(passed, owner);
		return new Ready(passed, owner, address);
	}

	/** A legacy-style property at the address of the source, owned by the account. */
	private String ownedProperty(Ready ready, String assetType) {
		String id = property(ready.owner().userId(), WARD, WARD_NAME, ready.address(), assetType,
				AddressNormalizer.normalize(ready.address()));
		ownership(id, ready.owner().userId());
		return id;
	}

	private void layout(String propertyId, String layout, String intakeIdOrNull) {
		jdbc.update("update properties set rental_layout = ?, intake_id = ? where id = ?", layout, intakeIdOrNull, propertyId);
	}

	private Set<String> warnings(JsonNode preview) {
		Set<String> warnings = new java.util.TreeSet<>();
		preview.path("warnings").forEach(warning -> warnings.add(warning.asString()));
		return warnings;
	}

	// ==================== search (S-8, S-10) ====================

	@Test
	void noProofReachesSearchWithoutCheck() throws Exception {
		Passed passed = house(unique("Lê Duẩn"));
		TestUser owner = owner();
		proved(passed, owner);

		assertRefused(409, "OWNER_PROOF_REQUIRED", search(sale, passed.leadId(), "q=abc"));
		assertRefused(409, "OWNER_PROOF_REQUIRED", createTarget(passed.leadId(), null));
		assertRefused(409, "OWNER_PROOF_REQUIRED", availability(passed.leadId(), List.of(
				availabilityItem(passed.spaceIds().getFirst(), "VACANT", today(), "Owner said so"))));
		assertRefused(409, "OWNER_PROOF_REQUIRED", preview(sale, passed.leadId()));
	}

	@Test
	void search_beforeProof_409() throws Exception {
		Passed passed = house(unique("Hùng Vương"));
		selected(passed.leadId(), passed.spaceIds());

		assertRefused(409, "OWNER_PROOF_REQUIRED", search(sale, passed.leadId(), "q=abc"));
	}

	@Test
	void search_notAssignee_403() throws Exception {
		Ready ready = readyHouse();

		assertRefused(403, "INTAKE_NOT_ASSIGNEE", search(leader, ready.leadId(), "q=abc"));
	}

	@Test
	void search_onlyConfirmedOwners() throws Exception {
		Ready ready = readyHouse();
		String mine = ownedProperty(ready, "HOUSE");
		String other = property(owner().userId(), WARD, WARD_NAME, ready.address(), "HOUSE", "x");

		JsonNode found = search(sale, ready.leadId(), "wardCode=" + WARD + "&q=" + urlEncoded(ready.address())).body();

		assertEquals(1, found.path("totalElements").asInt());
		assertEquals(mine, found.path("items").path(0).path("propertyId").asString());
		assertFalse(found.toString().contains(other));
	}

	@Test
	void search_fields() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		String unit = unit(id, "U1", "AVAILABLE");
		jdbc.update("update units set location_label = 'U1 · Floor 2' where id = ?", unit);
		String code = jdbc.queryForObject("select property_code from properties where id = ?", String.class, id);

		Result answer = search(sale, ready.leadId(), "propertyCode=" + code);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(Set.of("items", "page", "size", "totalElements"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(answer.body())));
		JsonNode item = answer.body().path("items").path(0);
		assertEquals(Set.of("propertyId", "propertyCode", "assetType", "wardName", "units"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(item)));
		assertEquals(code, item.path("propertyCode").asString());
		assertEquals(WARD_NAME, item.path("wardName").asString());
		assertEquals(Set.of("unitId", "unitCode", "location"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(item.path("units").path(0))));
		assertEquals("U1 · Floor 2", item.path("units").path(0).path("location").asString());
		assertEquals(20, answer.body().path("size").asInt());
	}

	@Test
	void search_logs() throws Exception {
		Ready ready = readyHouse();

		search(sale, ready.leadId(), "q=abc");

		assertEquals(1, count("select count(*) from inventory_access_logs where lead_id = ? and action = 'INVENTORY_SEARCH'",
				ready.leadId()));
	}

	@Test
	void search_wildcardsEscaped() throws Exception {
		Ready ready = readyHouse();
		ownedProperty(ready, "HOUSE");

		assertEquals(0, search(sale, ready.leadId(), "q=" + urlEncoded("%")).body().path("totalElements").asInt());
		assertEquals(0, search(sale, ready.leadId(), "q=" + urlEncoded("_")).body().path("totalElements").asInt());
	}

	/** A-6: q reads the normalized street, so no diacritic stops a match. */
	@Test
	void search_matchesNormalizedStreet() throws Exception {
		Ready ready = readyHouse();
		ownedProperty(ready, "HOUSE");

		assertEquals(1, search(sale, ready.leadId(), "q=" + urlEncoded("bach dang")).body().path("totalElements").asInt());
	}

	@Test
	void search_legacyNoWardCode_byCodeOrQ() throws Exception {
		Ready ready = readyHouse();
		String legacy = property(ready.owner().userId(), null, WARD_NAME, ready.address(), null, null);
		String code = jdbc.queryForObject("select property_code from properties where id = ?", String.class, legacy);

		assertEquals(1, search(sale, ready.leadId(), "propertyCode=" + code).body().path("totalElements").asInt());
		assertEquals(1, search(sale, ready.leadId(), "wardCode=" + WARD + "&q=" + urlEncoded(ready.address())).body()
				.path("totalElements").asInt());
		assertEquals(0, search(sale, ready.leadId(), "wardCode=" + WARD).body().path("totalElements").asInt());
	}

	@Test
	void search_noCriterion_400() throws Exception {
		Ready ready = readyHouse();

		assertRefused(400, "VALIDATION_FAILED", search(sale, ready.leadId(), "page=0"));
	}

	private static String urlEncoded(String text) {
		return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
	}

	// ==================== link (S-11, S-27) ====================

	@Test
	void link_ok_storesMappings_writesNoUnit() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		String unit = unit(id, "U1", "AVAILABLE");

		Result answer = linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unit));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("LINK", answer.body().path("target").path("mode").asString());
		assertTrue(jdbc.queryForObject("select target_mappings from inventory_intakes where lead_id = ?", String.class,
				ready.leadId()).contains(unit));
		assertEquals(1, count("select count(*) from units where property_id = ?", id));
		assertEquals(1, count("select count(*) from inventory_intake_events where intake_id = ? and action = 'TARGET'",
				intakeRow(ready.leadId()).get("id")));
	}

	@Test
	void link_unknownAndForeign_oneAnswer() throws Exception {
		Ready ready = readyHouse();
		String foreign = property(owner().userId(), WARD, WARD_NAME, ready.address(), "HOUSE", "x");
		String foreignUnit = unit(foreign, "U1", "AVAILABLE");

		Result unknown = linkTarget(ready.leadId(), "01AAAAAAAAAAAAAAAAAAAAAAAA", ready.passed().spaceIds(), List.of(foreignUnit));
		Result other = linkTarget(ready.leadId(), foreign, ready.passed().spaceIds(), List.of(foreignUnit));

		assertRefused(400, "LINK_MAPPING_INVALID", unknown);
		assertRefused(400, "LINK_MAPPING_INVALID", other);
		assertEquals(unknown.body().path("message"), other.body().path("message"));
	}

	/** SV-034: an ownership row from the backfill counts, whatever its origin. */
	@Test
	void link_backfilledOwnership_ok() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		jdbc.update("update property_ownerships set verification_state = 'UNVERIFIED', valid_from = ? where property_id = ?",
				LocalDate.of(2020, 1, 1), id);
		String unit = unit(id, "U1", "AVAILABLE");

		assertEquals(200, linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unit)).status());
	}

	/** SV-034: an account that no longer holds the ownership row reads as foreign. */
	@Test
	void link_endedOwnership_400() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		jdbc.update("update property_ownerships set valid_to = ? where property_id = ?", LocalDate.of(2026, 2, 1), id);
		String unit = unit(id, "U1", "AVAILABLE");

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unit)));
	}

	@Test
	void link_otherWardOrType_400() throws Exception {
		Ready ready = readyHouse();
		String otherWard = property(ready.owner().userId(), "99999", "Elsewhere", ready.address(), "HOUSE", "x");
		ownership(otherWard, ready.owner().userId());
		String otherType = ownedProperty(ready, "VILLA");

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), otherWard, ready.passed().spaceIds(),
				List.of(unit(otherWard, "U1", "AVAILABLE"))));
		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), otherType, ready.passed().spaceIds(),
				List.of(unit(otherType, "U1", "AVAILABLE"))));
	}

	@Test
	void link_legacyWardByName_ok() throws Exception {
		Ready ready = readyHouse();
		String legacy = property(ready.owner().userId(), null, WARD_NAME, ready.address(), null,
				AddressNormalizer.normalize(ready.address()));
		ownership(legacy, ready.owner().userId());

		Result answer = linkTarget(ready.leadId(), legacy, ready.passed().spaceIds(), List.of(unit(legacy, "U1", "AVAILABLE")));

		assertEquals(200, answer.status(), answer.rawBody());
	}

	@Test
	void link_overlap_400() throws Exception {
		Ready ready = readyParts("P101", "P102");
		String id = ownedProperty(ready, "WHOLE_BUILDING");
		layout(id, "PARTS", null);
		String unit = unit(id, "U1", "AVAILABLE");

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unit, unit)));
	}

	@Test
	void link_mappingMissesASpace_400() throws Exception {
		Ready ready = readyParts("P101", "P102");
		String id = ownedProperty(ready, "WHOLE_BUILDING");
		layout(id, "PARTS", null);

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds().subList(0, 1),
				List.of(unit(id, "U1", "AVAILABLE"))));
	}

	@Test
	void link_unitOfAnotherProperty_400() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		String otherProperty = ownedProperty(ready, "HOUSE");

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds(),
				List.of(unit(otherProperty, "U1", "AVAILABLE"))));
	}

	@Test
	void link_wholeOntoParts_400() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		layout(id, "PARTS", null);

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds(),
				List.of(unit(id, "U1", "AVAILABLE"))));
	}

	@Test
	void link_partsOntoWhole_400() throws Exception {
		Ready ready = readyParts("P101");
		String id = ownedProperty(ready, "WHOLE_BUILDING");
		layout(id, "WHOLE", (String) intakeRow(ready.leadId()).get("id"));

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds(),
				List.of(unit(id, "U1", "AVAILABLE"))));
	}

	/** E-6, BR-13: the only unit of a legacy property carries its code, and that one cannot take a part. */
	@Test
	void link_legacyCanonical_409() throws Exception {
		Ready ready = readyParts("P101");
		String id = ownedProperty(ready, "WHOLE_BUILDING");
		String code = jdbc.queryForObject("select property_code from properties where id = ?", String.class, id);

		assertRefused(409, "CANONICAL_UNRECONCILED", linkTarget(ready.leadId(), id, ready.passed().spaceIds(),
				List.of(unit(id, code, "AVAILABLE"))));
	}

	@Test
	void link_suspendedUnit_refused() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		String unit = unit(id, "U1", "AVAILABLE");
		jdbc.update("insert into inventory_suspensions (id, lead_id, unit_id, reason, created_by_user_id, created_at)"
						+ " values (?, ?, ?, 'Held', ?, now())", ids.generateId(), ready.leadId(), unit, manager.userId());

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unit)));
	}

	@Test
	void target_mappings_keptAndClearedWithTarget() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		String unit = unit(id, "U1", "AVAILABLE");
		linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unit));

		selected(ready.leadId(), ready.passed().spaceIds());

		Map<String, Object> row = intakeRow(ready.leadId());
		assertEquals(null, row.get("target_mode"));
		assertEquals(null, row.get("target_mappings"));
	}

	@Test
	void target_modeFieldsDoNotMix_400() throws Exception {
		Ready ready = readyHouse();
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("mode", "CREATE");
		body.put("propertyId", "01AAAAAAAAAAAAAAAAAAAAAAAA");
		body.put("expectedVersion", versionOf(sale, ready.leadId()));

		assertRefused(400, "VALIDATION_FAILED", api.put(sale, base(ready.leadId()) + "/target", body));
	}

	// ==================== create (S-14) ====================

	@Test
	void create_whole_noCanonical_ok() throws Exception {
		Ready ready = readyHouse();

		Result answer = createTarget(ready.leadId(), null);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("CREATE", answer.body().path("target").path("mode").asString());
	}

	@Test
	void create_parts_needsCanonicalOfTheSelection() throws Exception {
		Ready ready = readyParts("P101", "P102");

		assertRefused(400, "VALIDATION_FAILED", createTarget(ready.leadId(), null));
		assertRefused(400, "VALIDATION_FAILED", createTarget(ready.leadId(), "01AAAAAAAAAAAAAAAAAAAAAAAA"));

		Result answer = createTarget(ready.leadId(), ready.space());
		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(ready.space(), answer.body().path("target").path("canonicalSpaceId").asString());
	}

	@Test
	void layout_table() throws Exception {
		Passed mismatch = fixture.whole(leader, sale, "WHOLE_BUILDING", unique("Hoàng Diệu"));
		assertTrue(reasons(blockOk(sale, mismatch.leadId())).contains("TYPE_SCOPE_MISMATCH"));

		Passed fits = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Hoàng Hoa Thám"), "P1");
		assertFalse(reasons(blockOk(sale, fits.leadId())).contains("TYPE_SCOPE_MISMATCH"));
	}

	@Test
	void homestay_either() throws Exception {
		Passed whole = fixture.whole(leader, sale, "HOMESTAY", unique("An Thượng"));
		Passed parts = fixture.parts(leader, sale, "HOMESTAY", unique("An Hải"), "R1", "R2");

		assertFalse(reasons(blockOk(sale, whole.leadId())).contains("TYPE_SCOPE_MISMATCH"));
		assertFalse(reasons(blockOk(sale, parts.leadId())).contains("TYPE_SCOPE_MISMATCH"));
	}

	// ==================== availability (S-16, S-30) ====================

	@Test
	void availability_mapping() throws Exception {
		Map<String, String> table = Map.of("VACANT", "AVAILABLE", "OCCUPIED", "OCCUPIED", "UNDER_REPAIR", "MAINTENANCE",
				"PAUSED", "BLOCKED", "OWNER_UNSURE", "BLOCKED");
		List<String> codes = new ArrayList<>(List.of("P1", "P2", "P3", "P4", "P5"));
		Ready ready = readyParts(codes.toArray(String[]::new));
		createTarget(ready.leadId(), ready.space());
		List<String> states = new ArrayList<>(table.keySet());
		List<Map<String, Object>> items = new ArrayList<>();
		for (int i = 0; i < states.size(); i++) {
			items.add(availabilityItem(ready.passed().spaceIds().get(i), states.get(i), today(), "Owner said so"));
		}
		SurveyApiTestSupport.ok(availability(ready.leadId(), items));

		JsonNode units = previewOk(ready.leadId()).path("units");

		for (int i = 0; i < states.size(); i++) {
			assertEquals(table.get(states.get(i)), units.path(i).path("availability").asString(), states.get(i));
		}
	}

	@Test
	void availability_sourceConflict_blocked() throws Exception {
		Ready ready = readyHouse();
		createTarget(ready.leadId(), null);

		available(ready.leadId(), ready.passed().spaceIds(), "SOURCE_CONFLICT");

		assertEquals("BLOCKED", previewOk(ready.leadId()).path("units").path(0).path("availability").asString());
		assertEquals("SOURCE_CONFLICT", blockOk(sale, ready.leadId()).path("availability").path(0).path("state").asString());
	}

	@Test
	void availability_futureDate_400() throws Exception {
		Ready ready = readyHouse();

		assertRefused(400, "VALIDATION_FAILED", availability(ready.leadId(), List.of(
				availabilityItem(ready.space(), "VACANT", today().plusDays(1), "Owner said so"))));
	}

	@Test
	void availability_noStateForNotContacted() throws Exception {
		Ready ready = readyHouse();

		assertRefused(400, "VALIDATION_FAILED", availability(ready.leadId(), List.of(
				availabilityItem(ready.space(), "NOT_CONTACTED", today(), "No answer"))));
		assertRefused(400, "VALIDATION_FAILED", availability(ready.leadId(), List.of(
				availabilityItem(ready.space(), "VACANT", null, "No answer"))));
	}

	@Test
	void availability_mustCoverTheSelection_400() throws Exception {
		Ready ready = readyParts("P1", "P2");

		assertRefused(400, "VALIDATION_FAILED", availability(ready.leadId(), List.of(
				availabilityItem(ready.space(), "VACANT", today(), "Owner said so"))));
	}

	@Test
	void availability_keepsWhoTookIt() throws Exception {
		Ready ready = readyHouse();

		available(ready.leadId(), ready.passed().spaceIds(), "VACANT");

		assertTrue(jdbc.queryForObject("select availability from inventory_intakes where lead_id = ?", String.class,
				ready.leadId()).contains(sale.userId()));
		assertEquals(1, count("select count(*) from inventory_intake_events where intake_id = ? and action = 'AVAILABILITY'",
				intakeRow(ready.leadId()).get("id")));
	}

	@Test
	void text_phoneOrNul_400() throws Exception {
		Ready ready = readyHouse();

		for (String bad : List.of("Call 0905 123 456", "A\u0000B", "  ")) {
			Result answer = availability(ready.leadId(), List.of(availabilityItem(ready.space(), "VACANT", today(), bad)));
			assertRefused(400, "VALIDATION_FAILED", answer);
			assertEquals("note", answer.body().path("errors").path(0).path("field").asString());
		}
		assertRefused(400, "VALIDATION_FAILED", search(sale, ready.leadId(), "q=" + urlEncoded("0905 123 456")));
		assertRefused(400, "VALIDATION_FAILED", search(sale, ready.leadId(), "q=" + "x".repeat(101)));
	}

	/** S-30: U+0000 in an id must not reach PostgreSQL. */
	@Test
	void ids_nul_400() throws Exception {
		Ready ready = readyHouse();

		assertRefused(400, "VALIDATION_FAILED", linkTarget(ready.leadId(), "a\u0000", ready.passed().spaceIds(), List.of("unit")));
		assertRefused(400, "VALIDATION_FAILED", linkTarget(ready.leadId(), "01AAAAAAAAAAAAAAAAAAAAAAAA", ready.passed().spaceIds(),
				List.of("a\u0000")));
		assertRefused(400, "VALIDATION_FAILED", suspend(manager, ready.leadId(), "a\u0000", "A reason", versionOf(manager, ready.leadId())));
	}

	@Test
	void search_pageNotANumber_400() throws Exception {
		Ready ready = readyHouse();

		assertRefused(400, "VALIDATION_FAILED", search(sale, ready.leadId(), "q=abc&page=abc"));
	}

	// ==================== preview (S-32) ====================

	private Ready previewable() throws Exception {
		Ready ready = readyHouse();
		createTarget(ready.leadId(), null);
		available(ready.leadId(), ready.passed().spaceIds(), "VACANT");
		return ready;
	}

	@Test
	void preview_shapeAndSources() throws Exception {
		Ready ready = previewable();

		JsonNode preview = previewOk(ready.leadId());

		assertEquals(Set.of("previewHash", "owner", "property", "units", "source", "termsReference", "notCarried", "warnings"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(preview)));
		assertEquals(64, preview.path("previewHash").asString().length());
		assertEquals("Whole house, " + WARD_NAME, preview.path("property").path("title").asString());
		assertEquals("HOUSE", preview.path("property").path("assetType").asString());
		assertEquals("WHOLE", preview.path("property").path("layout").asString());
		assertEquals(ready.address(), preview.path("property").path("address").asString());
		assertEquals(ready.passed().checklistId(), preview.path("source").path("revisionId").asString());
		JsonNode unit = preview.path("units").path(0);
		assertEquals(Set.of("spaceCode", "unitCode", "location", "areaSqm", "bedrooms", "bathrooms", "condition", "amenities",
				"availability", "sources"), Set.copyOf(AcquisitionApiTestSupport.fieldNames(unit)));
		assertEquals("A1", unit.path("spaceCode").asString());
		assertFalse(unit.path("sources").isEmpty());
		assertEquals(Set.of("field", "source", "date"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(unit.path("sources").path(0))));
	}

	@Test
	void preview_stableHash_changesWithTheInputs() throws Exception {
		Ready ready = previewable();
		String first = previewOk(ready.leadId()).path("previewHash").asString();

		assertEquals(first, previewOk(ready.leadId()).path("previewHash").asString());

		available(ready.leadId(), ready.passed().spaceIds(), "OCCUPIED");
		assertFalse(first.equals(previewOk(ready.leadId()).path("previewHash").asString()));
	}

	@Test
	void preview_maskedAccount() throws Exception {
		Ready ready = previewable();

		JsonNode owner = previewOk(ready.leadId()).path("owner");

		assertEquals("CONFIRMED", owner.path("state").asString());
		assertEquals("DONE", owner.path("check").asString());
		assertFalse(owner.path("accountAddressMasked").asString().contains(address(ready.owner())));
		assertFalse(owner.path("accountAddressMasked").asString().isBlank());
	}

	@Test
	void preview_nullCodesOnCreate() throws Exception {
		Ready ready = readyParts("P101", "P102");
		createTarget(ready.leadId(), ready.passed().spaceIds().get(1));
		available(ready.leadId(), ready.passed().spaceIds(), "VACANT");

		JsonNode preview = previewOk(ready.leadId());

		assertTrue(preview.path("property").path("code").isNull());
		assertEquals("P101", preview.path("units").path(0).path("unitCode").asString());
		assertTrue(preview.path("units").path(1).path("unitCode").isNull());
		assertTrue(warnings(preview).contains("UNIT_UNCLASSIFIED"));
	}

	@Test
	void preview_link_readsProperty_marksDifferences() throws Exception {
		Ready ready = readyHouse();
		String id = ownedProperty(ready, "HOUSE");
		String unit = unit(id, "U1", "OCCUPIED");
		linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unit));
		available(ready.leadId(), ready.passed().spaceIds(), "VACANT");

		JsonNode preview = previewOk(ready.leadId());

		assertEquals(jdbc.queryForObject("select property_code from properties where id = ?", String.class, id),
				preview.path("property").path("code").asString());
		assertEquals("U1", preview.path("units").path(0).path("unitCode").asString());
		assertTrue(warnings(preview).containsAll(Set.of("AVAILABILITY_DIFFERS", "LEGACY_PROPERTY")));
	}

	@Test
	void preview_samePhoneWarning_visibleLeadsOnly() throws Exception {
		Ready ready = previewable();
		assertFalse(warnings(previewOk(ready.leadId())).contains("SAME_PHONE_LEAD"), "no other lead");

		String hidden = survey.submitted(survey.phone(ready.leadId()));
		assertFalse(warnings(previewOk(ready.leadId())).contains("SAME_PHONE_LEAD"), "a lead the Sale cannot see");

		jdbc.update("update owner_leads set assigned_user_id = ? where id = ?", sale.userId(), hidden);
		assertTrue(warnings(previewOk(ready.leadId())).contains("SAME_PHONE_LEAD"));
	}

	@Test
	void preview_conditionAndAmenitiesPerUnit() throws Exception {
		String address = unique("Trần Cao Vân");
		Passed passed = fixture.partsWithAmenity(leader, sale, "WHOLE_BUILDING", address, "BALCONY", 0, "P101", "P102");
		checked(passed, owner());
		createTarget(passed.leadId(), passed.spaceIds().getFirst());
		available(passed.leadId(), passed.spaceIds(), "VACANT");

		JsonNode units = previewOk(passed.leadId()).path("units");

		assertTrue(units.path(0).path("amenities").toString().contains("BALCONY"));
		assertFalse(units.path(1).path("amenities").toString().contains("BALCONY"));
		assertEquals(0, count("select count(*) from units u join properties p on p.id = u.property_id where p.street = ?", address));
	}

	@Test
	void preview_notCarried() throws Exception {
		Ready ready = previewable();

		JsonNode preview = previewOk(ready.leadId());

		assertEquals("[\"PHOTOS\",\"NOTES\",\"CONTACT\",\"TERMS\"]", preview.path("notCarried").toString());
	}

	@Test
	void preview_noTarget_409() throws Exception {
		Ready ready = readyHouse();
		available(ready.leadId(), ready.passed().spaceIds(), "VACANT");

		assertRefused(409, "INTAKE_STATE", preview(sale, ready.leadId()));
	}

	@Test
	void preview_noAvailability_409() throws Exception {
		Ready ready = readyHouse();
		createTarget(ready.leadId(), null);

		assertRefused(409, "INTAKE_STATE", preview(sale, ready.leadId()));
	}

	@Test
	void preview_writesAccessLog() throws Exception {
		Ready ready = previewable();

		previewOk(ready.leadId());

		assertEquals(1, count("select count(*) from inventory_access_logs where lead_id = ? and action = 'PREVIEW'",
				ready.leadId()));
	}

	@Test
	void preview_notAssignee_403() throws Exception {
		Ready ready = previewable();

		assertRefused(403, "INTAKE_NOT_ASSIGNEE", preview(leader, ready.leadId()));
	}
}
