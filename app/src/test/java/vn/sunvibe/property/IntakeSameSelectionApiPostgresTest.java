package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.domain.policy.intake.AddressNormalizer;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The same parts saved again change nothing (property-create SV-202, spec SS-1 to SS-6): the owner's request or proof, the
 * manager's check, the duplicate case, the target and the availability stay, the version does not move and no SELECTION
 * event is written; a state the read computed is stored as by any accepted command. Needs Docker.
 */
class IntakeSameSelectionApiPostgresTest extends IntakeApiTestBase {

	private static final String WARD_NAME = "Hải Châu";

	@Override
	String phonePrefix() {
		return "0957";
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

	private Object requestVoidedAt(String requestId) {
		return jdbc.queryForObject("select voided_at from inventory_owner_requests where id = ?", Object.class, requestId);
	}

	// ==================== SS-1 to SS-6 ====================

	@Test
	void sameSelection_ownerConfirmed_keepsProofTargetAndAvailability() throws Exception {
		Passed passed = house(unique("Same Parts"));
		String lead = passed.leadId();
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(lead, null));
		available(lead, passed.spaceIds(), "VACANT");
		JsonNode before = blockOk(sale, lead);
		Map<String, Object> row = intakeRow(lead);
		String proof = proofIdOf(lead);

		JsonNode block = selected(lead, passed.spaceIds());

		assertEquals("OWNER_CONFIRMED", block.path("status").asString(), block.toString());
		assertEquals(before.path("version").asLong(), block.path("version").asLong());
		assertEquals("CONFIRMED", block.path("ownerState").asString());
		assertEquals("DONE", block.path("ownerCheck").asString());
		assertEquals(before.path("target"), block.path("target"));
		assertEquals(before.path("availability"), block.path("availability"));
		assertEquals(row, intakeRow(lead));
		assertEquals(proof, proofIdOf(lead));
		Map<String, Object> ends = jdbc.queryForMap("select voided_at, withdrawn_at from inventory_owner_proofs where id = ?",
				proof);
		assertNull(ends.get("voided_at"));
		assertNull(ends.get("withdrawn_at"));
		assertEquals(1, selectionEvents(lead));
		completed(lead);
	}

	@Test
	void sameSelection_awaitingOwner_keepsTheRequest() throws Exception {
		Passed passed = house(unique("Same Request"));
		String lead = passed.leadId();
		selected(lead, passed.spaceIds());
		TestUser owner = owner();
		Issued issued = issued(lead, address(owner));
		Long version = versionOf(sale, lead);

		Result answer = select(sale, lead, passed.spaceIds(), version);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("AWAITING_OWNER", answer.body().path("status").asString(), answer.rawBody());
		assertEquals(version, answer.body().path("version").asLong());
		assertNull(requestVoidedAt(issued.requestId()));
		assertEquals(1, selectionEvents(lead));
		confirmed(owner, issued);
		assertEquals("CONFIRMED", blockOk(sale, lead).path("ownerState").asString());
	}

	@Test
	void sameSelection_caseOpen_keepsTheCase() throws Exception {
		String address = unique("Same Case");
		Passed passed = house(address);
		String lead = passed.leadId();
		seeded(address, owner().userId(), "HOUSE");
		checked(passed, owner());
		assertEquals("CASE_OPEN", createTarget(lead, null).body().path("status").asString());
		String caseId = openCaseOf(lead);
		String proof = proofIdOf(lead);

		JsonNode block = selected(lead, passed.spaceIds());

		assertEquals("CASE_OPEN", block.path("status").asString(), block.toString());
		assertEquals("OPEN", block.path("caseState").asString());
		assertEquals("OPEN", jdbc.queryForObject("select status from inventory_duplicate_cases where id = ?", String.class,
				caseId));
		assertEquals(proof, proofIdOf(lead));
		assertEquals(1, selectionEvents(lead));
	}

	@Test
	void sameSelection_otherOrder_isTheSameScope() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Same Order"), "P101", "P102");
		String lead = passed.leadId();
		String a = passed.spaceIds().get(0);
		String b = passed.spaceIds().get(1);
		selected(lead, List.of(a, b));
		Issued issued = issued(lead, address(owner()));
		Long version = versionOf(sale, lead);

		Result answer = select(sale, lead, List.of(b, a), version);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("AWAITING_OWNER", answer.body().path("status").asString(), answer.rawBody());
		assertEquals(version, answer.body().path("version").asLong());
		JsonNode stored = answer.body().path("selection").path("spaceIds");
		assertEquals(List.of(a, b), List.of(stored.path(0).asString(), stored.path(1).asString()));
		assertEquals(2, stored.size());
		assertNull(requestVoidedAt(issued.requestId()));
		assertEquals(1, selectionEvents(lead));
	}

	@Test
	void sameSelection_draft_writesNothing() throws Exception {
		Passed passed = house(unique("Same Draft"));
		String lead = passed.leadId();
		selected(lead, passed.spaceIds());
		Map<String, Object> row = intakeRow(lead);

		JsonNode block = selected(lead, passed.spaceIds());

		assertEquals("DRAFT", block.path("status").asString());
		assertEquals(0L, block.path("version").asLong(), block.toString());
		assertEquals(row, intakeRow(lead), "updated_at included");
		assertEquals(1, selectionEvents(lead));
	}

	@Test
	void sameSelection_expiredRequest_storesTheComputedDraftOnly() throws Exception {
		Passed passed = house(unique("Same Expired"));
		String lead = passed.leadId();
		selected(lead, passed.spaceIds());
		Issued issued = issued(lead, address(owner()));
		jdbc.update("update inventory_owner_requests set expires_at = ? where id = ?",
				Timestamp.from(Instant.now().minusSeconds(60)), issued.requestId());
		JsonNode read = blockOk(sale, lead);
		assertEquals("DRAFT", read.path("status").asString());
		assertEquals("AWAITING_OWNER", intakeRow(lead).get("status"));
		long version = read.path("version").asLong();

		JsonNode block = selected(lead, passed.spaceIds());

		assertEquals("DRAFT", block.path("status").asString());
		assertEquals(version + 1, block.path("version").asLong(), block.toString());
		assertEquals("DRAFT", intakeRow(lead).get("status"));
		assertEquals(1, selectionEvents(lead));
	}
}
