package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.application.usecase.UnfinishedAgreementView;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * What the stop tests of brokerage-agreement E05-S8 share (plan E05S8-P8, P9): one context, where the lapse, start and
 * expiry jobs run every hour, so the stores run only when a test calls them, and one test consumer of the
 * unit-advertising event, so a stop's event stays as a row; the chain of E05-S2 to E05-S6 over HTTP; SQL moves of the
 * dates, each written from the JVM's clock (testing.md); the four stops over HTTP; the check that a refused call wrote
 * nothing; and the held locks that force an interleaving. Every agreement, its suspensions (by the cascade) and its
 * unit's outbox rows go after each test. Needs Docker.
 */
@TestPropertySource(properties = {"app.brokerage.lapse-delay=1h", "app.brokerage.start-delay=1h",
		"app.brokerage.expiry-delay=1h"})
@Import(AgreementStopTestSubscription.class)
abstract class AgreementStopApiTestBase extends IntakeApiTestBase {

	static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	static final String ME = "/api/v1/me/brokerage-agreements";
	static final String OWNER_STATEMENT = "brokerage-agreement-2026-11";

	final ExecutorService executor = Executors.newCachedThreadPool();
	final List<String> created = new CopyOnWriteArrayList<>();
	final List<String> units = new CopyOnWriteArrayList<>();
	final List<String> owners = new CopyOnWriteArrayList<>();

	@Autowired
	AgreementCommandUseCase commands;

	@Autowired
	AgreementDomainRepository agreements;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	AdvertisingRightUseCase advertisingRights;

	/** The published standing of the agreement: whether it ended (E05-S6, unchanged). */
	boolean standingEnded(String agreementId) {
		return advertisingRights.standings(List.of(agreementId)).getFirst().ended();
	}

	/** The ids the published unfinished read answers for the unit; it reads the stored state. */
	List<String> unfinishedOf(String unitId) {
		return advertisingRights.unfinishedOf(List.of(unitId), "LONG_TERM_RENT").stream()
				.map(UnfinishedAgreementView::agreementId).toList();
	}

	/** The published answer of the unit under the agreement. */
	AdvertisingRightView answer(Chain chain) {
		return advertisingRights.answers(List.of(new AdvertisingPair(chain.unitId(), chain.agreementId()))).getFirst();
	}

	@AfterEach
	void deleteStopRows() {
		AgreementFixture.delete(jdbc, created);
		created.clear();
		for (String unit : units) {
			jdbc.update("delete from outbox_events where aggregate_id = ?", unit);
		}
		units.clear();
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
		owners.clear();
	}

	@AfterAll
	void stopExecutor() {
		executor.shutdownNow();
	}

	// ==================== the chain of E05-S2 to E05-S6 over HTTP ====================

