package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobClaim;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.support.MediaSql;

/**
 * Two workers racing for the due PROCESS jobs on PostgreSQL (survey-media spec 4.4 step 3, ADR-011: one run per job;
 * SV-093). A claim leaves the job PENDING and moves {@code due_at} to the lease end, so a second claim whose snapshot
 * predates the first commit finds the row unlocked, and only the row's new {@code due_at} can turn it away. The order is
 * forced: on the second connection only, a view shadows {@code survey_media_read_budgets} through {@code search_path}
 * and waits on an advisory lock, which parks that claim after its snapshot and before it locks a row. A job whose lease
 * ran out stays claimable with its old token (spec A-7). Needs Docker.
 */
class MediaJobClaimRacePostgresTest extends MediaApiTestBase {

	private static final long GATE = 7093L;
	private static final String FIRST_TOKEN = "01JCLAIMRACEFIRST000000001";
	private static final String SECOND_TOKEN = "01JCLAIMRACESECOND00000001";
	private static final Duration LEASE = Duration.ofMinutes(5);

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private SurveyMediaJobDomainRepository jobs;

	@Autowired
	private MediaClock clock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;
	private final ExecutorService pool = Executors.newSingleThreadExecutor();

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0997");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Claim Race Sale", "SALE");
		leader = survey.user("Claim Race Leader", "SALE_LEADER");
		jdbc.execute("create schema claim_gate");
		// a set-returning function always runs when scanned; a plain WHERE function could be skipped by the planner
		jdbc.execute("create function claim_gate.held_budgets() returns setof public.survey_media_read_budgets"
				+ " language plpgsql volatile as $$ begin perform pg_advisory_xact_lock_shared(" + GATE + ");"
				+ " return query select * from public.survey_media_read_budgets; end $$");
		jdbc.execute("create view claim_gate.survey_media_read_budgets as select * from claim_gate.held_budgets()");
	}

	@BeforeEach
	void onlyOwnJobs() {
		jdbc.update("update survey_media_jobs set state = 'DONE', lease_token = null where state = 'PENDING'");
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		jdbc.execute("drop schema if exists claim_gate cascade");
		survey.cleanUp();
	}

	@Test
	void secondClaim_snapshotBeforeFirstCommit_takesTheOtherJob() throws Exception {
		List<String> mediaIds = dueJobs(2);
		Instant now = clock.now().plus(Duration.ofSeconds(1));

		String first;
		String second;
		try (Connection gate = dataSource.getConnection()) {
			gate.setAutoCommit(false);
			execute(gate, "select pg_advisory_xact_lock(" + GATE + ")");
			Future<String> parked = pool.submit(() -> claimBehindTheGate(now));
			awaitClaimAtTheGate(parked);

			first = claim(now, FIRST_TOKEN).orElseThrow().job().mediaId().getValue();
			gate.commit();
			second = parked.get(30, TimeUnit.SECONDS);
		}

		assertNotEquals(first, second, "both workers claimed one job: " + jobRows(mediaIds));
		assertEquals(List.of(1, 1), jdbc.queryForList("select attempts from survey_media_jobs where media_id in (?, ?)",
				Integer.class, mediaIds.get(0), mediaIds.get(1)), jobRows(mediaIds));
	}

	@Test
	void claim_whileLeaseRunsThenAfterItEnds_takesTheJobOnlyAfter() throws Exception {
		String mediaId = dueJobs(1).get(0);
		Instant now = clock.now().plus(Duration.ofSeconds(1));
		assertEquals(mediaId, claim(now, FIRST_TOKEN).orElseThrow().job().mediaId().getValue());

		Optional<MediaJobClaim> whileLeased = claim(now.plus(Duration.ofMinutes(1)), SECOND_TOKEN);
		MediaJob afterTheLease = claim(now.plus(LEASE).plusSeconds(1), SECOND_TOKEN).orElseThrow().job();

		assertTrue(whileLeased.isEmpty(), "a running lease was taken over");
		assertEquals(mediaId, afterTheLease.mediaId().getValue());
		assertEquals(2, afterTheLease.attempts());
		assertEquals(SECOND_TOKEN, afterTheLease.leaseTokenOrNull());
	}

	private Optional<MediaJobClaim> claim(Instant now, String token) {
		return jobs.claimNextProcess(now, now.truncatedTo(ChronoUnit.HOURS), List.of(), token, now.plus(LEASE));
	}

	private List<String> dueJobs(int count) throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String checklistId = checklists.started(sale, visit.appointmentId());
		Instant dueAt = clock.now().minus(Duration.ofMinutes(1));
		List<String> ids = media.insertPhotos(checklistId, sale.userId(), Row.PROCESSING, count, dueAt, 100L,
				"claim race " + ulidGenerator.generateId());
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		for (String id : ids) {
			transaction.executeWithoutResult(status -> jobs.upsertProcess(SurveyMediaId.of(id), dueAt, dueAt));
		}
		return ids;
	}

	/** The real claim statement on its own connection, where the budgets table is the waiting view. */
	private String claimBehindTheGate(Instant now) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			execute(connection, "set local search_path = claim_gate, public");
			List<String> claimed = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true))
					.query(MediaSql.CLAIM_NEXT_PROCESS, new MapSqlParameterSource("token", SECOND_TOKEN)
							.addValue("leaseUntil", time(now.plus(LEASE)))
							.addValue("now", time(now))
							.addValue("hourStart", time(now.truncatedTo(ChronoUnit.HOURS)))
							.addValue("excluded", List.of("-")), (row, n) -> row.getString("media_id"));
			connection.commit();
			return claimed.isEmpty() ? null : claimed.get(0);
		}
	}

	private void awaitClaimAtTheGate(Future<String> parked) throws InterruptedException, ExecutionException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			if (parked.isDone()) {
				fail("the second claim ended before the gate: " + parked.get());
			}
			if (jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and objid = ?"
					+ " and not granted", Integer.class, GATE) > 0) {
				return;
			}
			Thread.sleep(20);
		}
		fail("the second claim never reached the gate");
	}

	private String jobRows(List<String> mediaIds) {
		return jdbc.queryForList("select media_id, state, attempts, lease_token from survey_media_jobs"
				+ " where media_id in (?, ?)", mediaIds.get(0), mediaIds.get(1)).toString();
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
