package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.outbox.OutboxConsumer;
import vn.sunvibe.outbox.OutboxRetryPolicy;
import vn.sunvibe.outbox.OutboxSubscriptions;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The outbox events of an activation on PostgreSQL (brokerage-agreement BR-12; E05-S6 spec S6-D12; ADR-012 decision 6;
 * ADR-014 decisions 2 to 4; criterion EV-1): with one consumer subscribed (the nested configuration, plan E05S6-P8), an
 * activation commits one row due now, one due at its start when that lies ahead, and one due at the end of its end date,
 * each only within the outbox's 799-day horizon, with the unit id as the only payload; a lost race, a refusal and the
 * lapse and start stores write none. The worker is off in the test profile, so the rows stay PENDING. Both jobs run every
 * hour here, as in {@link AgreementActivationLapseApiPostgresTest}, so the stores run only when a test calls them; a
 * context of its own (plan E05S6-P2). Needs Docker.
 */
@TestPropertySource(properties = {"app.brokerage.lapse-delay=1h", "app.brokerage.start-delay=1h"})
class AgreementActivationOutboxPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String OWNER_STATEMENT = "brokerage-agreement-2026-11";
	private static final String CONSUMER = "test-unit-advertising";
	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Duration FOURTEEN_DAYS = Duration.ofDays(14);

	/** EV-1's test subscription in place of the production list, which subscribes no consumer (plan E05S6-P8). */
	@TestConfiguration
	static class TestSubscription {

		@Bean
		@Primary
		OutboxSubscriptions testOutboxSubscriptions() {
			// listing-publish E06-S7 spec S-5: listing's real consumer is a bean of every context, so it is listed too
			return OutboxSubscriptions.of(Map.of(UnitAdvertisingChangedEvent.TYPE, new OutboxSubscriptions.Subscription(
					Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of(CONSUMER, "listing-unit-advertising"))));
		}

		/** Records nothing: the worker is off in the test profile, so nothing is delivered. */
		@Bean
		OutboxConsumer testUnitAdvertisingConsumer() {
			return new OutboxConsumer() {

				@Override
				public String name() {
					return CONSUMER;
				}

				@Override
				public OutboxRetryPolicy retryPolicy() {
					return OutboxRetryPolicy.DEFAULT;
				}

				@Override
				public void deliver(Delivery delivery) {
					// never called here
				}
			};
		}
	}

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<String> created = new CopyOnWriteArrayList<>();
	private final List<String> units = new CopyOnWriteArrayList<>();
	private final List<String> owners = new CopyOnWriteArrayList<>();

	@Autowired
	private AgreementCommandUseCase commands;

	@Autowired
	private AgreementDomainRepository agreements;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Override
	String phonePrefix() {
		return "0930";
	}

	@AfterEach
	void deleteRows() {
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

	// ==================== fixtures (as AgreementActivationLapseApiPostgresTest builds them) ====================

	private record Chain(String leadId, String unitId, String agreementId) {
	}

	/** A Sale's draft for a new owner's completed house, its period from start to end, sent and confirmed now. */
	private Chain confirmed(LocalDate start, LocalDate end) throws Exception {
		Passed passed = house(unique("Outbox Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		units.add(unit);
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
		Result sent = api.post(sale, AGREEMENTS + "/" + id + "/draft/submission",
				Map.of("expectedDataVersion", rowVersion(id)));
		assertEquals(204, sent.status(), sent.rawBody());
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("agreementId", id);
		item.put("versionNo", 1);
		Map<String, Object> confirmation = new LinkedHashMap<>();
		confirmation.put("currentPassword", passwordOf(owner));
		confirmation.put("statementVersion", OWNER_STATEMENT);
		confirmation.put("versions", List.of(item));
		Result confirmed = api.post(owner, ME + "/confirmations", confirmation);
		assertEquals(204, confirmed.status(), confirmed.rawBody());
		return new Chain(passed.leadId(), unit, id);
	}

	/** Moves version 1's sending and confirmation back together so that its activation lapse is due 1 s ago. */
	private void dueConfirmation(String agreementId) {
		Map<String, Object> version = jdbc.queryForMap("select sent_at, owner_answered_at from"
				+ " brokerage_agreement_versions where agreement_id = ? and version_no = 1", agreementId);
		Instant at = now().minus(FOURTEEN_DAYS).minusSeconds(1);
		Duration back = Duration.between(at, ((Timestamp) version.get("owner_answered_at")).toInstant());
		jdbc.update("update brokerage_agreement_versions set sent_at = ?, owner_answered_at = ? where agreement_id = ?"
				+ " and version_no = 1", Timestamp.from(((Timestamp) version.get("sent_at")).toInstant().minus(back)),
				Timestamp.from(at), agreementId);
	}

	/**
	 * ST-1's fixture: version 1 ACTIVATED with its activation record and a start of yesterday, written through the
	 * repository's update (no command, so no outbox row) of an aggregate activated when that start still lay ahead.
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
		assertEquals("ACTIVATED", versionStatus(chain.agreementId()));
		return chain;
	}

	private Result activate(TestUser caller, String agreementId, long dataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", dataVersion);
		body.put("versionNo", 1);
		body.put("statementVersion", ActivationStatement.VERSION);
		body.put("documentNumber", "HDMG-2026/014");
		body.put("signedOn", today().toString());
		body.put("sunvibeSignatoryName", "Nguyen Van A");
		body.put("sunvibeSignatoryTitle", "Director");
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation", body);
	}

	private Result activate(String agreementId) throws Exception {
		return activate(manager, agreementId, rowVersion(agreementId));
	}

	private Result refuse(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		body.put("versionNo", 1);
		body.put("reasonType", "DOCUMENT_DIFFERS");
		return api.post(manager, AGREEMENTS + "/" + agreementId + "/activation-refusal", body);
	}

	// ==================== reading rows ====================

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private String versionStatus(String agreementId) {
		return jdbc.queryForObject("select status from brokerage_agreement_versions where agreement_id = ?"
				+ " and version_no = 1", String.class, agreementId);
	}

	private String agreementStatus(String agreementId) {
		return jdbc.queryForObject("select status from brokerage_agreements where id = ?", String.class, agreementId);
	}

	/** The unit's rows of the test consumer, each one E05 event (listing-publish E06-S7 spec S-5). */
	private int outboxRows(String unitId) {
		return count("select count(*) from outbox_events where aggregate_id = ? and consumer = ?", unitId, CONSUMER);
	}

	/** One outbox row of the unit: when it is due and when it was appended. */
	private record Row(Instant due, Instant occurredAt) {
	}

	/**
	 * The unit's outbox rows, earliest due first, after checking that every one of them is EV-1's event as the test
	 * consumer receives it: the type, version 1, brokerage's unit, the unit id as the only payload key, PENDING, no attempt.
	 */
	private List<Row> eventRows(String unitId) {
		int rows = outboxRows(unitId);
		assertEquals(rows, count("select count(*) from outbox_events where aggregate_id = ? and event_type = ?"
				+ " and event_version = 1 and consumer = ? and source_module = 'brokerage' and aggregate_type = 'unit'"
				+ " and payload = jsonb_build_object('unitId', aggregate_id) and status = 'PENDING' and attempts = 0",
				unitId, UnitAdvertisingChangedEvent.TYPE, CONSUMER));
		assertEquals(rows, count("select count(distinct event_id) from outbox_events where aggregate_id = ?", unitId));
		return jdbc.query("select next_attempt_at, occurred_at from outbox_events where aggregate_id = ? and consumer = ?"
				+ " order by next_attempt_at, id", (row, index) -> new Row(row.getTimestamp("next_attempt_at").toInstant(),
				row.getTimestamp("occurred_at").toInstant()), unitId, CONSUMER);
	}

	private static void assertWithin(Instant from, Instant to, Instant value) {
		assertFalse(value.isBefore(from), value + " before " + from);
		assertFalse(value.isAfter(to), value + " after " + to);
	}

	private static Instant startsAt(LocalDate start) {
		return start.atStartOfDay(VIETNAM).toInstant();
	}

	private static Instant endsAt(LocalDate end) {
		return end.plusDays(1).atStartOfDay(VIETNAM).toInstant();
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	// ==================== the forced order (as AgreementActivationConcurrencyPostgresTest's) ====================

	/** A row lock a test transaction holds until it is closed. */
	private final class Hold implements AutoCloseable {

		private final CountDownLatch held = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final Future<?> owner;

		Hold(String sql, Object... arguments) throws Exception {
			owner = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				jdbc.query(sql, (RowCallbackHandler) row -> { }, arguments);
				held.countDown();
				try {
					release.await(120, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertTrue(held.await(30, TimeUnit.SECONDS), "the test never took its lock");
		}

		@Override
		public void close() throws Exception {
			release.countDown();
			owner.get(30, TimeUnit.SECONDS);
		}
	}

	private void awaitWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (count("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()")
					>= count) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(count + " commands never waited on a lock");
	}

	// ==================== EV-1 ====================

	/** EV-1: a start today: one row due now and one due at the end of the end date, the unit id as the only payload. */
	@Test
	void activate_startToday_twoRowsNowAndEnd() throws Exception {
		LocalDate end = today().plusMonths(6);
		Chain chain = confirmed(today(), end);
		Instant from = now();

		Result answer = activate(chain.agreementId());

		Instant to = now();
		assertEquals(204, answer.status(), answer.rawBody());
		List<Row> rows = eventRows(chain.unitId());
		assertEquals(2, rows.size(), rows.toString());
		assertWithin(from, to, rows.get(0).due());
		assertEquals(endsAt(end), rows.get(1).due());
		for (Row row : rows) {
			assertWithin(from, to, row.occurredAt());
		}
		assertEquals("EFFECTIVE", versionStatus(chain.agreementId()));
	}

	/** EV-1: a start ahead: rows due now, at the start and at the end of the end date. */
	@Test
	void activate_startAhead_threeRows() throws Exception {
		LocalDate start = today().plusDays(2);
		LocalDate end = start.plusMonths(6);
		Chain chain = confirmed(start, end);
		Instant from = now();

		Result answer = activate(chain.agreementId());

		Instant to = now();
		assertEquals(204, answer.status(), answer.rawBody());
		List<Row> rows = eventRows(chain.unitId());
		assertEquals(3, rows.size(), rows.toString());
		assertWithin(from, to, rows.get(0).due());
		assertEquals(startsAt(start), rows.get(1).due());
		assertEquals(endsAt(end), rows.get(2).due());
		assertEquals("ACTIVATED", versionStatus(chain.agreementId()));
	}

	/**
	 * EV-1 with AV-6: two independent managers activate at once (the basis lead's row held, then released): only the
	 * winner's rows commit; the lost activation's appends roll back with it.
	 */
	@Test
	void twoActivations_released_onlyTheWinnersRows() throws Exception {
		LocalDate end = today().plusMonths(6);
		Chain chain = confirmed(today(), end);
		long dataVersion = rowVersion(chain.agreementId());
		Future<Result> first;
		Future<Result> second;
		try (Hold hold = new Hold("select id from owner_leads where id = ? for update", chain.leadId())) {
			first = executor.submit(() -> activate(manager, chain.agreementId(), dataVersion));
			awaitWaiters(1);
			second = executor.submit(() -> activate(admin, chain.agreementId(), dataVersion));
			awaitWaiters(2);
		}

		Result one = first.get(60, TimeUnit.SECONDS);
		Result two = second.get(60, TimeUnit.SECONDS);

		List<Row> rows = eventRows(chain.unitId());
		assertEquals(2, rows.size(), rows + " / " + one.status() + " / " + two.status());
		assertEquals(endsAt(end), rows.get(1).due());
		assertEquals(1, List.of(one, two).stream().filter(answer -> answer.status() == 204).count(),
				one.rawBody() + " / " + two.rawBody());
		Result lost = one.status() == 204 ? two : one;
		assertEquals(409, lost.status(), lost.rawBody());
	}

	/** EV-1 (S6-D12): a refusal, the 14-day lapse store and the start store each write no outbox row. */
	@Test
	void refusalLapseAndStartStores_noRow() throws Exception {
		Chain refused = confirmed(today(), today().plusMonths(6));
		Chain lapsing = confirmed(today(), today().plusMonths(6));
		dueConfirmation(lapsing.agreementId());
		Chain starting = activatedWithPastStart();

		Result answer = refuse(refused.agreementId());
		int lapsed = commands.storeDueLapses();
		int started = commands.storeDueStarts();

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("ACTIVATION_REFUSED", versionStatus(refused.agreementId()));
		assertEquals(0, outboxRows(refused.unitId()));
		assertTrue(lapsed >= 1, String.valueOf(lapsed));
		assertEquals("LAPSED", versionStatus(lapsing.agreementId()));
		assertEquals(0, outboxRows(lapsing.unitId()));
		assertTrue(started >= 1, String.valueOf(started));
		assertEquals("EFFECTIVE", versionStatus(starting.agreementId()));
		assertEquals(0, outboxRows(starting.unitId()));
	}

	/** EV-1 (ADR-014 decision 2): an end beyond the horizon gets no row; the start's row within it is written. */
	@Test
	void activate_endBeyondHorizon_noEndRow() throws Exception {
		LocalDate start = today().plusDays(500);
		Chain chain = confirmed(start, start.plusMonths(12));
		Instant from = now();

		Result answer = activate(chain.agreementId());

		Instant to = now();
		assertEquals(204, answer.status(), answer.rawBody());
		List<Row> rows = eventRows(chain.unitId());
		assertEquals(2, rows.size(), rows.toString());
		assertWithin(from, to, rows.get(0).due());
		assertEquals(startsAt(start), rows.get(1).due());
	}

	/** EV-1 (ADR-014 decision 2): a start beyond the horizon: only the row due now; ACTIVATED and ACTIVE as AV-2. */
	@Test
	void activate_startBeyondHorizon_onlyNowRow() throws Exception {
		LocalDate start = today().plusDays(900);
		Chain chain = confirmed(start, start.plusMonths(6));
		Instant from = now();

		Result answer = activate(chain.agreementId());

		Instant to = now();
		assertEquals(204, answer.status(), answer.rawBody());
		List<Row> rows = eventRows(chain.unitId());
		assertEquals(1, rows.size(), rows.toString());
		assertWithin(from, to, rows.get(0).due());
		assertEquals("ACTIVATED", versionStatus(chain.agreementId()));
		assertEquals("ACTIVE", agreementStatus(chain.agreementId()));
	}
}