	record Chain(String leadId, TestUser owner, String propertyId, String unitId, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake for a new owner, and the Sale's complete draft on its unit. */
	Chain drafted(LocalDate start, LocalDate end) throws Exception {
		return drafted(unique("Stop Street"), start, end);
	}

	/** {@link #drafted(LocalDate, LocalDate)} on a house of that street. */
	Chain drafted(String street, LocalDate start, LocalDate end) throws Exception {
		Passed passed = house(street);
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		units.add(unit);
		Chain chain = new Chain(passed.leadId(), owner, propertyId, unit, create(sale, passed.leadId(), unit));
		saveComplete(chain.agreementId(), start, end);
		return chain;
	}

	/** {@link #drafted} sent by the Sale: version 1 waits for its owner. */
	Chain sent(LocalDate start, LocalDate end) throws Exception {
		Chain chain = drafted(start, end);
		send(chain.agreementId());
		return chain;
	}

	/** {@link #sent} and confirmed by its owner now: version 1 awaits activation. */
	Chain confirmed(LocalDate start, LocalDate end) throws Exception {
		Chain chain = sent(start, end);
		confirm(chain.owner(), chain.agreementId(), 1);
		return chain;
	}

	/** {@link #confirmed} and activated by the admin, who is not the assignee: the agreement ACTIVE. */
	Chain activated(LocalDate start, LocalDate end) throws Exception {
		Chain chain = confirmed(start, end);
		Result answer = activate(admin, chain.agreementId());
		assertEquals(204, answer.status(), answer.rawBody());
		return chain;
	}

	/** An agreement in effect from today for six months: version 1 EFFECTIVE at once. */
	Chain inEffect() throws Exception {
		return activated(today(), today().plusMonths(6));
	}

	String create(TestUser drafter, String leadId, String unitId) throws Exception {
		Result answer = createResult(drafter, leadId, unitId);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		return id;
	}

	Result createResult(TestUser drafter, String leadId, String unitId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", today().plusDays(10).toString());
		body.put("endDate", today().plusMonths(6).toString());
		Result answer = api.post(drafter, AGREEMENTS, body);
		if (answer.status() == 201) {
			String id = answer.body().path("agreementId").asString();
			if (!created.contains(id)) {
				created.add(id);
			}
		}
		return answer;
	}

	void saveComplete(String agreementId, LocalDate start, LocalDate end) throws Exception {
		Map<String, Object> body = AgreementFeePolicyApiPostgresTest.complete(rowVersion(agreementId));
		body.put("startDate", start.toString());
		body.put("endDate", end.toString());
		Result answer = api.put(sale, AGREEMENTS + "/" + agreementId + "/draft", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	void send(String agreementId) throws Exception {
		Result answer = api.post(sale, AGREEMENTS + "/" + agreementId + "/draft/submission",
				Map.of("expectedDataVersion", rowVersion(agreementId)));
		assertEquals(204, answer.status(), answer.rawBody());
	}

	void confirm(TestUser owner, String agreementId, int versionNo) throws Exception {
		Result answer = confirmResult(owner, agreementId, versionNo);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	Result confirmResult(TestUser owner, String agreementId, int versionNo) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", passwordOf(owner));
		body.put("statementVersion", OWNER_STATEMENT);
		body.put("versions", List.of(Map.of("agreementId", agreementId, "versionNo", versionNo)));
		return api.post(owner, ME + "/confirmations", body);
	}

	Result reject(TestUser owner, String agreementId, int versionNo) throws Exception {
		return api.post(owner, ME + "/" + agreementId + "/rejection",
				Map.of("versionNo", versionNo, "reason", "The rent is too low."));
	}

	Result openDraft(String agreementId, long expectedDataVersion) throws Exception {
		return api.post(sale, AGREEMENTS + "/" + agreementId + "/draft", Map.of("expectedDataVersion",
				expectedDataVersion));
	}

	Result activate(TestUser caller, String agreementId) throws Exception {
		return activate(caller, agreementId, rowVersion(agreementId), 1);
	}

	Result activate(TestUser caller, String agreementId, long dataVersion, int versionNo) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", dataVersion);
		body.put("versionNo", versionNo);
		body.put("statementVersion", ActivationStatement.VERSION);
		body.put("documentNumber", "HDMG-2026/014");
		body.put("signedOn", today().toString());
		body.put("sunvibeSignatoryName", "Nguyen Van A");
		body.put("sunvibeSignatoryTitle", "Director");
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation", body);
	}

	// ==================== the four stops over HTTP ====================

	Result suspendAgreement(TestUser caller, String agreementId, Object reasonType, Object note) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("reasonType", reasonType);
		body.put("note", note);
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/suspension", body);
	}

	/** The suspension for a complaint with a note; the answer must be 204. */
	void suspended(TestUser caller, String agreementId) throws Exception {
		Result answer = suspendAgreement(caller, agreementId, "COMPLAINT", "Tenant reported a different price.");
		assertEquals(204, answer.status(), answer.rawBody());
	}

	Result releaseSuspension(TestUser caller, String agreementId, Object expectedDataVersion, Object reason)
			throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", expectedDataVersion);
		body.put("reason", reason);
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/suspension-release", body);
	}

	/** The release with the stored data version and a reason. */
	Result releaseSuspension(TestUser caller, String agreementId) throws Exception {
		return releaseSuspension(caller, agreementId, rowVersion(agreementId), "The price was checked with the owner.");
	}

	Result terminate(TestUser caller, String agreementId, Map<String, Object> body) throws Exception {
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/termination", body);
	}

	Result cancel(TestUser caller, String agreementId, Object expectedDataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", expectedDataVersion);
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/cancellation", body);
	}

