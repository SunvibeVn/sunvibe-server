package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The termination over HTTP on PostgreSQL (brokerage-agreement BR-14, BR-16, BR-17, US-5 AC-2; E05-S8 spec 3.3, 4,
 * S8-D1, S8-D5; TE-1 to TE-7, Review Focus 4): who records which reason, the fields of each type, the answer off from
 * the commit, the pending version cancelled, the due lapse's row first, and nothing written on a refusal. Needs Docker.
 */
class AgreementTerminationApiPostgresTest extends AgreementStopApiTestBase {

	private static final List<String> TYPES = List.of("OWNER_WITHDREW", "UNIT_LET", "SUNVIBE_TERMINATED");
	private static final List<String> CHANNELS = List.of("PHONE_CALL", "IN_PERSON", "MESSAGE", "EMAIL", "OTHER");

	@Override
	String phonePrefix() {
		return "0925";
	}

	/** TE-1: the assigned Sale records that the unit is let; the answer is off; one event; no lead, notice or access write. */
	@Test
	void terminate_unitLetByAssignedSale_terminatedEventNoLeadChange() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		List<Object> lead = leadSnapshot(chain.leadId());
		int notices = notices(chain.owner());
		int access = accessRows(id);
		int outbox = outboxRows(chain.unitId());

