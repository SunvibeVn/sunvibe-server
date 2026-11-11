package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * Concurrent staff changes on PostgreSQL (acquisition-owner-lead-queue Q9-10, BR-9, E-5): two changes of one lead made
 * on the same version both pass the version check, then race on the row version of the update; the loser is a 409
 * {@code CONCURRENT_MODIFICATION}, never a 500, and leaves neither the lead nor a history entry behind. Two
 * assignments of a new ward race on the primary key, which the native upsert absorbs. Each case starts both HTTP calls
 * at one gate, 5 rounds with fresh leads (or a ward emptied again). The DUPLICATE closes (SV-003) force their order
 * instead: the test holds a row lock and sees each command wait on it in pg_stat_activity before it starts the next,
 * and the lock queue then runs them in that order. Real logins; the ward Bà Nà has nobody, Hòa Vang is used by no other
 * test class. Every lead, territory and user made here is removed afterwards. Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerLeadConcurrencyPostgresTest {

	private static final String LEADS = "/api/v1/staff/owner-leads";
	private static final String TERRITORIES = "/api/v1/staff/sale-territories";
	private static final String BA_NA = "20308";
	private static final String HOA_VANG = "20320";
	private static final int ROUNDS = 5;

	/** Phones 0982xxxxxx, apart from the other acquisition test classes. */
	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);

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
	private PlatformTransactionManager transactionManager;

	private AcquisitionApiTestSupport api;
	private ExecutorService executor;
	private final List<String> userIds = new ArrayList<>();
	private final List<String> leadIds = new ArrayList<>();

	private TestUser sale1;
	private TestUser sale2;
	private TestUser saleLeader;
	private TestUser admin;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		executor = Executors.newFixedThreadPool(2);
		sale1 = loggedIn("SALE");
		sale2 = loggedIn("SALE");
		saleLeader = loggedIn("SALE_LEADER");
		admin = loggedIn("ADMIN");
	}

	@AfterEach
	void removeTerritoriesAndLeads() {
		jdbc.update("delete from sale_territories where ward_code = ?", HOA_VANG);
		jdbc.update("delete from sale_territory_changes where ward_code = ?", HOA_VANG);
		AcquisitionApiTestSupport.deleteLeads(jdbc, leadIds);
		leadIds.clear();
	}

	@AfterAll
	void tearDown() {
		executor.shutdownNow();
		AcquisitionApiTestSupport.deleteUsers(jdbc, userIds);
		api.http().close();
	}

	/**
	 * Q9-10, acquisition-owner-lead-queue E-5: a leader gives one NEW lead to sale1 and to sale2 at the same moment,
	 * both on the version they read: one 200, one 409, one assignment entry, and the lead belongs to the winner.
	 */
	@Test
	void assign_twoConcurrentOnTheSameVersion_oneOkOneConflict() throws Exception {
		List<TestUser> assignees = List.of(sale1, sale2);
		for (int round = 1; round <= ROUNDS; round++) {
			String lead = submit();
			assertEquals("NEW", status(lead));
			long version = version(lead);

			List<Result> results = race(i -> api.post(saleLeader, LEADS + "/" + lead + "/assign",
					Map.of("assigneeUserId", assignees.get(i).userId(), "expectedVersion", version)));

			String context = "round " + round + ": " + statuses(results);
			int winner = assertOneOkOneConflict(results, context);
			assertEquals(assignees.get(winner).userId(), results.get(winner).body().path("assignedUserId").asString());
			assertEquals(version + 1, results.get(winner).body().path("version").asLong(), context);
			assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? "
					+ "and type in ('ASSIGNED', 'REASSIGNED')", lead), context);
			assertEquals(assignees.get(winner).userId(), jdbc.queryForObject(
					"select to_assignee_id from owner_lead_activities where lead_id = ? and type = 'ASSIGNED'",
					String.class, lead), context);
			assertEquals(assignees.get(winner).userId(), assignee(lead), context);
			assertEquals("ASSIGNED", status(lead), context);
			assertEquals(version + 1, version(lead), context);
		}
	}

	/**
	 * Q9-10, acquisition-owner-lead-queue BR-9: two status changes of one ASSIGNED lead on the same version: one 200,
	 * one 409, one STATUS_CHANGED entry, and the lead has the winner's status.
	 */
	@Test
	void statusChange_twoConcurrentOnTheSameVersion_oneOkOneConflict() throws Exception {
		List<String> targets = List.of("CONTACTED", "ON_HOLD");
		for (int round = 1; round <= ROUNDS; round++) {
			String lead = submit();
			Result assigned = api.post(saleLeader, LEADS + "/" + lead + "/assign",
					Map.of("assigneeUserId", sale1.userId(), "expectedVersion", version(lead)));
			assertEquals(200, assigned.status(), assigned.rawBody());
			long version = assigned.body().path("version").asLong();

			List<Result> results = race(i -> api.post(sale1, LEADS + "/" + lead + "/status-changes",
					Map.of("toStatus", targets.get(i), "expectedVersion", version)));

			String context = "round " + round + ": " + statuses(results);
			int winner = assertOneOkOneConflict(results, context);
			assertEquals(targets.get(winner), results.get(winner).body().path("status").asString(), context);
			assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? "
					+ "and type = 'STATUS_CHANGED'", lead), context);
			assertEquals(targets.get(winner), jdbc.queryForObject("select to_status from owner_lead_activities "
					+ "where lead_id = ? and type = 'STATUS_CHANGED'", String.class, lead), context);
			assertEquals(targets.get(winner), status(lead), context);
			assertEquals(version + 1, version(lead), context);
		}
	}

	/**
	 * Q9-12, acquisition-owner-lead-queue BR-5: an ADMIN gives a ward nobody holds to sale1 and to sale2 at the same
	 * moment: both inserts meet on the primary key, the native upsert turns the second into an update, so both answer
	 * 200, one row remains (the person of the change committed last) and each change of person wrote its change row.
	 */
	@Test
	void put_territory_twoConcurrentOnNewWard_bothSucceedOneRowRemains() throws Exception {
		List<TestUser> holders = List.of(sale1, sale2);
		for (int round = 1; round <= ROUNDS; round++) {
			jdbc.update("delete from sale_territories where ward_code = ?", HOA_VANG);
			jdbc.update("delete from sale_territory_changes where ward_code = ?", HOA_VANG);

			List<Result> results = race(i -> api.put(admin, TERRITORIES + "/" + HOA_VANG,
					Map.of("userId", holders.get(i).userId())));

			String context = "round " + round + ": " + statuses(results);
			for (int i = 0; i < results.size(); i++) {
				assertEquals(200, results.get(i).status(), context + ", " + results.get(i).rawBody());
				assertEquals(holders.get(i).userId(), results.get(i).body().path("userId").asString(), context);
			}
			List<String> rows = jdbc.queryForList("select user_id from sale_territories where ward_code = ?",
					String.class, HOA_VANG);
			assertEquals(1, rows.size(), context);
			assertTrue(Set.of(sale1.userId(), sale2.userId()).contains(rows.get(0)), context);
			assertEquals(Set.of(sale1.userId(), sale2.userId()), Set.copyOf(jdbc.queryForList(
					"select new_user_id from sale_territory_changes where ward_code = ?", String.class, HOA_VANG)),
					context);
			assertEquals(2, count("select count(*) from sale_territory_changes where ward_code = ?", HOA_VANG),
					context);
		}
	}

	/**
	 * acquisition-owner-lead-bulk-assign AC-10 (b): a bulk assignment to sale1 and a single assignment to sale2 of one NEW
	 * lead, both on the version read before, started at one gate. Both orders are valid ends, so the order is not forced:
	 * per round exactly one wins (bulk ASSIGNED or single 200); the loser is the bulk with 200 and STALE or the single
	 * with 409 CONCURRENT_MODIFICATION on expectedVersion; never a 500; one assignment entry, the lead held by the winner.
	 */
	@Test
	void assignLeads_vsSingleAssignOnTheSameVersion_oneWinnerPerRound() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			String lead = submit();
			assertEquals("NEW", status(lead));
			long version = version(lead);

			List<Result> results = race(i -> i == 0
					? api.post(saleLeader, LEADS + "/assignments", Map.of("assigneeUserId", sale1.userId(), "leads",
							List.of(Map.of("leadId", lead, "expectedVersion", version))))
					: api.post(saleLeader, LEADS + "/" + lead + "/assign",
							Map.of("assigneeUserId", sale2.userId(), "expectedVersion", version)));

			String context = "round " + round + ": " + statuses(results);
			Result bulk = results.get(0);
			Result single = results.get(1);
			assertEquals(200, bulk.status(), context + ", " + bulk.rawBody());
			assertEquals(lead, bulk.body().path("results").path(0).path("leadId").asString(), context);
			String outcome = bulk.body().path("results").path(0).path("outcome").asString();
			TestUser winner;
			if (outcome.equals("ASSIGNED")) {
				assertEquals(409, single.status(), context + ", " + single.rawBody());
				assertEquals("CONCURRENT_MODIFICATION", single.body().path("code").asString(), single.rawBody());
				assertEquals(1, single.body().path("errors").size(), single.rawBody());
				assertEquals("expectedVersion", single.body().path("errors").get(0).path("field").asString(),
						single.rawBody());
				winner = sale1;
			} else {
				assertEquals("STALE", outcome, context + ", " + bulk.rawBody());
				assertEquals(200, single.status(), context + ", " + single.rawBody());
				winner = sale2;
			}
			assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? "
					+ "and type in ('ASSIGNED', 'REASSIGNED')", lead), context);
			assertEquals(winner.userId(), jdbc.queryForObject(
					"select to_assignee_id from owner_lead_activities where lead_id = ? and type = 'ASSIGNED'",
					String.class, lead), context);
			assertEquals(winner.userId(), assignee(lead), context);
			assertEquals("ASSIGNED", status(lead), context);
			assertEquals(version + 1, version(lead), context);
		}
	}

	/**
	 * acquisition-owner-lead-queue BR-9, SV-003 DC-8: two DUPLICATE closes of one lead on the same version, forced to
	 * wait in this order on a row lock the test holds: the first waiter closes the lead, the second is a 409
	 * CONCURRENT_MODIFICATION on expectedVersion (never a 500), and the lead names the first original.
	 */
	@Test
	void statusChange_twoDuplicateClosesWaitingOnOneLead_oneOkOneConflict() throws Exception {
		String lead = submit();
		String first = submit();
		String second = submit();
		long version = version(lead);

		List<Result> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newCachedThreadPool()) {
			Future<Result> toFirst;
			Future<Result> toSecond;
			try (Hold hold = new Hold(pool, "select id from owner_leads where id = ? for no key update", lead)) {
				toFirst = pool.submit(() -> closeAsDuplicate(lead, first, version));
				awaitWaiters(1);
				toSecond = pool.submit(() -> closeAsDuplicate(lead, second, version));
				awaitWaiters(2);
			}
			results.add(toFirst.get(60, TimeUnit.SECONDS));
			results.add(toSecond.get(60, TimeUnit.SECONDS));
		}

		String context = statuses(results).toString();
		assertEquals(0, assertOneOkOneConflict(results, context), context);
		assertEquals("DUPLICATE", status(lead));
		assertEquals(first, duplicateOf(lead));
		assertEquals(version + 1, version(lead));
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? "
				+ "and type = 'STATUS_CHANGED'", lead));
	}

	// ==================== Helpers ====================

	/**
	 * A row lock a test transaction takes on the pool and holds until it is closed (the Hold of
	 * IntakeConcurrencyPostgresTest, on the executor the test passes in).
	 */
	private final class Hold implements AutoCloseable {

		private final CountDownLatch held = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final Future<?> owner;

		Hold(ExecutorService pool, String sql, Object... arguments) throws Exception {
			owner = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
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

	/**
	 * Waits until at least this many sessions of this test's database wait on a lock (pg_stat_activity), polling every
	 * 20 ms for at most 30 s; the wait proves the order, no sleep decides it.
	 */
	private void awaitWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (count("select count(*) from pg_stat_activity where wait_event_type = 'Lock' "
					+ "and datname = current_database()") >= count) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError(count + " commands never waited on a lock");
	}

	/** The leader closes the lead as a duplicate of the original on the version given. */
	private Result closeAsDuplicate(String leadId, String originalId, long expectedVersion) throws Exception {
		return api.post(saleLeader, LEADS + "/" + leadId + "/status-changes", Map.of("toStatus", "DUPLICATE",
				"expectedVersion", expectedVersion, "reason", "Trùng", "duplicateOfLeadId", originalId));
	}

	private String duplicateOf(String leadId) {
		return jdbc.queryForObject("select duplicate_of_lead_id from owner_leads where id = ?", String.class, leadId);
	}

	/** How many of these leads name, as their original, a lead that is itself DUPLICATE: the links of a chain. */
	private int storedChains(String... leadIds) {
		String placeholders = String.join(", ", Collections.nCopies(leadIds.length, "?"));
		return count("select count(*) from owner_leads d join owner_leads o on o.id = d.duplicate_of_lead_id "
				+ "where o.status = 'DUPLICATE' and d.id in (" + placeholders + ")", (Object[]) leadIds);
	}

	@FunctionalInterface
	private interface Call {
		Result send(int index) throws Exception;
	}

	/** Starts two calls at one gate and waits for both; no fixed sleeps. */
	private List<Result> race(Call call) throws Exception {
		int parallel = 2;
		CountDownLatch ready = new CountDownLatch(parallel);
		CountDownLatch gate = new CountDownLatch(1);
		List<CompletableFuture<Result>> futures = new ArrayList<>();
		for (int i = 0; i < parallel; i++) {
			int index = i;
			futures.add(CompletableFuture.supplyAsync(() -> {
				ready.countDown();
				try {
					gate.await();
					return call.send(index);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}, executor));
		}
		assertTrue(ready.await(30, TimeUnit.SECONDS), "workers did not start");
		gate.countDown();
		List<Result> results = new ArrayList<>();
		for (CompletableFuture<Result> future : futures) {
			results.add(future.get(60, TimeUnit.SECONDS));
		}
		return results;
	}

	/**
	 * Exactly one 200 and one 409 CONCURRENT_MODIFICATION on expectedVersion (never a 500).
	 *
	 * @return the index of the call that won
	 */
	private static int assertOneOkOneConflict(List<Result> results, String context) {
		assertEquals(1, results.stream().filter(r -> r.status() == 200).count(), context);
		assertEquals(1, results.stream().filter(r -> r.status() == 409).count(), context);
		int winner = results.get(0).status() == 200 ? 0 : 1;
		Result loser = results.get(1 - winner);
		assertEquals("CONCURRENT_MODIFICATION", loser.body().path("code").asString(), loser.rawBody());
		assertEquals(1, loser.body().path("errors").size(), loser.rawBody());
		assertEquals("expectedVersion", loser.body().path("errors").get(0).path("field").asString(), loser.rawBody());
		return winner;
	}

	private static List<Integer> statuses(List<Result> results) {
		return results.stream().map(Result::status).toList();
	}

	private TestUser loggedIn(String role) throws Exception {
		TestUser user = api.createUser(UserStatus.ACTIVE, role);
		userIds.add(user.userId());
		return api.logIn(user);
	}

	/** A NEW lead: Bà Nà has nobody. */
	private String submit() throws Exception {
		int n = PHONE_SEQUENCE.getAndIncrement();
		String phone = "0982" + String.format("%06d", n);
		String leadId = AcquisitionApiTestSupport.leadId(jdbc, api.submitLead(BA_NA, phone, n + " Hùng Vương"));
		leadIds.add(leadId);
		return leadId;
	}

	private String status(String leadId) {
		return jdbc.queryForObject("select status from owner_leads where id = ?", String.class, leadId);
	}

	private String assignee(String leadId) {
		return jdbc.queryForObject("select assigned_user_id from owner_leads where id = ?", String.class, leadId);
	}

	private long version(String leadId) {
		Long version = jdbc.queryForObject("select version from owner_leads where id = ?", Long.class, leadId);
		return version == null ? -1 : version;
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}
}