	/** A POST whose body is the raw text, sent as JSON. */
	Result postRaw(TestUser caller, String path, String raw) throws Exception {
		return api.send(api.request(caller, path).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(raw, StandardCharsets.UTF_8)).build());
	}

	static Map<String, Object> ownerWithdrew(LocalDate reportedOn, String channel, String note) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "OWNER_WITHDREW");
		body.put("reportedOn", reportedOn == null ? null : reportedOn.toString());
		body.put("channel", channel);
		body.put("note", note);
		return body;
	}

	static Map<String, Object> ownerWithdrew() {
		return ownerWithdrew(today(), "PHONE_CALL", "The owner sold the unit.");
	}

	static Map<String, Object> unitLet(LocalDate reportedOn, String introduced, String note) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "UNIT_LET");
		body.put("reportedOn", reportedOn == null ? null : reportedOn.toString());
		body.put("introducedBySunvibe", introduced);
		body.put("note", note);
		return body;
	}

	static Map<String, Object> unitLet() {
		return unitLet(today(), "UNKNOWN", null);
	}

	static Map<String, Object> sunvibeTerminated(String note) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "SUNVIBE_TERMINATED");
		body.put("note", note);
		return body;
	}

	JsonNode detail(TestUser caller, String agreementId) throws Exception {
		Result answer = api.get(caller, AGREEMENTS + "/" + agreementId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	static void assertCode(int status, String code, Result answer) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
	}

	/** 400 VALIDATION_FAILED with exactly one entry on the field, with these allowed values or none. */
	static void assertValidation(Result answer, String field, List<String> allowedOrNull) {
		assertCode(400, "VALIDATION_FAILED", answer);
		JsonNode errors = answer.body().path("errors");
		assertEquals(1, errors.size(), answer.rawBody());
		assertEquals(field, errors.path(0).path("field").asString(), answer.rawBody());
		if (allowedOrNull == null) {
			assertTrue(errors.path(0).path("allowedValues").isMissingNode(), answer.rawBody());
		} else {
			List<String> allowed = new java.util.ArrayList<>();
			errors.path(0).path("allowedValues").forEach(value -> allowed.add(value.asString()));
			assertEquals(allowedOrNull, allowed, answer.rawBody());
		}
	}

	/** 400 MALFORMED_REQUEST with its fixed message and no entry. */
	static void assertMalformed(Result answer) {
		assertCode(400, "MALFORMED_REQUEST", answer);
		assertEquals("The request could not be read.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals(0, answer.body().path("errors").size(), answer.rawBody());
	}

	/** The lead's state and version and the count of its History rows: what a stop never changes (BR-18). */
	List<Object> leadSnapshot(String leadId) {
		Map<String, Object> lead = jdbc.queryForMap("select status, version from owner_leads where id = ?", leadId);
		return List.of(lead.get("status"), lead.get("version"),
				count("select count(*) from owner_lead_activities where lead_id = ?", leadId));
	}

	int accessRows(String agreementId) {
		return count("select count(*) from brokerage_agreement_access_logs where agreement_id = ?", agreementId);
	}

	// ==================== reading and moving rows ====================

	long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	Map<String, Object> agreementRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreements where id = ?", agreementId);
	}

	String status(String agreementId) {
		return jdbc.queryForObject("select status from brokerage_agreements where id = ?", String.class, agreementId);
	}

	Map<String, Object> versionRow(String agreementId, int versionNo) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ? and version_no = ?",
				agreementId, versionNo);
	}

	String versionStatus(String agreementId, int versionNo) {
		return (String) versionRow(agreementId, versionNo).get("status");
	}

	/**
	 * The agreement's history as "event version actor", in the order the rows were written (a row of a due transition
	 * is dated back to its moment, so the time order differs from the writing order).
	 */
	List<String> events(String agreementId) {
		return jdbc.queryForList("select event_type || ' ' || version_no || ' ' || coalesce(actor_user_id, '-') from"
				+ " brokerage_agreement_events where agreement_id = ? order by id", String.class, agreementId);
	}

	/** The occurrence of the agreement's only event of that type. */
	Instant eventAt(String agreementId, String type) {
		return jdbc.queryForObject("select occurred_at from brokerage_agreement_events where agreement_id = ? and"
				+ " event_type = ?", Timestamp.class, agreementId, type).toInstant();
	}

	int eventCount(String agreementId, String type) {
		return count("select count(*) from brokerage_agreement_events where agreement_id = ? and event_type = ?",
				agreementId, type);
	}

	int outboxRows(String unitId) {
		return count("select count(*) from outbox_events where aggregate_id = ?", unitId);
	}

	int suspensionRows(String agreementId) {
		return count("select count(*) from brokerage_agreement_suspensions where agreement_id = ?", agreementId);
	}

	int openSuspensions(String agreementId) {
		return count("select count(*) from brokerage_agreement_suspensions where agreement_id = ? and released_at is null",
				agreementId);
	}

	int notices(TestUser owner) {
		return count("select count(*) from auth_email_notifications where user_id = ?", owner.userId());
	}

	/** Moves the period of a version, as a version signed long ago; written from the JVM's clock. */
	void movePeriod(String agreementId, int versionNo, LocalDate start, LocalDate end) {
		jdbc.update("update brokerage_agreement_versions set start_date = ?, end_date = ? where agreement_id = ?"
				+ " and version_no = ?", Date.valueOf(start), Date.valueOf(end), agreementId, versionNo);
	}

	/** Moves the sending time of a version back by that many days, from the JVM's clock; answers it. */
	Instant sentDaysAgo(String agreementId, int versionNo, long days) {
		Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(days, ChronoUnit.DAYS);
		jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ? and version_no = ?",
				Timestamp.from(sentAt), agreementId, versionNo);
		return sentAt;
	}

	/** Moves the owner's confirmation of a version back by that many days, from the JVM's clock; answers it. */
	Instant confirmedDaysAgo(String agreementId, int versionNo, long days) {
		Instant answeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(days, ChronoUnit.DAYS);
		jdbc.update("update brokerage_agreement_versions set sent_at = ?, owner_answered_at = ? where agreement_id = ?"
				+ " and version_no = ?", Timestamp.from(answeredAt.minus(1, ChronoUnit.HOURS)), Timestamp.from(answeredAt),
				agreementId, versionNo);
		return answeredAt;
	}

	/** The start of a calendar date in Vietnam. */
	static Instant startOf(LocalDate date) {
		return date.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
	}

	// ==================== nothing written (plan E05S8-P8) ====================

	/** What a refused call must leave as it is: the agreement row, its history, its suspensions, its unit's outbox. */
	record WriteSnapshot(String agreementId, long rowVersion, String status, int events, int suspensions, String unitId,
			int outboxRows) {
	}

	WriteSnapshot snapshot(String agreementId) {
		Map<String, Object> row = agreementRow(agreementId);
		String unitId = (String) row.get("unit_id");
		return new WriteSnapshot(agreementId, ((Number) row.get("version")).longValue(), (String) row.get("status"),
				count("select count(*) from brokerage_agreement_events where agreement_id = ?", agreementId),
				suspensionRows(agreementId), unitId, outboxRows(unitId));
	}

	void assertNothingWritten(WriteSnapshot before) {
		assertEquals(before, snapshot(before.agreementId()));
	}

	// ==================== forced interleavings (plan E05S8-P9) ====================

	/**
	 * A lock a test transaction holds until it is closed: the agreement row FOR NO KEY UPDATE, the history table in SHARE
	 * mode (every writer then waits at its history insert after its guarded update), or the share read of the published
	 * answer.
	 */
	final class Hold implements AutoCloseable {

		private final CountDownLatch held = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final Future<?> owner;
		private volatile Runnable beforeCommit = () -> { };

		Hold(String sql, Object... args) throws Exception {
			this(() -> jdbc.query(sql, (RowCallbackHandler) row -> { }, args));
		}

		Hold(Runnable lock) throws Exception {
			owner = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				lock.run();
				held.countDown();
				try {
					release.await(120, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				beforeCommit.run();
			}));
			assertTrue(held.await(30, TimeUnit.SECONDS), "the test never took its lock");
		}

		/** Statements the holding transaction runs after the release, before it commits. */
		Hold beforeCommit(Runnable statements) {
			beforeCommit = statements;
			return this;
		}

		@Override
		public void close() throws Exception {
			release.countDown();
			owner.get(30, TimeUnit.SECONDS);
		}
	}

	/** The agreement row held FOR NO KEY UPDATE, as a writer of it holds it. */
	Hold holdAgreementRow(String agreementId) throws Exception {
		return new Hold("select id from brokerage_agreements where id = ? for no key update", agreementId);
	}

	/** The history table held in SHARE mode: a writer waits at its history insert, its guarded update done. */
	Hold holdHistory() throws Exception {
		return new Hold(() -> jdbc.execute("lock table brokerage_agreement_events in share mode"));
	}

	/** Waits until this database shows {@code count} ungranted locks: that many sides wait. */
	void awaitWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (count("select count(*) from pg_locks l join pg_stat_activity a on a.pid = l.pid where not l.granted"
					+ " and a.datname = current_database()") >= count) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(count + " sides never waited on a lock");
	}
}
