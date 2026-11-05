package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.time.AppClock;

/**
 * The identifier schema of auth-email-access on a real PostgreSQL, built by Flyway (spec 5.2, I-2, I-3, S-1, S-3): the
 * keys are unique over every live row, a key can only be the folded form of the row's own email, and the mapping keeps
 * the new columns. Rows are written with SQL on purpose: each test states the row it means.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AuthIdentifierSchemaPostgresTest {

	private static final Path CORPUS = Path.of("..", "modules", "auth-service", "src", "test", "resources", "auth",
			"identifier-corpus.json");
	private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private TransactionTemplate transaction;

	@ParameterizedTest
	@ValueSource(strings = { "ACTIVE", "PENDING", "LOCK" })
	void schema_emailKeyUniqueAcrossStatuses(String status) {
		String key = "unique-" + status.toLowerCase() + "@example.com";
		insertUser(nextId(), null, key, key, null, "ACTIVE", null);

		DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
				() -> insertUser(nextId(), null, key.toUpperCase(), key, null, status, null));

		assertTrue(refused.getMessage().contains("uq_users_email_key"), "another index refused the row");
	}

	@ParameterizedTest
	@ValueSource(strings = { "ACTIVE", "PENDING", "LOCK" })
	void schema_phoneKeyUniqueAcrossStatuses(String status) {
		String national = nextNational();
		String key = "+84" + national.substring(1);
		insertUser(nextId(), national, null, null, key, "ACTIVE", null);

		DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
				() -> insertUser(nextId(), "84" + national.substring(1), null, null, key, status, null));

		assertTrue(refused.getMessage().contains("uq_users_phone_key"), "another index refused the row");
	}

	@Test
	void schema_softDeleted_freesKeys() {
		String email = "freed@example.com";
		String national = nextNational();
		String phoneKey = "+84" + national.substring(1);
		Instant deletedAt = now();
		insertUser(nextId(), national, email, email, phoneKey, "DELETED", deletedAt);

		insertUser(nextId(), national, email, email, phoneKey, "ACTIVE", null);

		assertEquals(1, count("SELECT count(*) FROM users WHERE email_key = ? AND deleted_at IS NULL", email));
	}

	@Test
	void schema_nullKeysDoNotCollide() {
		insertUser(nextId(), nextNational(), null, null, null, "ACTIVE", null);
		insertUser(nextId(), nextNational(), null, null, null, "ACTIVE", null);

		assertTrue(count("SELECT count(*) FROM users WHERE email_key IS NULL AND phone_key IS NULL") >= 2);
	}

	@Test
	void schema_emailKeyMustFoldEmail_checkRefusesOther() {
		DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
				() -> insertUser(nextId(), null, "someone@example.com", "other@example.com", null, "ACTIVE", null));
		assertTrue(refused.getMessage().contains("chk_users_email_key_matches_email"), "another rule refused the row");

		insertUser(nextId(), null, "  Folded.Case@Example.COM ", "folded.case@example.com", null, "ACTIVE", null);
	}

	@Test
	void schema_emailKeyWithNullEmail_refused() {
		DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
				() -> insertUser(nextId(), nextNational(), null, "orphan@example.com", null, "ACTIVE", null));

		assertTrue(refused.getMessage().contains("chk_users_email_key_matches_email"), "another rule refused the row");
	}

	@Test
	void schema_usernameNullable() {
		String id = nextId();

		insertUser(id, null, "no-phone@example.com", "no-phone@example.com", null, "ACTIVE", null);

		assertNull(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, id));
	}

	@Test
	void schema_email254_stored() {
		String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".vn";
		assertEquals(254, email.length());
		String id = nextId();

		insertUser(id, null, email, email, null, "ACTIVE", null);

		assertEquals(email, jdbc.queryForObject("SELECT email_key FROM users WHERE id = ?", String.class, id));
	}

	@Test
	void schema_oneActiveChallengePerEmail() {
		String key = "one-active@example.com";
		insertChallenge(1L, key, null);

		DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
				() -> insertChallenge(2L, key, null));
		assertTrue(refused.getMessage().contains("uq_registration_email_challenges_active"),
				"another index refused the row");

		jdbc.update("UPDATE registration_email_challenges SET consumed_at = ? WHERE id = 1", Timestamp.from(now()));
		insertChallenge(3L, key, null);
	}

	@Test
	void schema_constraintValidated_indexesPresent() {
		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT convalidated FROM pg_constraint"
				+ " WHERE conname = 'chk_users_email_key_matches_email'", Boolean.class));
		List<String> indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'users'",
				String.class);
		assertTrue(indexes.containsAll(List.of("uq_users_email_key", "uq_users_phone_key", "idx_users_keyless_email",
				"idx_users_email", "idx_users_username")), "users indexes: " + indexes);
		assertEquals(1, count("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_verification_codes_destination'"));
		assertEquals(1, count("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_verification_codes_expires_at'"));
		assertEquals(1, count("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_password_reset_token_expires_at'"));
		assertEquals(1, count("SELECT count(*) FROM pg_indexes"
				+ " WHERE indexname = 'idx_auth_email_notifications_next_attempt_at'"));
	}

	/** A row from before the release: stored values stay as they are, read and written back (spec I-1). */
	@Test
	void schema_existingRows_readUnchanged() {
		String id = nextId();
		String national = nextNational();
		insertUser(id, national, "Legacy.Mixed@Example.com", null, null, "ACTIVE", null);

		AppUser loaded = userRepository.findById(UserId.from(id)).orElseThrow();
		assertEquals("Legacy.Mixed@Example.com", loaded.getEmail().getValue());
		assertEquals(national, loaded.getUsername().getValue());
		assertNull(loaded.getEmailKey());
		assertNull(loaded.getPhoneKey());
		assertEquals(0L, loaded.getCredentialVersion());
		assertFalse(loaded.isEmailVerified());

		transaction.executeWithoutResult(status ->
				userRepository.save(userRepository.findByIdForSecurity(UserId.from(id)).orElseThrow()));

		assertEquals("Legacy.Mixed@Example.com",
				jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id));
	}

	@Test
	void mapper_roundTrip_keepsKeysVersionAndCounters() {
		String id = nextId();
		String national = nextNational();
		insertUser(id, national, "mapped@example.com", "mapped@example.com", "+84" + national.substring(1), "ACTIVE", null);
		Instant lockedUntil = now().plus(1, ChronoUnit.HOURS);
		jdbc.update("UPDATE users SET credential_version = 7, reauth_failed_attempts = 3, reauth_locked_until = ?"
				+ " WHERE id = ?", Timestamp.from(lockedUntil), id);

		AppUser loaded = userRepository.findById(UserId.from(id)).orElseThrow();
		assertEquals("mapped@example.com", loaded.getEmailKey());
		assertEquals("+84" + national.substring(1), loaded.getPhoneKey());
		assertEquals(7L, loaded.getCredentialVersion());
		assertEquals(3, loaded.getReauthFailedAttempts());
		assertEquals(lockedUntil, loaded.getReauthLockedUntil());

		transaction.executeWithoutResult(status ->
				userRepository.save(userRepository.findByIdForSecurity(UserId.from(id)).orElseThrow()));

		assertEquals(1, count("SELECT count(*) FROM users WHERE id = ? AND email_key = 'mapped@example.com'"
				+ " AND phone_key IS NOT NULL AND credential_version = 7 AND reauth_failed_attempts = 3"
				+ " AND reauth_locked_until IS NOT NULL", id));
	}

	/** S-1: every valid corpus value fits its columns and comes back whole, through the mapping. */
	@Test
	void corpus_validCases_roundTripOnPostgres() throws IOException {
		JsonNode corpus = JsonMapper.builder().build().readTree(Files.readAllBytes(CORPUS));
		int stored = 0;
		for (JsonNode c : corpus.get("email")) {
			if (!c.get("valid").asBoolean() || c.get("canonical").asString().isEmpty()) {
				continue;
			}
			String canonical = Email.create(c.get("raw").asString()).getValue();
			if (count("SELECT count(*) FROM users WHERE email_key = ? AND deleted_at IS NULL", canonical) > 0) {
				continue; // two raw forms of one address
			}
			String id = nextId();
			insertUser(id, null, canonical, canonical, null, "ACTIVE", null);
			AppUser loaded = userRepository.findById(UserId.from(id)).orElseThrow();
			assertEquals(c.get("canonical").asString(), loaded.getEmailKey(), c.get("id").asString());
			assertEquals(c.get("canonical").asString(), loaded.getEmail().getValue(), c.get("id").asString());
			stored++;
		}
		for (JsonNode c : corpus.get("phone")) {
			if (!c.get("valid").asBoolean()) {
				continue;
			}
			PhoneNumber phone = PhoneNumber.create(c.get("raw").asString());
			if (count("SELECT count(*) FROM users WHERE phone_key = ? AND deleted_at IS NULL", phone.key()) > 0) {
				continue; // two raw forms of one number
			}
			String id = nextId();
			insertUser(id, phone.national(), null, null, phone.key(), "ACTIVE", null);
			AppUser loaded = userRepository.findById(UserId.from(id)).orElseThrow();
			assertEquals(c.get("canonical").asString(), loaded.getPhoneKey(), c.get("id").asString());
			assertEquals(c.get("national").asString(), loaded.getUsername().getValue(), c.get("id").asString());
			stored++;
		}
		assertTrue(stored > 20, "only " + stored + " corpus values were stored");
	}

	private void insertUser(String id, String username, String email, String emailKey, String phoneKey, String status,
			Instant deletedAt) {
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO users (id, full_name, username, email, email_key, phone_key, password, kyc_status,"
				+ " user_status, created_date, last_update, deleted_at) VALUES (?, 'Schema test', ?, ?, ?, ?,"
				+ " 'hash', 'UNVERIFIED', ?, ?, ?, ?)", id, username, email, emailKey, phoneKey, status, now, now,
				deletedAt == null ? null : Timestamp.from(deletedAt));
	}

	private void insertChallenge(long id, String emailKey, Instant consumedAt) {
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO registration_email_challenges (id, email_key, code_hash, created_at, expires_at,"
				+ " consumed_at) VALUES (?, ?, 'hash', ?, ?, ?)", id, emailKey, now, now,
				consumedAt == null ? null : Timestamp.from(consumedAt));
	}

	private int count(String sql, Object... args) {
		Integer result = jdbc.queryForObject(sql, Integer.class, args);
		assertNotNull(result);
		return result;
	}

	private static String nextId() {
		return String.format("01SCHEMA%018d", SEQUENCE.getAndIncrement());
	}

	/** Numbers of the 09 range, apart from the corpus values. */
	private static String nextNational() {
		return String.format("0987%06d", SEQUENCE.getAndIncrement());
	}

	private static Instant now() {
		return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
	}
}
