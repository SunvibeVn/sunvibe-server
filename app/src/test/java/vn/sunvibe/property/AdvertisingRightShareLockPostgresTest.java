package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerNoticePort;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The locking read of one agreement on PostgreSQL (ADR-012 decision 2 "Commands", D12-13; E05-S6 spec S6-D11; criterion
 * LK-1): while a caller's transaction holds the agreement row FOR SHARE, an activation of that agreement waits on its
 * guarded update until the caller commits (another agreement's update does not wait); while an activation holds the row
 * after its update, a locked read waits for its commit and then answers what it committed; two locked reads do not wait
 * for each other. The activation is held after its update by an armed notice port, which it calls last (plan E05S6-P4,
 * P7). A context of its own (the nested configuration, plan E05S6-P2). Needs Docker; a Linux run is the evidence of a
 * race test (testing.md).
 */
class AdvertisingRightShareLockPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";

	/** Records each notice; when armed, the activation's notice waits until released, its transaction still open. */
	static final class ArmedNoticePort implements OwnerNoticePort {

		final List<String> activated = new CopyOnWriteArrayList<>();
		private volatile CountDownLatch release;
		private volatile CountDownLatch reached;

		void arm() {
			reached = new CountDownLatch(1);
			release = new CountDownLatch(1);
		}

		void awaitReached() throws InterruptedException {
			assertTrue(reached.await(30, TimeUnit.SECONDS), "the activation never reached its notice");
		}

		void release() {
			CountDownLatch latch = release;
			release = null;
			if (latch != null) {
				latch.countDown();
			}
		}

		@Override
		public void agreementPending(String ownerUserId) {
			// no notice is checked here
		}

		@Override
		public void agreementActivated(String ownerUserId) {
			activated.add(ownerUserId);
			CountDownLatch latch = release;
			if (latch != null) {
				reached.countDown();
				try {
					latch.await(60, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	@TestConfiguration
	static class ArmedNotices {

		@Bean
		@Primary
		ArmedNoticePort armedNoticePort() {
			return new ArmedNoticePort();
		}
	}

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<String> created = new CopyOnWriteArrayList<>();
	private final List<String> owners = new CopyOnWriteArrayList<>();

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private AdvertisingRightUseCase rights;

	@Autowired
	private ArmedNoticePort notices;

	@Override
	String phonePrefix() {
		return "0935";
	}

	@AfterEach
	void releaseNotices() {
		notices.release();
	}

	@AfterAll
	void stopExecutorAndDeleteRows() {
		executor.shutdownNow();
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== fixtures (as AgreementActivationApiPostgresTest builds them) ====================

	private record Chain(String unitId, String agreementId) {
	}

	private Chain confirmed() throws Exception {
		Passed passed = house(unique("Share Lock Street"));
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
		assertEquals(204, api.post(owner, ME + "/confirmations", confirmation).status());
		return new Chain(unit, id);
	}

	private Result activate(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		body.put("versionNo", 1);
		body.put("statementVersion", ActivationStatement.VERSION);
		body.put("documentNumber", "HDMG-2026/014");
		body.put("signedOn", today().toString());
		body.put("sunvibeSignatoryName", "Nguyen Van A");
		body.put("sunvibeSignatoryTitle", "Director");
		return api.post(manager, AGREEMENTS + "/" + agreementId + "/activation", body);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private int waiters() {
		return count("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()");
	}

	private void awaitWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (waiters() >= count) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(count + " transactions never waited on a lock");
	}

	/** A read-write test transaction that holds the locked read of the pair until it is closed. */
	private final class SharedRead implements AutoCloseable {

		private final CountDownLatch held = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final Future<Optional<AdvertisingRightView>> owner;

		SharedRead(AdvertisingPair pair) throws Exception {
			owner = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
				Optional<AdvertisingRightView> answer = rights.answerUnderShareLock(pair);
				held.countDown();
				try {
					release.await(120, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return answer;
			}));
			assertTrue(held.await(30, TimeUnit.SECONDS), "the locked read never ran");
		}

		@Override
		public void close() throws Exception {
			release.countDown();
			owner.get(30, TimeUnit.SECONDS);
		}
	}

	private Future<Optional<AdvertisingRightView>> lockedRead(AdvertisingPair pair) {
		return executor.submit(() -> new TransactionTemplate(transactionManager)
				.execute(status -> rights.answerUnderShareLock(pair)));
	}

	// ==================== LK-1 ====================

	/**
	 * LK-1: while a transaction holds A1's row FOR SHARE, A2's activation (another row) is not blocked, and A1's
	 * activation waits on its guarded update until that transaction commits, then commits.
	 */
	@Test
	void shareLockHeld_activationWaitsThenCommits() throws Exception {
		Chain first = confirmed();
		Chain second = confirmed();
		Future<Result> activation;
		try (SharedRead read = new SharedRead(new AdvertisingPair(first.unitId(), first.agreementId()))) {
			Result other = activate(second.agreementId());
			assertEquals(204, other.status(), other.rawBody());
			activation = executor.submit(() -> activate(first.agreementId()));
			awaitWaiters(1);
			Thread.sleep(300);
			assertFalse(activation.isDone(), "the activation finished while the row was held FOR SHARE");
			assertEquals("OWNER_CONFIRMED", jdbc.queryForObject("select status from brokerage_agreement_versions"
					+ " where agreement_id = ?", String.class, first.agreementId()));
		}

		Result answer = activation.get(60, TimeUnit.SECONDS);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("ACTIVE", jdbc.queryForObject("select status from brokerage_agreements where id = ?", String.class,
				first.agreementId()));
	}

	/**
	 * LK-1, the other order: the activation holds A1's row after its guarded update (held at its notice, before its
	 * commit); the locked read waits for that commit, then answers A1 allowed with its terms.
	 */
	@Test
	void activationHoldsRow_lockedReadWaitsThenAllowed() throws Exception {
		Chain chain = confirmed();
		notices.arm();
		Future<Result> activation = executor.submit(() -> activate(chain.agreementId()));
		notices.awaitReached();

		Future<Optional<AdvertisingRightView>> read = lockedRead(new AdvertisingPair(chain.unitId(), chain.agreementId()));
		awaitWaiters(1);
		Thread.sleep(300);
		assertFalse(read.isDone(), "the locked read answered before the activation committed");
		notices.release();

		assertEquals(204, activation.get(60, TimeUnit.SECONDS).status());
		AdvertisingRightView answer = read.get(60, TimeUnit.SECONDS).orElseThrow();
		assertTrue(answer.allowed(), answer.toString());
		assertEquals(8_000_000L, answer.termsInEffectOrNull().monthlyRentOrNull());
	}

	/** LK-1: two locked reads of one agreement do not wait for each other (FOR SHARE, not FOR UPDATE). */
	@Test
	void twoLockedReads_noWait() throws Exception {
		Chain chain = confirmed();
		AdvertisingPair pair = new AdvertisingPair(chain.unitId(), chain.agreementId());
		try (SharedRead first = new SharedRead(pair)) {
			Future<Optional<AdvertisingRightView>> second = lockedRead(pair);
			Optional<AdvertisingRightView> answer = second.get(10, TimeUnit.SECONDS);
			assertTrue(answer.isPresent());
			assertEquals(0, waiters());
		}
	}
}
