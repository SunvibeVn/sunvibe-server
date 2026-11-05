package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

/**
 * The trigger that counts a loss of sign-in (property-create intake spec 5.2 file 13, D5-10) on a real PostgreSQL, where
 * alone it exists. Every test starts from an ACTIVE row and changes it by SQL, as an administrator does: no application
 * code writes LOCK today. Needs Docker, like {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignInLossTriggerPostgresTest {

	private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-06T02:00:00Z"));

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger();
	private JdbcTemplate jdbc;

	@BeforeAll
	void migrate() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	@Test
	void trigger_lockBySql_counts() {
		String user = account("ACTIVE");

		setStatus(user, "LOCK");

		assertEquals(1, losses(user));
	}

	@Test
	void trigger_terminatedAndDeleted_count() {
		String terminated = account("ACTIVE");
		String deleted = account("ACTIVE");

		setStatus(terminated, "TERMINATED");
		setStatus(deleted, "DELETED");

		assertEquals(1, losses(terminated));
		assertEquals(1, losses(deleted));
	}

	@Test
	void trigger_deletedAtAlone_counts() {
		String user = account("ACTIVE");

		jdbc.update("UPDATE users SET deleted_at = ? WHERE id = ?", NOW, user);

		assertEquals(1, losses(user));
	}

	/** A row that could not sign in loses nothing more, so a second status change never counts twice. */
	@Test
	void trigger_lockedToTerminated_noSecondCount() {
		String user = account("ACTIVE");
		setStatus(user, "LOCK");

		setStatus(user, "TERMINATED");

		assertEquals(1, losses(user));
	}

	/** The count survives an unlock, so a proof made before the lock stays dead (D5-10). */
	@Test
	void trigger_unlock_noCount() {
		String user = account("ACTIVE");
		setStatus(user, "LOCK");

		setStatus(user, "ACTIVE");

		assertEquals(1, losses(user));
	}

	@Test
	void trigger_lockAgainAfterUnlock_countsAgain() {
		String user = account("ACTIVE");
		setStatus(user, "LOCK");
		setStatus(user, "ACTIVE");

		setStatus(user, "LOCK");

		assertEquals(2, losses(user));
	}

	@Test
	void trigger_pendingToActive_noCount() {
		String user = account("PENDING");

		setStatus(user, "ACTIVE");

		assertEquals(0, losses(user));
	}

	/** Hibernate writes every column on a save: an unchanged status is in the SET list and must not count. */
	@Test
	void trigger_sameStatusSave_noCount() {
		String user = account("ACTIVE");

		jdbc.update("UPDATE users SET user_status = user_status, deleted_at = deleted_at, last_update = ? WHERE id = ?",
				NOW, user);

		assertEquals(0, losses(user));
	}

	/** A save of a temporary login lock touches other columns only (auth D4-13). */
	@Test
	void trigger_temporaryLoginLock_noCount() {
		String user = account("ACTIVE");

		jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?", NOW, user);

		assertEquals(0, losses(user));
	}

	/** spec 5.2 file 13: the WHEN of the trigger lists exactly the statuses the application refuses at sign-in. */
	@Test
	void trigger_listEqualsSignInBlockedStatuses() {
		String definition = jdbc.queryForObject("SELECT pg_get_triggerdef(oid) FROM pg_trigger"
				+ " WHERE tgname = 'trg_users_count_sign_in_loss'", String.class);
		Matcher list = Pattern.compile("ARRAY\\[([^\\]]*)\\]").matcher(definition);
		Set<String> expected = AppUser.SIGN_IN_BLOCKED_STATUSES.stream().map(UserStatus::name)
				.collect(Collectors.toCollection(TreeSet::new));

		int lists = 0;
		while (list.find()) {
			lists++;
			Set<String> values = Arrays.stream(list.group(1).split(","))
					.map(value -> value.replaceAll("[^A-Z_]|varying|character", "").strip())
					.filter(value -> !value.isEmpty())
					.collect(Collectors.toCollection(TreeSet::new));
			assertEquals(expected, values, definition);
		}
		assertEquals(2, lists, definition);
	}

	@Test
	void trigger_isOnTheRightColumns() {
		String definition = jdbc.queryForObject("SELECT pg_get_triggerdef(oid) FROM pg_trigger"
				+ " WHERE tgname = 'trg_users_count_sign_in_loss'", String.class);

		assertEquals(List.of(true, true), List.of(definition.contains("BEFORE UPDATE OF user_status, deleted_at"),
				definition.contains("FOR EACH ROW")));
	}

	private void setStatus(String user, String status) {
		jdbc.update("UPDATE users SET user_status = ? WHERE id = ?", status, user);
	}

	private int losses(String user) {
		return jdbc.queryForObject("SELECT sign_in_losses FROM users WHERE id = ?", Integer.class, user);
	}

	private String account(String status) {
		String id = String.format("01TRIGGERUSER%013d", sequence.incrementAndGet());
		jdbc.update("INSERT INTO users (id, full_name, username, password, kyc_status, user_status, created_date,"
				+ " last_update) VALUES (?, 'Trigger test', ?, 'hash', 'UNVERIFIED', ?, ?, ?)", id, id.substring(10), status,
				NOW, NOW);
		return id;
	}
}
