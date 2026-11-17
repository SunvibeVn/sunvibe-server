package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The 7-day lapse over HTTP on PostgreSQL, with the lapse job held off (brokerage-agreement US-2 AC-3; BR-5, BR-10;
 * E05-S5 spec 3.4, S5-D8, S5-D19; OC-2 lapsed half, OC-6, RJ-1 lapsed, LP-1, LP-2, LP-4, ND-4): a version past its
 * deadline reads LAPSED at once, is refused to the owner, and is stored by a run of {@code storeDueLapses()} the test
 * calls itself, or first by the Sale's new draft. A due version is made by moving its {@code sent_at} 7 days and 1 s
 * before the JVM clock. The job runs every hour here ({@code app.brokerage.lapse-delay=1h}), so nothing changes by
 * itself; {@code OwnerAgreementConcurrencyPostgresTest} shares this context (plan E05S5-P2). A run stores every due row
 * of its database, so each test deletes every agreement it made. Needs Docker.
 */
@TestPropertySource(properties = "app.brokerage.lapse-delay=1h")
class OwnerAgreementLapseApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String STATEMENT_VERSION = "brokerage-agreement-2026-11";

	@Autowired
	private AgreementCommandUseCase commands;

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0937";
	}

	@AfterEach
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		created.clear();
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
		owners.clear();
	}

	// ==================== HTTP and fixtures ====================

	private record Done(String leadId, TestUser owner, String agreementId) {
	}

	private TestUser newOwner() throws Exception {
		TestUser owner = owner();
		owners.add(owner.userId());
		return owner;
	}

	/** A house lead of {@code sale} with a completed intake for the owner, and the Sale's complete draft sent to them. */
	private Done sent(TestUser owner) throws Exception {
		Passed passed = house(unique("Lapse Street"));
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", passed.leadId());
		body.put("unitId", unit);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		Result saved = api.put(sale, AGREEMENTS + "/" + id + "/draft", AgreementFeePolicyApiPostgresTest.complete(rowVersion(id)));
		assertEquals(204, saved.status(), saved.rawBody());
		Result sentAnswer = api.post(sale, AGREEMENTS + "/" + id + "/draft/submission",
				Map.of("expectedDataVersion", rowVersion(id)));
		assertEquals(204, sentAnswer.status(), sentAnswer.rawBody());
		return new Done(passed.leadId(), owner, id);
	}

	/** Moves the sending time of version 1 back by {@code ago}; answers the new sending time. */
	private Instant sentAgo(String agreementId, Duration ago) {
		Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(ago);
		jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ? and version_no = 1",
				Timestamp.from(sentAt), agreementId);
		return sentAt;
	}

	/** Past the deadline by one second: 7 days and 1 s ago, which only grows while the test runs (spec S5-D19). */
	private Instant due(String agreementId) {
		return sentAgo(agreementId, Duration.ofDays(7).plusSeconds(1));
	}

	private Result confirm(TestUser owner, String... agreementIds) throws Exception {
		List<Map<String, Object>> versions = new ArrayList<>();
		for (String id : agreementIds) {
			versions.add(Map.of("agreementId", id, "versionNo", 1));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", passwordOf(owner));
		body.put("statementVersion", STATEMENT_VERSION);
		body.put("versions", versions);
		return api.post(owner, ME + "/confirmations", body);
	}

	private Result openDraft(String agreementId, long expectedDataVersion) throws Exception {
		return api.post(sale, AGREEMENTS + "/" + agreementId + "/draft", Map.of("expectedDataVersion", expectedDataVersion));
	}

	// ==================== reading rows ====================

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private Map<String, Object> versionRow(String agreementId, int versionNo) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ? and version_no = ?",
				agreementId, versionNo);
	}

	private String agreementStatus(String agreementId) {
		return jdbc.queryForObject("select status from brokerage_agreements where id = ?", String.class, agreementId);
	}

	/**
	 * The agreement's history as "event version actor", in the order the rows were written: a fixture that moves the
	 * sending time back puts the lapse's time (its deadline) before the sending's own row time.
	 */
	private List<String> events(String agreementId) {
		return jdbc.queryForList("select event_type || ' ' || version_no || ' ' || coalesce(actor_user_id, '-') from"
				+ " brokerage_agreement_events where agreement_id = ? order by id", String.class, agreementId);
	}

	private Instant lapsedAt(String agreementId) {
		return jdbc.queryForObject("select occurred_at from brokerage_agreement_events where agreement_id = ?"
				+ " and event_type = 'VERSION_LAPSED'", Timestamp.class, agreementId).toInstant();
	}

	private int lapseRows(String agreementId) {
		return jdbc.queryForObject("select count(*) from brokerage_agreement_events where agreement_id = ?"
				+ " and event_type = 'VERSION_LAPSED'", Integer.class, agreementId);
	}

	private static void assertError(Result answer, int status, String code, String errors) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	// ==================== OC-2, OC-6, RJ-1: the owner's commands past the deadline ====================

	/** OC-2: one version past its deadline refuses the whole batch on its item alone; nothing is written for the other. */
	@Test
	void confirm_batchWithALapsedVersion_409NothingWritten() throws Exception {
		TestUser owner = newOwner();
		Done fine = sent(owner);
		Done lapsed = sent(owner);
		due(lapsed.agreementId());

		Result answer = confirm(owner, fine.agreementId(), lapsed.agreementId());

		assertError(answer, 409, "AGREEMENT_NOT_AWAITING_OWNER",
				"[{\"field\":\"versions[1]\",\"message\":\"This version cannot be answered now.\"}]");
		for (Done done : List.of(fine, lapsed)) {
			assertEquals("AWAITING_OWNER", versionRow(done.agreementId(), 1).get("status"));
			assertNull(versionRow(done.agreementId(), 1).get("owner_answer"));
		}
	}

	/** OC-6: a version sent 7 days and 1 s ago can no longer be confirmed. */
	@Test
	void confirm_dueVersion_409NothingWritten() throws Exception {
		Done done = sent(newOwner());
		due(done.agreementId());
		List<String> before = events(done.agreementId());

		assertError(confirm(done.owner(), done.agreementId()), 409, "AGREEMENT_NOT_AWAITING_OWNER",
				"[{\"field\":\"versions[0]\",\"message\":\"This version cannot be answered now.\"}]");
		assertEquals("AWAITING_OWNER", versionRow(done.agreementId(), 1).get("status"));
		assertEquals(before, events(done.agreementId()));
	}

	@Test
	void reject_dueVersion_409() throws Exception {
		Done done = sent(newOwner());
		due(done.agreementId());

		Result answer = api.post(done.owner(), ME + "/" + done.agreementId() + "/rejection",
				Map.of("versionNo", 1, "reason", "Too late"));

		assertError(answer, 409, "AGREEMENT_NOT_AWAITING_OWNER", "[]");
		assertNull(versionRow(done.agreementId(), 1).get("owner_rejection_reason"));
	}

	// ==================== LP-1: the reads before the store ====================

	@Test
	void reads_lapseDueNotStored_effectiveLapsedStoredRowsUnchanged() throws Exception {
		Done done = sent(newOwner());
		Instant sentAt = due(done.agreementId());
		long rowVersion = rowVersion(done.agreementId());

		Result list = api.get(done.owner(), ME);
		Result detail = api.get(sale, AGREEMENTS + "/" + done.agreementId());

		JsonNode item = list.body().path("items").path(0);
		assertEquals(done.agreementId(), item.path("agreementId").asString(), list.rawBody());
		assertEquals("DRAFTING", item.path("status").asString(), list.rawBody());
		assertEquals("LAPSED", item.path("versions").path(0).path("status").asString(), list.rawBody());
		assertTrue(item.path("statement").isNull(), list.rawBody());
		assertEquals(sentAt.plus(Duration.ofDays(7)), Instant.parse(item.path("versions").path(0).path("answerBy")
				.asString()));
		assertEquals("DRAFTING", detail.body().path("status").asString(), detail.rawBody());
		assertEquals("LAPSED", detail.body().path("versions").path(0).path("status").asString(), detail.rawBody());
		assertEquals(rowVersion, detail.body().path("dataVersion").asLong(), detail.rawBody());
		assertEquals("AWAITING_OWNER", versionRow(done.agreementId(), 1).get("status"));
		assertEquals("AWAITING_CONFIRMATION", agreementStatus(done.agreementId()));
		assertEquals(0, lapseRows(done.agreementId()));
		assertEquals(rowVersion, rowVersion(done.agreementId()));
	}

	// ==================== LP-2: the store ====================

	@Test
	void storeDueLapses_oneDue_storedOnceWithItsRow() throws Exception {
		Done done = sent(newOwner());
		Instant sentAt = due(done.agreementId());
		long rowVersion = rowVersion(done.agreementId());
		int accessRows = AgreementFixture.rows(jdbc, "brokerage_agreement_access_logs", done.agreementId());

		int stored = commands.storeDueLapses();

		assertEquals(1, stored);
		Map<String, Object> version = versionRow(done.agreementId(), 1);
		assertEquals("LAPSED", version.get("status"));
		assertNull(version.get("owner_answer"));
		assertNull(version.get("owner_answered_by"));
		assertEquals("DRAFTING", agreementStatus(done.agreementId()));
		assertEquals(rowVersion + 1, rowVersion(done.agreementId()));
		assertEquals("VERSION_LAPSED 1 -", events(done.agreementId()).getLast());
		assertEquals(1, lapseRows(done.agreementId()));
		assertEquals(sentAt.plus(Duration.ofDays(7)), lapsedAt(done.agreementId()));
		assertEquals(accessRows, AgreementFixture.rows(jdbc, "brokerage_agreement_access_logs", done.agreementId()));
		assertEquals(1, jdbc.queryForObject("select count(*) from auth_email_notifications where user_id = ? and kind ="
				+ " 'AGREEMENT_PENDING'", Integer.class, done.owner().userId()));
		assertEquals(rowVersion + 1, api.get(sale, AGREEMENTS + "/" + done.agreementId()).body().path("dataVersion")
				.asLong());

		assertEquals(0, commands.storeDueLapses());
		assertEquals(1, lapseRows(done.agreementId()));
	}

	/** LP-2: nothing that is not an AWAITING_OWNER version past its deadline is touched. */
	@Test
	void storeDueLapses_notDue_untouched() throws Exception {
		Done answerable = sent(newOwner());
		sentAgo(answerable.agreementId(), Duration.ofDays(7).minusHours(1));
		Instant old = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofDays(8));
		List<String> fixtures = new ArrayList<>();
		for (String[] state : List.of(new String[]{"AWAITING_CONFIRMATION", "OWNER_CONFIRMED"},
				new String[]{"DRAFTING", "OWNER_REJECTED"}, new String[]{"DRAFTING", "DRAFT"},
				new String[]{"AWAITING_CONFIRMATION", "AWAITING_OWNER"})) {
			String id = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), state[0], state[1]);
			created.add(id);
			fixtures.add(id);
		}
		for (String answered : fixtures.subList(0, 2)) {
			jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ?", Timestamp.from(old),
					answered);
		}
		List<Map<String, Object>> before = new ArrayList<>();
		for (String id : fixtures) {
			before.add(versionRow(id, 1));
		}
		Map<String, Object> answerableBefore = versionRow(answerable.agreementId(), 1);

		assertEquals(0, commands.storeDueLapses());

		for (int index = 0; index < fixtures.size(); index++) {
			assertEquals(before.get(index), versionRow(fixtures.get(index), 1), fixtures.get(index));
		}
		assertEquals(answerableBefore, versionRow(answerable.agreementId(), 1));
		assertEquals(0, lapseRows(answerable.agreementId()));
	}

	/** LP-2: a run stores the 100 oldest sendings; the next run takes the rest. */
	@Test
	void storeDueLapses_101Due_hundredOldestThenOne() {
		Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofDays(8));
		List<String> oldestFirst = new ArrayList<>();
		for (int minutes = 101; minutes >= 1; minutes--) {
			String id = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "AWAITING_CONFIRMATION",
					"AWAITING_OWNER");
			created.add(id);
			jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ?",
					Timestamp.from(base.minus(minutes, ChronoUnit.MINUTES)), id);
			oldestFirst.add(id);
		}

		assertEquals(100, commands.storeDueLapses());

		for (String id : oldestFirst.subList(0, 100)) {
			assertEquals("LAPSED", versionRow(id, 1).get("status"), id);
		}
		String last = oldestFirst.get(100);
		assertEquals("AWAITING_OWNER", versionRow(last, 1).get("status"));
		assertEquals(1, commands.storeDueLapses());
		assertEquals("LAPSED", versionRow(last, 1).get("status"));
		assertEquals("DRAFTING", agreementStatus(last));
	}

	// ==================== ND-4: the new draft as the backstop ====================

	@Test
	void newDraft_lapseDueNotStored_storedFirstThenDraft() throws Exception {
		Done done = sent(newOwner());
		Instant sentAt = due(done.agreementId());
		long dataVersion = api.get(sale, AGREEMENTS + "/" + done.agreementId()).body().path("dataVersion").asLong();

		Result answer = openDraft(done.agreementId(), dataVersion);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("LAPSED", versionRow(done.agreementId(), 1).get("status"));
		assertEquals("DRAFT", versionRow(done.agreementId(), 2).get("status"));
		List<String> events = events(done.agreementId());
		assertEquals(List.of("VERSION_LAPSED 1 -", "DRAFT_OPENED 2 " + sale.userId()),
				events.subList(events.size() - 2, events.size()));
		assertEquals(sentAt.plus(Duration.ofDays(7)), lapsedAt(done.agreementId()));
		// one command, one row version: the lapse's guarded update moves it and holds the row to the commit; the new
		// version's insert after it changes no column of the agreement row
		assertEquals(dataVersion + 1, rowVersion(done.agreementId()));

		assertEquals(0, commands.storeDueLapses());
		assertEquals(1, lapseRows(done.agreementId()));
	}

	@Test
	void newDraft_lapseAlreadyStored_noSecondLapseRow() throws Exception {
		Done done = sent(newOwner());
		due(done.agreementId());
		assertEquals(1, commands.storeDueLapses());

		Result answer = openDraft(done.agreementId(), rowVersion(done.agreementId()));

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(1, lapseRows(done.agreementId()));
		assertEquals("DRAFT_OPENED 2 " + sale.userId(), events(done.agreementId()).getLast());
	}
}
