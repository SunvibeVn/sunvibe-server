package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The expiry over HTTP and through the store on PostgreSQL (brokerage-agreement BR-5, BR-6, US-5 AC-1; E05-S8 spec 3.5,
 * 3.6, S8-D2 to S8-D4; EX-1, EX-2, EX-4 to EX-6): an agreement past its end reads EXPIRED at every read, the store
 * writes it once with its rows and no event, the create stores a due expiry first, and every command answers its code
 * on an expired agreement. The dates are moved in SQL from the JVM's today in Vietnam. Needs Docker.
 */
class AgreementExpiryApiPostgresTest extends AgreementStopApiTestBase {

	@Override
	String phonePrefix() {
		return "0922";
	}

	/** EX-1: the end date was yesterday in Vietnam: the detail reads EXPIRED with its end before any job. */
	@Test
	void detail_endDateYesterday_expiredBeforeAnyJob() throws Exception {
		Chain chain = inEffect();
		movePeriod(chain.agreementId(), 1, today().minusMonths(3), today().minusDays(1));

		JsonNode detail = detail(sale, chain.agreementId());

		assertEquals("EXPIRED", detail.path("status").asString());
		assertEquals(startOf(today()).toString(), detail.path("endedAt").asString());
		assertEquals("[\"ENDED\"]", detail.path("advertising").path("reasonGroups").toString());
		assertEquals("ACTIVE", status(chain.agreementId()));
	}

