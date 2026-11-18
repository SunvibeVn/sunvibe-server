package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The 14-day lapse of a confirmed version and the start of an activated one over HTTP on PostgreSQL, with both jobs held
 * off (brokerage-agreement US-3 AC-4, AC-5; BR-5, BR-6, BR-11, BR-17; E05-S6 spec 3.3, 3.4, S6-D5, S6-D6, S6-D23;
 * criteria LP-1, LP-2, ST-1): every read answers the due transition at once, the stores the test calls itself write it
 * once with its row at the deadline, and the Sale's new draft stores a due lapse first. A due confirmation is made by
 * moving its {@code sent_at} and {@code owner_answered_at} back together from the JVM clock (plan E05S6-P3). Both jobs run
 * every hour here, so nothing changes by itself; {@code AgreementActivationConcurrencyPostgresTest} shares this context
 * (plan E05S6-P2). A run stores every due row of its database, so each test deletes every agreement it made. Needs
 * Docker.
 */
@TestPropertySource(properties = {"app.brokerage.lapse-delay=1h", "app.brokerage.start-delay=1h"})
class AgreementActivationLapseApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String OWNER_STATEMENT = "brokerage-agreement-2026-11";
	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Duration FOURTEEN_DAYS = Duration.ofDays(14);

	@Autowired
	private AgreementCommandUseCase commands;

	@Autowired
	private AgreementDomainRepository agreements;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0932";
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

	// ==================== fixtures (as AgreementActivationApiPostgresTest builds them) ====================

	private record Chain(String leadId, TestUser owner, String unitId, String agreementId) {
	}

	private Chain drafted(LocalDate start, LocalDate end) throws Exception {
		Passed passed = house(unique("Lapse Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
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
		Map<String, Object> draft = AgreementFeePolicyApiPostgresTest.complete(rowVersion(id));
		draft.put("startDate", start.toString());
		draft.put("endDate", end.toString());
		Result saved = api.put(sale, AGREEMENTS + "/" + id + "/draft", draft);
		assertEquals(204, saved.status(), saved.rawBody());
		return new Chain(passed.leadId(), owner, unit, id);
	}

	private void send(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		Result answer = api.post(sale, AGREEMENTS + "/" + agreementId + "/draft/submission", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private Chain confirmed(LocalDate start, LocalDate end) throws Exception {
		Chain chain = drafted(start, end);
		send(chain.agreementId());
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("agreementId", chain.agreementId());
		item.put("versionNo", 1);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", passwordOf(chain.owner()));
		body.put("statementVersion", OWNER_STATEMENT);
		body.put("versions", List.of(item));
		Result answer = api.post(chain.owner(), ME + "/confirmations", body);
		assertEquals(204, answer.status(), answer.rawBody());
		return chain;
	}

	private Chain confirmed() throws Exception {
		return confirmed(today(), today().plusMonths(6));
	}

	/** Moves version 1's sending and owner confirmation back together so that the confirmation was at {@code at}. */
	private Instant confirmedAt(String agreementId, Instant at) {
		Map<String, Object> version = versionRow(agreementId, 1);
		Instant answered = ((Timestamp) version.get("owner_answered_at")).toInstant();
		Duration back = Duration.between(at, answered);
		Instant sent = ((Timestamp) version.get("sent_at")).toInstant().minus(back);
		jdbc.update("update brokerage_agreement_versions set sent_at = ?, owner_answered_at = ? where agreement_id = ?"
				+ " and version_no = 1", Timestamp.from(sent), Timestamp.from(at), agreementId);
		return at;
	}

	/** A confirmation 14 days and 1 s before the JVM clock: its activation lapse is due. */
	private Instant dueConfirmation(String agreementId) {
		return confirmedAt(agreementId, now().minus(FOURTEEN_DAYS).minusSeconds(1));
	}

	private Result activate(TestUser caller, String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		body.put("versionNo", 1);
		body.put("statementVersion", ActivationStatement.VERSION);
		body.put("documentNumber", "HDMG-2026/014");
		body.put("signedOn", today().toString());
		body.put("sunvibeSignatoryName", "Nguyen Van A");
		body.put("sunvibeSignatoryTitle", "Director");
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation", body);
	}

	private Result refuse(TestUser caller, String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		body.put("versionNo", 1);
		body.put("reasonType", "DOCUMENT_DIFFERS");
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation-refusal", body);
	}

	private void openDraft(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		Result answer = api.post(sale, AGREEMENTS + "/" + agreementId + "/draft", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private JsonNode detail(String agreementId) throws Exception {
		Result answer = api.get(manager, AGREEMENTS + "/" + agreementId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	/**
	 * ST-1's fixture: version 1 ACTIVATED with its activation record and a start date of yesterday, written through the
	 * repository's update of an aggregate activated when that start still lay ahead (the command itself would write
	 * EFFECTIVE for a past start).
	 */
	private Chain activatedWithPastStart() throws Exception {
		LocalDate yesterday = today().minusDays(1);
		Chain chain = confirmed(yesterday, yesterday.plusMonths(6));
		Instant beforeStart = yesterday.atStartOfDay(VIETNAM).toInstant().minus(Duration.ofHours(1));
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			BrokerageAgreement stored = agreements.findById(chain.agreementId()).orElseThrow();
			agreements.update(stored.activate(1, ActivationDecision.activated(manager.userId(), beforeStart,
					ActivationStatement.VERSION, new SignedDocument("HDMG-1", yesterday.minusDays(1), "A", "B")),
					beforeStart));
		});
		assertEquals("ACTIVATED", versionRow(chain.agreementId(), 1).get("status"));
		return chain;
	}

	// ==================== reading rows ====================

	private Map<String, Object> versionRow(String agreementId, int versionNo) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ? and version_no = ?",
				agreementId, versionNo);
	}

	private Map<String, Object> agreementRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreements where id = ?", agreementId);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	/** The agreement's history as "event version actor time", oldest first. */
	private List<String> events(String agreementId) {
		List<String> events = new ArrayList<>();
		jdbc.query("select event_type, version_no, actor_user_id, occurred_at from brokerage_agreement_events where"
				+ " agreement_id = ? order by id", row -> {
					events.add(row.getString("event_type") + " " + row.getInt("version_no") + " "
							+ (row.getString("actor_user_id") == null ? "-" : row.getString("actor_user_id")) + " "
							+ row.getTimestamp("occurred_at").toInstant());
				}, agreementId);
		return events;
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	// ==================== LP-1: read and refused before any store ====================

	/** LP-1: past its 14 days the detail and the owner read answer LAPSED and DRAFTING; nothing is stored. */
	@Test
	void reads_activationLapseDueNotStored_lapsedDraftingRowsUnchanged() throws Exception {
		Chain chain = confirmed();
		Instant confirmed = dueConfirmation(chain.agreementId());
		long stored = rowVersion(chain.agreementId());

		JsonNode detail = detail(chain.agreementId());

		assertEquals("DRAFTING", detail.path("status").asString());
		assertEquals("LAPSED", detail.path("versions").path(0).path("status").asString());
		assertTrue(detail.path("activationStatement").isNull(), detail.toString());
		assertEquals(stored, detail.path("dataVersion").asLong());
		assertEquals(confirmed.plus(FOURTEEN_DAYS),
				Instant.parse(detail.path("versions").path(0).path("activateBy").asString()));
		JsonNode owned = api.get(chain.owner(), ME).body().path("items").path(0);
		assertEquals("DRAFTING", owned.path("status").asString());
		assertEquals("LAPSED", owned.path("versions").path(0).path("status").asString());
		assertEquals("OWNER_CONFIRMED", versionRow(chain.agreementId(), 1).get("status"));
		assertEquals("AWAITING_CONFIRMATION", agreementRow(chain.agreementId()).get("status"));
		assertEquals(stored, rowVersion(chain.agreementId()));
	}

	/** LP-1: activation and refusal past activateBy answer 409 and write nothing. */
	@Test
	void activateOrRefuse_pastActivateBy_notAwaitingNothingWritten() throws Exception {
		Chain chain = confirmed();
		dueConfirmation(chain.agreementId());
		Map<String, Object> version = versionRow(chain.agreementId(), 1);
		List<String> events = events(chain.agreementId());

		for (Result answer : List.of(activate(manager, chain.agreementId()), refuse(manager, chain.agreementId()))) {
			assertEquals(409, answer.status(), answer.rawBody());
			assertEquals("AGREEMENT_NOT_AWAITING_ACTIVATION", answer.body().path("code").asString(), answer.rawBody());
		}
		assertEquals(version, versionRow(chain.agreementId(), 1));
		assertEquals(events, events(chain.agreementId()));
	}

	// ==================== LP-2: the stores ====================

	/**
	 * LP-2: one run stores the due 14-day lapse once with its row at activateBy and no actor, a 7-day lapse due in the
	 * same run with its row at answerBy; a confirmation 14 days less one hour old is untouched; a second run stores
	 * nothing; then the Sale opens a new draft.
	 */
	@Test
	void storeDueLapses_activationLapseDue_storedOnceAtActivateBy() throws Exception {
		Chain due = confirmed();
		Instant confirmed = dueConfirmation(due.agreementId());
		Chain notYet = confirmed();
		confirmedAt(notYet.agreementId(), now().minus(FOURTEEN_DAYS).plus(Duration.ofHours(1)));
		Chain unanswered = drafted(today(), today().plusMonths(6));
		send(unanswered.agreementId());
		Instant sent = now().minus(Duration.ofDays(7)).minusSeconds(1);
		jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ?", Timestamp.from(sent),
				unanswered.agreementId());

		int stored = commands.storeDueLapses();

		assertEquals(2, stored);
		assertEquals("LAPSED", versionRow(due.agreementId(), 1).get("status"));
		assertEquals("DRAFTING", agreementRow(due.agreementId()).get("status"));
		assertEquals("VERSION_LAPSED 1 - " + confirmed.plus(FOURTEEN_DAYS), events(due.agreementId()).getLast());
		assertEquals("VERSION_LAPSED 1 - " + sent.plus(Duration.ofDays(7)), events(unanswered.agreementId()).getLast());
		assertEquals("OWNER_CONFIRMED", versionRow(notYet.agreementId(), 1).get("status"));
		assertEquals(0, commands.storeDueLapses());
		assertEquals(1, events(due.agreementId()).stream().filter(event -> event.startsWith("VERSION_LAPSED")).count());

		openDraft(due.agreementId());
		assertEquals("DRAFT", versionRow(due.agreementId(), 2).get("status"));
	}

	/** LP-2: 101 due confirmations: a run stores 100 of this branch, the next run the last. */
	@Test
	void storeDueLapses_hundredAndOneDue_hundredThenOne() {
		Instant answered = now().minus(Duration.ofDays(20));
		List<String> fixtures = new ArrayList<>();
		for (int index = 0; index < 101; index++) {
			String id = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "AWAITING_CONFIRMATION",
					"OWNER_CONFIRMED");
			created.add(id);
			fixtures.add(id);
			jdbc.update("update brokerage_agreement_versions set sent_at = ?, owner_answer = 'CONFIRMED',"
					+ " owner_answered_by = ?, owner_answered_at = ?, owner_statement_version = ? where agreement_id = ?",
					Timestamp.from(answered.minus(Duration.ofHours(1))), AgreementFixture.FIXTURE_USER,
					Timestamp.from(answered), OWNER_STATEMENT, id);
		}

		assertEquals(100, commands.storeDueLapses());
		assertEquals(100, lapsed(fixtures));
		assertEquals(1, commands.storeDueLapses());
		assertEquals(101, lapsed(fixtures));
	}

	private int lapsed(List<String> agreementIds) {
		return (int) agreementIds.stream()
				.filter(id -> "LAPSED".equals(versionRow(id, 1).get("status"))).count();
	}

	/** LP-2 (S6-D5): the Sale's new draft stores a due 14-day lapse first, its row at activateBy before DRAFT_OPENED. */
	@Test
	void newDraft_activationLapseDueNotStored_rowAtActivateByBeforeDraftOpened() throws Exception {
		Chain chain = confirmed();
		Instant confirmed = dueConfirmation(chain.agreementId());

		openDraft(chain.agreementId());

		List<String> events = events(chain.agreementId());
		assertEquals("VERSION_LAPSED 1 - " + confirmed.plus(FOURTEEN_DAYS), events.get(events.size() - 2));
		assertTrue(events.getLast().startsWith("DRAFT_OPENED 2 " + sale.userId()), events.toString());
		assertEquals("LAPSED", versionRow(chain.agreementId(), 1).get("status"));
		assertEquals("DRAFT", versionRow(chain.agreementId(), 2).get("status"));
	}

	// ==================== ST-1: the start ====================

	/** ST-1: an ACTIVATED version whose start has passed reads EFFECTIVE and allowed before any store. */
	@Test
	void reads_startDueNotStored_effectiveAndAllowed() throws Exception {
		Chain chain = activatedWithPastStart();

		JsonNode detail = detail(chain.agreementId());

		assertEquals("EFFECTIVE", detail.path("versions").path(0).path("status").asString());
		assertEquals("ACTIVE", detail.path("status").asString());
		assertTrue(detail.path("advertising").path("allowed").asBoolean(), detail.path("advertising").toString());
		assertEquals("ACTIVATED", versionRow(chain.agreementId(), 1).get("status"));
	}

	/** ST-1: one run stores the start once with its row at startsAt and no actor; a second run stores nothing. */
	@Test
	void storeDueStarts_due_storedOnceAtStartsAt() throws Exception {
		Chain chain = activatedWithPastStart();
		Instant startsAt = today().minusDays(1).atStartOfDay(VIETNAM).toInstant();

		assertEquals(1, commands.storeDueStarts());

		assertEquals("EFFECTIVE", versionRow(chain.agreementId(), 1).get("status"));
		assertEquals("ACTIVATED", versionRow(chain.agreementId(), 1).get("activation_decision"));
		assertEquals("VERSION_STARTED 1 - " + startsAt, events(chain.agreementId()).getLast());
		assertEquals(0, commands.storeDueStarts());
		assertEquals(1, events(chain.agreementId()).stream().filter(event -> event.startsWith("VERSION_STARTED")).count());
	}

	/** ST-1, S6-D6: an ACTIVATED fixture row without its activation record is never started. */
	@Test
	void storeDueStarts_activatedWithoutRecord_neverStarted() {
		String id = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "ACTIVE", "ACTIVATED");
		created.add(id);
		LocalDate yesterday = today().minusDays(1);
		jdbc.update("update brokerage_agreement_versions set start_date = ?, end_date = ? where agreement_id = ?",
				Date.valueOf(yesterday), Date.valueOf(yesterday.plusMonths(6)), id);

		assertEquals(0, commands.storeDueStarts());

		assertEquals("ACTIVATED", versionRow(id, 1).get("status"));
		assertEquals(List.of(), events(id));
	}
}
