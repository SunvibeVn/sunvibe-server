package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordResetCommand;
import vn.sunvibe.auth.core.application.command.auth.TokenRefreshCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.auth.ContactVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordResetUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.TokenRefreshUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.exception.user.IdentifierConflictException;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserLoginAttemptRepository;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.AuthenticatedUserPrincipal;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * What commits and what rolls back, on PostgreSQL: these paths take row locks H2 does not have (auth-email-access spec
 * 5.4). Every service runs with its own transaction, so a rejected request really commits or rolls back. The races
 * themselves are in {@code AuthConcurrencyPostgresTest}.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
class AuthTransactionPostgresTest {

	private static final AtomicInteger PHONE = new AtomicInteger(1);
	private static final AtomicLong TOKEN_ID = new AtomicLong(9_000_000);

	@Autowired
	private LoginUseCase login;

	@Autowired
	private TokenRefreshUseCase tokenRefresh;

	@Autowired
	private PasswordResetUseCase passwordReset;

	@Autowired
	private ContactVerificationUseCase contactVerification;

	@Autowired
	private UserDomainRepository users;

	@Autowired
	private UserLoginAttemptRepository loginAttempts;

	@Autowired
	private RefreshTokenDomainRepository refreshTokens;

	@Autowired
	private PasswordResetTokenDomainRepository resetTokens;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private CapturingMessagesConfig messages;

	@AfterEach
	void signOut() {
		SecurityContextHolder.clearContext();
	}

	private static String nextPhone() {
		return String.format("0976%06d", PHONE.getAndIncrement());
	}

	private AppUser phoneUser(String password, UserStatus status) {
		Instant now = AppClock.currentUTC();
		AppUser user = AppUser.createWithPhone(UserId.create(ulidGenerator.generateId()), "Transaction user",
				PhoneNumber.create(nextPhone()), Password.create(passwordEncoder.encode(password)),
				ProfileImage.create(null), null, new HashSet<>(Set.of("TENANT")), now);
		user.setUserStatus(status);
		return users.create(user);
	}

