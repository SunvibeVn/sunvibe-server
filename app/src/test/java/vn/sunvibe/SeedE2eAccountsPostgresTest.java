package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The E2E seed script {@code scripts/dev/seed-e2e-accounts.sql} run with psql inside the test PostgreSQL container
 * (auth-browser-session spec §3.13; W10-15). The accounts it seeds sign in through {@code POST /api/v1/auth/login},
 * which proves that Spring's BCrypt encoder reads the pgcrypto hash. Passwords are random per run and never printed.
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
class SeedE2eAccountsPostgresTest {

	private static final Path SCRIPT = Path.of("..", "scripts", "dev", "seed-e2e-accounts.sql");
	private static final String SCRIPT_IN_CONTAINER = "/tmp/seed-e2e-accounts.sql";
	private static final String CUSTOMER_ID = "01JE2EACCT0000000000000001";
	private static final String SALE_ID = "01JE2EACCT0000000000000002";
	private static final String CUSTOMER_PHONE = "0900000101";
	private static final String SALE_PHONE = "0900000102";
	private static final String LEADER_ID = "01JE2EACCT0000000000000003";
	private static final String ADMIN_ID = "01JE2EACCT0000000000000004";
	private static final String LEADER_PHONE = "0900000103";
	private static final String ADMIN_PHONE = "0900000104";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private PostgreSQLContainer postgres;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final JsonMapper json = JsonMapper.builder().build();

	@AfterEach
	void removeSeededAccounts() {
		jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", CUSTOMER_ID, SALE_ID, LEADER_ID, ADMIN_ID);
	}

	@AfterAll
	void tearDown() {
		http.close();
	}

	/** W10-15: idempotent; a re-run resets the passwords and replaces the roles. */
	@Test
	void seed_runTwice_bothAccountsSignInWithTheGivenPasswords() throws Exception {
		String firstCustomerPassword = randomPassword();
		ExecResult first = runSeed("local", firstCustomerPassword, randomPassword());
		assertEquals(0, first.getExitCode(), "first run failed: " + first.getStderr());

		// a role granted by hand between the runs is removed by the re-run
		jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'ADMIN'",
				CUSTOMER_ID);

