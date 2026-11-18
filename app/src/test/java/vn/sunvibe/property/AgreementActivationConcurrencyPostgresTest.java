package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The races of activation on PostgreSQL (brokerage-agreement BR-5; US-5 AC-6 for this slice's commands; E05-S6 spec
 * S6-D1; criterion AV-6): two decisions on one confirmed version, and an activation against a lapse run across
 * activateBy, each forced by a row a test transaction holds until the sides wait on it, seen in pg_stat_activity of this
 * database, then released (plan E05S6-P7; testing.md: an order from locks, never from a sleep). With both jobs held off
 * ({@code @TestPropertySource} of {@link AgreementActivationLapseApiPostgresTest}, so the two share one context, plan
 * E05S6-P2). Needs Docker; a Linux run is the evidence of a race test (testing.md).
 */
@TestPropertySource(properties = {"app.brokerage.lapse-delay=1h", "app.brokerage.start-delay=1h"})
class AgreementActivationConcurrencyPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<String> created = new CopyOnWriteArrayList<>();
	private final List<String> owners = new CopyOnWriteArrayList<>();

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private AgreementCommandUseCase commands;

	@Override
	String phonePrefix() {
		return "0934";
	}

	@AfterEach
	void deleteRows() {
		AgreementFixture.delete(jdbc, created);
		created.clear();
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
		owners.clear();
	}

	@AfterAll
	void stopExecutor() {
		executor.shutdownNow();
	}

	// ==================== fixtures (as AgreementActivationApiPostgresTest builds them) ====================

	private record Chain(String leadId, TestUser owner, String agreementId) {
	}

	private Chain confirmed() throws Exception {
		Passed passed = house(unique("Activation Race Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String unit = completed(passed.leadId()).path("units").path(0).path("unitId").asString();
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
		draft.put("startDate", today().toString());
		draft.put("endDate", today().plusMonths(6).toString());
		assertEquals(204, api.put(sale, AGREEMENTS + "/" + id + "/draft", draft).status());
		assertEquals(204, api.post(sale, AGREEMENTS + "/" + id + "/draft/submission",
				Map.of("expectedDataVersion", rowVersion(id))).status());
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("agreementId", id);
		item.put("versionNo", 1);
		Map<String, Object> confirmation = new LinkedHashMap<>();
		confirmation.put("currentPassword", passwordOf(owner));
		confirmation.put("statementVersion", "brokerage-agreement-2026-11");
		confirmation.put("versions", List.of(item));
		Result confirmed = api.post(owner, ME + "/confirmations", confirmation);
		assertEquals(204, confirmed.status(), confirmed.rawBody());
		return new Chain(passed.leadId(), owner, id);
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

	private Result refuse(TestUser caller, String agreementId, long dataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", dataVersion);
		body.put("versionNo", 1);
		body.put("reasonType", "DOCUMENT_DIFFERS");
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation-refusal", body);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private int events(String agreementId, String type) {
		return count("select count(*) from brokerage_agreement_events where agreement_id = ? and event_type = ?",
				agreementId, type);
	}

	// ==================== the forced order (as AgreementSendConcurrencyPostgresTest's) ====================

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

	/** The basis lead's row, which both decisions lock first (S6-D1 step 4). */
	private Hold lead(String leadId) throws Exception {
		return new Hold("select id from owner_leads where id = ? for update", leadId);
	}

	/** The agreement's row, which an activation's guarded update and a lapse run's both need. */
	private Hold agreement(String agreementId) throws Exception {
		return new Hold("select id from brokerage_agreements where id = ? for update", agreementId);
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

	private static void assertLost(Result answer) {
		assertEquals(409, answer.status(), answer.rawBody());
		assertTrue(List.of("CONCURRENT_MODIFICATION", "AGREEMENT_NOT_AWAITING_ACTIVATION")
				.contains(answer.body().path("code").asString()), answer.rawBody());
	}

	// ==================== AV-6 ====================

	/** AV-6: two independent managers activate at once: one 204, the other refused, one ACTIVATED row. */
	@Test
	void twoActivations_released_oneActivatedOtherConflict() throws Exception {
		Chain chain = confirmed();
		long dataVersion = rowVersion(chain.agreementId());
		Future<Result> first;
		Future<Result> second;
		try (Hold hold = lead(chain.leadId())) {
			first = executor.submit(() -> activate(manager, chain.agreementId(), dataVersion));
			awaitWaiters(1);
			second = executor.submit(() -> activate(admin, chain.agreementId(), dataVersion));
			awaitWaiters(2);
		}

		Result one = first.get(60, TimeUnit.SECONDS);
		Result two = second.get(60, TimeUnit.SECONDS);

		Result won = one.status() == 204 ? one : two;
		Result lost = won == one ? two : one;
		assertEquals(204, won.status(), won.rawBody());
		assertLost(lost);
		assertEquals(1, events(chain.agreementId(), "ACTIVATED"));
		assertEquals(dataVersion + 1, rowVersion(chain.agreementId()));
	}

	/** AV-6: an activation and a refusal at once: one outcome, never both. */
	@Test
	void activationAndRefusal_released_oneOutcome() throws Exception {
		Chain chain = confirmed();
		long dataVersion = rowVersion(chain.agreementId());
		Future<Result> activation;
		Future<Result> refusal;
		try (Hold hold = lead(chain.leadId())) {
			activation = executor.submit(() -> activate(manager, chain.agreementId(), dataVersion));
			awaitWaiters(1);
			refusal = executor.submit(() -> refuse(admin, chain.agreementId(), dataVersion));
			awaitWaiters(2);
		}

		Result activated = activation.get(60, TimeUnit.SECONDS);
		Result refused = refusal.get(60, TimeUnit.SECONDS);

		assertEquals(1, List.of(activated, refused).stream().filter(answer -> answer.status() == 204).count(),
				activated.rawBody() + " / " + refused.rawBody());
		assertLost(activated.status() == 204 ? refused : activated);
		assertEquals(1, events(chain.agreementId(), "ACTIVATED") + events(chain.agreementId(), "ACTIVATION_REFUSED"));
		String status = jdbc.queryForObject("select status from brokerage_agreement_versions where agreement_id = ?",
				String.class, chain.agreementId());
		assertEquals(activated.status() == 204 ? "EFFECTIVE" : "ACTIVATION_REFUSED", status);
	}

	/**
	 * AV-6 (as S5's OC-7): an activation that passed its checks before activateBy waits on the agreement's row; a lapse
	 * run started after activateBy selects the agreement and waits too; released, the activation (queued first) wins and
	 * the run writes nothing: never both outcomes. The only wait for a time is bounded (20 s), never one for an order.
	 */
	@Test
	void activationAndLapseRun_acrossActivateBy_activationWinsRunWritesNothing() throws Exception {
		Chain chain = confirmed();
		Instant confirmedAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofDays(14)).plusSeconds(10);
		Map<String, Object> version = jdbc.queryForMap("select sent_at, owner_answered_at from"
				+ " brokerage_agreement_versions where agreement_id = ?", chain.agreementId());
		Duration back = Duration.between(confirmedAt, ((Timestamp) version.get("owner_answered_at")).toInstant());
		jdbc.update("update brokerage_agreement_versions set sent_at = ?, owner_answered_at = ? where agreement_id = ?",
				Timestamp.from(((Timestamp) version.get("sent_at")).toInstant().minus(back)), Timestamp.from(confirmedAt),
				chain.agreementId());
		Instant activateBy = confirmedAt.plus(Duration.ofDays(14));
		long dataVersion = rowVersion(chain.agreementId());
		Future<Result> activation;
		Future<Integer> run;
		try (Hold hold = agreement(chain.agreementId())) {
			activation = executor.submit(() -> activate(manager, chain.agreementId(), dataVersion));
			awaitWaiters(1);
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (!Instant.now().isAfter(activateBy.plusMillis(200)) && System.nanoTime() < deadline) {
				Thread.sleep(50);
			}
			assertTrue(Instant.now().isAfter(activateBy), "the clock never passed activateBy");
			run = executor.submit(() -> commands.storeDueLapses());
			awaitWaiters(2);
		}

		Result activated = activation.get(60, TimeUnit.SECONDS);
		int stored = run.get(60, TimeUnit.SECONDS);

		assertEquals(204, activated.status(), activated.rawBody());
		assertEquals(0, stored);
		assertEquals(1, events(chain.agreementId(), "ACTIVATED"));
		assertEquals(0, events(chain.agreementId(), "VERSION_LAPSED"));
		assertEquals("EFFECTIVE", jdbc.queryForObject("select status from brokerage_agreement_versions where"
				+ " agreement_id = ?", String.class, chain.agreementId()));
	}
}