	private void signInAs(String userId) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedUserPrincipal(userId), null, List.of()));
	}

	private Map<String, Object> row(String userId) {
		return jdbc.queryForMap("SELECT failed_login_attempts, locked_until, last_login, user_status, password,"
				+ " last_update FROM users WHERE id = ?", userId);
	}

	/** Moved from H2: the count and the lock outlive the refused request. */
	@Test
	void login_failedAttemptsCommitted_fifthLocks() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.ACTIVE);
		String username = user.getUsername().getValue();
		LoginCommand wrong = LoginCommand.byPhone(username, "Wrong@Pass1");

		for (int i = 1; i <= 4; i++) {
			assertThrows(AuthenticationFailedException.class, () -> login.execute(wrong, ClientInfo.unknown()));
			assertEquals(i, row(user.getId().getValue()).get("failed_login_attempts"));
		}
		assertThrows(AccountTemporarilyLockedException.class, () -> login.execute(wrong, ClientInfo.unknown()));

		Map<String, Object> locked = row(user.getId().getValue());
		assertEquals(5, locked.get("failed_login_attempts"));
		assertNotNull(locked.get("locked_until"));

		assertThrows(AccountTemporarilyLockedException.class, () -> login.execute(wrong, ClientInfo.unknown()));
		assertEquals(5, row(user.getId().getValue()).get("failed_login_attempts"), "a refusal while locked is not counted");
	}

	/** Moved from H2: the revocation commits although the request answers 401. */
	@Test
	void refresh_reuseAfterGrace_revocationCommitted() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.ACTIVE);
		String secret = UUID.randomUUID().toString();
		UUID familyId = UUID.randomUUID();
		Instant issuedAt = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
		Instant usedAt = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MICROS);
		long usedId = TOKEN_ID.incrementAndGet();
		long successorId = TOKEN_ID.incrementAndGet();
		transaction.executeWithoutResult(status -> {
			refreshTokens.save(RefreshToken.load(usedId, user.getId(), familyId, RefreshTokenSecrets.hash(secret), issuedAt,
					issuedAt.plus(Duration.ofDays(7)), issuedAt.plus(Duration.ofDays(30)), null, null, TokenStatus.USED,
					usedAt, null, null));
			refreshTokens.save(RefreshToken.load(successorId, user.getId(), familyId, "hash-" + secret, usedAt,
					usedAt.plus(Duration.ofDays(7)), issuedAt.plus(Duration.ofDays(30)), null, null, TokenStatus.ACTIVE,
					null, null, null));
		});

		assertThrows(InvalidRefreshTokenException.class,
				() -> tokenRefresh.execute(new TokenRefreshCommand(secret, ClientInfo.unknown())));

		assertEquals(TokenStatus.REVOKED.name(), jdbc.queryForObject(
				"SELECT status FROM refresh_tokens WHERE id = ?", String.class, successorId));
		assertEquals(RevokeReason.REUSE_DETECTED.name(), jdbc.queryForObject(
				"SELECT revoke_reason FROM refresh_tokens WHERE id = ?", String.class, successorId));
	}

	/** Moved from H2: guesses stay counted although each request fails. */
	@Test
	void reset_wrongCode_countCommitted() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.ACTIVE);
		String token = UUID.randomUUID().toString();
		long id = TOKEN_ID.incrementAndGet();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		transaction.executeWithoutResult(status -> resetTokens.save(PasswordResetToken.restore(id, PasswordResetToken.hashToken(token),
				user.getId().getValue(), VerificationChannel.EMAIL, "a@example.com", now, now.plusSeconds(900), false, null, 0,
				"123456", 0L)));
		PasswordResetCommand wrong = new PasswordResetCommand(token, "000000", "N3wP@ssw0rd!");

		for (int i = 1; i <= 4; i++) {
			assertThrows(InvalidOtpException.class, () -> passwordReset.verifyOtpAndResetPassword(wrong));
			assertEquals(i, jdbc.queryForObject("SELECT failed_attempts FROM password_reset_token WHERE id = ?",
					Integer.class, id));
		}
		assertThrows(PasswordResetTokenException.class, () -> passwordReset.verifyOtpAndResetPassword(wrong));

		assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_used FROM password_reset_token WHERE id = ?",
				Boolean.class, id));
	}

	/** Moved from H2: wrong codes of a contact verification are counted and burn the code. */
	@Test
	void verification_wrongCodes_countCommitted_fifthBurns() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.PENDING);
		signInAs(user.getId().getValue());
		contactVerification.sendCode();
		String wrongCode = messages.lastCode(user.getUsername().getValue()).equals("000000") ? "111111" : "000000";

		for (int i = 1; i <= 5; i++) {
			assertThrows(VerificationException.class,
					() -> contactVerification.confirm(wrongCode));
			assertEquals(i, jdbc.queryForObject("SELECT failed_attempts FROM verification_codes WHERE user_id = ?",
					Integer.class, user.getId().getValue()));
		}

		assertNotNull(jdbc.queryForObject("SELECT consumed_at FROM verification_codes WHERE user_id = ?",
				java.sql.Timestamp.class, user.getId().getValue()), "burned after the 5th wrong code");
	}

	/**
	 * Open-in-view keeps a persistence context for the whole request (spec A-24): the lock must read the row again even
	 * when that context already holds it. A second transaction changes the row between the first read and the lock.
	 */
	@Test
	void findByIdForSecurity_rowAlreadyManaged_readsTheRowAgain() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.ACTIVE);

		UserStatus seen = transaction.execute(status -> {
			users.findById(user.getId()).orElseThrow();
			inAnotherTransaction("UPDATE users SET user_status = 'LOCK' WHERE id = ?", user.getId().getValue());
			return users.findByIdForSecurity(user.getId()).orElseThrow().user().getUserStatus();
		});

		assertEquals(UserStatus.LOCK, seen);
	}

	@Test
	void phoneConfirm_readsFreshRow() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.PENDING);
		signInAs(user.getId().getValue());
		contactVerification.sendCode();
		String code = messages.lastCode(user.getUsername().getValue());

		VerificationException refused = assertThrows(VerificationException.class, () -> transaction.executeWithoutResult(status -> {
			users.findById(user.getId()).orElseThrow();
			inAnotherTransaction("UPDATE users SET phone_verified_at = ? WHERE id = ?",
					Timestamp.from(AppClock.currentUTC()), user.getId().getValue());
			contactVerification.confirm(code);
		}));

		assertEquals(VerificationException.Reason.ALREADY_VERIFIED, refused.getReason());
	}

	@Test
	void findByIdForSecurity_softDeleted_empty() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.ACTIVE);
		jdbc.update("UPDATE users SET deleted_at = ?, user_status = 'DELETED' WHERE id = ?",
				Timestamp.from(AppClock.currentUTC()), user.getId().getValue());

		assertTrue(transaction.execute(status -> users.findByIdForSecurity(user.getId())).isEmpty());
	}

	/** auth-email-access PD-4: the login writes its three columns and nothing it read earlier. */
	@Test
	void recordLoginSuccess_writesOnlyItsColumns() {
		AppUser user = phoneUser("Right@Pass1", UserStatus.ACTIVE);
		String id = user.getId().getValue();
		jdbc.update("UPDATE users SET failed_login_attempts = 3, locked_until = ? WHERE id = ?",
				Timestamp.from(AppClock.currentUTC()), id);
		Object lastUpdate = row(id).get("last_update");
		Instant when = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);

		transaction.executeWithoutResult(status -> loginAttempts.recordLoginSuccess(user.getId(), when));

		Map<String, Object> stored = row(id);
		assertEquals(0, stored.get("failed_login_attempts"));
		assertNull(stored.get("locked_until"));
		assertNotNull(stored.get("last_login"));
		assertEquals(lastUpdate, stored.get("last_update"));
	}

	/** auth-email-access PD-4: only PENDING moves. */
	@Test
	void activateIfPending_otherStatus_untouched() {
		AppUser pending = phoneUser("Right@Pass1", UserStatus.PENDING);
		AppUser locked = phoneUser("Right@Pass1", UserStatus.LOCK);

		transaction.executeWithoutResult(status -> {
			loginAttempts.activateIfPending(pending.getId());
			loginAttempts.activateIfPending(locked.getId());
		});

		assertEquals("ACTIVE", row(pending.getId().getValue()).get("user_status"));
		assertEquals("LOCK", row(locked.getId().getValue()).get("user_status"));
	}

	/** auth-email-access PD-5: the loser of a race for an identifier gets a typed conflict, whichever index refused it. */
	@Test
	void create_emailKeyHeld_identifierConflict() {
		String email = "conflict-" + UUID.randomUUID() + "@example.com";
		users.create(emailUser(email));

		IdentifierConflictException refused = assertThrows(IdentifierConflictException.class,
				() -> users.create(emailUser(email)));

		assertEquals(IdentifierConflictException.Kind.EMAIL, refused.getKind());
		assertFalse(refused.getMessage().contains(email), "the exception names no value");
	}

	@Test
	void create_rawUsernameHeld_identifierConflict() {
		AppUser first = phoneUser("Right@Pass1", UserStatus.ACTIVE);
		Instant now = AppClock.currentUTC();
		AppUser second = AppUser.createWithPhone(UserId.create(ulidGenerator.generateId()), "Twin",
				PhoneNumber.create(first.usernameValue()), Password.create("hash"), ProfileImage.create(null), null,
				new HashSet<>(Set.of("TENANT")), now);
		// A second row of the number with no key: only the raw index can refuse it
		second.setPhoneKey(null);

		IdentifierConflictException refused = assertThrows(IdentifierConflictException.class, () -> users.create(second));

		assertEquals(IdentifierConflictException.Kind.PHONE, refused.getKind());
	}

	@Test
	void save_emailKeyHeldByAnother_identifierConflict() {
		String email = "held-" + UUID.randomUUID() + "@example.com";
		users.create(emailUser(email));
		AppUser other = users.create(emailUser("other-" + UUID.randomUUID() + "@example.com"));

		IdentifierConflictException refused = assertThrows(IdentifierConflictException.class,
				() -> transaction.executeWithoutResult(status -> {
					LockedUser locked = users.findByIdForSecurity(other.getId()).orElseThrow();
					locked.user().setEmail(Email.create(email));
					locked.user().setEmailKey(email);
					users.save(locked);
				}));

		assertEquals(IdentifierConflictException.Kind.EMAIL, refused.getKind());
	}

	/** Another unique index is no identifier race: it stays what it is. */
	@Test
	void create_otherConstraint_notAnIdentifierConflict() {
		String idCard = String.format("%012d", System.nanoTime() % 1_000_000_000_000L);
		users.create(withIdCard(idCard));

		RuntimeException refused = assertThrows(RuntimeException.class, () -> users.create(withIdCard(idCard)));

		assertFalse(refused instanceof IdentifierConflictException);
	}

	private AppUser emailUser(String email) {
		return AppUser.registerWithEmail(UserId.create(ulidGenerator.generateId()), "Email user", Email.create(email),
				Password.create("hash"), new HashSet<>(Set.of("TENANT")), AppClock.currentUTC());
	}

	private AppUser withIdCard(String idCard) {
		return AppUser.createWithPhone(UserId.create(ulidGenerator.generateId()), "Card holder",
				PhoneNumber.create(nextPhone()), Password.create("hash"), ProfileImage.create(null),
				IdCardNumber.create(idCard),
				new HashSet<>(Set.of("TENANT")), AppClock.currentUTC());
	}

	/** Commits on its own connection while the caller's transaction stays open. */
	private void inAnotherTransaction(String sql, Object... args) {
		TransactionTemplate other = new TransactionTemplate(transactionManager);
		other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		other.executeWithoutResult(status -> jdbc.update(sql, args));
	}
}