		Result answer = terminate(sale, id, unitLet(today(), "UNKNOWN", null));

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(outbox + 1, outboxRows(chain.unitId()));
		assertEquals("{\"unitId\": \"" + chain.unitId() + "\"}", jdbc.queryForObject("select payload::text from"
				+ " outbox_events where aggregate_id = ? order by id desc limit 1", String.class, chain.unitId()));
		assertEquals(access, accessRows(id));
		assertEquals(lead, leadSnapshot(chain.leadId()));
		assertEquals(notices, notices(chain.owner()));
		Map<String, Object> row = agreementRow(id);
		assertEquals("TERMINATED", row.get("status"));
		assertNotNull(row.get("ended_at"));
		assertEquals("UNIT_LET", row.get("termination_type"));
		assertEquals("TERMINATED " + 1 + " " + sale.userId(), events(id).getLast());
		assertTrue(standingEnded(id));
		assertFalse(unfinishedOf(chain.unitId()).contains(id));
		JsonNode advertising = detail(sale, id).path("advertising");
		assertFalse(advertising.path("allowed").asBoolean());
		assertEquals("[\"ENDED\"]", advertising.path("reasonGroups").toString());
	}

	/** TE-2: the owner's withdrawal by the assignee or any manager; a SALE who is not the assignee does not see the lead. */
	@Test
	void terminate_ownerWithdrew_assigneeAndManagersOtherSaleNotFound() throws Exception {
		for (TestUser recorder : List.of(sale, leader, manager, admin)) {
			Chain chain = drafted(today().plusDays(10), today().plusMonths(6));
			if (recorder == sale) {
				WriteSnapshot before = snapshot(chain.agreementId());
				assertCode(404, "NOT_FOUND", terminate(otherSale, chain.agreementId(), ownerWithdrew()));
				assertNothingWritten(before);
			}

			Result answer = terminate(recorder, chain.agreementId(), ownerWithdrew());

			assertEquals(204, answer.status(), recorder.userId() + " " + answer.rawBody());
			assertEquals("TERMINATED", status(chain.agreementId()));
			assertEquals("TERMINATED 1 " + recorder.userId(), events(chain.agreementId()).getLast());
		}
	}

	/** TE-2: the unit let is the assignee's, Sunvibe's decision a manager's. */
	@Test
	void terminate_unitLetAndSunvibe_byRole() throws Exception {
		Chain chain = drafted(today().plusDays(10), today().plusMonths(6));
		WriteSnapshot before = snapshot(chain.agreementId());
		assertCode(403, "AGREEMENT_ASSIGNEE_ONLY", terminate(manager, chain.agreementId(), unitLet()));
		assertCode(403, "AGREEMENT_TERMINATION_NOT_ALLOWED", terminate(sale, chain.agreementId(),
				sunvibeTerminated("A dispute with the owner.")));
		assertNothingWritten(before);

		Result sunvibe = terminate(manager, chain.agreementId(), sunvibeTerminated("A dispute with the owner."));
		assertEquals(204, sunvibe.status(), sunvibe.rawBody());
		assertEquals("SUNVIBE_TERMINATED", agreementRow(chain.agreementId()).get("termination_type"));
		assertEquals("A dispute with the owner.", agreementRow(chain.agreementId()).get("termination_note"));

		Chain byLeader = drafted(today().plusDays(10), today().plusMonths(6));
		fixture.checklists().reassign(leader, byLeader.leadId(), leader);
		Result let = terminate(leader, byLeader.agreementId(), unitLet());
		assertEquals(204, let.status(), let.rawBody());
		assertEquals("UNIT_LET", agreementRow(byLeader.agreementId()).get("termination_type"));
	}

	/**
	 * TE-3 and the 400 cases of spec section 4: the type, the fields of another type, the missing ones, the date's bound,
	 * the note's rules (501 as sent refused even when it trims to 499, plan E05S8-P5), a body or a date that cannot be
	 * read; no message quotes a value; nothing written.
	 */
	@Test
	void terminate_badBody_fieldEntriesNoValueQuoted() throws Exception {
		Chain chain = drafted(today().plusDays(10), today().plusMonths(6));
		String id = chain.agreementId();
		WriteSnapshot before = snapshot(id);
		Map<String, Object> noType = ownerWithdrew();
		noType.remove("type");
		Map<String, Object> unknownType = ownerWithdrew();
		unknownType.put("type", "FORGOTTEN_TYPE");
		Map<String, Object> withdrewWithIntroduced = ownerWithdrew();
		withdrewWithIntroduced.put("introducedBySunvibe", "YES");
		Map<String, Object> letWithChannel = unitLet();
		letWithChannel.put("channel", "EMAIL");
		Map<String, Object> sunvibeWithDate = sunvibeTerminated("A reason.");
		sunvibeWithDate.put("reportedOn", today().toString());

		assertValidation(terminate(sale, id, noType), "type", TYPES);
		Result unknown = terminate(sale, id, unknownType);
		assertValidation(unknown, "type", TYPES);
		assertFalse(unknown.rawBody().contains("FORGOTTEN_TYPE"), unknown.rawBody());
		assertValidation(terminate(sale, id, withdrewWithIntroduced), "introducedBySunvibe", null);
		assertValidation(terminate(sale, id, letWithChannel), "channel", null);
		assertValidation(terminate(sale, id, sunvibeWithDate), "reportedOn", null);
		assertValidation(terminate(sale, id, ownerWithdrew(today(), null, null)), "channel", null);
		assertValidation(terminate(sale, id, ownerWithdrew(null, "EMAIL", null)), "reportedOn", null);
		assertValidation(terminate(sale, id, unitLet(null, "YES", null)), "reportedOn", null);
		assertValidation(terminate(sale, id, unitLet(today(), null, null)), "introducedBySunvibe", null);
		assertValidation(terminate(sale, id, sunvibeTerminated(null)), "note", null);
		Result letter = terminate(sale, id, ownerWithdrew(today(), "LETTER_POST", null));
		assertValidation(letter, "channel", CHANNELS);
		assertFalse(letter.rawBody().contains("LETTER_POST"), letter.rawBody());
		assertValidation(terminate(sale, id, unitLet(today(), "MAYBE", null)), "introducedBySunvibe",
				List.of("YES", "NO", "UNKNOWN"));
		assertValidation(terminate(sale, id, unitLet(today().plusDays(1), "YES", null)), "reportedOn", null);
		assertValidation(terminate(sale, id, unitLet(LocalDate.parse("1999-12-31"), "YES", null)), "reportedOn", null);
		for (String note : List.of("a".repeat(501), " " + "a".repeat(499) + " ", "A\u0001B", "A‮B")) {
			Result refused = terminate(sale, id, ownerWithdrew(today(), "EMAIL", note));
			assertValidation(refused, "note", null);
			assertFalse(refused.rawBody().contains("aaaaaaaaaa"), refused.rawBody());
		}
		assertValidation(terminate(manager, id, sunvibeTerminated("   ")), "note", null);
		assertMalformed(postRaw(sale, AGREEMENTS + "/" + id + "/termination", "not json"));
		Map<String, Object> badDate = unitLet();
		badDate.put("reportedOn", "2026-13-45");
		Result unreadable = terminate(sale, id, badDate);
		assertMalformed(unreadable);
		assertFalse(unreadable.rawBody().contains("2026-13-45"), unreadable.rawBody());
		assertNothingWritten(before);
	}

	/** TE-4: before the activated version starts, and while suspended; the answer reports the end. */
	@Test
	void terminate_activatedStartAheadOrSuspended_terminatedAnswerEnded() throws Exception {
		Chain ahead = activated(today().plusDays(10), today().plusMonths(6));
		assertEquals("ACTIVATED", versionStatus(ahead.agreementId(), 1));

		Result first = terminate(sale, ahead.agreementId(), ownerWithdrew());

		assertEquals(204, first.status(), first.rawBody());
		assertEquals("CANCELLED", versionStatus(ahead.agreementId(), 1));
		JsonNode advertising = detail(sale, ahead.agreementId()).path("advertising");
		assertEquals("[\"ENDED\"]", advertising.path("reasonGroups").toString());
		assertTrue(advertising.path("endDate").isNull(), advertising.toString());
		assertTrue(advertising.path("endsAt").isNull(), advertising.toString());

		Chain suspended = inEffect();
		suspended(manager, suspended.agreementId());
		Result second = terminate(manager, suspended.agreementId(), ownerWithdrew());

		assertEquals(204, second.status(), second.rawBody());
		JsonNode detail = detail(manager, suspended.agreementId());
		assertEquals("TERMINATED", detail.path("status").asString());
		assertEquals(1, openSuspensions(suspended.agreementId()));
		assertTrue(detail.path("suspensions").path(0).path("releasedAt").isNull(), detail.toString());
		assertEquals("EFFECTIVE", versionStatus(suspended.agreementId(), 1));
		assertEquals("[\"ENDED\"]", detail.path("advertising").path("reasonGroups").toString());
	}

	/** TE-5 and the access cases: no token, a CUSTOMER, unknown and malformed ids, a SALE who does not see the lead. */
	@Test
	void terminate_unknownMalformedNoTokenCustomerInvisible_notFoundUnauthorizedForbidden() throws Exception {
		Chain chain = drafted(today().plusDays(10), today().plusMonths(6));
		WriteSnapshot before = snapshot(chain.agreementId());

		assertEquals(401, terminate(null, chain.agreementId(), ownerWithdrew()).status());
		assertCode(403, "FORBIDDEN", terminate(chain.owner(), chain.agreementId(), ownerWithdrew()));
		Result unknown = terminate(sale, "01JUNKNOWN0000000000000000", ownerWithdrew());
		Result malformed = terminate(sale, "not-an-id", ownerWithdrew());
		assertCode(404, "NOT_FOUND", unknown);
		assertCode(404, "NOT_FOUND", malformed);
		assertEquals(unknown.body().path("message"), malformed.body().path("message"));
		assertEquals(unknown.body().path("errors"), malformed.body().path("errors"));
		for (Map<String, Object> body : List.of(ownerWithdrew(), unitLet(), sunvibeTerminated("A reason."))) {
			assertCode(404, "NOT_FOUND", terminate(otherSale, chain.agreementId(), body));
		}
		assertNothingWritten(before);
	}

	/** TE-6: before it took effect, by each reason; the pending version cancelled, an owner's answer kept. */
	@Test
	void terminate_beforeEffect_versionCancelledOwnerReadsIt() throws Exception {
		Chain drafting = drafted(today().plusDays(10), today().plusMonths(6));
		assertEquals(204, terminate(sale, drafting.agreementId(), ownerWithdrew()).status());
		assertEquals("CANCELLED", versionStatus(drafting.agreementId(), 1));

		Chain awaiting = sent(today().plusDays(10), today().plusMonths(6));
		assertEquals(204, terminate(manager, awaiting.agreementId(), ownerWithdrew()).status());
		assertEquals("CANCELLED", versionStatus(awaiting.agreementId(), 1));
		assertOwnerReads(awaiting, "CANCELLED");

		Chain confirmed = confirmed(today().plusDays(10), today().plusMonths(6));
		assertEquals(204, terminate(manager, confirmed.agreementId(), sunvibeTerminated("The papers were refused."))
				.status());
		Map<String, Object> version = versionRow(confirmed.agreementId(), 1);
		assertEquals("CANCELLED", version.get("status"));
		assertEquals("CONFIRMED", version.get("owner_answer"));
		assertNotNull(version.get("owner_answered_by"));
		assertNotNull(version.get("owner_answered_at"));
		assertNotNull(version.get("owner_statement_version"));
		assertOwnerReads(confirmed, "CANCELLED");

		Chain rejected = sent(today().plusDays(10), today().plusMonths(6));
		assertEquals(204, reject(rejected.owner(), rejected.agreementId(), 1).status());
		assertEquals("DRAFTING", status(rejected.agreementId()));
		assertEquals(204, terminate(sale, rejected.agreementId(), unitLet()).status());
		assertEquals("OWNER_REJECTED", versionStatus(rejected.agreementId(), 1));

		for (Chain chain : List.of(drafting, awaiting, confirmed, rejected)) {
			assertEquals("TERMINATED", status(chain.agreementId()));
			assertTrue(standingEnded(chain.agreementId()));
			assertEquals(0, detail(manager, chain.agreementId()).path("viewerStopActions").size());
		}
	}

	/** TE-7: a version past its 7 days, its lapse not stored: the lapse's row at its deadline, then the termination's. */
	@Test
	void terminate_lapseDueNotStored_lapsedRowThenTerminated() throws Exception {
		Chain chain = sent(today().plusDays(10), today().plusMonths(6));
		Instant sentAt = sentDaysAgo(chain.agreementId(), 1, 8);

		assertEquals(204, terminate(sale, chain.agreementId(), ownerWithdrew()).status());

		List<String> events = events(chain.agreementId());
		assertEquals(List.of("VERSION_LAPSED 1 -", "TERMINATED 1 " + sale.userId()), events.subList(events.size() - 2,
				events.size()));
		assertEquals(sentAt.plus(java.time.Duration.ofDays(7)), eventAt(chain.agreementId(), "VERSION_LAPSED"));
		assertEquals("LAPSED", versionStatus(chain.agreementId(), 1));
		assertEquals("TERMINATED", status(chain.agreementId()));
	}

	/** Review Focus 4: after a reassignment the former Sale gets 404, the new one records the termination. */
	@Test
	void terminate_afterReassignment_formerSaleNotFoundNewSaleRecords() throws Exception {
		Chain chain = drafted(today().plusDays(10), today().plusMonths(6));
		fixture.checklists().reassign(leader, chain.leadId(), otherSale);
		WriteSnapshot before = snapshot(chain.agreementId());

		assertCode(404, "NOT_FOUND", terminate(sale, chain.agreementId(), unitLet()));
		assertNothingWritten(before);

		Result answer = terminate(otherSale, chain.agreementId(), unitLet());
		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("TERMINATED 1 " + otherSale.userId(), events(chain.agreementId()).getLast());
	}

	/** The owner's read lists the agreement TERMINATED with its version in that state. */
	private void assertOwnerReads(Chain chain, String versionStatus) throws Exception {
		Result answer = api.get(chain.owner(), ME);
		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode item = null;
		for (JsonNode each : answer.body().path("items")) {
			if (chain.agreementId().equals(each.path("agreementId").asString())) {
				item = each;
			}
		}
		assertNotNull(item, answer.rawBody());
		assertEquals("TERMINATED", item.path("status").asString());
		assertEquals(versionStatus, item.path("versions").path(0).path("status").asString());
	}
}
