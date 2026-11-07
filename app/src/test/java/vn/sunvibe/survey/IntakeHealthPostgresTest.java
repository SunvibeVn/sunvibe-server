package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.shared.media.IntakeGate;
import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.IntakeHealthJdbcAdapter;

/**
 * The intake health row on PostgreSQL (survey-media spec A-18, plan Task A5; SM-14 health rules): writes in their own
 * short transactions holding no other lock, the resume guarded by {@code failure_seq}, {@code state_epoch} moving only
 * on a real pause or resume, the read joining the caller's snapshot, and the gate needing no second connection.
 */
class IntakeHealthPostgresTest extends MediaApiTestBase {

	private static final String LEAD = "01JLEADMED1AHEA1THAAAAAAAA";
	private static final Instant T = Instant.parse("2026-11-01T02:00:00.123456Z");

	@Autowired
	private IntakeHealthRepository health;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterAll
	void restore() {
		MediaTestRows.delete(jdbc, List.of(LEAD));
		MediaIntakeFixture.reset(jdbc);
	}

	@Test
	void recordFailure_duringSweep_resumeOfThatSweepUpdatesNothing() {
		health.recordFailure(PauseReason.STORE, true, T);
		long seqAtStart = health.read().failureSeq();

		health.recordFailure(PauseReason.SCANNER, false, T);
		boolean resumed = health.resumeIf(seqAtStart, T);

		assertFalse(resumed);
		IntakeHealth row = health.read();
		assertTrue(row.paused());
		assertEquals(PauseReason.STORE, row.pauseReason());
		assertEquals(seqAtStart + 1, row.failureSeq());
	}

	@Test
	void resume_sameSeq_opensAndRecordsTheSweep() {
		health.recordFailure(PauseReason.PROCESSOR, true, T);
		IntakeHealth paused = health.read();

		assertTrue(health.resumeIf(paused.failureSeq(), T.plusSeconds(5)));

		IntakeHealth row = health.read();
		assertFalse(row.paused());
		assertNull(row.pauseReason());
		assertEquals(paused.stateEpoch() + 1, row.stateEpoch());
		assertEquals(T.plusSeconds(5), row.lastSweepOkAt());
	}

	@Test
	void resume_notPaused_changesNothing() {
		IntakeHealth before = health.read();

		assertFalse(health.resumeIf(before.failureSeq(), T.plusSeconds(60)));

		assertEquals(before, health.read());
	}

	@Test
	void sweepCompleted_recordsTheTimeWithoutResuming() {
		health.recordFailure(PauseReason.STORE, true, T);
		IntakeHealth paused = health.read();

		health.recordSweepCompleted(T.plusSeconds(9));

		IntakeHealth row = health.read();
		assertEquals(T.plusSeconds(9), row.lastSweepOkAt());
		assertTrue(row.paused());
		assertEquals(paused.stateEpoch(), row.stateEpoch());
		assertEquals(paused.failureSeq(), row.failureSeq());
	}

	@Test
	void stateEpoch_movesOnlyOnRealPauseAndResume() {
		long epoch = health.read().stateEpoch();

		health.recordFailure(PauseReason.STORE, false, T);
		assertEquals(epoch, health.read().stateEpoch());
		health.recordFailure(PauseReason.STORE, true, T);
		assertEquals(epoch + 1, health.read().stateEpoch());
		health.recordFailure(PauseReason.SCANNER, true, T);
		assertEquals(epoch + 1, health.read().stateEpoch());
		health.resumeIf(health.read().failureSeq(), T);
		assertEquals(epoch + 2, health.read().stateEpoch());
		health.resumeIf(health.read().failureSeq(), T);
		assertEquals(epoch + 2, health.read().stateEpoch());
	}

