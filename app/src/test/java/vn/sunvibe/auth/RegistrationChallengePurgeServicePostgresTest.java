package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationChallengePurgeUseCase;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationPurpose;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationEmailChallengeRepository;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/** Proofs leave storage two days after their last expiry, batch after batch (auth-email-access spec 5.7, D4-14). */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class RegistrationChallengePurgeServicePostgresTest {

	private static final AtomicLong IDS = new AtomicLong(7_000_000);
	private static final String CODES = "verification_codes";
	private static final String TOKENS = "password_reset_token";
	private static final String CHALLENGES = "registration_email_challenges";

	@Autowired
	private RegistrationChallengePurgeUseCase purge;

	@Autowired
	private RegistrationEmailChallengeRepository challenges;

	@Autowired
	private VerificationCodeRepository codes;

	@Autowired
	private PasswordResetTokenDomainRepository resetTokens;

	@Autowired
	private UserDomainRepository users;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	private String user() {
		Instant now = AppClock.currentUTC();
		return users.create(AppUser.registerWithEmail(UserId.create(ulidGenerator.generateId()), "Purge user",
				Email.create("purge-" + UUID.randomUUID() + "@example.com"), Password.create("hash"),
				new HashSet<>(Set.of("CUSTOMER")), now)).getId().getValue();
	}

	private long code(String userId, Instant expiresAt) {
		long id = IDS.incrementAndGet();
		transaction.executeWithoutResult(status -> codes.insert(VerificationCode.load(id, userId, VerificationChannel.EMAIL,
				VerificationPurpose.EMAIL_BIND, "a@example.com", "hash", expiresAt.minus(Duration.ofMinutes(10)), expiresAt, 0,
				null, 0L)));
		return id;
	}

	private long resetToken(String userId, Instant expiresAt) {
		long id = IDS.incrementAndGet();
		transaction.executeWithoutResult(status -> resetTokens.save(PasswordResetToken.restore(id, "token-" + id, userId,
				VerificationChannel.EMAIL, "a@example.com", expiresAt.minus(Duration.ofMinutes(15)), expiresAt, false, null, 0,
				"hash", 0L)));
		return id;
	}

	private long challenge(Instant expiresAt, Instant tokenExpiresAt) {
		long id = IDS.incrementAndGet();
		String tokenHash = tokenExpiresAt == null ? null : "token-" + id;
		transaction.executeWithoutResult(status -> assertTrue(challenges.insert(RegistrationEmailChallenge.load(id,
				"purge-" + id + "@example.com", "hash", expiresAt.minus(Duration.ofMinutes(10)), expiresAt, 0, null, null,
				tokenHash, tokenExpiresAt, null))));
		return id;
	}

	private boolean exists(String table, long id) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id) == 1;
	}

	@Test
	void purge_keepsLiveRows() {
		String user = user();
		Instant now = AppClock.currentUTC();
		long liveCode = code(user, now.plus(Duration.ofMinutes(5)));
		long recentCode = code(user, now.minus(Duration.ofDays(1)));
		long liveToken = resetToken(user, now.plus(Duration.ofMinutes(5)));
		long recentToken = resetToken(user, now.minus(Duration.ofDays(1)));
		long liveChallenge = challenge(now.plus(Duration.ofMinutes(5)), null);
		long verifiedLongAgoStillUsable = challenge(now.minus(Duration.ofDays(3)), now.plus(Duration.ofMinutes(5)));

		purge.execute();

		assertTrue(exists(CODES, liveCode));
		assertTrue(exists(CODES, recentCode));
		assertTrue(exists(TOKENS, liveToken));
		assertTrue(exists(TOKENS, recentToken));
		assertTrue(exists(CHALLENGES, liveChallenge));
		assertTrue(exists(CHALLENGES, verifiedLongAgoStillUsable), "the last expiry counts");
	}

	@Test
	void purge_removesTwoDaysAfterExpiry() {
		String user = user();
		Instant now = AppClock.currentUTC();
		Instant past = now.minus(Duration.ofDays(2).plusMinutes(5));
		Instant notYet = now.minus(Duration.ofDays(2).minusMinutes(5));
		long oldCode = code(user, past);
		long youngCode = code(user, notYet);
		long oldToken = resetToken(user, past);
		long youngToken = resetToken(user, notYet);
		long oldChallenge = challenge(past, null);
		long oldVerified = challenge(past, past);
		long youngVerified = challenge(past, notYet);

		purge.execute();

		assertEquals(false, exists(CODES, oldCode));
		assertTrue(exists(CODES, youngCode));
		assertEquals(false, exists(TOKENS, oldToken));
		assertTrue(exists(TOKENS, youngToken));
		assertEquals(false, exists(CHALLENGES, oldChallenge));
		assertEquals(false, exists(CHALLENGES, oldVerified));
		assertTrue(exists(CHALLENGES, youngVerified));
	}

	/** More rows than one batch: the run goes on until none is left. */
	@Test
	void purge_batches() {
		String user = user();
		Instant expiredAt = AppClock.currentUTC().minus(Duration.ofDays(3));
		int rows = 2_500;
		jdbc.update("INSERT INTO verification_codes (id, user_id, channel, destination, code_hash, created_at, expires_at, purpose)"
				+ " SELECT ? + g, ?, 'EMAIL', 'a@example.com', 'hash', ?, ?, 'EMAIL_BIND' FROM generate_series(1, ?) g",
				IDS.addAndGet(rows + 1L), user, Timestamp.from(expiredAt), Timestamp.from(expiredAt), rows);

		int deleted = purge.execute();

		assertTrue(deleted >= rows, "deleted " + deleted);
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM verification_codes WHERE user_id = ?", Integer.class, user));
	}
}