	/** EX-2: the store writes EXPIRED once with its end and one row without an actor; no outbox row. */
	@Test
	void storeDueExpiries_endedYesterday_storedOnceWithRow() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));
		int outbox = outboxRows(chain.unitId());
		long rowVersion = rowVersion(id);

		commands.storeDueExpiries();
		commands.storeDueExpiries();

		Map<String, Object> row = agreementRow(id);
		assertEquals("EXPIRED", row.get("status"));
		assertEquals(startOf(today()), ((Timestamp) row.get("ended_at")).toInstant());
		assertEquals(rowVersion + 1, rowVersion(id));
		assertEquals(1, eventCount(id, "EXPIRED"));
		assertEquals("EXPIRED 1 -", events(id).getLast());
		assertEquals(startOf(today()), eventAt(id, "EXPIRED"));
		assertEquals(outbox, outboxRows(chain.unitId()));
		assertEquals("EFFECTIVE", versionStatus(id, 1));
	}

	/** EX-2: an activated version that started and ended with neither stored: started first, then expired; EFFECTIVE. */
	@Test
	void storeDueExpiries_startAndEndNotStored_versionEffectiveStartedThenExpired() throws Exception {
		Chain chain = activated(today().plusDays(2), today().plusMonths(6));
		String id = chain.agreementId();
		movePeriod(id, 1, today().minusMonths(2), today().minusDays(1));

		commands.storeDueExpiries();

		assertEquals("EXPIRED", status(id));
		Map<String, Object> version = versionRow(id, 1);
		assertEquals("EFFECTIVE", version.get("status"));
		assertEquals("ACTIVATED", version.get("activation_decision"));
		List<String> events = events(id);
		assertEquals(List.of("VERSION_STARTED 1 -", "EXPIRED 1 -"), events.subList(events.size() - 2, events.size()));
		assertEquals(startOf(today().minusMonths(2)), eventAt(id, "VERSION_STARTED"));
		assertEquals(startOf(today()), eventAt(id, "EXPIRED"));
	}

	/** EX-4: the create on the unit of an agreement past its end stores the expiry first, then inserts. */
	@Test
	void create_earlierEndedNotStored_createdAndExpiredRow() throws Exception {
		Chain chain = inEffect();
		movePeriod(chain.agreementId(), 1, today().minusMonths(3), today().minusDays(1));

		Result created = createResult(sale, chain.leadId(), chain.unitId());

		assertEquals(201, created.status(), created.rawBody());
		assertEquals("EXPIRED", status(chain.agreementId()));
		assertEquals("EXPIRED 1 -", events(chain.agreementId()).getLast());
		assertEquals("DRAFTING", status(created.body().path("agreementId").asString()));
	}

	/** EX-4, its due-start clause: the earlier v1 started and ended unstored: its start, its expiry, then the create. */
	@Test
	void create_earlierStartAndEndNotStored_startedThenExpiredRowsAndCreated() throws Exception {
		Chain chain = activated(today().plusDays(2), today().plusMonths(6));
		String id = chain.agreementId();
		movePeriod(id, 1, today().minusMonths(2), today().minusDays(1));

		Result created = createResult(sale, chain.leadId(), chain.unitId());

		assertEquals(201, created.status(), created.rawBody());
		assertEquals("EXPIRED", status(id));
		Map<String, Object> version = versionRow(id, 1);
		assertEquals("EFFECTIVE", version.get("status"));
		assertNotNull(version.get("activation_decided_at"));
		List<String> events = events(id);
		assertEquals(List.of("VERSION_STARTED 1 -", "EXPIRED 1 -"), events.subList(events.size() - 2, events.size()));
		String newId = created.body().path("agreementId").asString();
		long createdRowId = jdbc.queryForObject("select id from brokerage_agreement_events where agreement_id = ? and"
				+ " event_type = 'CREATED'", Long.class, newId);
		long expiredRowId = jdbc.queryForObject("select id from brokerage_agreement_events where agreement_id = ? and"
				+ " event_type = 'EXPIRED'", Long.class, id);
		assertTrue(expiredRowId < createdRowId);
	}

	/** EX-5: every command on an expired agreement answers its code and writes nothing. */
	@Test
	void stops_onExpired_agreementEndedOthersKeepCodes() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));
		WriteSnapshot before = snapshot(id);

		assertCode(409, "AGREEMENT_ENDED", suspendAgreement(manager, id, "COMPLAINT", null));
		assertCode(409, "AGREEMENT_ENDED", releaseSuspension(manager, id));
		assertCode(409, "AGREEMENT_ENDED", terminate(sale, id, ownerWithdrew()));
		assertCode(409, "AGREEMENT_ENDED", cancel(sale, id, rowVersion(id)));
		assertCode(409, "AGREEMENT_NOT_AWAITING_ACTIVATION", activate(admin, id));
		assertCode(409, "AGREEMENT_NEW_DRAFT_NOT_ALLOWED", openDraft(id, rowVersion(id)));
		Map<String, Object> edit = AgreementFeePolicyApiPostgresTest.complete(rowVersion(id));
		edit.put("startDate", today().plusDays(10).toString());
		edit.put("endDate", today().plusMonths(6).toString());
		assertCode(409, "AGREEMENT_NOT_DRAFT", api.put(sale, AGREEMENTS + "/" + id + "/draft", edit));
		assertCode(409, "AGREEMENT_NOT_DRAFT", api.post(sale, AGREEMENTS + "/" + id + "/draft/submission",
				Map.of("expectedDataVersion", rowVersion(id))));
		assertCode(409, "AGREEMENT_NOT_AWAITING_OWNER", confirmResult(chain.owner(), id, 1));
		assertNothingWritten(before);
	}

	/** EX-6: a suspended agreement past its end is stored EXPIRED with its open record left open. */
	@Test
	void storeDueExpiries_suspendedPastEnd_recordStaysOpen() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		suspended(manager, id);
		movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));

		commands.storeDueExpiries();

		assertEquals("EXPIRED", status(id));
		assertEquals(1, openSuspensions(id));
		JsonNode detail = detail(manager, id);
		assertEquals("EXPIRED", detail.path("status").asString());
		assertTrue(detail.path("suspensions").path(0).path("releasedAt").isNull(), detail.toString());
		assertEquals("[\"ENDED\"]", detail.path("advertising").path("reasonGroups").toString());
		assertTrue(standingEnded(id));
	}
}
