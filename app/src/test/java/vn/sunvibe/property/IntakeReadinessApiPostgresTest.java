package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.InventoryIntakeReadinessUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Readiness, restoration and the re-survey (property-create intake spec 4.11, S-9, S-23, S-24, S-25, S-26) through the real filter
 * chain. Needs Docker.
 */
class IntakeReadinessApiPostgresTest extends IntakeApiTestBase {

	@Autowired
	private InventoryIntakeReadinessUseCase readiness;

	@Autowired
	private UnitIntakeUseCase unitIntakes;

	@Override
	String phonePrefix() {
		return "0969";
	}

	private record Done(Passed passed, TestUser owner, JsonNode result, Issued issued) {
		String leadId() {
			return passed.leadId();
		}
	}

	private Done done() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		TestUser owner = owner();
		Issued issued = checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return new Done(passed, owner, completed(passed.leadId()), issued);
	}

	private JsonNode readinessOf(TestUser caller, String leadId) throws Exception {
		return blockOk(caller, leadId).path("readiness");
	}

	private void assertReadiness(String leadId, String state, String reason) throws Exception {
		JsonNode read = readinessOf(leader, leadId);
		assertEquals(state, read.path("state").asString(), read.toString());
		assertTrue(read.path("reasons").toString().contains(reason), read.toString());
	}

	private Result restore(TestUser caller, String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", versionOf(leader, leadId));
		return api.post(caller, base(leadId) + "/restoration", body);
	}

	/** A new confirmation and check of a result by its owner, up to what restoration needs. */
	private void reproved(Done done) throws Exception {
		Issued again = issued(done.leadId(), address(done.owner()));
		confirmed(done.owner(), again);
		assertEquals(204, enter(done.owner(), again.requestId(), started(manager, done.leadId())).status());
	}

	private void withdrawn(Done done) throws Exception {
		assertEquals(204, withdraw(done.owner(), done.issued().requestId(), passwordOf(done.owner())).status());
	}

	private void lockAndUnlock(TestUser user) {
		jdbc.update("update users set user_status = 'LOCK' where id = ?", user.userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", user.userId());
	}

	private void resume(String leadId) {
		jdbc.update("update owner_leads set status = 'QUALIFIED', version = version + 1 where id = ?", leadId);
	}

	// ==================== readiness (S-23) ====================

	@Test
	void readiness_noResult_none() throws Exception {
		Passed passed = house(unique("Pasteur"));

		JsonNode read = readinessOf(sale, passed.leadId());

		assertEquals("NONE", read.path("state").asString());
		IntakeBlockView.Readiness published = readiness.readiness(passed.leadId());
		assertEquals("NONE", published.state());
	}

	@Test
	void readiness_usable_carriesSourceRevision() throws Exception {
		Done done = done();

		JsonNode read = readinessOf(sale, done.leadId());

		assertEquals("USABLE", read.path("state").asString());
		assertEquals(0, read.path("reasons").size());
		assertEquals(done.passed().checklistId(), read.path("sourceRevisionId").asString());
		assertTrue(read.path("units").path(0).path("usable").asBoolean());
		assertEquals(done.passed().checklistId(), readiness.readiness(done.leadId()).sourceRevisionId());
	}

	@Test
	void missingForBrokerage_fixed() throws Exception {
		Done done = done();

		assertEquals("[\"AGREEMENT\",\"ADVERTISING_PERMISSION\",\"CONFIRMED_TERMS\",\"PHOTO_RIGHTS\"]",
				readinessOf(sale, done.leadId()).path("missingForBrokerage").toString());
	}

	@Test
	void readiness_leadOnHold_suspended() throws Exception {
		Done done = done();

		hold(done.leadId());

		assertReadiness(done.leadId(), "SUSPENDED", "LEAD_ON_HOLD");
	}

	@Test
	void readiness_evidenceHeld_suspended() throws Exception {
		Done done = done();

		jdbc.update("update survey_media set held_at = now(), held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT' where id = ?",
				admin.userId(), done.passed().photoIds().getFirst());

		assertReadiness(done.leadId(), "SUSPENDED", "EVIDENCE_HELD");
	}

	@Test
	void readiness_ownerNotEligible_suspended() throws Exception {
		Done done = done();

		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'", done.owner().userId());

		assertReadiness(done.leadId(), "SUSPENDED", "OWNER_NOT_ELIGIBLE");
	}

	@Test
	void readiness_managerSuspension_restricted_noFreeText() throws Exception {
		Done done = done();

		suspend(manager, done.leadId(), null, "Between managers", versionOf(manager, done.leadId()));

		assertReadiness(done.leadId(), "SUSPENDED", "RESTRICTED");
		assertFalse(readinessOf(leader, done.leadId()).toString().contains("Between managers"));
	}

	@Test
	void readiness_leadClosed_ended_overSuspended() throws Exception {
		Done done = done();
		suspend(manager, done.leadId(), null, "A reason", versionOf(manager, done.leadId()));

		close(done.leadId());

		JsonNode read = readinessOf(leader, done.leadId());
		assertEquals("ENDED", read.path("state").asString());
		assertEquals("[\"WITHDRAWN\"]", read.path("reasons").toString());
	}

	@Test
	void readiness_afterMark_latestCompleted_endedRequalified() throws Exception {
		Done done = done();

		fixture.requalify(leader, done.passed().checklistId(), "OTHER");

		assertReadiness(done.leadId(), "ENDED", "REQUALIFIED");
		assertEquals(done.passed().checklistId(), readinessOf(leader, done.leadId()).path("sourceRevisionId").asString());
	}

	/** Pin (E05-S6b): a frozen photo of the basis's source lost its content, so the result has ended (spec 4.11). */
	@Test
	void readiness_evidenceGone_ended() throws Exception {
		Done done = done();

		jdbc.update("update survey_media set content_missing_at = ? where id = ?", java.sql.Timestamp.from(Instant.now()),
				done.passed().photoIds().getFirst());

		JsonNode read = readinessOf(leader, done.leadId());
		assertEquals("ENDED", read.path("state").asString(), read.toString());
		assertEquals("[\"EVIDENCE_GONE\"]", read.path("reasons").toString());
		IntakeBlockView.Readiness published = readiness.readiness(done.leadId());
		assertEquals("ENDED", published.state());
		assertEquals(List.of("EVIDENCE_GONE"), published.reasons());
	}

	/** Pin (E05-S6b): the lead's intent changed after the completion, so the result has ended (spec 4.11). */
	@Test
	void readiness_intentChanged_ended() throws Exception {
		Done done = done();

		jdbc.update("update owner_leads set intent_cycle = intent_cycle + 1 where id = ?", done.leadId());

		JsonNode read = readinessOf(leader, done.leadId());
		assertEquals("ENDED", read.path("state").asString(), read.toString());
		assertEquals("[\"INTENT_CHANGED\"]", read.path("reasons").toString());
		IntakeBlockView.Readiness published = readiness.readiness(done.leadId());
		assertEquals("ENDED", published.state());
		assertEquals(List.of("INTENT_CHANGED"), published.reasons());
	}

	@Test
	void complete_writesNoVerified() throws Exception {
		Done done = done();

		assertEquals(0, count("select count(*) from property_ownerships where property_id = ? and verification_state <> 'UNVERIFIED'",
				done.result().path("propertyId").asString()));
	}

	// ==================== proofs after completion (S-9, S-24) ====================

	@Test
	void withdraw_afterCompletion_suspended() throws Exception {
		Done done = done();

		withdrawn(done);

		assertEquals("COMPLETED", blockOk(sale, done.leadId()).path("status").asString());
		assertReadiness(done.leadId(), "SUSPENDED", "OWNER_NOT_CONFIRMED");
	}

	@Test
	void withdrawal_historyHasNoActor() throws Exception {
		Done done = done();

		withdrawn(done);

		Map<String, Object> row = jdbc.queryForMap("select * from owner_lead_activities where lead_id = ? and"
				+ " type = 'INVENTORY_INTAKE_SUSPENDED'", done.leadId());
		assertEquals(null, row.get("actor_user_id"));
		assertEquals(intakeRow(done.leadId()).get("id"), row.get("reference_id"));
		assertEquals(1, count("select count(*) from auth_email_notifications where user_id = ? and kind = 'OWNER_WITHDRAWN'",
				done.owner().userId()));
	}

	/** spec 4.12: only the proof in force of a result writes History, not another proof of the row. */
	@Test
	void withdrawal_ofAProofNotInForce_writesNoHistory() throws Exception {
		Done done = done();
		lockAndUnlock(done.owner());
		Issued again = issued(done.leadId(), address(done.owner()));
		confirmed(done.owner(), again);

		assertEquals(204, withdraw(done.owner(), again.requestId(), passwordOf(done.owner())).status());

		assertEquals(0, count("select count(*) from owner_lead_activities where lead_id = ? and type = 'INVENTORY_INTAKE_SUSPENDED'",
				done.leadId()));
	}

	@Test
	void lockUnlock_openRow_readsDraft() throws Exception {
		Passed passed = house(unique("Khoa"));
		TestUser owner = owner();
		checked(passed, owner);

		lockAndUnlock(owner);

		JsonNode block = blockOk(sale, passed.leadId());
		assertEquals("DRAFT", block.path("status").asString());
		assertEquals("NONE", block.path("ownerState").asString());
	}

	@Test
	void lockUnlock_completed_ownerNotConfirmed() throws Exception {
		Done done = done();

		lockAndUnlock(done.owner());

		assertEquals("COMPLETED", blockOk(sale, done.leadId()).path("status").asString());
		assertReadiness(done.leadId(), "SUSPENDED", "OWNER_NOT_CONFIRMED");
	}

	@Test
	void deletedAtAlone_counts() throws Exception {
		Done done = done();

		jdbc.update("update users set deleted_at = ? where id = ?", java.sql.Timestamp.from(Instant.now()), done.owner().userId());
		jdbc.update("update users set deleted_at = null where id = ?", done.owner().userId());

		assertReadiness(done.leadId(), "SUSPENDED", "OWNER_NOT_CONFIRMED");
	}

	@Test
	void temporaryLoginLockOrReset_keepsProof() throws Exception {
		Done done = done();

		jdbc.update("update users set locked_until = ? where id = ?", java.sql.Timestamp.from(Instant.now().plusSeconds(600)),
				done.owner().userId());
		jdbc.update("update users set password = password || 'x' where id = ?", done.owner().userId());

		assertEquals("USABLE", readinessOf(sale, done.leadId()).path("state").asString());
	}

	@Test
	void pendingToActive_keepsProof() throws Exception {
		Done done = done();

		jdbc.update("update users set user_status = 'PENDING' where id = ?", done.owner().userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", done.owner().userId());

		assertEquals("USABLE", readinessOf(sale, done.leadId()).path("state").asString());
	}

	@Test
	void staffRoleHeldAndEnded_keepsProof() throws Exception {
		Done done = done();
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'", done.owner().userId());
		assertReadiness(done.leadId(), "SUSPENDED", "OWNER_NOT_ELIGIBLE");

		jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
				done.owner().userId());

		assertEquals("USABLE", readinessOf(sale, done.leadId()).path("state").asString());
	}

	// ==================== restoration (S-9) ====================

	@Test
	void restore_ok_proofInForce_history() throws Exception {
		Done done = done();
		withdrawn(done);
		reproved(done);

		Result answer = restore(sale, done.leadId());

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("USABLE", answer.body().path("readiness").path("state").asString());
		assertEquals(proofIdOf(done.leadId()), intakeRow(done.leadId()).get("proof_id"));
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type = 'INVENTORY_INTAKE_RESTORED'",
				done.leadId()));
		assertEquals(1, count("select count(*) from inventory_intake_events where intake_id = ? and action = 'RESTORATION'",
				intakeRow(done.leadId()).get("id")));
	}

	@Test
	void restore_healthyRow_refused() throws Exception {
		Done done = done();

		assertRefused(409, "INTAKE_STATE", restore(sale, done.leadId()));
	}

	@Test
	void restore_withoutCheck_409() throws Exception {
		Done done = done();
		withdrawn(done);
		confirmed(done.owner(), issued(done.leadId(), address(done.owner())));

		assertRefused(409, "OWNER_PROOF_REQUIRED", restore(sale, done.leadId()));
	}

	@Test
	void restore_noProof_409() throws Exception {
		Done done = done();
		withdrawn(done);

		assertRefused(409, "OWNER_PROOF_REQUIRED", restore(sale, done.leadId()));
	}

	@Test
	void restore_otherUser_refused() throws Exception {
		Done done = done();
		withdrawn(done);
		reproved(done);
		TestUser stranger = owner();
		jdbc.update("update inventory_owner_proofs set user_id = ?, sign_in_losses = 0 where id = ?", stranger.userId(),
				proofIdOf(done.leadId()));

		assertRefused(409, "OWNER_PROOF_REQUIRED", restore(sale, done.leadId()));
	}

	@Test
	void restore_disputed_409() throws Exception {
		Done done = done();
		withdrawn(done);
		reproved(done);
		String street = jdbc.queryForObject("select street from properties where id = ?", String.class,
				done.result().path("propertyId").asString());
		Passed rival = house(street);
		checked(rival, owner());
		SurveyApiTestSupport.ok(createTarget(rival.leadId(), null));
		assertEquals("CASE_OPEN", blockOk(sale, rival.leadId()).path("status").asString());

		assertRefused(409, "INTAKE_DISPUTED", restore(sale, done.leadId()));
	}

	/** Review focus 4: restoration waits for the resume and the new check, and never picks up the old proof. */
	@Test
	void restore_afterHoldAndWithdrawal_inOrder() throws Exception {
		Done done = done();
		String oldProof = proofIdOf(done.leadId());
		withdrawn(done);
		confirmed(done.owner(), issued(done.leadId(), address(done.owner())));
		String newProof = proofIdOf(done.leadId());
		hold(done.leadId());

		assertRefused(409, "INTAKE_GATE_FAILED", restore(sale, done.leadId()));

		resume(done.leadId());
		assertRefused(409, "OWNER_PROOF_REQUIRED", restore(sale, done.leadId()));

		String request = jdbc.queryForObject("select request_id from inventory_owner_proofs where id = ?", String.class, newProof);
		assertEquals(204, enter(done.owner(), request, started(manager, done.leadId())).status());
		assertEquals(200, restore(sale, done.leadId()).status());

		assertEquals(newProof, intakeRow(done.leadId()).get("proof_id"));
		assertFalse(oldProof.equals(newProof));
	}

	// ==================== reassignment (S-25) ====================

	@Test
	void reassign_oldAssigneeRefused_proofStays() throws Exception {
		TestUser boss = survey.user("Reassigned Boss", "SALE_LEADER");
		Passed passed = house(unique("Reassign Street"));
		checked(passed, owner());
		fixture.checklists().reassign(leader, passed.leadId(), boss);
		SurveyApiTestSupport.ok(api.get(boss, base(passed.leadId()) + "/inventory-search?q=abc"));

		fixture.checklists().reassign(leader, passed.leadId(), otherSale);

		assertRefused(403, "INTAKE_NOT_ASSIGNEE", api.get(boss, base(passed.leadId()) + "/inventory-search?q=abc"));
		JsonNode block = blockOk(otherSale, passed.leadId());
		assertEquals("CONFIRMED", block.path("ownerState").asString());
		assertEquals("DONE", block.path("ownerCheck").asString());
	}

	// ==================== re-survey (S-26) ====================

	private Passed revisited(Done done, String scope, String assetType, String... codes) throws Exception {
		String address = jdbc.queryForObject("select street from properties where id = ?", String.class,
				done.result().path("propertyId").asString());
		return fixture.revisit(leader, sale, done.leadId(), scope, assetType, address, codes);
	}

	@Test
	void afterMark_blockReadsNoneWithEarlierResult() throws Exception {
		Done done = done();

		fixture.requalify(leader, done.passed().checklistId(), "OTHER");

		JsonNode block = blockOk(sale, done.leadId());
		assertTrue(block.path("status").isNull());
		assertTrue(block.path("version").isNull());
		assertEquals(done.result().path("propertyCode").asString(), block.path("earlierResult").path("propertyCode").asString());
	}

	@Test
	void earlierResult_staysAfterNewSelection() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "OTHER");
		Passed again = revisited(done, "WHOLE", "HOUSE", "A1");

		selected(again.leadId(), again.spaceIds());

		assertEquals(done.result().path("propertyCode").asString(),
				blockOk(sale, done.leadId()).path("earlierResult").path("propertyCode").asString());
	}

	@Test
	void newPassed_needsNewProofAndCheck() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "OTHER");
		Passed again = revisited(done, "WHOLE", "HOUSE", "A1");
		selected(again.leadId(), again.spaceIds());

		assertEquals("NONE", blockOk(sale, done.leadId()).path("ownerState").asString());
		assertRefused(409, "OWNER_PROOF_REQUIRED", createTarget(done.leadId(), null));
	}

	@Test
	void linkToEarlier_ok() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "OTHER");
		Passed again = revisited(done, "WHOLE", "HOUSE", "A1");
		checked(again, done.owner());
		String property = done.result().path("propertyId").asString();
		String unit = done.result().path("units").path(0).path("unitId").asString();

		Result linked = linkTarget(done.leadId(), property, again.spaceIds(), List.of(unit));

		assertEquals(200, linked.status(), linked.rawBody());
		assertEquals("OWNER_CONFIRMED", linked.body().path("status").asString());
		available(done.leadId(), again.spaceIds(), "VACANT");
		assertEquals(property, completed(done.leadId()).path("propertyId").asString());
	}

	@Test
	void createAfterEarlier_409() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "OTHER");
		Passed again = revisited(done, "WHOLE", "HOUSE", "A1");
		checked(again, done.owner());

		assertRefused(409, "INTAKE_CREATE_NOT_ALLOWED", createTarget(done.leadId(), null));
	}

	@Test
	void linkOtherThanEarlier_400() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "OTHER");
		Passed again = revisited(done, "WHOLE", "HOUSE", "A1");
		checked(again, done.owner());
		String street = jdbc.queryForObject("select street from properties where id = ?", String.class,
				done.result().path("propertyId").asString());
		String other = property(done.owner().userId(), WARD, "Hải Châu", street, "HOUSE", "x");
		ownership(other, done.owner().userId());

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(done.leadId(), other, again.spaceIds(), List.of(unit(other, "U1", "AVAILABLE"))));
	}

	@Test
	void wrongAssetOrOwner_reconciliationRequired() throws Exception {
		for (String kind : List.of("WRONG_ASSET", "WRONG_OWNER")) {
			Done done = done();
			fixture.requalify(leader, done.passed().checklistId(), kind);
			revisited(done, "WHOLE", "HOUSE", "A1");

			assertTrue(reasons(blockOk(sale, done.leadId())).contains("RECONCILIATION_REQUIRED"), kind);
		}
	}

	@Test
	void wrongAsset_withNoResult_stopsNothing() throws Exception {
		Passed passed = house(unique("No Result"));
		fixture.requalify(leader, passed.checklistId(), "WRONG_ASSET");
		String street = unique("No Result Again");

		Passed again = fixture.revisit(leader, sale, passed.leadId(), "WHOLE", "HOUSE", street, "A1");

		assertFalse(reasons(blockOk(sale, again.leadId())).contains("RECONCILIATION_REQUIRED"));
	}

	@Test
	void wrongScope_unmappable_linkInvalid() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "WRONG_SCOPE");
		Passed again = revisited(done, "PARTS", "HOMESTAY", "R1", "R2");
		checked(again, done.owner());

		assertRefused(400, "LINK_MAPPING_INVALID", linkTarget(done.leadId(), done.result().path("propertyId").asString(),
				again.spaceIds(), List.of(done.result().path("units").path(0).path("unitId").asString(),
						done.result().path("units").path(0).path("unitId").asString())));
	}

	/** D5-12, BR-20: a ruling that the earlier result is another asset leaves no legal target. */
	@Test
	void link_earlierResultRuledOut_reconciliationRequired() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "OTHER");
		String street = unique("A Moved House");
		Passed again = fixture.revisit(leader, sale, done.leadId(), "WHOLE", "HOUSE", street, "A1");
		checked(again, done.owner());
		String property = done.result().path("propertyId").asString();
		String unit = done.result().path("units").path(0).path("unitId").asString();
		SurveyApiTestSupport.ok(linkTarget(done.leadId(), property, again.spaceIds(), List.of(unit)));
		assertEquals("CASE_OPEN", blockOk(sale, done.leadId()).path("status").asString());
		String caseId = jdbc.queryForObject("select id from inventory_duplicate_cases where candidate_property_id = ?", String.class,
				property);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("outcome", "DIFFERENT_ASSET");
		body.put("basis", "Another house");
		body.put("expectedVersion", 0);
		SurveyApiTestSupport.ok(api.post(manager, CASES + "/" + caseId + "/decision", body));

		assertTrue(reasons(blockOk(sale, done.leadId())).contains("RECONCILIATION_REQUIRED"));
	}

	// ==================== read 1 of E05-S6b: parity with the per-lead readiness (SR-1, SR-2) ====================

	private UnitIntakeParity parity() {
		return new UnitIntakeParity(unitIntakes, readiness, jdbc);
	}

	@Test
	void readiness_noResult_none_forUnitsMatches() throws Exception {
		Passed passed = house(unique("Pasteur"));

		parity().check(passed.leadId());
	}

	@Test
	void readiness_usable_carriesSourceRevision_forUnitsMatches() throws Exception {
		Done done = done();

		parity().check(done.leadId());
	}

	@Test
	void readiness_leadOnHold_suspended_forUnitsMatches() throws Exception {
		Done done = done();

		hold(done.leadId());

		parity().check(done.leadId());
	}

	@Test
	void readiness_evidenceHeld_suspended_forUnitsMatches() throws Exception {
		Done done = done();

		jdbc.update("update survey_media set held_at = now(), held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT' where id = ?",
				admin.userId(), done.passed().photoIds().getFirst());

		parity().check(done.leadId());
	}

	@Test
	void readiness_ownerNotEligible_suspended_forUnitsMatches() throws Exception {
		Done done = done();

		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'", done.owner().userId());

		parity().check(done.leadId());
	}

	@Test
	void readiness_managerSuspension_restricted_noFreeText_forUnitsMatches() throws Exception {
		Done done = done();

		suspend(manager, done.leadId(), null, "Between managers", versionOf(manager, done.leadId()));

		parity().check(done.leadId());
	}

	@Test
	void readiness_leadClosed_ended_overSuspended_forUnitsMatches() throws Exception {
		Done done = done();
		suspend(manager, done.leadId(), null, "A reason", versionOf(manager, done.leadId()));

		close(done.leadId());

		parity().check(done.leadId());
	}

	@Test
	void readiness_afterMark_latestCompleted_endedRequalified_forUnitsMatches() throws Exception {
		Done done = done();

		fixture.requalify(leader, done.passed().checklistId(), "OTHER");

		parity().check(done.leadId());
	}

	@Test
	void readiness_evidenceGone_ended_forUnitsMatches() throws Exception {
		Done done = done();

		jdbc.update("update survey_media set content_missing_at = ? where id = ?", java.sql.Timestamp.from(Instant.now()),
				done.passed().photoIds().getFirst());

		parity().check(done.leadId());
	}

	@Test
	void readiness_intentChanged_ended_forUnitsMatches() throws Exception {
		Done done = done();

		jdbc.update("update owner_leads set intent_cycle = intent_cycle + 1 where id = ?", done.leadId());

		parity().check(done.leadId());
	}

	@Test
	void withdraw_afterCompletion_suspended_forUnitsMatches() throws Exception {
		Done done = done();

		withdrawn(done);

		assertEquals("COMPLETED", blockOk(sale, done.leadId()).path("status").asString());
		parity().check(done.leadId());
	}

	@Test
	void lockUnlock_completed_ownerNotConfirmed_forUnitsMatches() throws Exception {
		Done done = done();

		lockAndUnlock(done.owner());

		parity().check(done.leadId());
	}

	@Test
	void restore_ok_proofInForce_history_forUnitsMatches() throws Exception {
		Done done = done();
		withdrawn(done);
		reproved(done);

		Result answer = restore(sale, done.leadId());

		assertEquals(200, answer.status(), answer.rawBody());
		parity().check(done.leadId());
	}

	@Test
	void afterMark_blockReadsNoneWithEarlierResult_forUnitsMatches() throws Exception {
		Done done = done();

		fixture.requalify(leader, done.passed().checklistId(), "OTHER");

		parity().check(done.leadId());
	}

	/** Also SR-2 (b): the unit both completed rows of the lead map has one entry, the latest row. */
	@Test
	void linkToEarlier_ok_forUnitsMatches() throws Exception {
		Done done = done();
		fixture.requalify(leader, done.passed().checklistId(), "OTHER");
		Passed again = revisited(done, "WHOLE", "HOUSE", "A1");
		checked(again, done.owner());
		String property = done.result().path("propertyId").asString();
		String unit = done.result().path("units").path(0).path("unitId").asString();

		Result linked = linkTarget(done.leadId(), property, again.spaceIds(), List.of(unit));

		assertEquals(200, linked.status(), linked.rawBody());
		available(done.leadId(), again.spaceIds(), "VACANT");
		assertEquals(property, completed(done.leadId()).path("propertyId").asString());
		parity().check(done.leadId());
		assertEquals(2, count("select count(*) from inventory_unit_mappings m join inventory_intakes i on i.id = m.intake_id"
				+ " where m.unit_id = ? and i.status = 'COMPLETED'", unit));
		List<UnitIntakeView> answered = unitIntakes.forUnits(List.of(unit));
		assertEquals(1, answered.size());
		assertEquals(List.of(latestCompletedRow(done.leadId())),
				answered.getFirst().intakes().stream().map(UnitIntakeView.IntakeEntry::intakeId).toList());
	}

	/** SR-2 (a): a unit only the lead's earlier COMPLETED row maps has no entry; the unit both map has the latest row only. */
	@Test
	void forUnits_unitOnlyAnEarlierRowMaps_noEntry() throws Exception {
		String street = unique("Earlier Rows");
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", street, "P101", "P102");
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), passed.spaceIds().get(0)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String earlierRow = latestCompletedRow(passed.leadId());
		Map<String, String> unitBySpaceCode = new LinkedHashMap<>();
		jdbc.query("select space_code, unit_id from inventory_unit_mappings where intake_id = ?",
				row -> {
					unitBySpaceCode.put(row.getString("space_code"), row.getString("unit_id"));
				}, earlierRow);
		String p101 = unitBySpaceCode.get("P101");
		String p102 = unitBySpaceCode.get("P102");
		fixture.requalify(leader, passed.checklistId(), "OTHER");
		Passed again = fixture.revisit(leader, sale, passed.leadId(), "PARTS", "WHOLE_BUILDING", street, "P101");
		checked(again, owner);
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), propertyId, again.spaceIds(), List.of(p101)));
		available(passed.leadId(), again.spaceIds(), "VACANT");
		completed(passed.leadId());
		String latestRow = latestCompletedRow(passed.leadId());
		assertNotEquals(earlierRow, latestRow);

		List<UnitIntakeView> answered = unitIntakes.forUnits(List.of(p101, p102));

		IntakeBlockView.Readiness read = readiness.readiness(passed.leadId());
		assertEquals(List.of(p101), read.units().stream().map(IntakeBlockView.ReadinessUnit::unitId).toList());
		Map<String, UnitIntakeView> byUnit = new LinkedHashMap<>();
		answered.forEach(view -> byUnit.put(view.unitId(), view));
		assertEquals(Set.of(p101, p102), byUnit.keySet());
		assertEquals(List.of(), byUnit.get(p102).intakes());
		assertEquals(jdbc.queryForObject("select availability_state from units where id = ?", String.class, p102),
				byUnit.get(p102).availabilityState());
		assertEquals(List.of(new UnitIntakeView.IntakeEntry(latestRow, read.state(), read.sourceRevisionId(),
				read.units().getFirst().reasons())), byUnit.get(p101).intakes());
	}

	/** SR-2 (c): a unit no intake maps, an unknown, malformed, repeated or null id, and an empty input. */
	@Test
	void forUnits_legacyUnknownMalformedRepeatedOrEmpty_asStored() throws Exception {
		String street = unique("Legacy Unit");
		String propertyId = property(owner().userId(), WARD, "Hải Châu", street, "HOUSE", "x");
		String unit = unit(propertyId, "U1", "OCCUPIED");

		List<UnitIntakeView> answered = unitIntakes.forUnits(Arrays.asList(unit, unit, "01JUNKNOWN0000000000000000",
				"not-a-ulid", null));

		assertEquals(List.of(new UnitIntakeView(unit, "U1", propertyId, "OCCUPIED", List.of())), answered);
		assertEquals(List.of(), unitIntakes.forUnits(List.of()));
	}

	/** S6b-D11: an intake whose lead acquisition does not return fails the whole read, with a message naming no id. */
	@Test
	void forUnits_intakeOfAMissingLead_failsWithoutAnId() throws Exception {
		Done done = done();
		String unit = done.result().path("units").path(0).path("unitId").asString();
		String missing = "01JMISSINGLEAD000000000000";
		jdbc.update("update inventory_intakes set lead_id = ? where id = ?", missing, intakeRow(done.leadId()).get("id"));

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> unitIntakes.forUnits(List.of(unit)));

		assertFalse(String.valueOf(thrown.getMessage()).contains(missing), thrown.getMessage());
		assertFalse(String.valueOf(thrown.getMessage()).contains(unit), thrown.getMessage());
	}

	private String latestCompletedRow(String leadId) {
		return jdbc.queryForObject("select id from inventory_intakes where lead_id = ? and status = 'COMPLETED'"
				+ " order by intake_no desc limit 1", String.class, leadId);
	}
}