	@Test
	void twoInstancesPausing_epochBumpedOnce() throws Exception {
		IntakeHealth before = health.read();
		CountDownLatch start = new CountDownLatch(1);

		CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
			await(start);
			health.recordFailure(PauseReason.STORE, true, T);
		});
		CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
			await(start);
			health.recordFailure(PauseReason.SCANNER, true, T.plusSeconds(1));
		});
		start.countDown();
		CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

		IntakeHealth row = health.read();
		assertEquals(before.stateEpoch() + 1, row.stateEpoch());
		assertEquals(before.failureSeq() + 2, row.failureSeq());
		assertTrue(row.paused());
	}

	@Test
	void scanBlocked_setIndependentlyOfPauseReason() {
		health.recordFailure(PauseReason.STORE, true, T);

		health.setScanBlocked(T.plusSeconds(1));
		health.setScanBlocked(T.plusSeconds(2));

		IntakeHealth row = health.read();
		assertEquals(T.plusSeconds(1), row.scanBlockedSince());
		assertEquals(PauseReason.STORE, row.pauseReason());
		health.resumeIf(row.failureSeq(), T.plusSeconds(3));
		assertTrue(health.read().scanBlocked());
		health.recordProbePassed(false, T.plusSeconds(4));
		assertTrue(health.read().scanBlocked(), "a passing store or processor probe keeps the scan block");
		health.recordProbePassed(true, T.plusSeconds(5));
		assertFalse(health.read().scanBlocked());
	}

	/** spec 5.2, A-18 (code review M3, SV-069): a passing probe writes its time and changes nothing else. */
	@Test
	void probePassed_writesLastProbeOkAt_keepsPauseAndSequence() {
		health.recordFailure(PauseReason.STORE, true, T);
		IntakeHealth before = health.read();

		health.recordProbePassed(false, T.plusSeconds(7));

		IntakeHealth row = health.read();
		assertEquals(T.plusSeconds(7), row.lastProbeOkAt());
		assertEquals(before.failureSeq(), row.failureSeq());
		assertEquals(before.stateEpoch(), row.stateEpoch());
		assertEquals(before.intakePausedSince(), row.intakePausedSince());
		assertEquals(PauseReason.STORE, row.pauseReason());
	}

	@Test
	void writes_holdNoOtherLock() throws Exception {
		String checklist = MediaTestRows.checklist(jdbc, LEAD, 1);
		jdbc.update("INSERT INTO survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " VALUES (?, 0, 0, now())", checklist);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(1);

		CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager)
				.executeWithoutResult(tx -> {
					jdbc.queryForList("SELECT checklist_id FROM survey_media_sets WHERE checklist_id = ? FOR UPDATE",
							checklist);
					locked.countDown();
					await(done);
				}));
		await(locked);
		try {
			CompletableFuture.runAsync(() -> {
				health.recordFailure(PauseReason.STORE, true, T);
				health.beatWorker(T);
			}).get(5, TimeUnit.SECONDS);
		} finally {
			done.countDown();
			holder.get(10, TimeUnit.SECONDS);
			MediaTestRows.delete(jdbc, List.of(LEAD));
		}
		assertTrue(health.read().paused());
	}

	@Test
	void beats_writeAppClockTimes() {
		health.beatWorker(T);
		health.beatDeletion(T.plusSeconds(7));

		IntakeHealth row = health.read();
		assertEquals(T, row.lastWorkerBeatAt());
		assertEquals(T.plusSeconds(7), row.lastDeletionBeatAt());
	}

	@Test
	void read_joinsCallerSnapshot() throws Exception {
		TransactionTemplate repeatable = new TransactionTemplate(transactionManager);
		repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		repeatable.setReadOnly(true);

		List<IntakeHealth> seen = repeatable.execute(tx -> {
			IntakeHealth first = health.read();
			try {
				CompletableFuture.runAsync(() -> health.recordFailure(PauseReason.STORE, true, T))
						.get(5, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			return List.of(first, health.read());
		});

		assertFalse(seen.get(0).paused());
		assertEquals(seen.get(0), seen.get(1));
		assertTrue(health.read().paused());
	}

	@Test
	void gateInsideCommand_withPoolOfOne_takesNoSecondConnection() {
		try (HikariDataSource pool = poolOfOne()) {
			DataSourceTransactionManager poolManager = new DataSourceTransactionManager(pool);
			IntakeHealthJdbcAdapter poolHealth = new IntakeHealthJdbcAdapter(new NamedParameterJdbcTemplate(pool),
					poolManager);
			IntakeGate gate = new IntakeGate(poolHealth, () -> Instant.now());
			TransactionTemplate command = new TransactionTemplate(poolManager);

			command.executeWithoutResult(tx -> {
				gate.requireOpen();
				new JdbcTemplate(pool).queryForObject("SELECT 1", Integer.class);
			});

			jdbc.update("UPDATE survey_media_health SET last_sweep_ok_at = now() - interval '11 minutes'"
					+ " WHERE id = 1");
			MediaIntakePausedException paused = assertThrows(MediaIntakePausedException.class,
					() -> command.executeWithoutResult(tx -> gate.requireOpen()));
			gate.recordRefusal(paused);
		}
		IntakeHealth row = health.read();
		assertEquals(PauseReason.SWEEP_STALE, row.pauseReason());
		assertTrue(row.paused());
	}

	private HikariDataSource poolOfOne() {
		HikariDataSource shared;
		try {
			shared = dataSource.unwrap(HikariDataSource.class);
		} catch (java.sql.SQLException e) {
			throw new IllegalStateException(e);
		}
		HikariDataSource pool = new HikariDataSource();
		pool.setJdbcUrl(shared.getJdbcUrl());
		pool.setUsername(shared.getUsername());
		pool.setPassword(shared.getPassword());
		pool.setMaximumPoolSize(1);
		pool.setConnectionTimeout(1500);
		return pool;
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(10, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
