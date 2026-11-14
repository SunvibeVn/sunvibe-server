package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.domain.policy.intake.AddressNormalizer;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * What the selection command keeps doing around property-create SV-202 (spec SS-7, SS-8; impact map U-2, U-4): a different
 * set of parts still voids the proof, closes the undecided case and clears the target and availability (property-create
 * intake spec 4.2, S-2), and the gate and the assignee check still refuse the same parts on an existing row first. Pins
 * written before the change; they pass on the code before it. Needs Docker.
 */
class IntakeSelectionUnchangedApiPostgresTest extends IntakeApiTestBase {

	private static final String WARD_NAME = "Hải Châu";

	@Override
	String phonePrefix() {
		return "0956";
	}

	// ==================== fixtures ====================

	private int selectionEvents(String leadId) {
		return count("select count(*) from inventory_intake_events e join inventory_intakes i on i.id = e.intake_id"
				+ " where i.lead_id = ? and e.action = 'SELECTION'", leadId);
	}

	/** A property at this address, with an ownership row and one unit; answers its id. */
	private String seeded(String address, String ownerId, String assetType) {
		String id = property(ownerId, WARD, WARD_NAME, address, assetType, AddressNormalizer.normalize(address));
		ownership(id, ownerId);
		unit(id, "U1", "AVAILABLE");
		return id;
	}

	private String openCaseOf(String leadId) {
		return jdbc.queryForObject("select c.id from inventory_duplicate_cases c join inventory_intakes i on i.id = c.intake_id"
				+ " where i.lead_id = ? and c.status = 'OPEN'", String.class, leadId);
	}

	// ==================== a different set (SS-7) ====================

	@Test
	void differentSelection_checkedProof_voidsAndClearsAll() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Other Part"), "P101", "P102", "P103");
		String lead = passed.leadId();
		String a = passed.spaceIds().get(0);
		String b = passed.spaceIds().get(1);
		String c = passed.spaceIds().get(2);
		TestUser owner = owner();
		Issued issued = proved(lead, List.of(a, b), owner);
		assertEquals(204, enter(owner, issued.requestId(), started(manager, lead)).status());
		SurveyApiTestSupport.ok(createTarget(lead, a));
		available(lead, List.of(a, b), "VACANT");
		String proof = proofIdOf(lead);
		Long version = versionOf(sale, lead);

		JsonNode block = selected(lead, List.of(a, c));

		assertEquals("DRAFT", block.path("status").asString());
		assertEquals(version + 1, block.path("version").asLong());
		assertTrue(block.path("target").isNull(), block.toString());
		assertTrue(block.path("availability").isArray(), block.toString());
		assertEquals(0, block.path("availability").size(), block.toString());
		assertNotNull(jdbc.queryForObject("select voided_at from inventory_owner_proofs where id = ?", Object.class, proof));
		Map<String, Object> row = intakeRow(lead);
		assertEquals(null, row.get("target_mode"));
		assertEquals(null, row.get("availability"));
		assertEquals(2, selectionEvents(lead));
	}

	@Test
	void differentSelection_openCase_closesIt() throws Exception {
		String address = unique("Case Part");
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", address, "P101", "P102");
		String lead = passed.leadId();
		String a = passed.spaceIds().get(0);
		seeded(address, owner().userId(), "WHOLE_BUILDING");
		checked(passed, owner());
		assertEquals("CASE_OPEN", createTarget(lead, a).body().path("status").asString());
		String caseId = openCaseOf(lead);

		JsonNode block = selected(lead, List.of(a));

		assertEquals("DRAFT", block.path("status").asString());
		assertEquals("CLOSED", jdbc.queryForObject("select status from inventory_duplicate_cases where id = ?", String.class,
				caseId));
	}

	// ==================== the checks come first (SS-8) ====================

	@Test
	void sameSelection_heldLead_gateFailed() throws Exception {
		Passed passed = house(unique("Held Same"));
		String lead = passed.leadId();
		selected(lead, passed.spaceIds());
		Long version = versionOf(sale, lead);
		Map<String, Object> before = intakeRow(lead);
		hold(lead);

		assertRefused(409, "INTAKE_GATE_FAILED", select(sale, lead, passed.spaceIds(), version));
		assertEquals(before, intakeRow(lead));
	}

	@Test
	void sameSelection_notAssignee_refused() throws Exception {
		Passed passed = house(unique("Not Mine Same"));
		String lead = passed.leadId();
		selected(lead, passed.spaceIds());
		Long version = versionOf(sale, lead);
		Map<String, Object> before = intakeRow(lead);

		assertRefused(403, "INTAKE_NOT_ASSIGNEE", select(manager, lead, passed.spaceIds(), version));
		assertRefused(404, "NOT_FOUND", select(otherSale, lead, passed.spaceIds(), version));
		assertEquals(before, intakeRow(lead));
	}
}
