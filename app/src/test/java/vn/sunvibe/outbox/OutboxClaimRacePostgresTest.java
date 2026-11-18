package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * Claims of one consumer's rows racing on PostgreSQL (E05-S5b spec 4.5 step 2, S5b-AC-6; SV-093). The order is forced,
 * never slept: an uncommitted claim holds its rows on its own connection, and a second claim is parked after its
 * snapshot by a gate. The gate, as MediaJobClaimRacePostgresTest's: on the parked connection only, an updatable view
 * shadows {@code outbox_events} through {@code search_path}, and its condition waits on an advisory lock the test holds.
 * Needs Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxClaimRacePostgresTest extends OutboxPostgresTestBase {

	private static final long GATE = 7094L;
	private static final long BOUND_SECONDS = 30;

	@Autowired
	private DataSource dataSource;

	private final List<OutboxScheduling> schedulings = new ArrayList<>();
	private ExecutorService pool;

	@BeforeAll
	void createGate() {
		jdbc.execute("CREATE SCHEMA outbox_claim_gate");
		jdbc.execute("CREATE FUNCTION outbox_claim_gate.held() RETURNS boolean LANGUAGE plpgsql VOLATILE AS $$"
				+ " BEGIN PERFORM pg_advisory_xact_lock_shared(" + GATE + "); RETURN true; END $$");
		jdbc.execute("CREATE VIEW outbox_claim_gate.outbox_events AS SELECT * FROM public.outbox_events"
				+ " WHERE outbox_claim_gate.held()");
	}

	@AfterAll
	void dropGate() {
		jdbc.execute("DROP SCHEMA IF EXISTS outbox_claim_gate CASCADE");
	}

	@BeforeEach
	void setUp() {
		clock.set(START);
		pool = Executors.newSingleThreadExecutor();
	}

	@AfterEach
	void tearDown() {
		pool.shutdownNow();
		schedulings.forEach(OutboxScheduling::destroy);
	}

	@Test
	void twoWorkers_claimOneConsumerAtOnce_eachRowOnce() throws Exception {
		appendEvents(adapter(subscriptions("x")), 15);
		OutboxWorker worker = worker("x");
		Instant now = clock.instant();

		List<Long> first;
		List<OutboxWorker.ClaimedRow> second;
		try (Connection other = dataSource.getConnection()) {
			other.setAutoCommit(false);
			first = claimOn(other, "token-of-the-other-worker", now, limits(Duration.ofSeconds(1)));
			assertThat(first).hasSize(10);

			second = worker.claim("x", now);

			other.commit();
		}

		Set<Long> secondIds = new HashSet<>();
		second.forEach(row -> secondIds.add(row.id()));
		assertThat(second).hasSize(5);
		assertThat(secondIds).doesNotContainAnyElementsOf(first);
		Set<Long> all = new HashSet<>(first);
		all.addAll(secondIds);
		assertThat(all).containsExactlyInAnyOrderElementsOf(
				jdbc.queryForList("SELECT id FROM outbox_events", Long.class));
		assertThat(jdbc.queryForList("SELECT attempts FROM outbox_events", Integer.class)).hasSize(15).containsOnly(1);
		assertThat(jdbc.queryForList("SELECT DISTINCT lease_token FROM outbox_events", String.class)).hasSize(2);
	}

	@Test
	void claimAfterOtherCommit_movedLease_rowSkipped() throws Exception {
		appendEvents(adapter(subscriptions("x")), 1);
		long rowId = jdbc.queryForObject("SELECT id FROM outbox_events", Long.class);
		OutboxWorker worker = worker("x");
		Instant now = clock.instant();

		List<Long> parkedResult;
		String firstToken;
		try (Connection gate = dataSource.getConnection()) {
			gate.setAutoCommit(false);
			execute(gate, "SELECT pg_advisory_xact_lock(" + GATE + ")");
			Future<List<Long>> parked = pool.submit(() -> claimBehindTheGate(now));
			awaitClaimAtTheGate(parked);

			List<OutboxWorker.ClaimedRow> first = worker.claim("x", now);
			assertThat(first).extracting(OutboxWorker.ClaimedRow::id).containsExactly(rowId);
			firstToken = first.get(0).token();
			gate.commit();
			parkedResult = parked.get(BOUND_SECONDS, TimeUnit.SECONDS);
		}

		assertThat(parkedResult).doesNotContain(rowId);
		assertThat(jdbc.queryForObject("SELECT lease_token FROM outbox_events WHERE id = ?", String.class, rowId))
				.isEqualTo(firstToken);
		assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id = ?", Integer.class, rowId))
				.isEqualTo(1);
	}

	@Test
	void claim_takesOnlyTheNamedConsumersRows() {
		EventOutboxAdapter toY = adapter(subscriptions("y"));
		EventOutboxAdapter toX = adapter(subscriptions("x"));
		for (int i = 0; i < 12; i++) {
			String unitId = unitId(i);
			inTransaction(status -> toY.append(UnitAdvertisingChangedEvent.now(unitId)));
			clock.advance(Duration.ofSeconds(1));
		}
		for (int i = 0; i < 12; i++) {
			String unitId = unitId(100 + i);
			inTransaction(status -> toX.append(UnitAdvertisingChangedEvent.now(unitId)));
			clock.advance(Duration.ofSeconds(1));
		}
		List<Long> oldestOfX = jdbc.queryForList("SELECT id FROM outbox_events WHERE consumer = 'x'"
				+ " ORDER BY next_attempt_at, id LIMIT 10", Long.class);

		List<OutboxWorker.ClaimedRow> claimed = worker("x", "y").claim("x", clock.instant());

		assertThat(claimed).hasSize(10).allSatisfy(row -> assertThat(row.consumer()).isEqualTo("x"));
		assertThat(claimed).extracting(OutboxWorker.ClaimedRow::id).containsExactlyInAnyOrderElementsOf(oldestOfX);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE consumer = 'y' AND attempts > 0",
				Integer.class)).isZero();
	}

	private OutboxWorker worker(String... consumers) {
		OutboxScheduling scheduling = new OutboxScheduling(List.of(consumers));
		schedulings.add(scheduling);
		List<OutboxConsumer> beans = new ArrayList<>();
		for (String name : consumers) {
			beans.add(new TestConsumer(name));
		}
		return worker(subscriptions(consumers), scheduling, limits(Duration.ofSeconds(1)),
				beans.toArray(OutboxConsumer[]::new));
	}

	/** {@link OutboxSql#CLAIM_DUE} on {@code connection} for consumer x, bound in its documented order. */
	private static List<Long> claimOn(Connection connection, String token, Instant now, OutboxWorker.Limits limits)
			throws SQLException {
		List<Long> ids = new ArrayList<>();
		try (PreparedStatement claim = connection.prepareStatement(OutboxSql.CLAIM_DUE)) {
			claim.setString(1, token);
			claim.setObject(2, time(now.plus(limits.lease())));
			claim.setString(3, "x");
			claim.setObject(4, time(now));
			claim.setInt(5, limits.batchSize());
			claim.setString(6, "x");
			claim.setObject(7, time(now));
			try (ResultSet rows = claim.executeQuery()) {
				while (rows.next()) {
					ids.add(rows.getLong("id"));
				}
			}
		}
		return ids;
	}

	/** The real claim statement on its own connection, where {@code outbox_events} is the waiting view. */
	private List<Long> claimBehindTheGate(Instant now) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			execute(connection, "SET LOCAL search_path = outbox_claim_gate, public");
			List<Long> claimed = claimOn(connection, "token-behind-the-gate", now, limits(Duration.ofSeconds(1)));
			connection.commit();
			return claimed;
		}
	}

	private void awaitClaimAtTheGate(Future<List<Long>> parked) throws InterruptedException, ExecutionException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			if (parked.isDone()) {
				fail("the second claim ended before the gate: " + parked.get());
			}
			// pg_locks is cluster-wide, and the contexts share one server (PostgresContainerConfig)
			if (jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND objid = ?"
					+ " AND NOT granted AND database = (SELECT oid FROM pg_database WHERE datname = current_database())",
					Integer.class, GATE) > 0) {
				return;
			}
			Thread.sleep(20);
		}
		fail("the second claim never reached the gate");
	}

	private static void execute(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static OffsetDateTime time(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
