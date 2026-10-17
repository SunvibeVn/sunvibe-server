package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * Concurrent submissions on PostgreSQL (acquisition-owner-intake spec 5.1 order of operations; W9-10, W9-10b): the
 * advisory locks (key first, then phone) are taken before any lookup and every lookup after them sees what the other
 * request committed (READ COMMITTED), so racing duplicates never create a second lead and never fail with 500. Each
 * case starts 2 and 8 HTTP calls at one gate, 5 rounds with fresh data. Same context as OwnerIntakeApiPostgresTest
 * (rate limit off); phones 092xxxxxxx keep the two classes apart. Needs Docker.
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
class OwnerIntakeConcurrencyPostgresTest {

	private static final String ENDPOINT = "/api/v1/public/owner-intake-requests";
	private static final int ROUNDS = 5;
	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);
	private static final AtomicInteger ADDRESS_SEQUENCE = new AtomicInteger(1);

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

	private AcquisitionApiTestSupport api;
	private ExecutorService executor;

	@BeforeAll
	void setUp() {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		executor = Executors.newFixedThreadPool(8);
	}

	@AfterAll
	void tearDown() {
		executor.shutdownNow();
		api.http().close();
	}

	/** W9-10: identical bodies without a key are one lead; every call gets its code (which one replays is open). */
	@ParameterizedTest(name = "{0} parallel calls")
	@ValueSource(ints = { 2, 8 })
	void post_twoConcurrentIdenticalWithoutKey_createsExactlyOneLead(int parallel) throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			String phone = nextPhone();
			String body = json(validBody(phone, nextAddress(), null));

			List<Result> results = race(parallel, i -> intake(body, null));

			assertAll202(results, "round " + round);
			assertEquals(1, codes(results).size(), "round " + round + ": " + codes(results));
			assertEquals(1, leadsOf(phone), "round " + round);
		}
	}

	/** W9-10b: the same key and body: one lead, every call 202 with the same code. */
	@ParameterizedTest(name = "{0} parallel calls")
	@ValueSource(ints = { 2, 8 })
	void post_twoConcurrentSameKeySameBody_createsOneLeadBoth202SameCode(int parallel) throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			String phone = nextPhone();
			String key = newKey();
			String body = json(validBody(phone, nextAddress(), null));

			List<Result> results = race(parallel, i -> intake(body, key));

			assertAll202(results, "round " + round);
			assertEquals(1, codes(results).size(), "round " + round + ": " + codes(results));
			assertEquals(1, leadsOf(phone), "round " + round);
			assertEquals(1, count("select count(*) from owner_intake_idempotency_keys where idempotency_key = ?", key));
		}
	}

	/** W9-10b: the same key with different bodies: exactly one 202, the others 409, never 500. */
	@ParameterizedTest(name = "{0} parallel calls")
	@ValueSource(ints = { 2, 8 })
	void post_twoConcurrentSameKeyDifferentBodies_oneAccepted202OneConflict409_never500(int parallel)
			throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			String phone = nextPhone();
			String key = newKey();
			String address = nextAddress();
			List<String> bodies = new ArrayList<>();
			for (int i = 0; i < parallel; i++) {
				bodies.add(json(validBody(phone, address, "Ghi chú " + i)));
			}

			List<Result> results = race(parallel, i -> intake(bodies.get(i), key));

			String context = "round " + round + ": " + statuses(results);
			assertEquals(1, results.stream().filter(r -> r.status() == 202).count(), context);
			assertEquals(parallel - 1, results.stream().filter(r -> r.status() == 409).count(), context);
			results.stream().filter(r -> r.status() == 409).forEach(r ->
					assertEquals("IDEMPOTENCY_KEY_REUSED", r.body().path("code").asString(), r.rawBody()));
			assertEquals(1, leadsOf(phone), context);
		}
	}

	/**
	 * The phone lock is per phone, not global: while another transaction holds the lock of phone A, a submission
	 * for phone B completes and the one for A waits; all of them are accepted.
	 */
	@Test
	void post_concurrentDifferentPhones_doNotBlockEachOther() throws Exception {
		String blockedPhone = nextPhone();
		String freePhone = nextPhone();

		try (Connection holder = dataSource.getConnection()) {
			holder.setAutoCommit(false);
			try (PreparedStatement lock = holder.prepareStatement("select pg_advisory_xact_lock(hashtext(?))")) {
				lock.setString(1, "phone:" + normalized(blockedPhone));
				lock.execute();
			}

			Future<Result> blocked = executor.submit(() -> intake(json(validBody(blockedPhone, nextAddress(), null)),
					null));
			Result free = intake(json(validBody(freePhone, nextAddress(), null)), null);

			assertEquals(202, free.status(), free.rawBody());
			assertFalse(blocked.isDone(), "the request of the locked phone must wait for the lock");
			holder.rollback();

			Result released = blocked.get(30, TimeUnit.SECONDS);
			assertEquals(202, released.status(), released.rawBody());
		}

		for (int round = 1; round <= ROUNDS; round++) {
			List<String> phones = List.of(nextPhone(), nextPhone(), nextPhone(), nextPhone(), nextPhone(),
					nextPhone(), nextPhone(), nextPhone());
			List<Result> results = race(phones.size(),
					i -> intake(json(validBody(phones.get(i), nextAddress(), null)), null));

			assertAll202(results, "round " + round);
			assertEquals(phones.size(), codes(results).size(), "round " + round);
			for (String phone : phones) {
				assertEquals(1, leadsOf(phone), "round " + round);
			}
		}
		assertEquals(1, leadsOf(blockedPhone));
		assertEquals(1, leadsOf(freePhone));
	}

	// ==================== Helpers ====================

	@FunctionalInterface
	private interface Call {
		Result send(int index) throws Exception;
	}

	/** Starts {@code parallel} calls at one gate and waits for all of them. */
	private List<Result> race(int parallel, Call call) throws Exception {
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

	private Result intake(String body, String key) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(api.uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		return api.send(request.build());
	}

	private static void assertAll202(List<Result> results, String context) {
		for (Result result : results) {
			assertEquals(202, result.status(), context + ": " + result.rawBody());
		}
	}

	private static Set<String> codes(List<Result> results) {
		return results.stream().map(r -> r.body().path("requestCode").asString()).collect(Collectors.toSet());
	}

	private static List<Integer> statuses(List<Result> results) {
		return results.stream().map(Result::status).toList();
	}

	private String json(Map<String, Object> body) {
		return api.json().writeValueAsString(body);
	}

	/** The example body of spec 6.3 with the given phone, address and notes. */
	private static Map<String, Object> validBody(String phone, String address, String notes) {
		Map<String, Object> consent = new LinkedHashMap<>();
		consent.put("accepted", true);
		consent.put("noticeVersion", "owner-intake-2026-10");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("contactName", "Trần Văn Song");
		body.put("phone", phone);
		body.put("intent", "LONG_TERM_RENT");
		body.put("provinceCode", "48");
		body.put("wardCode", "20242");
		body.put("addressDetail", address);
		body.put("propertyType", "TWO_BEDROOM");
		if (notes != null) {
			body.put("notes", notes);
		}
		body.put("consent", consent);
		return body;
	}

	/** A new phone as an owner types it: {@code 0920 000 001}. */
	private static String nextPhone() {
		String digits = "092" + String.format("%07d", PHONE_SEQUENCE.getAndIncrement());
		return digits.substring(0, 4) + " " + digits.substring(4, 7) + " " + digits.substring(7);
	}

	private static String normalized(String typedPhone) {
		return "+84" + typedPhone.replace(" ", "").substring(1);
	}

	private static String nextAddress() {
		return ADDRESS_SEQUENCE.getAndIncrement() + " Lê Duẩn, song song";
	}

	private static String newKey() {
		return "race-" + UUID.randomUUID();
	}

	private int leadsOf(String typedPhone) {
		return count("select count(*) from owner_leads where phone_normalized = ?", normalized(typedPhone));
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}
}
