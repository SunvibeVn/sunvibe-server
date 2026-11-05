package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;

/**
 * The owner's request, confirmation, decline and withdrawal (property-create intake spec 4.6, 7.3, S-4 to S-9) through the real
 * filter chain. Needs Docker.
 */
class OwnerConfirmationApiPostgresTest extends IntakeApiTestBase {

	private static final String SELECTED = "update inventory_owner_requests set expires_at = ? where id = ?";

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	String phonePrefix() {
		return "0964";
	}

	private Passed draft() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		selected(passed.leadId(), passed.spaceIds());
		return passed;
	}

	private int failedAttempts(String requestId) {
		return count("select failed_attempts from inventory_owner_requests where id = ?", requestId);
	}

	private String snapshot(String requestId) {
		return jdbc.queryForObject("select display_snapshot from inventory_owner_requests where id = ?", String.class, requestId);
	}

	private int proofs(String leadId) {
		return count("select count(*) from inventory_owner_proofs where lead_id = ?", leadId);
	}

	private int notices(TestUser owner, String kind) {
		return count("select count(*) from auth_email_notifications where user_id = ? and kind = ?", owner.userId(), kind);
	}

	private JsonNode list(TestUser owner) throws Exception {
		Result answer = api.get(owner, CONFIRMATIONS);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body().path("items");
	}

	// ==================== the request (S-4) ====================

	/** BR-5, E-3: nobody learns whether an address matched. */
	@Test
	void request_sameAnswerForEveryValue() throws Exception {
		TestUser found = owner();
		TestUser unverified = owner();
		jdbc.update("update users set email_verified_at = null where id = ?", unverified.userId());
		List<String> values = List.of(address(found), "nobody" + unique("x").replace(" ", "") + "@example.com",
				address(unverified), "phone.only@example.com");

		List<Set<String>> shapes = new ArrayList<>();
		for (String value : values) {
			Passed passed = draft();
			Result answer = requestOwner(sale, passed.leadId(), value, versionOf(sale, passed.leadId()));
			assertEquals(200, answer.status(), answer.rawBody());
			shapes.add(Set.copyOf(AcquisitionApiTestSupport.fieldNames(answer.body())));
			assertTrue(answer.body().path("code").asString().matches("[0-9A-HJKMNP-TV-Z]{8}"));
		}

		assertEquals(Set.of(Set.of("requestId", "code", "expiresAt")), Set.copyOf(shapes));
	}

	@Test
	void request_invalidSyntaxOnly_400() throws Exception {
		Passed passed = draft();

		Result refused = requestOwner(sale, passed.leadId(), "not an email", versionOf(sale, passed.leadId()));

		assertRefused(400, "VALIDATION_FAILED", refused);
		assertEquals("ownerEmail", refused.body().path("errors").path(0).path("field").asString());
	}

	@Test
	void request_sixthIn24h_429() throws Exception {
		Passed passed = draft();
		for (int i = 0; i < 5; i++) {
			issued(passed.leadId(), "person" + i + "@example.com");
		}

		assertRefused(429, "OWNER_REQUEST_LIMIT", requestOwner(sale, passed.leadId(), "last@example.com",
				versionOf(sale, passed.leadId())));
	}

	/** A new request voids the old one. */
	@Test
	void request_new_voidsTheOld() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued first = issued(passed.leadId(), address(owner));

		Issued second = issued(passed.leadId(), address(owner));

		assertRefused(400, "CONFIRMATION_INVALID", details(owner, first.requestId(), first.code()));
		assertEquals(200, details(owner, second.requestId(), second.code()).status());
	}

	@Test
	void block_showsNoDifference() throws Exception {
		TestUser owner = owner();
		Passed matched = draft();
		Passed unmatched = draft();

		issued(matched.leadId(), address(owner));
		issued(unmatched.leadId(), "nobody.at.all@example.com");

		JsonNode first = blockOk(sale, matched.leadId());
		JsonNode second = blockOk(sale, unmatched.leadId());
		assertEquals("AWAITING_OWNER", first.path("status").asString());
		assertEquals(first.path("status"), second.path("status"));
		assertEquals(first.path("version"), second.path("version"));
		assertEquals("WAITING", first.path("ownerState").asString());
		assertEquals(first.path("ownerState"), second.path("ownerState"));
		assertEquals("NONE", first.path("ownerCheck").asString());
	}

	/** A-13: a decline does not show; the Sale still reads WAITING until the request ends. */
	@Test
	void decline_notVisibleToStaff() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));
		long version = blockOk(sale, passed.leadId()).path("version").asLong();

		assertEquals(204, decline(owner, issued.requestId(), issued.code()).status());

		JsonNode block = blockOk(sale, passed.leadId());
		assertEquals("WAITING", block.path("ownerState").asString());
		assertEquals(version, block.path("version").asLong());
	}

	@Test
	void decline_raisesNoVersion() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));
		long before = (Long) intakeRow(passed.leadId()).get("version");

		decline(owner, issued.requestId(), issued.code());

		assertEquals(before, intakeRow(passed.leadId()).get("version"));
		assertEquals(1, count("select count(*) from inventory_owner_requests where id = ? and declined_at is not null",
				issued.requestId()));
		assertNull(snapshot(issued.requestId()));
	}

	// ==================== before the code (S-5) ====================

	@Test
	void beforeCode_onlyIdStatusExpiry() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		JsonNode items = list(owner);

		assertEquals(1, items.size());
		assertEquals(Set.of("requestId", "status", "expiresAt"), Set.copyOf(AcquisitionApiTestSupport.fieldNames(items.path(0))));
		assertEquals(issued.requestId(), items.path(0).path("requestId").asString());
		assertEquals("PENDING", items.path(0).path("status").asString());
	}

	@Test
	void details_fields() throws Exception {
		String address = unique("Lê Duẩn");
		Passed passed = house(address);
		selected(passed.leadId(), passed.spaceIds());
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		Result answer = details(owner, issued.requestId(), issued.code());

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode body = answer.body();
		assertEquals(Set.of("leadCode", "wardName", "assetType", "address", "parts", "statement"),
				Set.copyOf(AcquisitionApiTestSupport.fieldNames(body)));
		assertEquals(address, body.path("address").asString());
		assertEquals("HOUSE", body.path("assetType").asString());
		assertEquals("Hải Châu", body.path("wardName").asString());
		assertEquals("A1", body.path("parts").path(0).path("code").asString());
		assertEquals("Floor 1", body.path("parts").path(0).path("location").asString());
		assertEquals(STATEMENT, body.path("statement").path("version").asString());
		assertTrue(body.path("statement").path("text").asString().startsWith("I confirm that this Sunvibe account is mine"));
	}

	@Test
	void details_reChecksSourceAndCreator() throws Exception {
		Passed marked = draft();
		TestUser owner = owner();
		Issued first = issued(marked.leadId(), address(owner));
		fixture.requalify(leader, marked.checklistId(), "OTHER");
		assertRefused(400, "CONFIRMATION_INVALID", details(owner, first.requestId(), first.code()));

		Passed reassigned = draft();
		Issued second = issued(reassigned.leadId(), address(owner));
		fixture.checklists().reassign(leader, reassigned.leadId(), otherSale);
		assertRefused(400, "CONFIRMATION_INVALID", details(owner, second.requestId(), second.code()));
	}

	@Test
	void wrongCodes_fiveAcrossVerbs_void() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		assertRefused(400, "CONFIRMATION_INVALID", details(owner, issued.requestId(), "WRONG000"));
		assertRefused(400, "CONFIRMATION_INVALID", details(owner, issued.requestId(), "WRONG000"));
		assertRefused(400, "CONFIRMATION_INVALID", confirm(owner, issued.requestId(), "WRONG000", passwordOf(owner), STATEMENT));
		assertRefused(400, "CONFIRMATION_INVALID", decline(owner, issued.requestId(), "WRONG000"));
		assertRefused(400, "CONFIRMATION_INVALID", decline(owner, issued.requestId(), "WRONG000"));

		assertEquals(5, failedAttempts(issued.requestId()));
		assertRefused(400, "CONFIRMATION_INVALID", details(owner, issued.requestId(), issued.code()));
		assertEquals(1, count("select count(*) from inventory_owner_requests where id = ? and voided_at is not null",
				issued.requestId()));
		assertEquals("WAITING", blockOk(sale, passed.leadId()).path("ownerState").asString(), "no oracle (spec 4.2)");
	}

	@Test
	void wrongCodes_parallel_allCount() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));
		CountDownLatch start = new CountDownLatch(1);
		List<CompletableFuture<Integer>> tries = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			tries.add(CompletableFuture.supplyAsync(() -> {
				try {
					start.await();
					return details(owner, issued.requestId(), "WRONG000").status();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}));
		}

		start.countDown();

		for (CompletableFuture<Integer> attempt : tries) {
			assertEquals(400, attempt.get(60, TimeUnit.SECONDS));
		}
		assertEquals(4, failedAttempts(issued.requestId()));
	}

	@Test
	void expired_refused() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));
		jdbc.update(SELECTED, Timestamp.from(Instant.now().minusSeconds(60)), issued.requestId());

		assertRefused(400, "CONFIRMATION_INVALID", details(owner, issued.requestId(), issued.code()));
		assertEquals(0, failedAttempts(issued.requestId()), "an expired code is no wrong code");
		assertEquals("DRAFT", blockOk(sale, passed.leadId()).path("status").asString());
	}

	@Test
	void notFoundRow_noSnapshot() throws Exception {
		Passed passed = draft();

		Issued issued = issued(passed.leadId(), "nobody.here@example.com");

		assertNull(snapshot(issued.requestId()));
		assertEquals(1, count("select count(*) from inventory_owner_requests where id = ? and target_user_id is null",
				issued.requestId()));
	}

	@Test
	void snapshot_clearedOnVoidWithdrawalAndExpiry() throws Exception {
		TestUser owner = owner();
		Passed voided = draft();
		Issued first = issued(voided.leadId(), address(owner));
		assertTrue(snapshot(first.requestId()).contains("leadCode"));
		issued(voided.leadId(), "someone.else@example.com");
		assertNull(snapshot(first.requestId()));

		Passed withdrawn = draft();
		Issued second = issued(withdrawn.leadId(), address(owner));
		confirmed(owner, second);
		assertEquals(204, withdraw(owner, second.requestId(), passwordOf(owner)).status());
		assertNull(snapshot(second.requestId()));

		Passed expired = draft();
		Issued third = issued(expired.leadId(), address(owner));
		jdbc.update(SELECTED, Timestamp.from(Instant.now().minusSeconds(60)), third.requestId());
		issued(expired.leadId(), "next.person@example.com");
		assertNull(snapshot(third.requestId()));
	}

	@Test
	void otherAccount_fourVerbs_404_counterUnchanged() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		TestUser stranger = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		assertRefused(404, "NOT_FOUND", details(stranger, issued.requestId(), issued.code()));
		assertRefused(404, "NOT_FOUND", confirm(stranger, issued.requestId(), issued.code(), passwordOf(stranger), STATEMENT));
		assertRefused(404, "NOT_FOUND", decline(stranger, issued.requestId(), issued.code()));
		assertRefused(404, "NOT_FOUND", withdraw(stranger, issued.requestId(), passwordOf(stranger)));

		assertEquals(0, failedAttempts(issued.requestId()));
		assertEquals(0, proofs(passed.leadId()));
	}

	@Test
	void sourceDiesAfterDetails_noProofNoNotice() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));
		assertEquals(200, details(owner, issued.requestId(), issued.code()).status());
		fixture.requalify(leader, passed.checklistId(), "OTHER");

		assertRefused(400, "CONFIRMATION_INVALID", confirm(owner, issued.requestId(), issued.code(), passwordOf(owner), STATEMENT));

		assertEquals(0, proofs(passed.leadId()));
		assertEquals(0, notices(owner, "OWNER_CONFIRMED"));
	}

	// ==================== confirm (S-6, S-7) ====================

	@Test
	void confirm_needsAllThree() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		assertRefused(400, "CONFIRMATION_INVALID", confirm(owner, issued.requestId(), issued.code(), passwordOf(owner), "other"));
		assertRefused(400, "CONFIRMATION_INVALID", confirm(owner, issued.requestId(), "WRONG000", passwordOf(owner), STATEMENT));
		assertRefused(400, "VALIDATION_FAILED", confirm(owner, issued.requestId(), issued.code(), "wrong-password", STATEMENT));
		assertEquals(0, proofs(passed.leadId()));

		confirmed(owner, issued);

		JsonNode block = blockOk(sale, passed.leadId());
		assertEquals("OWNER_CONFIRMED", block.path("status").asString());
		assertEquals("CONFIRMED", block.path("ownerState").asString());
		assertEquals("REQUIRED", block.path("ownerCheck").asString());
		Map<String, Object> proof = jdbc.queryForMap("select * from inventory_owner_proofs where lead_id = ?", passed.leadId());
		assertEquals(owner.userId(), proof.get("user_id"));
		assertEquals(STATEMENT, proof.get("statement_version"));
		assertEquals("REQUIRED", proof.get("owner_check"));
		assertEquals(0, proof.get("sign_in_losses"));
	}

	@Test
	void wrongPassword_400() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		Result refused = confirm(owner, issued.requestId(), issued.code(), "wrong-password", STATEMENT);

		assertRefused(400, "VALIDATION_FAILED", refused);
		assertEquals("currentPassword", refused.body().path("errors").path(0).path("field").asString());
	}

	@Test
	void locked_423() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		Result last = null;
		for (int i = 0; i < 12 && (last == null || last.status() != 423); i++) {
			last = confirm(owner, issued.requestId(), issued.code(), "wrong-password", STATEMENT);
		}

		assertRefused(423, "ACCOUNT_LOCKED", last);
	}

	@Test
	void otherAccount_404() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		assertRefused(404, "NOT_FOUND", confirm(owner(), issued.requestId(), issued.code(), "x", STATEMENT));
	}

	@Test
	void staff_404() throws Exception {
		Passed passed = draft();
		Issued issued = issued(passed.leadId(), "a.staff.member@example.com");
		jdbc.update("update inventory_owner_requests set target_user_id = ? where id = ?", otherSale.userId(), issued.requestId());

		assertRefused(404, "NOT_FOUND", details(otherSale, issued.requestId(), issued.code()));
		assertEquals(0, list(otherSale).size());
	}

	/** S-7, example 6: a PENDING account can confirm; no ACTIVE, phone or KYC condition. */
	@Test
	void pendingAccount_canConfirm() throws Exception {
		Passed passed = draft();
		TestUser owner = owner(UserStatus.PENDING);
		Issued issued = issued(passed.leadId(), address(owner));

		confirmed(owner, issued);

		assertEquals(1, proofs(passed.leadId()));
	}

	// ==================== insider (S-8) ====================

	@Test
	void reassigned_requestDead() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		fixture.checklists().reassign(leader, passed.leadId(), otherSale);

		assertRefused(400, "CONFIRMATION_INVALID", confirm(owner, issued.requestId(), issued.code(), passwordOf(owner), STATEMENT));
		assertEquals(0, proofs(passed.leadId()));
	}

	@Test
	void notice_commitsWithConfirmAndWithdraw() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		confirmed(owner, issued);
		assertEquals(1, notices(owner, "OWNER_CONFIRMED"));

		assertEquals(204, withdraw(owner, issued.requestId(), passwordOf(owner)).status());
		assertEquals(1, notices(owner, "OWNER_WITHDRAWN"));
	}

	// ==================== proofs (S-9) ====================

	/** US-2 AC-3: bind or replace of email keeps the user id, so the proof survives. */
	@Test
	void emailReplace_keepsProof() throws Exception {
		Passed passed = house(unique("Trần Phú"));
		TestUser owner = owner();
		proved(passed, owner);

		jdbc.update("update users set email = ?, email_key = ? where id = ?", "new." + address(owner), "new." + address(owner),
				owner.userId());

		assertEquals("CONFIRMED", blockOk(sale, passed.leadId()).path("ownerState").asString());
	}

	@Test
	void withdraw_beforeCompletion_toDraft() throws Exception {
		Passed passed = house(unique("Lý Tự Trọng"));
		TestUser owner = owner();
		Issued issued = proved(passed, owner);

		assertEquals(204, withdraw(owner, issued.requestId(), passwordOf(owner)).status());

		JsonNode block = blockOk(sale, passed.leadId());
		assertEquals("DRAFT", block.path("status").asString());
		assertEquals("NONE", block.path("ownerState").asString());
		assertEquals(1, count("select count(*) from inventory_owner_proofs where lead_id = ? and withdrawn_at is not null",
				passed.leadId()));
	}

	@Test
	void withdraw_wrongPassword_400_proofStays() throws Exception {
		Passed passed = house(unique("Yên Bái"));
		TestUser owner = owner();
		Issued issued = proved(passed, owner);

		assertRefused(400, "VALIDATION_FAILED", withdraw(owner, issued.requestId(), "wrong-password"));

		assertEquals("CONFIRMED", blockOk(sale, passed.leadId()).path("ownerState").asString());
	}

	/** A second confirm voids the first proof with no 500: the partial unique index counts one in force. */
	@Test
	void secondConfirm_voidsFirst() throws Exception {
		Passed passed = house(unique("Nguyễn Du"));
		TestUser owner = owner();
		Issued first = proved(passed, owner);
		assertEquals(204, withdraw(owner, first.requestId(), passwordOf(owner)).status());

		Issued second = issued(passed.leadId(), address(owner));
		confirmed(owner, second);

		assertEquals(2, proofs(passed.leadId()));
		assertEquals(1, count("select count(*) from inventory_owner_proofs where lead_id = ? and withdrawn_at is null"
				+ " and voided_at is null", passed.leadId()));
	}

	@Test
	void list_onlyLiveProofsOfCurrentRows() throws Exception {
		Passed passed = house(unique("Phan Đình Phùng"));
		TestUser owner = owner();
		Issued issued = proved(passed, owner);

		JsonNode confirmed = list(owner).path(0);
		assertEquals("CONFIRMED", confirmed.path("status").asString());
		assertEquals("HOUSE", confirmed.path("details").path("assetType").asString());
		assertFalse(confirmed.path("checkOpen").asBoolean());

		fixture.requalify(leader, passed.checklistId(), "OTHER");
		assertEquals(0, list(owner).size());
		assertRefused(404, "NOT_FOUND", withdraw(owner, issued.requestId(), passwordOf(owner)));
	}

	@Test
	void withdraw_deadProof_404() throws Exception {
		Passed passed = house(unique("Hoàng Văn Thụ"));
		TestUser owner = owner();
		Issued issued = proved(passed, owner);
		assertEquals(204, withdraw(owner, issued.requestId(), passwordOf(owner)).status());

		assertRefused(404, "NOT_FOUND", withdraw(owner, issued.requestId(), passwordOf(owner)));
	}

	/** I-3: a confirm voids an earlier row, live or not, so a dead proof does not fail the partial index. */
	@Test
	void confirm_afterDeadProof_noIndexFailure() throws Exception {
		Passed passed = house(unique("Nguyễn Hoàng"));
		TestUser owner = owner();
		proved(passed, owner);
		jdbc.update("update users set user_status = 'LOCK' where id = ?", owner.userId());
		jdbc.update("update users set user_status = 'ACTIVE' where id = ?", owner.userId());
		assertEquals("DRAFT", blockOk(sale, passed.leadId()).path("status").asString());

		Issued next = issued(passed.leadId(), address(owner));
		confirmed(owner, next);

		assertEquals("OWNER_CONFIRMED", blockOk(sale, passed.leadId()).path("status").asString());
		assertEquals(1, count("select count(*) from inventory_owner_proofs where lead_id = ? and voided_at is not null",
				passed.leadId()));
	}

	/** D5-10: a read before a loss stores the lower count, so the proof dies when the administrator commits. */
	@Test
	void confirm_duringOpenAdminTransaction_proofDiesOnCommit() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			jdbc.update("update users set user_status = 'LOCK' where id = ?", owner.userId());
			try {
				Result answer = confirm(owner, issued.requestId(), issued.code(), passwordOf(owner), STATEMENT);
				assertEquals(204, answer.status(), answer.rawBody());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});

		assertEquals(0, count("select sign_in_losses from inventory_owner_proofs where lead_id = ?", passed.leadId()));
		assertEquals(1, count("select sign_in_losses from users where id = ?", owner.userId()));
		JsonNode block = blockOk(sale, passed.leadId());
		assertEquals("DRAFT", block.path("status").asString());
		assertEquals("NONE", block.path("ownerState").asString());
	}

	@Test
	void request_onConfirmedRow_refused() throws Exception {
		Passed passed = house(unique("Ngũ Hành Sơn"));
		proved(passed, owner());

		assertRefused(409, "INTAKE_STATE", requestOwner(sale, passed.leadId(), "x@example.com", versionOf(sale, passed.leadId())));
	}

	@Test
	void request_noRow_refused() throws Exception {
		Passed passed = house(unique("Sơn Trà"));

		assertRefused(409, "INTAKE_STATE", requestOwner(sale, passed.leadId(), "x@example.com", null));
	}

	@Test
	void codes_neverStoredInClear() throws Exception {
		Passed passed = draft();
		TestUser owner = owner();
		Issued issued = issued(passed.leadId(), address(owner));

		assertNotEquals(issued.code(), jdbc.queryForObject("select code_hash from inventory_owner_requests where id = ?",
				String.class, issued.requestId()));
		assertEquals(64, jdbc.queryForObject("select length(code_hash) from inventory_owner_requests where id = ?",
				Integer.class, issued.requestId()));
	}
}
