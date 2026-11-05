package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.infrastructure.persistence.migration.UserIdentifierKeyBackfill;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

/**
 * The Java backfill and the two read-only scripts on a real PostgreSQL (auth-email-access spec 5.3, 8, S-41). The database
 * stops at the last version before the backfill, is filled the way old rows look, then goes to the latest version.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class IdentifierBackfillMigrationPostgresTest {

	private static final String LAST_VERSION_BEFORE = "202611051010";
	private static final Path PREFLIGHT = Path.of("..", "scripts", "ops", "auth-identifier-preflight.sql");
	private static final Path VERIFY = Path.of("..", "scripts", "ops", "auth-identifier-verify.sql");
	private static final String LOG_LINE = "auth.backfill.identifier-keys";
	private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");
	private static final String COUNT_LINE = "[a-z0-9_]+\\|[0-9a-f]+";

	private static final String VALID = id(1);
	private static final String CASE_UPPER = id(2);
	private static final String CASE_LOWER = id(3);
	private static final String ALIAS_NATIONAL = id(4);
	private static final String ALIAS_COUNTRY = id(5);
	private static final String INVALID = id(6);
	private static final String DELETED = id(7);
	private static final String SAME_AS_DELETED = id(8);
	private static final String SPACED = id(9);
	private static final String DOUBLE_ZERO = id(10);
	/** More rows than one page of the backfill. */
	private static final int BULK_ROWS = 1_200;

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private DriverManagerDataSource dataSource;
	private JdbcTemplate jdbc;
	private Map<String, String> storedBefore;
	private ExecResult preflight;
	private Map<String, String> storedAfterPreflight;
	private String migrationOutput;

	private static String id(int number) {
		return String.format("01BACKFILL%016d", number);
	}

	@BeforeAll
	void migrateSeedAndBackfill(CapturedOutput output) throws Exception {
		postgres.start();
		dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(LAST_VERSION_BEFORE).load().migrate();

		user(VALID, "Valid.Person+tag@Example.COM", "0912000001", null);
		user(CASE_UPPER, "Twin@Example.com", null, null);
		user(CASE_LOWER, "twin@example.com", null, null);
		user(ALIAS_NATIONAL, null, "0912000002", null);
		user(ALIAS_COUNTRY, null, "84912000002", null);
		user(INVALID, "not-an-email", "12345", null);
		user(DELETED, "gone@example.com", "0912000003", Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")));
		user(SAME_AS_DELETED, "Gone@Example.com", "0912000004", null);
		user(SPACED, " Spaced@Example.com ", null, null);
		user(DOUBLE_ZERO, null, "00912000006", null);
		Timestamp createdAt = Timestamp.from(CREATED_AT);
		jdbc.update("INSERT INTO users (id, full_name, username, password, kyc_status, user_status, created_date, last_update)"
				+ " SELECT '01BULK' || lpad(g::text, 20, '0'), 'Bulk user', '0913' || lpad(g::text, 6, '0'), 'hashed', 'UNVERIFIED',"
				+ " 'ACTIVE', ?, ? FROM generate_series(1, ?) g", createdAt, createdAt, BULK_ROWS);
		storedBefore = stored();

		preflight = psql(PREFLIGHT);
		storedAfterPreflight = stored();

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		migrationOutput = output.getAll();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	private void user(String id, String email, String username, Timestamp deletedAt) {
		Timestamp createdAt = Timestamp.from(CREATED_AT);
		jdbc.update("INSERT INTO users (id, full_name, username, email, password, kyc_status, user_status, created_date,"
				+ " last_update, deleted_at) VALUES (?, 'Backfill user', ?, ?, 'hashed', 'UNVERIFIED', ?, ?, ?, ?)", id, username,
				email, deletedAt == null ? "ACTIVE" : "DELETED", createdAt, createdAt, deletedAt);
	}

	/** Every stored column the backfill must leave alone, by id. */
	private Map<String, String> stored() {
		Map<String, String> rows = new LinkedHashMap<>();
		jdbc.query("SELECT id, email, username, user_status, created_date, last_update, email_verified_at, phone_verified_at,"
				+ " deleted_at FROM users ORDER BY id", row -> {
					rows.put(row.getString("id"), String.join("|", String.valueOf(row.getString("email")),
							String.valueOf(row.getString("username")), row.getString("user_status"),
							String.valueOf(row.getTimestamp("created_date")), String.valueOf(row.getTimestamp("last_update")),
							String.valueOf(row.getTimestamp("email_verified_at")),
							String.valueOf(row.getTimestamp("phone_verified_at")), String.valueOf(row.getTimestamp("deleted_at"))));
				});
		return rows;
	}

	private String emailKey(String id) {
		return jdbc.queryForObject("SELECT email_key FROM users WHERE id = ?", String.class, id);
	}

	private String phoneKey(String id) {
		return jdbc.queryForObject("SELECT phone_key FROM users WHERE id = ?", String.class, id);
	}

	private Map<String, String> keys() {
		Map<String, String> rows = new LinkedHashMap<>();
		jdbc.query("SELECT id, email_key, phone_key FROM users ORDER BY id",
				row -> {
					rows.put(row.getString("id"), row.getString("email_key") + "|" + row.getString("phone_key"));
				});
		return rows;
	}

	private ExecResult psql(Path script) throws IOException, InterruptedException {
		assertTrue(Files.isRegularFile(script), "script missing: " + script.toAbsolutePath().normalize());
		String inContainer = "/tmp/" + script.getFileName();
		postgres.copyFileToContainer(MountableFile.forHostPath(script), inContainer);
		return postgres.execInContainer("psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(), "-f", inContainer);
	}

	private static List<String> lines(ExecResult result) {
		return result.getStdout().lines().filter(line -> !line.isBlank()).toList();
	}

	private static void assertCountsOnly(ExecResult result) {
		assertFalse(lines(result).isEmpty(), "the script printed nothing");
		lines(result).forEach(line -> assertTrue(line.matches(COUNT_LINE), "not a count line"));
		assertFalse(result.getStdout().contains("example"), "an address in the output");
		assertFalse(result.getStdout().contains("0912"), "a number in the output");
	}

	private static String metric(ExecResult result, String name) {
		return lines(result).stream().filter(line -> line.startsWith(name + "|")).findFirst()
				.map(line -> line.substring(name.length() + 1)).orElseThrow(() -> new AssertionError("no metric " + name));
	}

	private static AuthValidationException refused() {
		return new AuthValidationException(AuthFieldName.EMAIL, "refused");
	}

	@Test
	void backfill_keepsIdsAndStoredValues() {
		assertEquals(storedBefore, stored());
	}

	@Test
	void backfill_validValues_getKeys() {
		assertEquals("valid.person+tag@example.com", emailKey(VALID));
		assertEquals("+84912000001", phoneKey(VALID));
		assertEquals("spaced@example.com", emailKey(SPACED));
	}

	@Test
	void backfill_caseCollision_noKeyEitherSide() {
		assertNull(emailKey(CASE_UPPER));
		assertNull(emailKey(CASE_LOWER));
	}

	@Test
	void backfill_phoneAliasCollision_noKeyEitherSide() {
		assertNull(phoneKey(ALIAS_NATIONAL));
		assertNull(phoneKey(ALIAS_COUNTRY));
	}

	@Test
	void backfill_invalidValues_noKey() {
		assertNull(emailKey(INVALID));
		assertNull(phoneKey(INVALID));
		assertNull(phoneKey(DOUBLE_ZERO), "spec 4.3: no 00, or it would take the number of somebody else");
	}

	/** Rows past the first page are read too, in id order. */
	@Test
	void backfill_morePagesThanOne_allKeyed() {
		assertEquals(BULK_ROWS, jdbc.queryForObject("SELECT count(*) FROM users WHERE id LIKE '01BULK%' AND phone_key IS NOT NULL",
				Integer.class));
	}

	/** Spec 5.3 step 3: a key already held by another live row is written to nobody else. */
	@Test
	void backfill_keyHeldByAnotherRow_notWritten() throws SQLException {
		String sameNumber = id(11);
		String freshNumber = id(12);
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			// The holder has a value the policy refuses, so it is no candidate of this run and only the column can tell
			try (PreparedStatement holder = connection.prepareStatement("UPDATE users SET phone_key = '+84912000008' WHERE id = ?")) {
				holder.setString(1, INVALID);
				assertEquals(1, holder.executeUpdate());
			}
			try (PreparedStatement insert = connection.prepareStatement("INSERT INTO users (id, full_name, username, password,"
					+ " kyc_status, user_status, created_date, last_update) VALUES (?, 'Late user', ?, 'hashed', 'UNVERIFIED', 'ACTIVE',"
					+ " now(), now())")) {
				insert.setString(1, sameNumber);
				insert.setString(2, "0912000008");
				insert.executeUpdate();
				insert.setString(1, freshNumber);
				insert.setString(2, "0912000007");
				insert.executeUpdate();
			}

			new UserIdentifierKeyBackfill().run(connection);

			try (PreparedStatement read = connection.prepareStatement("SELECT phone_key FROM users WHERE id = ?")) {
				read.setString(1, sameNumber);
				try (ResultSet row = read.executeQuery()) {
					assertTrue(row.next());
					assertNull(row.getString(1));
				}
				read.setString(1, freshNumber);
				try (ResultSet row = read.executeQuery()) {
					assertTrue(row.next());
					assertEquals("+84912000007", row.getString(1));
				}
			}
			connection.rollback();
		}
	}

	/** A deleted row keeps NULL keys and blocks nobody. */
	@Test
	void backfill_softDeleted_untouched() {
		assertNull(emailKey(DELETED));
		assertNull(phoneKey(DELETED));
		assertEquals("gone@example.com", emailKey(SAME_AS_DELETED));
		assertEquals("+84912000004", phoneKey(SAME_AS_DELETED));
	}

	@Test
	void backfill_lastUpdateUnchanged() {
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE last_update <> created_date", Integer.class));
	}

	@Test
	void backfill_logsNoIdentifier() {
		String line = migrationOutput.lines().filter(text -> text.contains(LOG_LINE)).findFirst()
				.orElseThrow(() -> new AssertionError("no count line in the log"));

		assertTrue(line.contains("rows=" + (9 + BULK_ROWS)), line);
		assertTrue(line.contains("emails-keyed=3"), line);
		assertTrue(line.contains("emails-invalid=1"), line);
		assertTrue(line.contains("emails-in-collision=2"), line);
		assertTrue(line.contains("phones-keyed=" + (2 + BULK_ROWS)), line);
		assertTrue(line.contains("phones-invalid=2"), line);
		assertTrue(line.contains("phones-in-collision=2"), line);
		assertFalse(migrationOutput.contains("example.com"), "an address in the log");
		assertFalse(migrationOutput.contains("0912000"), "a number in the log");
	}

	/** The row Flyway keeps: a Java migration, so its discovery is proven, with its constant checksum. */
	@Test
	void backfill_recordedAsAJavaMigration() {
		Map<String, Object> row = jdbc.queryForMap("SELECT type, script, success, checksum FROM flyway_schema_history"
				+ " WHERE version = '202611051011'");

		assertEquals("JDBC", row.get("type"));
		assertEquals("db.migration.V202611051011__Backfill_user_identifier_keys", row.get("script"));
		assertEquals(Boolean.TRUE, row.get("success"));
		assertEquals(1, row.get("checksum"));
	}

	/** A broken parser must not commit "all invalid": the run fails and Flyway rolls it back. */
	@Test
	void backfill_parserBroken_failsAndRollsBack() throws SQLException {
		Map<String, String> before = keys();
		UnaryOperator<String> refusesAll = raw -> {
			throw refused();
		};

		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			UserIdentifierKeyBackfill broken = new UserIdentifierKeyBackfill(refusesAll, refusesAll);

			assertThrows(IllegalStateException.class, () -> broken.run(connection));

			connection.rollback();
		}
		assertEquals(before, keys());
	}

	/** Only a refusal of the policy means no key; anything else is a defect and stops the run. */
	@Test
	void backfill_unexpectedException_notCountedInvalid() throws SQLException {
		UnaryOperator<String> defect = raw -> {
			throw new IllegalArgumentException("a defect of the parser");
		};

		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			UserIdentifierKeyBackfill broken = new UserIdentifierKeyBackfill(defect, defect);

			assertThrows(IllegalArgumentException.class, () -> broken.run(connection));

			connection.rollback();
		}
	}

	@Test
	void preflight_writesNothing_printsOnlyCounts() {
		assertEquals(0, preflight.getExitCode(), "exit " + preflight.getExitCode());
		assertCountsOnly(preflight);
		assertEquals(storedBefore, storedAfterPreflight);
		assertEquals(String.valueOf(9 + BULK_ROWS), metric(preflight, "live_accounts"));
		assertEquals("1", metric(preflight, "email_collision_groups"));
		assertEquals("1", metric(preflight, "phone_digit_collision_groups"));
		assertEquals("1", metric(preflight, "phone_shape_00"));
	}

	@Test
	void verify_passesOnFixture() throws Exception {
		ExecResult verify = psql(VERIFY);

		assertEquals(0, verify.getExitCode(), "exit " + verify.getExitCode());
		assertCountsOnly(verify);
		assertEquals(metric(preflight, "row_hash"), metric(verify, "row_hash"), "the stored values did not move");
		assertEquals("3", metric(verify, "emails_keyed"));
		assertEquals(String.valueOf(2 + BULK_ROWS), metric(verify, "phones_keyed"));
		assertEquals("0", metric(verify, "key_on_deleted_row"));
		assertEquals("0", metric(verify, "key_differs_from_email"));
		assertEquals("0", metric(verify, "canonical_shared_with_key"));
	}

	/** Spec O-3: rows hold values and no key was written, so the migration did not run. */
	@Test
	void verify_noKeyAtAll_fails() throws Exception {
		Map<String, String> before = keys();
		jdbc.execute("CREATE TABLE verify_keys_backup AS SELECT id, email_key, phone_key FROM users");
		try {
			jdbc.update("UPDATE users SET email_key = NULL, phone_key = NULL");

			assertNotEquals(0, psql(VERIFY).getExitCode());
		} finally {
			jdbc.update("UPDATE users u SET email_key = b.email_key, phone_key = b.phone_key FROM verify_keys_backup b WHERE u.id = b.id");
			jdbc.execute("DROP TABLE verify_keys_backup");
		}
		assertEquals(before, keys());
	}

	/** A class missing from the executable jar is skipped silently, so the history is checked too. */
	@Test
	void verify_backfillNotRecorded_fails() throws Exception {
		jdbc.update("UPDATE flyway_schema_history SET success = false WHERE version = '202611051011'");
		try {
			ExecResult verify = psql(VERIFY);

			assertNotEquals(0, verify.getExitCode());
			assertTrue(verify.getStderr().contains("not recorded"), "the reason is named");
		} finally {
			jdbc.update("UPDATE flyway_schema_history SET success = true WHERE version = '202611051011'");
		}
	}

	@Test
	void verify_keyOnDeletedRow_fails() throws Exception {
		jdbc.update("UPDATE users SET phone_key = '+84912000003' WHERE id = ?", DELETED);
		try {
			assertNotEquals(0, psql(VERIFY).getExitCode());
		} finally {
			jdbc.update("UPDATE users SET phone_key = NULL WHERE id = ?", DELETED);
		}
	}
}
