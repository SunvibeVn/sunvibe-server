package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The manager's owner check, the owner's phrase and the suspensions (property-create intake spec 4.6 step 6, 4.11, S-8, S-27,
 * S-28, S-30) through the real filter chain. Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class OwnerCheckApiPostgresTest extends IntakeApiTestBase {

	private static final String UNKNOWN_ID = "01AAAAAAAAAAAAAAAAAAAAAAAA";
	private static final String WRONG_PHRASE = "XXXXXX";

	@Override
	String phonePrefix() {
		return "0965";
	}

	private Passed confirmedHouse(TestUser owner) throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		proved(passed, owner);
		return passed;
	}

	/** A SALE_LEADER who takes the lead: a manager role does not make the assignee a checker. */
	private TestUser boss() throws Exception {
		return survey.user("Intake Boss", "SALE_LEADER");
	}

	private JsonNode contextOk(TestUser caller, String leadId) throws Exception {
		Result answer = context(caller, leadId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	private int failedPhrases(String leadId) {
		return count("select phrase_failed_attempts from inventory_owner_proofs where lead_id = ? order by confirmed_at desc"
				+ " limit 1", leadId);
	}

	private String ownerCheck(String leadId) throws Exception {
		return blockOk(sale, leadId).path("ownerCheck").asString();
	}

	private Map<String, Object> proofRow(String proofId) {
		return jdbc.queryForMap("select * from inventory_owner_proofs where id = ?", proofId);
	}

	private void refused(TestUser checker, String leadId, String basis) throws Exception {
		SurveyApiTestSupport.ok(refuse(checker, leadId, proofIdOf(leadId), basis, versionOf(checker, leadId)));
	}

	// ==================== the insider cases (S-8) ====================

	@Test
	void insider_formLeadEqualEmail_stillRequired() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Lê Lợi"));
		jdbc.update("update owner_leads set email = ? where id = ?", address(owner), passed.leadId());

		proved(passed, owner);

		assertEquals("REQUIRED", ownerCheck(passed.leadId()));
	}

	/** Review focus 3: the masked address reads alike, but the real owner cannot enter a phrase in an account not theirs. */
	@Test
	void check_lookAlikeAccount_notEntered_staysRequired() throws Exception {
		TestUser lookAlike = owner();
		Passed passed = confirmedHouse(lookAlike);

		started(manager, passed.leadId());

		assertEquals("REQUIRED", ownerCheck(passed.leadId()));
		assertEquals(0, count("select count(*) from inventory_owner_proofs where lead_id = ? and owner_check = 'DONE'",
				passed.leadId()));
	}

	/** D5-7: whoever worked the lead cannot check it, a manager role or not. */
	@Test
	void check_assigneeEarlierAssigneeSurveyorRecorderActors_403() throws Exception {
		TestUser surveyor = boss();
		Passed passed = fixture.whole(leader, surveyor, "HOUSE", unique("Quang Trung"));
		select(surveyor, passed.leadId(), passed.spaceIds(), null);

		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", context(surveyor, passed.leadId()));

		fixture.checklists().reassign(leader, passed.leadId(), otherSale);
		String lead = passed.leadId();
		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", context(surveyor, lead));
		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", startCheck(surveyor, lead, UNKNOWN_ID, "A note", 1L));
		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", refuse(surveyor, lead, UNKNOWN_ID, "A note", 1L));

		jdbc.update("insert into inventory_intake_events (intake_id, actor_user_id, action, occurred_at) values (?, ?, ?, ?)",
				intakeRow(lead).get("id"), manager.userId(), "SELECTION", Timestamp.from(Instant.now()));
		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", context(manager, lead));
	}

	@Test
	void context_assignee_403() throws Exception {
		TestUser assignee = boss();
		Passed passed = house(unique("Hùng Vương"));
		fixture.checklists().reassign(leader, passed.leadId(), assignee);

		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", context(assignee, passed.leadId()));
	}

	@Test
	void context_writesAccessLog() throws Exception {
		Passed passed = confirmedHouse(owner());

		JsonNode body = contextOk(manager, passed.leadId());

		assertEquals(Set.of("proofId", "accountAddressMasked", "accountAgeDays", "confirmedAt", "basis", "signals", "refusals"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(body)));
		assertEquals(proofIdOf(passed.leadId()), body.path("proofId").asString());
		assertTrue(body.path("basis").isNull());
		assertEquals(1, count("select count(*) from inventory_access_logs where lead_id = ? and user_id = ? and"
				+ " action = 'OWNER_CHECK_CONTEXT'", passed.leadId(), manager.userId()));
	}

	@Test
	void context_noProof_409() throws Exception {
		Passed passed = house(unique("Pasteur"));
		selected(passed.leadId(), passed.spaceIds());

		assertRefused(409, "INTAKE_STATE", context(manager, passed.leadId()));
	}

	@Test
	void context_signalsPerAccountAndCreator() throws Exception {
		TestUser owner = owner();
		Passed first = confirmedHouse(owner);
		Passed second = confirmedHouse(owner);
		Passed other = confirmedHouse(owner());

		JsonNode signals = contextOk(manager, first.leadId()).path("signals");
		assertEquals(2, signals.path("accountProofs").asInt());
		assertEquals(0, signals.path("accountRefusals").asInt());
		assertEquals(2, signals.path("creatorProofs").asInt());
		int creatorBefore = signals.path("creatorRefusals").asInt();

		refused(manager, second.leadId(), "Reached a stranger");
		refused(manager, other.leadId(), "Reached a stranger");

		signals = contextOk(manager, first.leadId()).path("signals");
		assertEquals(1, signals.path("accountProofs").asInt(), "live proofs only");
		assertEquals(1, signals.path("accountRefusals").asInt());
		assertEquals(2, signals.path("creatorProofs").asInt());
		assertEquals(creatorBefore + 2, signals.path("creatorRefusals").asInt(), "every refused proof of that creator");
	}

	@Test
	void context_otherOwnersOnPhone() throws Exception {
		TestUser owner = owner();
		Passed mine = confirmedHouse(owner);
		Passed sameOwner = confirmedHouse(owner);
		Passed otherOwner = confirmedHouse(owner());
		for (Passed passed : List.of(sameOwner, otherOwner)) {
			jdbc.update("update owner_leads set phone_normalized = (select phone_normalized from owner_leads where id = ?)"
					+ " where id = ?", mine.leadId(), passed.leadId());
		}

		JsonNode signals = contextOk(manager, mine.leadId()).path("signals");

		assertEquals(2, signals.path("otherOpenLeadsOfPhone").asInt());
		assertEquals(1, signals.path("otherOwnersOnPhone").asInt());
	}

	@Test
	void context_refusalsForTheNextChecker() throws Exception {
		TestUser owner = owner();
		Passed passed = confirmedHouse(owner);
		refused(manager, passed.leadId(), "Reached a stranger");

		confirmed(owner, issued(passed.leadId(), address(owner)));

		JsonNode refusals = contextOk(admin, passed.leadId()).path("refusals");
		assertEquals(1, refusals.size());
		assertEquals("Reached a stranger", refusals.path(0).path("basis").asString());
		assertFalse(refusals.path(0).path("checkedAt").asString().isBlank());
	}

	@Test
	void refusalWithNoStart_counts() throws Exception {
		TestUser owner = owner();
		Passed kept = confirmedHouse(owner);
		Passed passed = confirmedHouse(owner);

		refused(manager, passed.leadId(), "Nobody knew the asset");

		assertEquals(1, contextOk(manager, kept.leadId()).path("signals").path("accountRefusals").asInt());
	}

	@Test
	void notLiveAfterStartWithoutDone_countsAsRefused() throws Exception {
		TestUser owner = owner();
		TestUser sameUser = owner();
		Passed passed = confirmedHouse(owner);
		Passed other = confirmedHouse(sameUser);
		started(manager, passed.leadId());
		int before = contextOk(manager, other.leadId()).path("signals").path("creatorRefusals").asInt();

		jdbc.update("update users set user_status = 'LOCK' where id = ?", owner.userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", owner.userId());

		assertEquals(before + 1, contextOk(manager, other.leadId()).path("signals").path("creatorRefusals").asInt());
	}

	@Test
	void fiveWrongThenWithdrawal_stillCounts() throws Exception {
		TestUser owner = owner();
		Passed kept = confirmedHouse(owner);
		Passed passed = house(unique("Tô Hiến Thành"));
		Issued issued = proved(passed, owner);
		started(manager, passed.leadId());
		for (int i = 0; i < 5; i++) {
			assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), WRONG_PHRASE));
		}

		assertEquals(204, withdraw(owner, issued.requestId(), passwordOf(owner)).status());

		assertEquals(1, contextOk(manager, kept.leadId()).path("signals").path("accountRefusals").asInt());
	}

	@Test
	void leadClosedAfterStart_stillCounts() throws Exception {
		TestUser owner = owner();
		Passed kept = confirmedHouse(owner);
		Passed passed = confirmedHouse(owner);
		started(manager, passed.leadId());

		close(passed.leadId());

		assertEquals(1, contextOk(manager, kept.leadId()).path("signals").path("accountRefusals").asInt());
	}

	/** A proof that died after a start counts for the signals, not against the manager's next refusal. */
	@Test
	void refusalLimit_readsRefusedOnly() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Lê Thánh Tôn"));
		Issued first = proved(passed, owner);
		started(manager, passed.leadId());
		assertEquals(204, withdraw(owner, first.requestId(), passwordOf(owner)).status());

		confirmed(owner, issued(passed.leadId(), address(owner)));

		refused(manager, passed.leadId(), "Reached a stranger");
	}

	// ==================== the start and the phrase ====================

	@Test
	void start_answersPhraseOnce() throws Exception {
		Passed passed = confirmedHouse(owner());
		long before = versionOf(manager, passed.leadId());

		Result answer = startCheck(manager, passed.leadId(), proofIdOf(passed.leadId()), "Second number on file", before);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(Set.of("phrase", "expiresAt"), Set.copyOf(AcquisitionApiTestSupport.fieldNames(answer.body())));
		String phrase = answer.body().path("phrase").asString();
		assertTrue(phrase.matches("[0-9A-HJKMNP-TV-Z]{6}"));
		Map<String, Object> proof = proofRow(proofIdOf(passed.leadId()));
		assertNotEquals(phrase, proof.get("phrase_hash"));
		assertEquals(manager.userId(), proof.get("owner_check_by_user_id"));
		assertEquals("Second number on file", contextOk(manager, passed.leadId()).path("basis").asString());
		assertEquals(before + 1, versionOf(manager, passed.leadId()), "A-13: every command that writes raises the version");
	}

	@Test
	void phrase_neverInContextBlockListLogsNotices(CapturedOutput output) throws Exception {
		TestUser owner = owner();
		Passed passed = confirmedHouse(owner);

		String phrase = started(manager, passed.leadId());

		assertFalse(context(manager, passed.leadId()).rawBody().contains(phrase));
		assertFalse(block(manager, passed.leadId()).rawBody().contains(phrase));
		assertFalse(api.get(owner, CONFIRMATIONS).rawBody().contains(phrase));
		assertEquals(0, jdbc.queryForList("select * from auth_email_notifications where user_id = ?", owner.userId()).stream()
				.filter(row -> row.toString().contains(phrase)).count());
		assertFalse(output.getAll().contains(phrase));
	}

	@Test
	void list_neverCarriesPhrase() throws Exception {
		TestUser owner = owner();
		Passed passed = confirmedHouse(owner);

		String phrase = started(manager, passed.leadId());

		Result list = api.get(owner, CONFIRMATIONS);
		assertFalse(list.rawBody().contains(phrase));
		assertTrue(list.body().path("items").path(0).path("checkOpen").asBoolean());
	}

	@Test
	void entry_right_setsDone_startingManagerRecorded_hashCleared_expiryKept() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Thái Phiên"));
		Issued issued = proved(passed, owner);
		String proofId = proofIdOf(passed.leadId());
		String phrase = started(manager, passed.leadId());
		long before = versionOf(sale, passed.leadId());

		Result entry = enter(owner, issued.requestId(), phrase.toLowerCase(java.util.Locale.ROOT));

		assertEquals(204, entry.status(), entry.rawBody());
		Map<String, Object> proof = proofRow(proofId);
		assertEquals("DONE", proof.get("owner_check"));
		assertEquals(manager.userId(), proof.get("owner_check_by_user_id"));
		assertNull(proof.get("phrase_hash"));
		assertNotNull(proof.get("phrase_expires_at"));
		assertEquals("DONE", ownerCheck(passed.leadId()));
		assertEquals(before + 1, versionOf(sale, passed.leadId()));
		assertFalse(api.get(owner, CONFIRMATIONS).body().path("items").path(0).path("checkOpen").asBoolean());
	}

	@Test
	void entry_wrong_400_counted() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Đống Đa"));
		Issued issued = proved(passed, owner);
		started(manager, passed.leadId());
		long version = versionOf(sale, passed.leadId());

		assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), WRONG_PHRASE));

		assertEquals(1, failedPhrases(passed.leadId()));
		assertEquals("REQUIRED", ownerCheck(passed.leadId()));
		assertEquals(version, versionOf(sale, passed.leadId()), "A-13: a wrong phrase raises none");
	}

	@Test
	void entry_parallelWrong_allCount() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Nguyễn Trãi"));
		Issued issued = proved(passed, owner);
		started(manager, passed.leadId());
		CountDownLatch start = new CountDownLatch(1);
		List<CompletableFuture<Integer>> tries = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tries.add(CompletableFuture.supplyAsync(() -> {
				try {
					start.await();
					return enter(owner, issued.requestId(), WRONG_PHRASE).status();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}));
		}

		start.countDown();

		for (CompletableFuture<Integer> attempt : tries) {
			assertEquals(400, attempt.get(60, TimeUnit.SECONDS));
		}
		assertEquals(4, failedPhrases(passed.leadId()));
	}

	@Test
	void entry_expired_refusedUncounted() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Lý Thường Kiệt"));
		Issued issued = proved(passed, owner);
		String phrase = started(manager, passed.leadId());
		jdbc.update("update inventory_owner_proofs set phrase_expires_at = ? where lead_id = ?",
				Timestamp.from(Instant.now().minusSeconds(60)), passed.leadId());

		assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), phrase));
		assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), WRONG_PHRASE));

		assertEquals(0, failedPhrases(passed.leadId()));
		assertEquals("REQUIRED", ownerCheck(passed.leadId()));
	}

	@Test
	void entry_fifthWrong_endsPhrase_proofStays() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Ông Ích Khiêm"));
		Issued issued = proved(passed, owner);
		String phrase = started(manager, passed.leadId());
		for (int i = 0; i < 5; i++) {
			assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), WRONG_PHRASE));
		}

		assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), phrase));

		assertEquals(5, failedPhrases(passed.leadId()));
		JsonNode block = blockOk(sale, passed.leadId());
		assertEquals("CONFIRMED", block.path("ownerState").asString());
		assertEquals("REQUIRED", block.path("ownerCheck").asString());
		assertFalse(api.get(owner, CONFIRMATIONS).body().path("items").path(0).path("checkOpen").asBoolean());
	}

	/** BR-15: a proof survives a reassignment, so its check does too. */
	@Test
	void entry_afterReassignment_works() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Trưng Nữ Vương"));
		Issued issued = proved(passed, owner);
		String phrase = started(manager, passed.leadId());

		fixture.checklists().reassign(leader, passed.leadId(), otherSale);

		assertEquals(204, enter(owner, issued.requestId(), phrase).status());
		assertEquals("DONE", blockOk(otherSale, passed.leadId()).path("ownerCheck").asString());
	}

	@Test
	void otherAccount_checkVerb_404_counterUnchanged() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Phạm Văn Đồng"));
		Issued issued = proved(passed, owner);
		String phrase = started(manager, passed.leadId());

		assertRefused(404, "NOT_FOUND", enter(owner(), issued.requestId(), WRONG_PHRASE));
		assertRefused(404, "NOT_FOUND", enter(owner(), issued.requestId(), phrase));

		assertEquals(0, failedPhrases(passed.leadId()));
		assertEquals("REQUIRED", ownerCheck(passed.leadId()));
	}

	@Test
	void entry_afterTheProofDied_refused() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Võ Văn Kiệt"));
		Issued issued = proved(passed, owner);
		String phrase = started(manager, passed.leadId());

		fixture.requalify(leader, passed.checklistId(), "OTHER");

		assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), phrase));
		assertEquals(0, count("select count(*) from inventory_owner_proofs where lead_id = ? and owner_check = 'DONE'",
				passed.leadId()));
	}

	@Test
	void start_again_replacesPhrase_zeroesCounter() throws Exception {
		TestUser owner = owner();
		Passed passed = house(unique("Nguyễn Văn Linh"));
		Issued issued = proved(passed, owner);
		String first = started(manager, passed.leadId());
		assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), WRONG_PHRASE));

		String second = started(admin, passed.leadId());

		assertEquals(0, failedPhrases(passed.leadId()));
		if (!first.equals(second)) {
			assertRefused(400, "CONFIRMATION_INVALID", enter(owner, issued.requestId(), first));
		}
		assertEquals(204, enter(owner, issued.requestId(), second).status());
		assertEquals(admin.userId(), proofRow(proofIdOf(passed.leadId())).get("owner_check_by_user_id"));
	}

	@Test
	void start_deadSource_refused() throws Exception {
		Passed passed = confirmedHouse(owner());
		String proofId = proofIdOf(passed.leadId());
		long version = versionOf(manager, passed.leadId());

		fixture.requalify(leader, passed.checklistId(), "OTHER");

		assertRefused(409, "INTAKE_STATE", startCheck(manager, passed.leadId(), proofId, "A note", version));
	}

	@Test
	void start_checkDone_refused() throws Exception {
		Passed passed = house(unique("Hàm Nghi"));
		checked(passed, owner());

		assertRefused(409, "INTAKE_STATE", startCheck(manager, passed.leadId(), proofIdOf(passed.leadId()), "A note",
				versionOf(manager, passed.leadId())));
		assertEquals(200, context(manager, passed.leadId()).status(), "the context still reads a DONE check");
	}

	@Test
	void check_staleProofId_409() throws Exception {
		Passed passed = confirmedHouse(owner());
		long version = versionOf(manager, passed.leadId());

		assertRefused(409, "CONCURRENT_MODIFICATION", startCheck(manager, passed.leadId(), UNKNOWN_ID, "A note", version));
		assertRefused(409, "CONCURRENT_MODIFICATION", refuse(manager, passed.leadId(), UNKNOWN_ID, "A note", version));
	}

	@Test
	void check_staleVersion_409() throws Exception {
		Passed passed = confirmedHouse(owner());
		long version = versionOf(manager, passed.leadId());

		assertRefused(409, "CONCURRENT_MODIFICATION", startCheck(manager, passed.leadId(), proofIdOf(passed.leadId()), "A note",
				version - 1));
	}

	// ==================== the refusal ====================

	@Test
	void refusal_openRow_toDraft() throws Exception {
		Passed passed = confirmedHouse(owner());
		String proofId = proofIdOf(passed.leadId());

		Result answer = refuse(manager, passed.leadId(), proofId, "Reached a stranger", versionOf(manager, passed.leadId()));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("DRAFT", answer.body().path("status").asString());
		assertEquals("NONE", answer.body().path("ownerState").asString());
		Map<String, Object> proof = proofRow(proofId);
		assertEquals("REFUSED", proof.get("owner_check"));
		assertNotNull(proof.get("voided_at"));
	}

	@Test
	void refusal_neutralNotice() throws Exception {
		TestUser owner = owner();
		Passed passed = confirmedHouse(owner);

		refused(manager, passed.leadId(), "Reached a stranger");

		assertEquals(1, count("select count(*) from auth_email_notifications where user_id = ? and kind = 'OWNER_CHECK_REFUSED'",
				owner.userId()));
		assertEquals(0, count("select count(*) from auth_email_notifications where user_id = ? and kind = 'OWNER_WITHDRAWN'",
				owner.userId()));
	}

	@Test
	void refusal_overwritesByBasisTime() throws Exception {
		Passed passed = confirmedHouse(owner());
		String proofId = proofIdOf(passed.leadId());
		started(leader, passed.leadId());
		Object startedAt = proofRow(proofId).get("owner_check_at");

		refused(manager, passed.leadId(), "Reached a stranger");

		Map<String, Object> proof = proofRow(proofId);
		assertEquals(manager.userId(), proof.get("owner_check_by_user_id"));
		assertEquals("Reached a stranger", proof.get("owner_check_basis"));
		assertNotEquals(startedAt, proof.get("owner_check_at"));
		assertNull(proof.get("phrase_hash"));
	}

	@Test
	void refusal_sameManagerTwice_403() throws Exception {
		TestUser owner = owner();
		Passed passed = confirmedHouse(owner);
		refused(manager, passed.leadId(), "Reached a stranger");
		confirmed(owner, issued(passed.leadId(), address(owner)));

		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", refuse(manager, passed.leadId(), proofIdOf(passed.leadId()),
				"Again a stranger", versionOf(manager, passed.leadId())));

		refused(admin, passed.leadId(), "Reached a stranger too");
	}

	@Test
	void managerWhoReviewedOrSuspended_canStillAct() throws Exception {
		Passed passed = confirmedHouse(owner());
		Result held = suspend(manager, passed.leadId(), null, "Paperwork looks wrong", versionOf(manager, passed.leadId()));
		SurveyApiTestSupport.ok(held);
		SurveyApiTestSupport.ok(release(manager, passed.leadId(), held.body().path("suspensions").path(0).path("id").asString(),
				"Paperwork fine", versionOf(manager, passed.leadId())));

		started(manager, passed.leadId());

		assertEquals(200, context(leader, passed.leadId()).status(), "the leader reviewed the survey");
	}

	// ==================== suspensions (S-27) ====================

	@Test
	void suspend_noIntakeRow_ok() throws Exception {
		Passed passed = house(unique("Thanh Khê"));

		Result answer = suspend(manager, passed.leadId(), null, "Dispute with a neighbour", null);

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode suspension = answer.body().path("suspensions").path(0);
		assertEquals("Dispute with a neighbour", suspension.path("reason").asString());
		assertTrue(suspension.path("unitCode").isNull());
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and"
				+ " type = 'INVENTORY_INTAKE_SUSPENDED' and reference_id is null and reference_no is null", passed.leadId()));
		assertEquals(1, count("select count(*) from inventory_suspensions where lead_id = ? and intake_id is null",
				passed.leadId()));
	}

	@Test
	void suspend_writesNoReasonInHistory() throws Exception {
		Passed passed = house(unique("Liên Chiểu"));

		suspend(manager, passed.leadId(), null, "Secret reason", null);

		assertEquals(0, count("select count(*) from owner_lead_activities where lead_id = ? and"
				+ " (note like '%Secret%' or reference_detail like '%Secret%')", passed.leadId()));
	}

	@Test
	void suspend_foreignUnit_404() throws Exception {
		Passed passed = house(unique("Cẩm Lệ"));
		String property = property(owner().userId(), WARD, "Hải Châu", unique("Foreign"), "HOUSE", "foreign");
		String foreign = unit(property, "U1", "UNKNOWN");

		assertRefused(404, "NOT_FOUND", suspend(manager, passed.leadId(), foreign, "A reason", null));
	}

	@Test
	void release_otherLead_404() throws Exception {
		Passed first = house(unique("Hòa Vang"));
		Passed second = house(unique("Hòa Xuân"));
		String id = suspend(manager, first.leadId(), null, "A reason", null).body().path("suspensions").path(0).path("id")
				.asString();

		assertRefused(404, "NOT_FOUND", release(manager, second.leadId(), id, "Fine", null));
		assertRefused(404, "NOT_FOUND", release(manager, second.leadId(), UNKNOWN_ID, "Fine", null));
	}

	@Test
	void release_restoresTheGate_writesHistory() throws Exception {
		Passed passed = house(unique("Hòa Minh"));
		String id = suspend(manager, passed.leadId(), null, "A reason", null).body().path("suspensions").path(0).path("id")
				.asString();

		Result answer = release(manager, passed.leadId(), id, "Fine", null);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(0, answer.body().path("suspensions").size());
		assertFalse(reasons(answer.body()).contains("RESTRICTED"));
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and"
				+ " type = 'INVENTORY_INTAKE_RESTORED'", passed.leadId()));
	}

	@Test
	void release_twice_409() throws Exception {
		Passed passed = house(unique("Hòa Khánh"));
		String id = suspend(manager, passed.leadId(), null, "A reason", null).body().path("suspensions").path(0).path("id")
				.asString();
		release(manager, passed.leadId(), id, "Fine", null);

		assertRefused(409, "INTAKE_STATE", release(manager, passed.leadId(), id, "Fine", null));
	}

	@Test
	void suspend_assignee_403() throws Exception {
		TestUser assignee = boss();
		Passed passed = house(unique("Hòa Cường"));
		fixture.checklists().reassign(leader, passed.leadId(), assignee);

		assertRefused(403, "OWNER_CHECK_NOT_ALLOWED", suspend(assignee, passed.leadId(), null, "A reason", null));
	}

	@Test
	void suspend_second_allowed() throws Exception {
		Passed passed = house(unique("Hòa Thuận"));

		suspend(manager, passed.leadId(), null, "First", null);
		Result second = suspend(leader, passed.leadId(), null, "Second", null);

		assertEquals(200, second.status(), second.rawBody());
		assertEquals(2, second.body().path("suspensions").size());
	}

	@Test
	void suspend_staleVersion_409() throws Exception {
		Passed passed = house(unique("Hòa An"));
		selected(passed.leadId(), passed.spaceIds());

		assertRefused(409, "CONCURRENT_MODIFICATION", suspend(manager, passed.leadId(), null, "A reason", null));
	}

	@Test
	void suspend_raisesTheVersion() throws Exception {
		Passed passed = house(unique("Hòa Phát"));
		selected(passed.leadId(), passed.spaceIds());
		long before = versionOf(manager, passed.leadId());

		Result answer = suspend(manager, passed.leadId(), null, "A reason", before);

		assertEquals(before + 1, answer.body().path("version").asLong());
	}

	@Test
	void reason_omittedForAssigneeManager() throws Exception {
		TestUser assignee = boss();
		Passed passed = house(unique("Hòa Thọ"));
		fixture.checklists().reassign(leader, passed.leadId(), assignee);

		suspend(manager, passed.leadId(), null, "Between managers", null);

		assertTrue(blockOk(assignee, passed.leadId()).path("suspensions").path(0).path("reason").isMissingNode());
		assertEquals("Between managers", blockOk(manager, passed.leadId()).path("suspensions").path(0).path("reason").asString());
		Passed own = house(unique("Hòa Tiến"));
		suspend(manager, own.leadId(), null, "Between managers", null);
		assertEquals(0, blockOk(sale, own.leadId()).path("suspensions").size());
	}

	@Test
	void wholeLead_failsGate_newIntakeToo() throws Exception {
		Passed passed = house(unique("Hòa Quý"));

		suspend(manager, passed.leadId(), null, "A reason", null);

		assertTrue(reasons(blockOk(sale, passed.leadId())).contains("RESTRICTED"));
		Result refused = select(sale, passed.leadId(), passed.spaceIds(), null);
		assertRefused(409, "INTAKE_GATE_FAILED", refused);
		assertEquals("gate.RESTRICTED", refused.body().path("errors").path(0).path("field").asString());
	}

	// ==================== roles and text (S-28, S-30) ====================

	@Test
	void sale_refusedOnCheckContextSuspensionsRelease() throws Exception {
		Passed passed = confirmedHouse(owner());
		String lead = passed.leadId();
		String proofId = proofIdOf(lead);

		for (TestUser caller : List.of(sale, otherSale)) {
			assertEquals(403, context(caller, lead).status());
			assertEquals(403, startCheck(caller, lead, proofId, "A note", 1L).status());
			assertEquals(403, refuse(caller, lead, proofId, "A note", 1L).status());
			assertEquals(403, suspend(caller, lead, null, "A reason", 1L).status());
			assertEquals(403, release(caller, lead, UNKNOWN_ID, "A reason", 1L).status());
		}
	}

	@Test
	void text_phoneOrNul_400() throws Exception {
		Passed passed = confirmedHouse(owner());
		String lead = passed.leadId();
		String proofId = proofIdOf(lead);
		long version = versionOf(manager, lead);

		for (String bad : List.of("Call 0905 123 456 first", "A\u0000B", "   ")) {
			Result start = startCheck(manager, lead, proofId, bad, version);
			assertRefused(400, "VALIDATION_FAILED", start);
			assertEquals("basis", start.body().path("errors").path(0).path("field").asString());
			Result refusal = refuse(manager, lead, proofId, bad, version);
			assertRefused(400, "VALIDATION_FAILED", refusal);
			Result suspension = suspend(manager, lead, null, bad, version);
			assertRefused(400, "VALIDATION_FAILED", suspension);
			assertEquals("reason", suspension.body().path("errors").path(0).path("field").asString());
		}
		assertRefused(400, "VALIDATION_FAILED", startCheck(manager, lead, proofId, "x".repeat(2001), version));
	}
}
