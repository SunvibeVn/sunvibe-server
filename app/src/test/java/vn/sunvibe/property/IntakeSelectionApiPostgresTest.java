package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The intake block and the selection (property-create intake spec 4.1, 4.2, 4.4, 4.5, S-1, S-2, S-3), through the real filter
 * chain on real PASSED revisions. Needs Docker.
 */
class IntakeSelectionApiPostgresTest extends IntakeApiTestBase {

	@Override
	String phonePrefix() {
		return "0963";
	}

	// ==================== the gate (S-1) ====================

	@Test
	void block_usablePassed_showsTheSourceAndNoRow() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Bạch Đằng"), "P101", "P102");

		JsonNode block = blockOk(sale, passed.leadId());

		assertTrue(block.path("status").isNull());
		assertTrue(block.path("version").isNull());
		assertTrue(block.path("gate").path("ready").asBoolean());
		assertEquals(0, block.path("gate").path("reasons").size());
		assertEquals(passed.checklistId(), block.path("source").path("revisionId").asString());
		assertEquals("PARTS", block.path("source").path("rentalScope").asString());
		assertEquals("WHOLE_BUILDING", block.path("source").path("assetType").asString());
		assertEquals(2, block.path("source").path("spaces").size());
		JsonNode first = block.path("source").path("spaces").path(0);
		assertEquals(passed.spaceIds().getFirst(), first.path("spaceId").asString());
		assertEquals("P101", first.path("code").asString());
		assertEquals("Floor 1", first.path("location").asString());
		assertEquals("ENTERED_ALL", first.path("accessLimits").asString());
		assertFalse(first.path("sourceDate").asString().isEmpty());
		assertEquals("NONE", block.path("ownerState").asString());
		assertEquals("NONE", block.path("ownerCheck").asString());
		assertEquals("NONE", block.path("caseState").asString());
		assertEquals("NONE", block.path("readiness").path("state").asString());
		assertTrue(block.path("result").isNull());
		assertTrue(block.path("earlierResult").isNull());
	}

	@Test
	void gate_eachReasonOnRead() throws Exception {
		String shortTerm = survey.lead(leader, sale, "CONTACTED", "SHORT_TERM_RENT", null);
		assertEquals(Set.of("NOT_LONG_TERM", "NOT_QUALIFIED", "NO_PASSED"), reasons(blockOk(sale, shortTerm)));

		String contacted = survey.lead(leader, sale, "CONTACTED");
		assertEquals(Set.of("NOT_QUALIFIED", "NO_PASSED"), reasons(blockOk(sale, contacted)));

		Passed held = house(unique("Lê Duẩn"));
		hold(held.leadId());
		assertEquals(Set.of("NOT_QUALIFIED"), reasons(blockOk(sale, held.leadId())));

		Passed photoHeld = house(unique("Trần Phú"));
		jdbc.update("update survey_media set held_at = ?, held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT' where id = ?",
				Timestamp.from(Instant.now()), admin.userId(), photoHeld.photoIds().getFirst());
		assertEquals(Set.of("PASSED_NOT_USABLE"), reasons(blockOk(sale, photoHeld.leadId())));

		Passed mismatch = fixture.parts(leader, sale, "HOUSE", unique("Hùng Vương"), "P1", "P2");
		assertEquals(Set.of("TYPE_SCOPE_MISMATCH"), reasons(blockOk(sale, mismatch.leadId())));

		Passed restricted = house(unique("Quang Trung"));
		jdbc.update("insert into inventory_suspensions (id, lead_id, reason, created_by_user_id, created_at)"
				+ " values (?, ?, 'Data request', ?, ?)", ids.generateId(), restricted.leadId(), manager.userId(),
				Timestamp.from(Instant.now()));
		assertEquals(Set.of("RESTRICTED"), reasons(blockOk(sale, restricted.leadId())));
	}

	@Test
	void gate_errorShape_fieldPerReason() throws Exception {
		Passed passed = house(unique("Nguyễn Trãi"));
		hold(passed.leadId());

		Result refused = select(sale, passed.leadId(), passed.spaceIds(), null);

		assertRefused(409, "INTAKE_GATE_FAILED", refused);
		JsonNode errors = refused.body().path("errors");
		assertEquals(1, errors.size());
		assertEquals("gate.NOT_QUALIFIED", errors.path(0).path("field").asString());
		assertFalse(errors.path(0).path("message").asString().isEmpty());
		assertTrue(errors.path(0).path("allowedValues").isMissingNode());
	}

	/** spec 4.4: nobody can skip the gate; ADMIN is refused at the assignee check, and has no override either. */
	@Test
	void admin_cannotSkip() throws Exception {
		Passed passed = house(unique("Lý Thường Kiệt"));
		hold(passed.leadId());

		assertRefused(403, "INTAKE_NOT_ASSIGNEE", select(admin, passed.leadId(), passed.spaceIds(), null));
		assertEquals(0, count("select count(*) from inventory_intakes where lead_id = ?", passed.leadId()));
	}

	/** D5-12: a wrong-asset mark with no COMPLETED intake on its source leaves nothing to reconcile. */
	@Test
	void gate_wrongAssetMark_noCompletedIntake_stopsNothing() throws Exception {
		Passed first = house(unique("Hải Phòng"));
		fixture.requalify(leader, first.checklistId(), "WRONG_ASSET");

		Passed second = fixture.revisit(leader, sale, first.leadId(), "WHOLE", "HOUSE", unique("Hải Phòng"), "A1");

		JsonNode block = blockOk(sale, first.leadId());
		assertTrue(block.path("gate").path("ready").asBoolean(), block.toString());
		assertEquals(second.checklistId(), block.path("source").path("revisionId").asString());
	}

	// ==================== the selection (S-2) ====================

	@Test
	void selection_first_createsDraft() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Ông Ích Khiêm"), "P101", "P102", "P103");

		JsonNode block = selected(passed.leadId(), passed.spaceIds().subList(0, 2));

		assertEquals("DRAFT", block.path("status").asString());
		assertEquals(0L, block.path("version").asLong());
		assertEquals(2, block.path("selection").path("spaceIds").size());
		Map<String, Object> row = intakeRow(passed.leadId());
		assertEquals(1, row.get("intake_no"));
		assertEquals("DRAFT", row.get("status"));
		assertEquals(passed.checklistId(), row.get("source_checklist_id"));
		assertEquals("PARTS", row.get("rental_scope"));
		assertEquals("WHOLE_BUILDING", row.get("asset_type"));
		assertEquals(WARD, row.get("ward_code"));
	}

	@Test
	void selection_outsidePassed_400() throws Exception {
		Passed passed = house(unique("Phan Châu Trinh"));

		assertRefused(400, "INTAKE_SELECTION_INVALID", select(sale, passed.leadId(), List.of(ids.generateId()), null));
		assertRefused(400, "INTAKE_SELECTION_INVALID", select(sale, passed.leadId(),
				List.of(passed.spaceIds().getFirst(), passed.spaceIds().getFirst()), null));
		assertEquals(0, count("select count(*) from inventory_intakes where lead_id = ?", passed.leadId()));
	}

	/** A WHOLE source has one space, and it is the selection. */
	@Test
	void selection_whole_needsTheOneSpace() throws Exception {
		Passed passed = house(unique("Bùi Thị Xuân"));

		assertEquals(1, selected(passed.leadId(), passed.spaceIds()).path("selection").path("spaceIds").size());
	}

	@Test
	void selection_changeClearsEverything() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Điện Biên Phủ"), "P101", "P102");
		selected(passed.leadId(), passed.spaceIds());
		String intake = intakeRow(passed.leadId()).get("id").toString();
		jdbc.update("update inventory_intakes set status = 'AWAITING_OWNER', target_mode = 'CREATE',"
				+ " availability = '[]' where id = ?", intake);
		String request = ids.generateId();
		jdbc.update("insert into inventory_owner_requests (id, intake_id, code_hash, expires_at, created_by_user_id, created_at)"
				+ " values (?, ?, ?, ?, ?, ?)", request, intake, "h".repeat(64), Timestamp.from(Instant.now().plusSeconds(600)),
				sale.userId(), Timestamp.from(Instant.now()));

		JsonNode block = selected(passed.leadId(), passed.spaceIds().subList(0, 1));

		assertEquals("DRAFT", block.path("status").asString());
		Map<String, Object> row = intakeRow(passed.leadId());
		assertEquals(intake, row.get("id"), "the same row goes on");
		assertEquals(null, row.get("target_mode"));
		assertEquals(null, row.get("availability"));
		assertEquals(1, count("select count(*) from inventory_owner_requests where id = ? and voided_at is not null", request));
	}

	/** spec 4.10: a forced property binds one scope only; a new selection is a new pair. */
	@Test
	void selection_otherScope_noForcedProperty() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Ngô Quyền"), "P101", "P102");
		selected(passed.leadId(), passed.spaceIds());
		String intake = intakeRow(passed.leadId()).get("id").toString();
		String scope = jdbc.queryForObject("select encode(sha256(convert_to(?, 'UTF8')), 'hex')", String.class,
				String.join(",", passed.spaceIds().stream().sorted().toList()));
		String property = property(ids.generateId(), WARD, "Hải Châu", "1 Ngô Quyền", "WHOLE_BUILDING", "1 ngo quyen");
		jdbc.update("insert into inventory_duplicate_cases (id, intake_id, candidate_property_id, pair_key, scope_hash, signal,"
				+ " status, outcome, decided_at, created_at) values (?, ?, ?, ?, ?, 'ADDRESS', 'DECIDED', 'SAME_ASSET', ?, ?)",
				ids.generateId(), intake, property, "P:" + ids.generateId(), scope, Timestamp.from(Instant.now()),
				Timestamp.from(Instant.now()));

		JsonNode block = selected(passed.leadId(), passed.spaceIds().subList(0, 1));

		assertTrue(block.path("forcedPropertyCode").isNull(), block.toString());
		assertEquals("NONE", block.path("caseState").asString());
	}

	/** I-1: two first selections leave one row; the other answers 409, never 500. */
	@Test
	void selection_twoFirstCalls_oneRow() throws Exception {
		Passed passed = house(unique("Hoàng Diệu"));
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<Result> first = CompletableFuture.supplyAsync(() -> afterStart(start, passed));
		CompletableFuture<Result> second = CompletableFuture.supplyAsync(() -> afterStart(start, passed));

		start.countDown();

		List<Integer> statuses = List.of(first.get(60, TimeUnit.SECONDS).status(), second.get(60, TimeUnit.SECONDS).status());
		assertEquals(1, count("select count(*) from inventory_intakes where lead_id = ?", passed.leadId()));
		assertTrue(statuses.contains(200), statuses.toString());
		assertTrue(statuses.contains(409), statuses.toString());
	}

	private Result afterStart(CountDownLatch start, Passed passed) {
		try {
			start.await();
			return select(sale, passed.leadId(), passed.spaceIds(), null);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ==================== state and version (S-3) ====================

	@Test
	void get_changesNothing() throws Exception {
		Passed passed = house(unique("Nguyễn Chí Thanh"));
		selected(passed.leadId(), passed.spaceIds());
		Map<String, Object> before = intakeRow(passed.leadId());

		blockOk(sale, passed.leadId());
		fixture.requalify(leader, passed.checklistId(), "OTHER");
		JsonNode dead = blockOk(sale, passed.leadId());

		assertTrue(dead.path("status").isNull(), "a dead source reads none");
		assertEquals(before, intakeRow(passed.leadId()), "a GET stores nothing");
	}

	@Test
	void deadSource_nextCommandAbandons() throws Exception {
		Passed first = house(unique("Trưng Nữ Vương"));
		selected(first.leadId(), first.spaceIds());
		String dead = intakeRow(first.leadId()).get("id").toString();
		fixture.requalify(leader, first.checklistId(), "OTHER");
		Passed second = fixture.revisit(leader, sale, first.leadId(), "WHOLE", "HOUSE", unique("Trưng Nữ Vương"), "A1");

		JsonNode block = selected(first.leadId(), second.spaceIds());

		assertEquals("DRAFT", block.path("status").asString());
		assertEquals("ABANDONED", jdbc.queryForObject("select status from inventory_intakes where id = ?", String.class, dead));
		Map<String, Object> row = intakeRow(first.leadId());
		assertEquals(2, row.get("intake_no"));
		assertEquals(second.checklistId(), row.get("source_checklist_id"));
	}

	@Test
	void version_nullAndValueRules() throws Exception {
		Passed passed = house(unique("Thái Phiên"));

		assertRefused(409, "CONCURRENT_MODIFICATION", select(sale, passed.leadId(), passed.spaceIds(), 0L));
		selected(passed.leadId(), passed.spaceIds());
		assertRefused(409, "CONCURRENT_MODIFICATION", select(sale, passed.leadId(), passed.spaceIds(), null));
		assertRefused(409, "CONCURRENT_MODIFICATION", select(sale, passed.leadId(), passed.spaceIds(), 9L));
		SurveyApiTestSupport.ok(select(sale, passed.leadId(), passed.spaceIds(), 0L));
	}

	@Test
	void completedRow_selection_409() throws Exception {
		Passed passed = house(unique("Pasteur"));
		selected(passed.leadId(), passed.spaceIds());
		Map<String, Object> row = intakeRow(passed.leadId());
		completeBySql(row.get("id").toString(), passed);

		assertRefused(409, "INTAKE_STATE", select(sale, passed.leadId(), passed.spaceIds(), versionOf(sale, passed.leadId())));
	}

	private void completeBySql(String intake, Passed passed) {
		String request = ids.generateId();
		String proof = ids.generateId();
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("insert into inventory_owner_requests (id, intake_id, code_hash, expires_at, consumed_at, created_by_user_id,"
				+ " created_at) values (?, ?, ?, ?, ?, ?, ?)", request, intake, "h".repeat(64), now, now, sale.userId(), now);
		jdbc.update("insert into inventory_owner_proofs (id, intake_id, request_id, lead_id, intent_cycle, checklist_id, user_id,"
				+ " scope_hash, statement_version, confirmed_at, owner_check, sign_in_losses) values (?, ?, ?, ?, 1, ?, ?, ?,"
				+ " 'owner-link-2026-11', ?, 'DONE', 0)", proof, intake, request, passed.leadId(), passed.checklistId(),
				sale.userId(), "s".repeat(64), now);
		jdbc.update("update inventory_intakes set status = 'COMPLETED', result_property_id = ?, completed_at = ?, proof_id = ?,"
				+ " version = 5 where id = ?", property(sale.userId(), WARD, "Hải Châu", "x", "HOUSE", "x"), now, proof, intake);
	}

	// ==================== the trail ====================

	@Test
	void block_writesAccessLog() throws Exception {
		Passed passed = house(unique("Lê Lợi"));

		blockOk(sale, passed.leadId());

		assertEquals(1, count("select count(*) from inventory_access_logs where lead_id = ? and user_id = ?"
				+ " and action = 'BLOCK_READ'", passed.leadId(), sale.userId()));
	}

	@Test
	void event_writtenBySelection() throws Exception {
		Passed passed = house(unique("Hàm Nghi"));

		selected(passed.leadId(), passed.spaceIds());

		assertEquals(1, count("select count(*) from inventory_intake_events e join inventory_intakes i on i.id = e.intake_id"
				+ " where i.lead_id = ? and e.actor_user_id = ? and e.action = 'SELECTION'", passed.leadId(), sale.userId()));
	}

	// ==================== who may read ====================

	@Test
	void block_otherSale_404() throws Exception {
		Passed passed = house(unique("Lê Hồng Phong"));

		assertRefused(404, "NOT_FOUND", block(otherSale, passed.leadId()));
	}

	@Test
	void block_manager_ok() throws Exception {
		Passed passed = house(unique("Đống Đa"));

		assertEquals(passed.checklistId(), blockOk(manager, passed.leadId()).path("source").path("revisionId").asString());
	}

	@Test
	void selection_otherAssigneeOrManager_refused() throws Exception {
		Passed passed = house(unique("Hùng Vương"));

		assertRefused(404, "NOT_FOUND", select(otherSale, passed.leadId(), passed.spaceIds(), null));
		assertRefused(403, "INTAKE_NOT_ASSIGNEE", select(manager, passed.leadId(), passed.spaceIds(), null));
	}
}
