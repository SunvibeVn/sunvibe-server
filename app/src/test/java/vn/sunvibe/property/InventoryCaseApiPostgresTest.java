package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
 * The duplicate gate and the manager's cases (property-create intake spec 4.10, S-21, S-22, S-28, S-30) through the real filter
 * chain, with the link rulings of 4.7 that need a real decision. The rows that need a completed winner run in
 * IntakeCompletionApiPostgresTest. Needs Docker.
 */
class InventoryCaseApiPostgresTest extends IntakeApiTestBase {

	private static final String WARD_NAME = "Hải Châu";

	@Override
	String phonePrefix() {
		return "0967";
	}

	private record Ready(Passed passed, TestUser owner, String address) {
		String leadId() {
			return passed.leadId();
		}
	}

	private Ready ready(String address) throws Exception {
		Passed passed = house(address);
		TestUser owner = owner();
		checked(passed, owner);
		return new Ready(passed, owner, address);
	}

	/** A property at this address, with an ownership row and one unit. */
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

	private String intakeOf(String leadId) {
		return (String) intakeRow(leadId).get("id");
	}

	private String caseOf(String leadId) {
		return jdbc.queryForObject("select c.id from inventory_duplicate_cases c join inventory_intakes i on"
				+ " i.id in (c.intake_id, c.candidate_intake_id) where i.lead_id = ? order by c.created_at desc, c.id desc limit 1",
				String.class, leadId);
	}

	private int casesOf(String leadId) {
		return count("select count(*) from inventory_duplicate_cases c join inventory_intakes i on"
				+ " i.id in (c.intake_id, c.candidate_intake_id) where i.lead_id = ?", leadId);
	}

	private long versionOfCase(String caseId) {
		return jdbc.queryForObject("select version from inventory_duplicate_cases where id = ?", Long.class, caseId);
	}