		String customerPassword = randomPassword();
		String salePassword = randomPassword();
		ExecResult second = runSeed("local", customerPassword, salePassword);
		assertEquals(0, second.getExitCode(), "second run failed: " + second.getStderr());

		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN (?, ?)", Integer.class,
				CUSTOMER_ID, SALE_ID));
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT user_status, password, phone_verified_at FROM users WHERE id IN (?, ?)", CUSTOMER_ID, SALE_ID);
		for (Map<String, Object> row : rows) {
			assertEquals("ACTIVE", row.get("user_status"));
			assertTrue(String.valueOf(row.get("password")).startsWith("$2a$10$"), "a pgcrypto bcrypt hash");
			assertTrue(row.get("phone_verified_at") != null, "phone verified");
		}

		JsonNode customer = logIn(CUSTOMER_PHONE, customerPassword);
		assertEquals(CUSTOMER_ID, customer.path("user").path("userId").asString());
		assertEquals("E2E Customer", customer.path("user").path("fullName").asString());
		assertEquals(List.of("CUSTOMER", "TENANT"), strings(customer.path("user").path("roles")));
		assertTrue(customer.path("user").path("phoneVerified").asBoolean());

		JsonNode sale = logIn(SALE_PHONE, salePassword);
		assertEquals(SALE_ID, sale.path("user").path("userId").asString());
		assertEquals("E2E Sale", sale.path("user").path("fullName").asString());
		assertEquals(List.of("SALE"), strings(sale.path("user").path("roles")));
		assertTrue(sale.path("user").path("phoneVerified").asBoolean());

		assertEquals(401, login(CUSTOMER_PHONE, firstCustomerPassword).statusCode(), "the old password is reset");
	}

	/** W10-15: any target but {@code local} (or none) fails before the first change. */
	@Test
	void seed_targetNotLocal_failsAndChangesNothing() throws Exception {
		ExecResult prod = runSeed("prod", randomPassword(), randomPassword());
		assertNotEquals(0, prod.getExitCode(), "target=prod must fail");
		assertTrue(prod.getStderr().contains("target=local"), "the refusal names the fix");

		ExecResult missing = execPsql(List.of("-v", "customer_password=" + randomPassword(),
				"-v", "sale_password=" + randomPassword()));
		assertNotEquals(0, missing.getExitCode(), "no target must fail");

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN (?, ?) OR username IN (?, ?)",
				Integer.class, CUSTOMER_ID, SALE_ID, CUSTOMER_PHONE, SALE_PHONE));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id IN (?, ?)",
				Integer.class, CUSTOMER_ID, SALE_ID));
	}

	/** Spec §3.13: both passwords are required; a missing one fails before the first change. */
	@Test
	void seed_missingPassword_failsAndChangesNothing() throws Exception {
		ExecResult result = execPsql(List.of("-v", "target=local", "-v", "customer_password=" + randomPassword()));

		assertNotEquals(0, result.getExitCode(), "a missing sale_password must fail");
		assertTrue(result.getStderr().contains("sale_password"), "the refusal names the fix");
		assertNothingSeeded();
	}

	/** Spec §3.13: a password shorter than 8 characters is refused before the first change. */
	@Test
	void seed_shortPassword_failsAndChangesNothing() throws Exception {
		String shortPassword = randomPassword().substring(0, 7);

		ExecResult result = runSeed("local", randomPassword(), shortPassword);

		assertNotEquals(0, result.getExitCode(), "a 7-character password must fail");
		assertTrue(result.getStderr().contains("at least 8 characters"), "the refusal names the rule");
		assertNothingSeeded();
	}

	/** Spec §3.13: a seed phone number held by another account fails with a clear error and changes nothing. */
	@Test
	void seed_phoneTakenByAnotherAccount_failsAndChangesNothing() throws Exception {
		String otherId = "01JE2EOTHER000000000000001";
		jdbc.update("INSERT INTO users (id, full_name, username, password, user_status, failed_login_attempts, "
				+ "created_date, last_update) VALUES (?, 'Someone else', ?, 'not-a-hash', 'ACTIVE', 0, now(), now())",
				otherId, SALE_PHONE);
		try {
			ExecResult result = runSeed("local", randomPassword(), randomPassword());

			assertNotEquals(0, result.getExitCode(), "a taken phone number must fail");
			assertTrue(result.getStderr().contains("0900000101 or 0900000102 already belongs to another account"),
					"the refusal names the conflict");
			assertNothingSeeded();
			assertEquals("Someone else", jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?",
					String.class, otherId), "the other account is untouched");
		} finally {
			jdbc.update("DELETE FROM users WHERE id = ?", otherId);
		}
	}

	/**
	 * Spec §3.13: the plaintext passwords never reach an INSERT or UPDATE; psql -e echoes each statement as sent, and
	 * the users INSERT carries only the hashes.
	 */
	@Test
	void seed_insertStatement_carriesOnlyTheHashes() throws Exception {
		String customerPassword = randomPassword();
		String salePassword = randomPassword();

		String leaderPassword = randomPassword();
		String adminPassword = randomPassword();

		ExecResult result = execPsql(List.of("-e", "-v", "target=local", "-v", "customer_password=" + customerPassword,
				"-v", "sale_password=" + salePassword, "-v", "leader_password=" + leaderPassword,
				"-v", "admin_password=" + adminPassword));

		assertEquals(0, result.getExitCode(), "seed failed");
		String echoed = result.getStdout();
		int insert = echoed.indexOf("INSERT INTO users");
		assertTrue(insert >= 0, "the users INSERT is echoed");
		String statement = echoed.substring(insert, echoed.indexOf(';', insert));
		assertFalse(statement.contains(customerPassword), "no plaintext customer password in the INSERT");
		assertFalse(statement.contains(salePassword), "no plaintext sale password in the INSERT");
		assertFalse(statement.contains(leaderPassword), "no plaintext leader password in the INSERT");
		assertFalse(statement.contains(adminPassword), "no plaintext admin password in the INSERT");
		assertTrue(statement.contains("$2a$10$"), "the INSERT carries the bcrypt hashes");
	}

	/** The lead queue staff accounts: the SALE_LEADER and the ADMIN sign in with their own passwords and roles. */
	@Test
	void seed_leaderAndAdmin_signInWithTheirRolesAndNames() throws Exception {
		String leaderPassword = randomPassword();
		String adminPassword = randomPassword();
		ExecResult first = runSeed("local", randomPassword(), randomPassword(), randomPassword(), randomPassword());
		assertEquals(0, first.getExitCode(), "first run failed: " + first.getStderr());
		// a role granted by hand between the runs is removed by the re-run
		jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'SALES_MANAGER'",
				LEADER_ID);
		ExecResult second = runSeed("local", randomPassword(), randomPassword(), leaderPassword, adminPassword);
		assertEquals(0, second.getExitCode(), "second run failed: " + second.getStderr());

		assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN (?, ?, ?, ?)", Integer.class,
				CUSTOMER_ID, SALE_ID, LEADER_ID, ADMIN_ID));
		JsonNode leader = logIn(LEADER_PHONE, leaderPassword);
		assertEquals(LEADER_ID, leader.path("user").path("userId").asString());
		assertEquals("E2E Trưởng nhóm", leader.path("user").path("fullName").asString());
		assertEquals(List.of("SALE_LEADER"), strings(leader.path("user").path("roles")));
		assertTrue(leader.path("user").path("phoneVerified").asBoolean());

		JsonNode admin = logIn(ADMIN_PHONE, adminPassword);
		assertEquals(ADMIN_ID, admin.path("user").path("userId").asString());
		assertEquals("E2E Admin", admin.path("user").path("fullName").asString());
		assertEquals(List.of("ADMIN"), strings(admin.path("user").path("roles")));
		assertTrue(admin.path("user").path("phoneVerified").asBoolean());
		assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id IN (?, ?, ?, ?)",
				Integer.class, CUSTOMER_ID, SALE_ID, LEADER_ID, ADMIN_ID), "TENANT, CUSTOMER, SALE, SALE_LEADER, ADMIN");
	}

	/** Each of the two new passwords is required and at least 8 characters; the refusal comes before any change. */
	@Test
	void seed_missingOrShortLeaderOrAdminPassword_failsAndChangesNothing() throws Exception {
		ExecResult noLeader = execPsql(List.of("-v", "target=local", "-v", "customer_password=" + randomPassword(),
				"-v", "sale_password=" + randomPassword(), "-v", "admin_password=" + randomPassword()));
		assertNotEquals(0, noLeader.getExitCode(), "a missing leader_password must fail");
		assertTrue(noLeader.getStderr().contains("-v leader_password"), "the refusal names the missing variable");
		assertFalse(noLeader.getStderr().contains("admin_password"), "and only that variable");

		ExecResult noAdmin = execPsql(List.of("-v", "target=local", "-v", "customer_password=" + randomPassword(),
				"-v", "sale_password=" + randomPassword(), "-v", "leader_password=" + randomPassword()));
		assertNotEquals(0, noAdmin.getExitCode(), "a missing admin_password must fail");
		assertTrue(noAdmin.getStderr().contains("-v admin_password"), "the refusal names the missing variable");
		assertFalse(noAdmin.getStderr().contains("leader_password"), "and only that variable");

		ExecResult shortLeader = runSeed("local", randomPassword(), randomPassword(),
				randomPassword().substring(0, 7), randomPassword());
		assertNotEquals(0, shortLeader.getExitCode(), "a 7-character leader password must fail");
		assertTrue(shortLeader.getStderr().contains("at least 8 characters"), "the refusal names the rule");

		ExecResult shortAdmin = runSeed("local", randomPassword(), randomPassword(),
				randomPassword(), randomPassword().substring(0, 7));
		assertNotEquals(0, shortAdmin.getExitCode(), "a 7-character admin password must fail");
		assertTrue(shortAdmin.getStderr().contains("at least 8 characters"), "the refusal names the rule");

		assertNothingSeeded();
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN (?, ?)", Integer.class,
				LEADER_ID, ADMIN_ID));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id IN (?, ?)",
				Integer.class, LEADER_ID, ADMIN_ID));
	}

	/** The phone guard covers 0900000103 and 0900000104 too: another account holding one fails before any change. */
	@Test
	void seed_leaderOrAdminPhoneTakenByAnotherAccount_failsAndChangesNothing() throws Exception {
		for (String phone : List.of(LEADER_PHONE, ADMIN_PHONE)) {
			String otherId = "01JE2EOTHER000000000000002";
			jdbc.update("INSERT INTO users (id, full_name, username, password, user_status, failed_login_attempts, "
					+ "created_date, last_update) VALUES (?, 'Someone else', ?, 'not-a-hash', 'ACTIVE', 0, now(), now())",
					otherId, phone);
			try {
				ExecResult result = runSeed("local", randomPassword(), randomPassword());

				assertNotEquals(0, result.getExitCode(), phone + " taken must fail");
				assertTrue(result.getStderr().contains("0900000103 or 0900000104 already belongs to another account"),
						"the refusal names the conflict");
				assertNothingSeeded();
				assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN (?, ?)", Integer.class,
						LEADER_ID, ADMIN_ID));
				assertEquals("Someone else", jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?",
						String.class, otherId), "the other account is untouched");
			} finally {
				jdbc.update("DELETE FROM users WHERE id = ?", otherId);
			}
		}
	}

	private void assertNothingSeeded() {
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE id IN (?, ?)", Integer.class,
				CUSTOMER_ID, SALE_ID));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id IN (?, ?)",
				Integer.class, CUSTOMER_ID, SALE_ID));
	}

	private ExecResult runSeed(String target, String customerPassword, String salePassword) throws Exception {
		return runSeed(target, customerPassword, salePassword, randomPassword(), randomPassword());
	}

	private ExecResult runSeed(String target, String customerPassword, String salePassword, String leaderPassword,
			String adminPassword) throws Exception {
		return execPsql(List.of("-v", "target=" + target, "-v", "customer_password=" + customerPassword,
				"-v", "sale_password=" + salePassword, "-v", "leader_password=" + leaderPassword,
				"-v", "admin_password=" + adminPassword));
	}

	private ExecResult execPsql(List<String> variables) throws Exception {
		assertTrue(Files.isRegularFile(SCRIPT), "seed script missing: " + SCRIPT.toAbsolutePath().normalize());
		postgres.copyFileToContainer(MountableFile.forHostPath(SCRIPT), SCRIPT_IN_CONTAINER);
		List<String> command = new ArrayList<>(List.of("psql", "-U", postgres.getUsername(),
				"-d", postgres.getDatabaseName(), "-v", "ON_ERROR_STOP=1"));
		command.addAll(variables);
		command.addAll(List.of("-f", SCRIPT_IN_CONTAINER));
		return postgres.execInContainer(command.toArray(String[]::new));
	}

	private JsonNode logIn(String username, String password) throws Exception {
		HttpResponse<String> response = login(username, password);
		assertEquals(200, response.statusCode(), "login failed, status " + response.statusCode());
		return json.readTree(response.body());
	}

	private HttpResponse<String> login(String username, String password) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						json.writeValueAsString(Map.of("username", username, "password", password))))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asString()));
		return values;
	}

	private static String randomPassword() {
		return "Aa1!" + UUID.randomUUID();
	}
}
