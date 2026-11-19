package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The suspension and its release over HTTP on PostgreSQL (brokerage-agreement BR-15 to BR-17, US-5 AC-2, AC-5; E05-S8
 * spec 3.1, 3.2, 4, S8-D6; SU-1 to SU-3, RL-1 to RL-4, SR-1): a manager suspends an agreement in effect, a manager who
 * is not the lead's assignee lifts it, the records follow the rule of S8-D6, and nothing is written on a refusal. Needs
 * Docker.
 */
class AgreementSuspensionApiPostgresTest extends AgreementStopApiTestBase {

	private static final List<String> REASONS = List.of("COMPLAINT", "SUSPECTED_FRAUD", "DISPUTE", "NEEDS_CHECK");

	@Override
	String phonePrefix() {
		return "0924";
	}

	/** SU-1: a SALES_MANAGER suspends with a note; one row, one open record, one event; the answer reads SUSPENDED. */
	@Test
	void suspend_managerWithNote_suspendedRecordRowEvent() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		int outbox = outboxRows(chain.unitId());

		Result answer = suspendAgreement(manager, id, "COMPLAINT", "  Tenant reported a different price.  ");

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("SUSPENDED", status(id));
		Map<String, Object> record = jdbc.queryForMap("select * from brokerage_agreement_suspensions where agreement_id = ?",
				id);
		assertEquals("COMPLAINT", record.get("reason_type"));
		assertEquals("Tenant reported a different price.", record.get("note"));
		assertEquals(manager.userId(), record.get("suspended_by"));
		assertEquals(null, record.get("released_at"));
		assertEquals("SUSPENDED 1 " + manager.userId(), events(id).getLast());
		assertEquals(outbox + 1, outboxRows(chain.unitId()));
		JsonNode advertising = detail(manager, id).path("advertising");
		assertFalse(advertising.path("allowed").asBoolean());
		assertTrue(advertising.path("reasonGroups").toString().contains("\"SUSPENDED\""), advertising.toString());
	}

	/** SU-1: a SALE_LEADER who is the basis lead's assignee may suspend; independence is asked only for the release. */
	@Test
	void suspend_saleLeaderAssignee_suspended() throws Exception {
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), leader);
		int outbox = outboxRows(chain.unitId());

		Result answer = suspendAgreement(leader, chain.agreementId(), "NEEDS_CHECK", null);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("SUSPENDED", status(chain.agreementId()));
		assertEquals(1, openSuspensions(chain.agreementId()));
		assertEquals("SUSPENDED 1 " + leader.userId(), events(chain.agreementId()).getLast());
		assertEquals(outbox + 1, outboxRows(chain.unitId()));
	}

	/** SU-1: a SALE is refused by the method rule; the body's rules answer 400 on their field; nothing written. */
	@Test
	void suspend_saleOrBadBody_forbiddenOrValidation() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		WriteSnapshot before = snapshot(id);

		assertCode(403, "FORBIDDEN", suspendAgreement(sale, id, "COMPLAINT", null));
		Result unknown = suspendAgreement(manager, id, "UNHAPPY_OWNER", null);
		assertValidation(unknown, "reasonType", REASONS);
		assertFalse(unknown.rawBody().contains("UNHAPPY_OWNER"), unknown.rawBody());
		assertValidation(suspendAgreement(manager, id, null, null), "reasonType", REASONS);
		for (String note : List.of("a".repeat(501), " " + "a".repeat(499) + " ", "A\u0001B")) {
			assertValidation(suspendAgreement(manager, id, "COMPLAINT", note), "note", null);
		}
		assertMalformed(postRaw(manager, AGREEMENTS + "/" + id + "/suspension", "not json"));
		assertNothingWritten(before);
	}

	/** The access cases: no token, a CUSTOMER, unknown and malformed ids answer as the spec says; nothing written. */
	@Test
	void suspend_unknownMalformedNoTokenCustomer_notFoundUnauthorizedForbidden() throws Exception {
		Chain chain = inEffect();
		WriteSnapshot before = snapshot(chain.agreementId());

		assertEquals(401, suspendAgreement(null, chain.agreementId(), "COMPLAINT", null).status());
		assertCode(403, "FORBIDDEN", suspendAgreement(chain.owner(), chain.agreementId(), "COMPLAINT", null));
		Result unknown = suspendAgreement(manager, "01JUNKNOWN0000000000000000", "COMPLAINT", null);
		Result malformed = suspendAgreement(manager, "not-an-id", "COMPLAINT", null);
		assertCode(404, "NOT_FOUND", unknown);
		assertCode(404, "NOT_FOUND", malformed);
		assertEquals(unknown.body().path("message"), malformed.body().path("message"));
		assertEquals(unknown.body().path("errors"), malformed.body().path("errors"));
		assertNothingWritten(before);
	}

	/** SU-2: not in effect, already suspended, ended (stored or past its end): each its code, nothing written. */
	@Test
	void suspend_wrongState_conflictCodes() throws Exception {
		Chain drafting = drafted(today().plusDays(10), today().plusMonths(6));
		assertRefusedNothingWritten(drafting.agreementId(), "AGREEMENT_NOT_IN_EFFECT");
		Chain awaiting = sent(today().plusDays(10), today().plusMonths(6));
		assertRefusedNothingWritten(awaiting.agreementId(), "AGREEMENT_NOT_IN_EFFECT");

		Chain suspended = inEffect();
		suspended(manager, suspended.agreementId());
		assertRefusedNothingWritten(suspended.agreementId(), "AGREEMENT_ALREADY_SUSPENDED");

		assertEquals(204, terminate(sale, drafting.agreementId(), ownerWithdrew()).status());
		assertRefusedNothingWritten(drafting.agreementId(), "AGREEMENT_ENDED");

		Chain pastEnd = inEffect();
		movePeriod(pastEnd.agreementId(), 1, today().minusMonths(3), today().minusDays(1));
		assertRefusedNothingWritten(pastEnd.agreementId(), "AGREEMENT_ENDED");
	}

	/** SU-3: the start that came and was not stored is stored first, its row before the suspension's; v1 EFFECTIVE. */
	@Test
	void suspend_startDueNotStored_startedRowThenSuspended() throws Exception {
		Chain chain = activated(today().plusDays(2), today().plusMonths(6));
		movePeriod(chain.agreementId(), 1, today().minusDays(1), today().plusMonths(6));

		suspended(manager, chain.agreementId());

		List<String> events = events(chain.agreementId());
		assertEquals(List.of("VERSION_STARTED 1 -", "SUSPENDED 1 " + manager.userId()),
				events.subList(events.size() - 2, events.size()));
		assertEquals(startOf(today().minusDays(1)), eventAt(chain.agreementId(), "VERSION_STARTED"));
		assertEquals("EFFECTIVE", versionStatus(chain.agreementId(), 1));
		assertEquals("SUSPENDED", status(chain.agreementId()));
	}

	/** RL-1: an independent ADMIN lifts it: ACTIVE, the record closed, one row, one event; the answer allowed again. */
	@Test
	void release_independentAdmin_activeAnswerBack() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		suspended(manager, id);
		int outbox = outboxRows(chain.unitId());

		Result answer = releaseSuspension(admin, id, rowVersion(id), "  The price was checked.  ");

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("ACTIVE", status(id));
		Map<String, Object> record = jdbc.queryForMap("select * from brokerage_agreement_suspensions where agreement_id = ?",
				id);
		assertEquals(admin.userId(), record.get("released_by"));
		assertEquals("The price was checked.", record.get("release_reason"));
		assertEquals("SUSPENSION_RELEASED 1 " + admin.userId(), events(id).getLast());
		assertEquals(outbox + 1, outboxRows(chain.unitId()));
		JsonNode advertising = detail(admin, id).path("advertising");
		assertTrue(advertising.path("allowed").asBoolean(), advertising.toString());
	}

	/** RL-2: the assignee, a SALE, a stale data version, the wrong states and the bodies' rules; nothing written. */
	@Test
	void release_refusals_codesNothingWritten() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		suspended(manager, id);
		WriteSnapshot before = snapshot(id);

		fixture.checklists().reassign(leader, chain.leadId(), leader);
		assertCode(403, "AGREEMENT_RELEASER_NOT_INDEPENDENT", releaseSuspension(leader, id));
		assertCode(403, "FORBIDDEN", releaseSuspension(sale, id));
		Result stale = releaseSuspension(manager, id, rowVersion(id) - 1, "The check is done.");
		assertCode(409, "CONCURRENT_MODIFICATION", stale);
		assertEquals("expectedDataVersion", stale.body().path("errors").path(0).path("field").asString());
		assertValidation(releaseSuspension(manager, id, null, "The check is done."), "expectedDataVersion", null);
		assertValidation(releaseSuspension(manager, id, -1, "The check is done."), "expectedDataVersion", null);
		for (String reason : java.util.Arrays.asList(null, "   ", "a".repeat(501), " " + "a".repeat(499) + " ", "A\u0001B")) {
			assertValidation(releaseSuspension(manager, id, rowVersion(id), reason), "reason", null);
		}
		assertMalformed(postRaw(manager, AGREEMENTS + "/" + id + "/suspension-release", "not json"));
		assertNothingWritten(before);

		Chain active = inEffect();
		WriteSnapshot activeBefore = snapshot(active.agreementId());
		assertCode(409, "AGREEMENT_NOT_SUSPENDED", releaseSuspension(manager, active.agreementId()));
		assertNothingWritten(activeBefore);

		movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));
		WriteSnapshot pastEnd = snapshot(id);
		assertCode(409, "AGREEMENT_ENDED", releaseSuspension(manager, id));
		assertNothingWritten(pastEnd);
		JsonNode detail = detail(manager, id);
		assertEquals("EXPIRED", detail.path("status").asString());
		assertTrue(detail.path("suspensions").path(0).path("releasedAt").isNull(), detail.toString());
	}

	@Test
	void release_unknownMalformedNoTokenCustomer_notFoundUnauthorizedForbidden() throws Exception {
		Chain chain = inEffect();
		suspended(manager, chain.agreementId());
		WriteSnapshot before = snapshot(chain.agreementId());

		assertEquals(401, releaseSuspension(null, chain.agreementId()).status());
		assertCode(403, "FORBIDDEN", releaseSuspension(chain.owner(), chain.agreementId()));
		Result unknown = releaseSuspension(manager, "01JUNKNOWN0000000000000000", 0, "The check is done.");
		Result malformed = releaseSuspension(manager, "not-an-id", 0, "The check is done.");
		assertCode(404, "NOT_FOUND", unknown);
		assertCode(404, "NOT_FOUND", malformed);
		assertEquals(unknown.body().path("message"), malformed.body().path("message"));
		assertEquals(unknown.body().path("errors"), malformed.body().path("errors"));
		assertNothingWritten(before);
	}

	/** RL-3: suspended and lifted again and again: the newest 20 records first, the open one on top, and more. */
	@Test
	void release_suspendAgain_recordsNewestFirstTwentyShown() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		for (int index = 0; index < 21; index++) {
			suspended(manager, id);
			assertEquals(204, releaseSuspension(admin, id).status());
		}
		suspended(manager, id);

		JsonNode detail = detail(manager, id);

		assertEquals(20, detail.path("suspensions").size());
		assertTrue(detail.path("moreSuspensions").asBoolean());
		assertTrue(detail.path("suspensions").path(0).path("releasedAt").isNull(), detail.toString());
		assertFalse(detail.path("suspensions").path(1).path("releasedAt").isNull(), detail.toString());
		Instant newest = Instant.parse(detail.path("suspensions").path(0).path("suspendedAt").asString());
		Instant next = Instant.parse(detail.path("suspensions").path(1).path("suspendedAt").asString());
		assertTrue(newest.isAfter(next), detail.toString());
		assertEquals(22, suspensionRows(id));
		assertEquals(1, openSuspensions(id));
	}

	/** RL-4: the start that came while suspended is stored first, then the release; a second release is refused. */
	@Test
	void release_startCameWhileSuspended_startedRowThenReleased() throws Exception {
		Chain chain = activated(today().plusDays(2), today().plusMonths(6));
		String id = chain.agreementId();
		suspended(manager, id);
		movePeriod(id, 1, today().minusDays(1), today().plusMonths(6));

		assertEquals(204, releaseSuspension(admin, id).status());

		List<String> events = events(id);
		assertEquals(List.of("VERSION_STARTED 1 -", "SUSPENSION_RELEASED 1 " + admin.userId()),
				events.subList(events.size() - 2, events.size()));
		assertEquals("EFFECTIVE", versionStatus(id, 1));
		WriteSnapshot before = snapshot(id);
		assertCode(409, "AGREEMENT_NOT_SUSPENDED", releaseSuspension(admin, id));
		assertNothingWritten(before);
	}

	/**
	 * SR-1: after each command on each state it acts on, at most one open record, open only while SUSPENDED or in
	 * TERMINATED or EXPIRED reached from it; the database refuses a second open one; a release whose open record is
	 * missing answers 500 and rolls back whole.
	 */
	@Test
	void suspensionRecords_everyCommandAndState_ruleHolds() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		suspended(manager, id);
		assertEquals(1, openSuspensions(id));
		assertEquals(204, releaseSuspension(admin, id).status());
		assertEquals(0, openSuspensions(id));
		suspended(manager, id);
		assertEquals(204, terminate(manager, id, ownerWithdrew()).status());
		assertEquals("TERMINATED", status(id));
		assertEquals(1, openSuspensions(id));

		Chain expiring = inEffect();
		suspended(manager, expiring.agreementId());
		movePeriod(expiring.agreementId(), 1, today().minusMonths(3), today().minusDays(1));
		commands.storeDueExpiries();
		assertEquals("EXPIRED", status(expiring.agreementId()));
		assertEquals(1, openSuspensions(expiring.agreementId()));

		Chain cancelled = drafted(today().plusDays(10), today().plusMonths(6));
		assertEquals(204, cancel(sale, cancelled.agreementId(), rowVersion(cancelled.agreementId())).status());
		assertEquals(0, suspensionRows(cancelled.agreementId()));
		Chain active = inEffect();
		assertEquals(204, suspendAgreement(manager, active.agreementId(), "DISPUTE", null).status());
		assertEquals(204, releaseSuspension(admin, active.agreementId()).status());
		assertEquals("ACTIVE", status(active.agreementId()));
		assertEquals(0, openSuspensions(active.agreementId()));

		Chain broken = inEffect();
		suspended(manager, broken.agreementId());
		DataIntegrityViolationException second = assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("insert into brokerage_agreement_suspensions (id, agreement_id, reason_type,"
						+ " suspended_by, suspended_at) values (?, ?, 'DISPUTE', ?, ?)", ids.generateId(),
						broken.agreementId(), manager.userId(), Timestamp.from(Instant.now())));
		assertTrue(second.getMessage().contains("uq_brokerage_agreement_suspensions_open"), second.getMessage());
		jdbc.update("update brokerage_agreement_suspensions set released_by = ?, released_at = ?, release_reason ="
				+ " 'Closed by hand.' where agreement_id = ?", admin.userId(), Timestamp.from(Instant.now()),
				broken.agreementId());
		WriteSnapshot before = snapshot(broken.agreementId());

		assertCode(500, "INTERNAL_ERROR", releaseSuspension(admin, broken.agreementId()));

		assertNothingWritten(before);
		assertEquals("SUSPENDED", status(broken.agreementId()));
	}

	private void assertRefusedNothingWritten(String agreementId, String code) throws Exception {
		WriteSnapshot before = snapshot(agreementId);
		assertCode(409, code, suspendAgreement(manager, agreementId, "COMPLAINT", null));
		assertNothingWritten(before);
	}
}