	private Result decide(TestUser caller, String caseId, String outcome, String proceedingOrNull, String basis) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("outcome", outcome);
		body.put("proceedingIntakeId", proceedingOrNull);
		body.put("basis", basis);
		body.put("expectedVersion", versionOfCase(caseId));
		return api.post(caller, CASES + "/" + caseId + "/decision", body);
	}

	private void decided(String caseId, String outcome, String proceedingOrNull) throws Exception {
		SurveyApiTestSupport.ok(decide(manager, caseId, outcome, proceedingOrNull, "Checked both files"));
	}

	private String status(String leadId) throws Exception {
		return blockOk(sale, leadId).path("status").asString();
	}

	private String caseState(String leadId) throws Exception {
		return blockOk(sale, leadId).path("caseState").asString();
	}

	/** Every page of the list, because the context is shared and the first page need not hold the case. */
	private List<String> listed(TestUser caller, String query) throws Exception {
		List<String> ids = new java.util.ArrayList<>();
		String separator = query.isEmpty() ? "?" : query + "&";
		for (int page = 0; ; page++) {
			Result answer = api.get(caller, CASES + separator + "size=50&page=" + page);
			assertEquals(200, answer.status(), answer.rawBody());
			if (answer.body().path("items").isEmpty()) {
				return ids;
			}
			answer.body().path("items").forEach(item -> ids.add(item.path("caseId").asString()));
		}
	}

	/** A new owner, confirmed and checked on the same selection of a lead back at DRAFT. */
	private TestUser reproved(Ready ready) throws Exception {
		TestUser owner = owner();
		reproved(ready, owner);
		return owner;
	}

	private void reproved(Ready ready, TestUser owner) throws Exception {
		Issued issued = issued(ready.leadId(), address(owner));
		confirmed(owner, issued);
		assertEquals(204, enter(owner, issued.requestId(), started(manager, ready.leadId())).status());
	}

	private void exitToDraft(Ready ready) throws Exception {
		selected(ready.leadId(), ready.passed().spaceIds());
	}

	// ==================== the signal (S-21) ====================

	@Test
	void signal_sameText() throws Exception {
		String address = unique("Bạch Đằng");
		Ready ready = ready(address);
		seeded(address.toUpperCase(java.util.Locale.ROOT), owner().userId());

		Result answer = createTarget(ready.leadId(), null);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("CASE_OPEN", answer.body().path("status").asString());
		assertEquals("OPEN", answer.body().path("caseState").asString());
		assertEquals("ADDRESS", jdbc.queryForObject("select signal from inventory_duplicate_cases where id = ?", String.class,
				caseOf(ready.leadId())));
	}

	@Test
	void signal_pinInAndOut() throws Exception {
		BigDecimal lat = new BigDecimal("16.0600000");
		BigDecimal lon = new BigDecimal("108.2200000");
		Passed passed = fixture.whole(leader, sale, "HOUSE", unique("Pin Road"), lat, lon);
		checked(passed, owner());
		String far = seeded(unique("Far Road"), owner().userId());
		jdbc.update("update properties set latitude = 16.0610000, longitude = 108.2200000 where id = ?", far);

		assertEquals("OWNER_CONFIRMED", createTarget(passed.leadId(), null).body().path("status").asString());

		String near = seeded(unique("Near Road"), owner().userId());
		jdbc.update("update properties set latitude = 16.0602000, longitude = 108.2200000 where id = ?", near);

		assertEquals("CASE_OPEN", createTarget(passed.leadId(), null).body().path("status").asString());
		assertEquals("PIN", jdbc.queryForObject("select signal from inventory_duplicate_cases where id = ?", String.class,
				caseOf(passed.leadId())));
	}

	/** Example 4: two flats of one building share the place, not the code. */
	@Test
	void signal_a1205A1206_notFlagged() throws Exception {
		String address = unique("Sky Tower");
		Passed passed = fixture.whole(leader, sale, "APARTMENT", address);
		checked(passed, owner());
		String other = property(owner().userId(), WARD, WARD_NAME, address, "APARTMENT", AddressNormalizer.normalize(address));
		unit(other, "A1206", "AVAILABLE");

		assertEquals("OWNER_CONFIRMED", createTarget(passed.leadId(), null).body().path("status").asString());
		unit(other, "a-1", "AVAILABLE");
		assertEquals("CASE_OPEN", createTarget(passed.leadId(), null).body().path("status").asString());
	}

	@Test
	void signal_p103SameOwner_flagged() throws Exception {
		String address = unique("Tòa nhà Một");
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", address, "P103");
		TestUser owner = owner();
		checked(passed, owner);
		String mine = property(owner.userId(), WARD, WARD_NAME, address, "WHOLE_BUILDING", AddressNormalizer.normalize(address));
		unit(mine, "P101", "AVAILABLE");

		assertEquals("CASE_OPEN", createTarget(passed.leadId(), passed.spaceIds().getFirst()).body().path("status").asString());
	}

	@Test
	void signal_p103OtherOwner_flagged() throws Exception {
		String address = unique("Tòa nhà Hai");
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", address, "P103");
		checked(passed, owner());
		String other = property(owner().userId(), WARD, WARD_NAME, address, "WHOLE_BUILDING", AddressNormalizer.normalize(address));
		unit(other, "P101", "AVAILABLE");

		assertEquals("CASE_OPEN", createTarget(passed.leadId(), passed.spaceIds().getFirst()).body().path("status").asString());
	}

	@Test
	void signal_legacyByWardName_noAssetType() throws Exception {
		String address = unique("Legacy Street");
		Ready ready = ready(address);
		property(owner().userId(), null, WARD_NAME, address, null, null);

		assertEquals("CASE_OPEN", createTarget(ready.leadId(), null).body().path("status").asString());
	}

	@Test
	void signal_closedCompetitor_notLive() throws Exception {
		String address = unique("Hai Bà Trưng");
		Ready competitor = ready(address);
		Ready ready = ready(address);

		close(competitor.leadId());

		assertEquals("OWNER_CONFIRMED", createTarget(ready.leadId(), null).body().path("status").asString());
		assertEquals(0, casesOf(ready.leadId()));
	}

	@Test
	void signal_competitorWithNoProof_notLive() throws Exception {
		String address = unique("Hai Tháng Chín");
		Passed draft = house(address);
		selected(draft.leadId(), draft.spaceIds());
		Ready ready = ready(address);

		assertEquals("OWNER_CONFIRMED", createTarget(ready.leadId(), null).body().path("status").asString());
	}

	/** Review focus 2: a legacy row with an unknown code or status fails nothing, because the signal reads scalars. */
	@Test
	void signal_oddLegacyRow_noFailure() throws Exception {
		String address = unique("Odd Street");
		Ready ready = ready(address);
		String odd = property(owner().userId(), WARD, WARD_NAME, unique("Odd Other"), "NOT_A_TYPE", null);
		jdbc.update("update properties set status = 'ODD', property_type = 'ODD' where id = ?", odd);

		Result answer = createTarget(ready.leadId(), null);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("OWNER_CONFIRMED", answer.body().path("status").asString());
	}

	@Test
	void signal_otherWard_notFlagged() throws Exception {
		String address = unique("Ward Street");
		Ready ready = ready(address);
		property(owner().userId(), "99999", "Elsewhere", address, "HOUSE", AddressNormalizer.normalize(address));

		assertEquals("OWNER_CONFIRMED", createTarget(ready.leadId(), null).body().path("status").asString());
	}

	// ==================== link place (S-11) ====================

	@Test
	void link_placeDiffers_opensCase() throws Exception {
		Ready ready = ready(unique("Link Street"));
		String id = seeded(unique("Another Street"), ready.owner().userId());

		Result answer = linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unitOf(id)));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("CASE_OPEN", answer.body().path("status").asString());
		assertEquals("LINK_PLACE", jdbc.queryForObject("select signal from inventory_duplicate_cases where id = ?", String.class,
				caseOf(ready.leadId())));
	}

	@Test
	void link_samePlace_opensNoCase() throws Exception {
		String address = unique("Same Street");
		Ready ready = ready(address);
		String id = seeded(address, ready.owner().userId());

		assertEquals("OWNER_CONFIRMED", linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unitOf(id))).body()
				.path("status").asString());
		assertEquals(0, casesOf(ready.leadId()));
	}

	@Test
	void linkPlace_decisions() throws Exception {
		Ready same = ready(unique("Link Same"));
		String mine = seeded(unique("Link Same Other"), same.owner().userId());
		linkTarget(same.leadId(), mine, same.passed().spaceIds(), List.of(unitOf(mine)));

		decided(caseOf(same.leadId()), "SAME_ASSET", null);

		JsonNode block = blockOk(sale, same.leadId());
		assertEquals("OWNER_CONFIRMED", block.path("status").asString());
		assertEquals("LINK", block.path("target").path("mode").asString(), "the link goes on");

		Ready different = ready(unique("Link Different"));
		String other = seeded(unique("Link Different Other"), different.owner().userId());
		linkTarget(different.leadId(), other, different.passed().spaceIds(), List.of(unitOf(other)));

		decided(caseOf(different.leadId()), "DIFFERENT_ASSET", null);

		block = blockOk(sale, different.leadId());
		assertEquals("OWNER_CONFIRMED", block.path("status").asString());
		assertTrue(block.path("target").isNull(), "the ruling clears the link");
	}

	@Test
	void link_differentAssetRuling_binds() throws Exception {
		Ready ready = ready(unique("Ruling Street"));
		String id = seeded(unique("Ruling Other"), ready.owner().userId());
		linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unitOf(id)));
		decided(caseOf(ready.leadId()), "DIFFERENT_ASSET", null);

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unitOf(id))));
	}

	@Test
	void linkPlace_notADispute() throws Exception {
		Ready first = ready(unique("Dispute One"));
		String id = seeded(unique("Dispute Place"), first.owner().userId());
		linkTarget(first.leadId(), id, first.passed().spaceIds(), List.of(unitOf(id)));
		assertEquals("OPEN", caseState(first.leadId()));

		String sameAddress = jdbc.queryForObject("select street from properties where id = ?", String.class, id);
		Passed second = house(sameAddress);
		checked(second, first.owner());

		assertEquals(200, linkTarget(second.leadId(), id, second.spaceIds(), List.of(unitOf(id))).status());
	}

	// ==================== dispute and forced property (S-11, S-22) ====================

	@Test
	void link_disputed_409() throws Exception {
		String address = unique("Disputed Street");
		TestUser owner = owner();
		String id = seeded(address, owner.userId());
		Ready other = ready(address);
		createTarget(other.leadId(), null);
		assertEquals("CASE_OPEN", status(other.leadId()));

		Passed passed = house(address);
		checked(passed, owner);

		assertRefused(409, "INTAKE_DISPUTED", linkTarget(passed.leadId(), id, passed.spaceIds(), List.of(unitOf(id))));
	}

	@Test
	void sameUser_clearsCreateAndForces() throws Exception {
		String address = unique("Forced Street");
		Ready ready = ready(address);
		String id = seeded(address, ready.owner().userId());
		createTarget(ready.leadId(), null);

		decided(caseOf(ready.leadId()), "SAME_ASSET", null);

		JsonNode block = blockOk(sale, ready.leadId());
		assertEquals("OWNER_CONFIRMED", block.path("status").asString());
		assertTrue(block.path("target").isNull(), "the CREATE target is gone");
		assertEquals(jdbc.queryForObject("select property_code from properties where id = ?", String.class, id),
				block.path("forcedPropertyCode").asString());
		assertEquals("DECIDED", block.path("caseState").asString());
	}

	@Test
	void create_forcedLink_409() throws Exception {
		String address = unique("Forced Create");
		Ready ready = ready(address);
		seeded(address, ready.owner().userId());
		createTarget(ready.leadId(), null);
		decided(caseOf(ready.leadId()), "SAME_ASSET", null);

		assertRefused(409, "INTAKE_CREATE_NOT_ALLOWED", createTarget(ready.leadId(), null));
	}

	@Test
	void link_forcedProperty_binds() throws Exception {
		String address = unique("Forced Link");
		Ready ready = ready(address);
		String forced = seeded(address, ready.owner().userId());
		String other = seeded(address + " B", ready.owner().userId());
		createTarget(ready.leadId(), null);
		decided(caseOf(ready.leadId()), "SAME_ASSET", null);

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(ready.leadId(), other, ready.passed().spaceIds(), List.of(unitOf(other))));
		assertEquals(200, linkTarget(ready.leadId(), forced, ready.passed().spaceIds(), List.of(unitOf(forced))).status());
	}

	@Test
	void otherUser_holdsAgainAfterExit() throws Exception {
		String address = unique("Held Street");
		Ready ready = ready(address);
		seeded(address, owner().userId());
		createTarget(ready.leadId(), null);

		decided(caseOf(ready.leadId()), "SAME_ASSET", null);
		assertEquals("CASE_OPEN", status(ready.leadId()));
		assertEquals("WAITING", caseState(ready.leadId()));

		exitToDraft(ready);
		assertEquals("DRAFT", status(ready.leadId()));
		reproved(ready, ready.owner());

		assertEquals("CASE_OPEN", status(ready.leadId()), "the hold returns at the new proof's DONE");
		assertEquals("WAITING", caseState(ready.leadId()));
	}

	@Test
	void secondSameUserSameAsset_409() throws Exception {
		String address = unique("Two Forced");
		Ready ready = ready(address);
		seeded(address, ready.owner().userId());
		seeded(address, ready.owner().userId());
		createTarget(ready.leadId(), null);
		List<String> both = jdbc.queryForList("select id from inventory_duplicate_cases where intake_id = ? order by id",
				String.class, intakeOf(ready.leadId()));
		assertEquals(2, both.size());

		decided(both.get(0), "SAME_ASSET", null);

		assertRefused(409, "INTAKE_STATE", decide(manager, both.get(1), "SAME_ASSET", null, "Same again"));
	}

	@Test
	void differentAsset_survivesReselectAndNewProof() throws Exception {
		String address = unique("Survives Street");
		Ready ready = ready(address);
		seeded(address, owner().userId());
		createTarget(ready.leadId(), null);
		decided(caseOf(ready.leadId()), "DIFFERENT_ASSET", null);
		assertEquals("OWNER_CONFIRMED", status(ready.leadId()));

		exitToDraft(ready);
		reproved(ready);
		int before = casesOf(ready.leadId());

		assertEquals("OWNER_CONFIRMED", createTarget(ready.leadId(), null).body().path("status").asString());
		assertEquals(before, casesOf(ready.leadId()));
	}

	/** Who owns what is read each time: a later checked proof by another account gets the other row of the table. */
	@Test
	void sameAsset_laterCheckedProofOtherAccount_flipsForcedAndHeld() throws Exception {
		String address = unique("Flip Street");
		Ready ready = ready(address);
		String id = seeded(address, ready.owner().userId());
		createTarget(ready.leadId(), null);
		decided(caseOf(ready.leadId()), "SAME_ASSET", null);
		assertFalse(blockOk(sale, ready.leadId()).path("forcedPropertyCode").isNull());

		exitToDraft(ready);
		reproved(ready);

		JsonNode block = blockOk(sale, ready.leadId());
		assertEquals("CASE_OPEN", block.path("status").asString());
		assertEquals("WAITING", block.path("caseState").asString());
		assertTrue(block.path("forcedPropertyCode").isNull());
		assertRefused(409, "INTAKE_STATE", linkTarget(ready.leadId(), id, ready.passed().spaceIds(), List.of(unitOf(id))));
	}

	@Test
	void flipToHeld_bothTargetModes_409_onPAndL() throws Exception {
		Ready onLink = ready(unique("Flip Link"));
		String id = seeded(unique("Flip Link Other"), onLink.owner().userId());
		linkTarget(onLink.leadId(), id, onLink.passed().spaceIds(), List.of(unitOf(id)));
		decided(caseOf(onLink.leadId()), "SAME_ASSET", null);
		exitToDraft(onLink);
		reproved(onLink);

		assertEquals("CASE_OPEN", status(onLink.leadId()));
		assertRefused(409, "INTAKE_STATE", createTarget(onLink.leadId(), null));
	}

	// ==================== two intakes (S-22) ====================

	private record Pair(Ready first, Ready second, String caseId) {
	}

	private Pair competing(String text) throws Exception {
		String address = unique(text);
		Ready first = ready(address);
		Ready second = ready(address);
		createTarget(first.leadId(), null);
		assertEquals("CASE_OPEN", status(first.leadId()));
		return new Pair(first, second, caseOf(first.leadId()));
	}

	@Test
	void decision_eachRow_intakePair() throws Exception {
		Pair pair = competing("Pair Street");
		String winner = intakeOf(pair.first().leadId());

		decided(pair.caseId(), "SAME_ASSET", winner);

		assertEquals("OWNER_CONFIRMED", status(pair.first().leadId()));
		assertEquals("CASE_OPEN", status(pair.second().leadId()));
		assertEquals("WAITING", caseState(pair.second().leadId()));
	}

	@Test
	void decision_proceedingIntakeRules() throws Exception {
		Pair pair = competing("Rule Street");

		assertRefused(400, "VALIDATION_FAILED", decide(manager, pair.caseId(), "SAME_ASSET", null, "Checked"));
		assertRefused(400, "VALIDATION_FAILED", decide(manager, pair.caseId(), "SAME_ASSET", "01AAAAAAAAAAAAAAAAAAAAAAAA", "Checked"));
		assertRefused(400, "VALIDATION_FAILED", decide(manager, pair.caseId(), "DIFFERENT_ASSET", intakeOf(pair.first().leadId()),
				"Checked"));

		String address = unique("Rule Property");
		Ready onProperty = ready(address);
		seeded(address, owner().userId());
		createTarget(onProperty.leadId(), null);
		assertRefused(400, "VALIDATION_FAILED", decide(manager, caseOf(onProperty.leadId()), "SAME_ASSET",
				intakeOf(onProperty.leadId()), "Checked"));
	}

	@Test
	void winnerAbandoned_loserReleased() throws Exception {
		Pair pair = competing("Abandon Street");
		decided(pair.caseId(), "SAME_ASSET", intakeOf(pair.first().leadId()));
		assertEquals("CASE_OPEN", status(pair.second().leadId()));

		close(pair.first().leadId());

		assertEquals("OWNER_CONFIRMED", status(pair.second().leadId()));
	}

	@Test
	void candidateLeadClosed_readsConfirmed() throws Exception {
		Pair pair = competing("Close Street");

		close(pair.second().leadId());

		assertEquals("OWNER_CONFIRMED", status(pair.first().leadId()));
	}

	/** A winner that proves again holds the loser again, checked or not. */
	@Test
	void winnerProvesAgain_holdsLoser() throws Exception {
		Pair pair = competing("Prove Street");
		String winner = intakeOf(pair.first().leadId());
		decided(pair.caseId(), "SAME_ASSET", winner);
		assertEquals("CASE_OPEN", status(pair.second().leadId()));

		jdbc.update("update users set user_status = 'LOCK' where id = ?", pair.first().owner().userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", pair.first().owner().userId());
		assertEquals("OWNER_CONFIRMED", status(pair.second().leadId()), "the winner's proof is dead, so the loser goes on");

		Issued again = issued(pair.first().leadId(), address(pair.first().owner()));
		confirmed(pair.first().owner(), again);

		assertEquals("CASE_OPEN", status(pair.second().leadId()));
	}

	@Test
	void lookup_readsEitherSideOfIPair() throws Exception {
		Pair pair = competing("Lookup Street");

		assertEquals("OPEN", caseState(pair.first().leadId()));
		assertEquals("OPEN", caseState(pair.second().leadId()));

		assertEquals("CASE_OPEN", createTarget(pair.second().leadId(), null).body().path("status").asString());
		assertEquals(1, casesOf(pair.second().leadId()), "the pair key names one case from either side");
	}

	@Test
	void exit_closesUndecidedOnEitherSide() throws Exception {
		Pair pair = competing("Exit Street");

		exitToDraft(pair.second());

		assertEquals("CLOSED", jdbc.queryForObject("select status from inventory_duplicate_cases where id = ?", String.class,
				pair.caseId()));
		assertEquals("OWNER_CONFIRMED", status(pair.first().leadId()));
	}

	@Test
	void closedThenSameScope_opensNewCase() throws Exception {
		String address = unique("Reopen Street");
		Ready ready = ready(address);
		seeded(address, owner().userId());
		createTarget(ready.leadId(), null);
		String first = caseOf(ready.leadId());

		exitToDraft(ready);
		reproved(ready, ready.owner());
		assertEquals("CASE_OPEN", createTarget(ready.leadId(), null).body().path("status").asString());

		assertEquals(2, casesOf(ready.leadId()));
		assertFalse(first.equals(caseOf(ready.leadId())));
	}

	@Test
	void decidedPair_sameScope_noReopen() throws Exception {
		String address = unique("No Reopen");
		Ready ready = ready(address);
		seeded(address, owner().userId());
		createTarget(ready.leadId(), null);
		decided(caseOf(ready.leadId()), "DIFFERENT_ASSET", null);

		assertEquals("OWNER_CONFIRMED", createTarget(ready.leadId(), null).body().path("status").asString());
		assertEquals(1, casesOf(ready.leadId()));
	}

	@Test
	void decidedPair_newScope_reopens() throws Exception {
		String address = unique("New Scope");
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", address, "P1", "P2");
		TestUser owner = owner();
		selected(passed.leadId(), passed.spaceIds().subList(0, 1));
		Issued issued = issued(passed.leadId(), address(owner));
		confirmed(owner, issued);
		assertEquals(204, enter(owner, issued.requestId(), started(manager, passed.leadId())).status());
		String other = property(owner().userId(), WARD, WARD_NAME, address, "WHOLE_BUILDING", AddressNormalizer.normalize(address));
		unit(other, "U1", "AVAILABLE");
		createTarget(passed.leadId(), passed.spaceIds().getFirst());
		decided(caseOf(passed.leadId()), "DIFFERENT_ASSET", null);

		checked(passed, owner);

		assertEquals("CASE_OPEN", createTarget(passed.leadId(), passed.spaceIds().getFirst()).body().path("status").asString());
		assertEquals(2, casesOf(passed.leadId()));
	}

	@Test
	void deadSide_readsClosedForListDecisionDispute() throws Exception {
		Pair pair = competing("Dead Side");
		assertTrue(listed(manager, "").contains(pair.caseId()));

		close(pair.second().leadId());

		assertFalse(listed(manager, "").contains(pair.caseId()));
		assertRefused(409, "INTAKE_STATE", decide(manager, pair.caseId(), "DIFFERENT_ASSET", null, "Checked"));
	}

	// ==================== list, read and decision (S-22, S-28, S-30) ====================

	@Test
	void decision_notOpen_409() throws Exception {
		Pair pair = competing("Not Open");
		decided(pair.caseId(), "DIFFERENT_ASSET", null);

		assertRefused(409, "INTAKE_STATE", decide(manager, pair.caseId(), "DIFFERENT_ASSET", null, "Again"));
	}

	@Test
	void decision_staleVersion_409() throws Exception {
		Pair pair = competing("Stale Case");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("outcome", "DIFFERENT_ASSET");
		body.put("basis", "Checked");
		body.put("expectedVersion", versionOfCase(pair.caseId()) + 1);

		assertRefused(409, "CONCURRENT_MODIFICATION", api.post(manager, CASES + "/" + pair.caseId() + "/decision", body));
	}

	@Test
	void list_statusFilter() throws Exception {
		Pair open = competing("List Open");
		Pair done = competing("List Done");
		decided(done.caseId(), "DIFFERENT_ASSET", null);

		assertTrue(listed(manager, "").contains(open.caseId()));
		assertFalse(listed(manager, "").contains(done.caseId()));
		assertTrue(listed(manager, "?status=DECIDED").contains(done.caseId()));
		assertFalse(listed(manager, "?status=DECIDED").contains(open.caseId()));
		assertEquals(400, api.get(manager, CASES + "?status=CLOSED").status());
	}

	@Test
	void list_shape_pageBounds() throws Exception {
		competing("List Shape");

		Result answer = api.get(manager, CASES + "?size=50");

		assertEquals(Set.of("items", "page", "size", "totalElements"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(answer.body())));
		assertEquals(Set.of("caseId", "leadCode", "wardName", "createdAt"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(answer.body().path("items").path(0))));
		assertEquals(400, api.get(manager, CASES + "?size=51").status());
	}

	@Test
	void list_filtersWhoWorkedEitherLead_totalAfterFilter() throws Exception {
		TestUser worker = survey.user("Case Worker", "SALE_LEADER");
		Pair pair = competing("Worked Street");
		int before = api.get(worker, CASES).body().path("totalElements").asInt();
		assertTrue(listed(worker, "").contains(pair.caseId()));

		fixture.checklists().reassign(leader, pair.second().leadId(), worker);

		assertFalse(listed(worker, "").contains(pair.caseId()));
		assertEquals(before - 1, api.get(worker, CASES).body().path("totalElements").asInt());
	}

	@Test
	void read_workedEitherLead_403() throws Exception {
		TestUser worker = survey.user("Read Worker", "SALE_LEADER");
		Pair pair = competing("Read Worked");
		fixture.checklists().reassign(leader, pair.first().leadId(), worker);

		assertRefused(403, "CASE_NOT_ALLOWED", api.get(worker, CASES + "/" + pair.caseId()));
	}

	@Test
	void decision_workedEitherLead_403() throws Exception {
		TestUser worker = survey.user("Decide Worker", "SALE_LEADER");
		Pair pair = competing("Decide Worked");
		fixture.checklists().reassign(leader, pair.second().leadId(), worker);

		assertRefused(403, "CASE_NOT_ALLOWED", decide(worker, pair.caseId(), "DIFFERENT_ASSET", null, "Checked"));
	}

	@Test
	void read_shape_writesLog() throws Exception {
		String address = unique("Read Street");
		Ready ready = ready(address);
		String id = seeded(address, ready.owner().userId());
		createTarget(ready.leadId(), null);
		String caseId = caseOf(ready.leadId());

		Result answer = api.get(manager, CASES + "/" + caseId);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(Set.of("caseId", "status", "signal", "outcome", "basis", "decidedAt", "intake", "candidate", "version"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(answer.body())));
		JsonNode candidate = answer.body().path("candidate");
		assertEquals("PROPERTY", candidate.path("kind").asString());
		assertTrue(candidate.path("intakeId").isNull());
		assertEquals(jdbc.queryForObject("select property_code from properties where id = ?", String.class, id),
				candidate.path("code").asString());
		assertEquals(address, candidate.path("boundary").path("addressText").asString());
		assertEquals("U1", candidate.path("units").path(0).path("code").asString());
		assertTrue(candidate.path("ownerMatches").asBoolean());
		assertEquals("A1", answer.body().path("intake").path("parts").path(0).path("code").asString());
		assertEquals(1, count("select count(*) from inventory_access_logs where case_id = ? and action = 'CASE_READ'", caseId));
	}

	@Test
	void read_intakeCandidate_shape() throws Exception {
		Pair pair = competing("Read Pair");

		JsonNode candidate = api.get(manager, CASES + "/" + pair.caseId()).body().path("candidate");

		assertEquals("INTAKE", candidate.path("kind").asString());
		assertEquals(intakeOf(pair.second().leadId()), candidate.path("intakeId").asString());
		assertFalse(candidate.path("ownerMatches").asBoolean());
		assertEquals("A1", candidate.path("units").path(0).path("code").asString());
	}

	/** A value that is no number is the mistake of the caller: 400, not a 500 with the value in the error log. */
	@Test
	void list_notANumber_400() throws Exception {
		assertRefused(400, "VALIDATION_FAILED", api.get(manager, CASES + "?page=abc"));
		assertRefused(400, "VALIDATION_FAILED", api.get(manager, CASES + "?size=abc"));
	}

	@Test
	void read_unknown_404() throws Exception {
		assertRefused(404, "NOT_FOUND", api.get(manager, CASES + "/01AAAAAAAAAAAAAAAAAAAAAAAA"));
	}

	@Test
	void sale_seesNoOtherOwner() throws Exception {
		String address = unique("Sale Street");
		Ready ready = ready(address);
		TestUser other = owner();
		seeded(address, other.userId());

		Result answer = createTarget(ready.leadId(), null);

		assertFalse(answer.rawBody().contains(other.userId()));
		assertFalse(block(sale, ready.leadId()).rawBody().contains(other.userId()));
	}

	@Test
	void case_saleRefused() throws Exception {
		Pair pair = competing("Sale Refused");

		assertEquals(403, api.get(sale, CASES).status());
		assertEquals(403, api.get(sale, CASES + "/" + pair.caseId()).status());
		assertEquals(403, decide(sale, pair.caseId(), "DIFFERENT_ASSET", null, "Checked").status());
	}

	@Test
	void text_phoneOrNul_400() throws Exception {
		Pair pair = competing("Text Street");

		for (String bad : List.of("Call 0905 123 456", "A\u0000B", "  ")) {
			Result answer = decide(manager, pair.caseId(), "DIFFERENT_ASSET", null, bad);
			assertRefused(400, "VALIDATION_FAILED", answer);
			assertEquals("basis", answer.body().path("errors").path(0).path("field").asString());
		}
	}
}
