package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.core.application.shared.external.token.AccessTokenIssuerPort;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.audit.AuthAuditWriter;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/** Recovery by email end to end on PostgreSQL (auth-email-access 4.7, 5.4..5.6, S-29..S-34). */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthV1EmailRecoveryApiPostgresTest {

	private static final String RESET_EMAIL = "/api/v1/auth/password-reset/email";
	private static final String RESET_COMPLETE = "/api/v1/auth/password-reset/complete";
	private static final String LOGIN = "/api/v1/auth/login";
	private static final String REFRESH = "/api/v1/auth/session/refresh";
	private static final String PASSWORD_CHANGE = "/api/v1/me/password/change";
	private static final String BIND_SEND = "/api/v1/me/email/send-code";
	private static final String BIND_CONFIRM = "/api/v1/me/email/confirm-code";
	private static final String NEW_PASSWORD = "Sunvibe@2027";

	private record Reset(String token, String code) {
		@Override
		public String toString() {
			return "Reset[redacted]";
		}
	}

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private AccessTokenIssuerPort accessTokens;

	@Autowired
	private CapturingMessagesConfig messages;

	@Autowired
	private AuthAuditWriter auditWriter;

	@Autowired
	private JdbcTemplate jdbc;

	private AuthV1ApiTestSupport api;

	@BeforeAll
	void setUp() {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
	}

	@AfterAll
	void tearDown() {
		api.close();
	}

	private String json(Map<String, ?> body) throws Exception {
		return api.json().writeValueAsString(body);
	}

	private HttpResponse<String> forgot(String email) throws Exception {
		return api.post(RESET_EMAIL, null, null, null, json(Map.of("email", email)));
	}

	private Reset startReset(String email) throws Exception {
		assertEquals(200, forgot(email).statusCode());
		return new Reset(messages.awaitResetToken(email), messages.awaitCode(email));
	}

	private HttpResponse<String> complete(Reset reset) throws Exception {
		return api.post(RESET_COMPLETE, null, null, null, json(Map.of("resetToken", reset.token(), "otpCode", reset.code(),
				"newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)));
	}

	private void assertRefused(HttpResponse<String> response) {
		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		assertEquals("RESET_TOKEN_INVALID", api.body(response).path("code").asString());
	}

	private String tokenOf(Created user) {
		return accessTokens.issue(userRepository.findById(UserId.from(user.userId())).orElseThrow());
	}

	private String passwordHash(String userId) {
		return jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, userId);
	}

	/** What a caller can see of an answer; {@code expiresAt} moves with the clock in every state. */
	private String shape(HttpResponse<String> response) {
		return response.statusCode() + " " + api.body(response).propertyNames().stream().sorted().toList() + " "
				+ api.body(response).path("otpExpirationMinutes").asLong();
	}

	/** The proofs of the flow are hashes: a reader of the table can use neither of them (SV-096). */
	@Test
	void stored_tokenAndOtp_areHashes() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");

		Reset reset = startReset(user.username());

		Map<String, Object> row = jdbc.queryForMap("SELECT token, otp_code FROM password_reset_token WHERE user_id = ?",
				user.userId());
		assertNotEquals(reset.token(), row.get("token"));
		assertNotEquals(reset.code(), row.get("otp_code"));
		assertTrue(row.get("token").toString().matches("[0-9a-f]{64}"), "SHA-256 of the token");
		assertTrue(row.get("otp_code").toString().matches("[0-9a-f]{64}"), "keyed hash of the code");
		assertEquals(204, complete(reset).statusCode());
	}

	/** BR-7: unknown, unverified, locked, in the cooldown and over the cap all answer like a code that left. */
	@Test
	void forgot_sameResponse_unknownUnverifiedLockedCooldownCap() throws Exception {
		Created sent = api.createEmailUser("CUSTOMER");
		String expected = shape(forgot(sent.username()));
		assertTrue(messages.awaitMessage(sent.username()));

		String unknown = "nobody-" + UUID.randomUUID() + "@example.com";
		assertEquals(expected, shape(forgot(unknown)));

		Created unverified = api.createEmailUser("CUSTOMER");
		jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", unverified.userId());
		assertEquals(expected, shape(forgot(unverified.username())));

		Created locked = api.createEmailUser("CUSTOMER");
		jdbc.update("UPDATE users SET user_status = 'LOCK' WHERE id = ?", locked.userId());
		assertEquals(expected, shape(forgot(locked.username())));

		assertEquals(expected, shape(forgot(sent.username())), "in the cooldown");

		Created capped = api.createEmailUser("CUSTOMER");
		for (int i = 0; i < 5; i++) {
			forgot(capped.username());
			jdbc.update("UPDATE password_reset_token SET created_at = created_at - INTERVAL '2 minutes' WHERE user_id = ?",
					capped.userId());
		}
		assertEquals(expected, shape(forgot(capped.username())), "over the cap");
		assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM password_reset_token WHERE user_id = ?", Integer.class,
				capped.userId()));

		assertFalse(messages.hasMessage(unknown));
		assertFalse(messages.hasMessage(unverified.username()));
		assertFalse(messages.hasMessage(locked.username()));
	}

	/** spec 5.6: the answer left already, so a failed mail is a warn line and an audit event without the address. */
	@Test
	void forgot_mailFails_sameResponse_auditSendFailed() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		messages.mailDown(true);
		try {
			HttpResponse<String> response = forgot(user.username());

			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			eventually(() -> sendFailedEvents(user) == 1);
		} finally {
			messages.mailDown(false);
		}

		assertTrue(auditWriter.awaitIdle(Duration.ofSeconds(10)));
		assertNull(jdbc.queryForObject("SELECT subject FROM auth_audit_events WHERE user_id = ? AND details = 'EMAIL SEND_FAILED'",
				String.class, user.userId()));
	}

	private int sendFailedEvents(Created user) {
		assertTrue(auditWriter.awaitIdle(Duration.ofSeconds(10)));
		return jdbc.queryForObject("SELECT count(*) FROM auth_audit_events WHERE user_id = ? AND details = 'EMAIL SEND_FAILED'",
				Integer.class, user.userId());
	}

	@Test
	void forgot_doesNotWaitForSmtp() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		CountDownLatch held = new CountDownLatch(1);
		messages.holdMail(held);
		try {
			CompletableFuture<Integer> answer = CompletableFuture.supplyAsync(() -> {
				try {
					return forgot(user.username()).statusCode();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});

			assertEquals(200, answer.get(5, TimeUnit.SECONDS));
			assertFalse(messages.hasMessage(user.username()), "the mail is still held");
		} finally {
			held.countDown();
			messages.holdMail(null);
		}

		assertTrue(messages.awaitMessage(user.username()));
	}

	/** spec 4.7: the lock on the address comes before the throttle, so parallel requests leave one live token. */
	@Test
	void forgot_parallel_capHolds_oneLiveToken() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		int requests = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<Integer>> calls = new ArrayList<>();
		for (int i = 0; i < requests; i++) {
			calls.add(() -> {
				start.await();
				return forgot(user.username()).statusCode();
			});
		}
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		try {
			List<Future<Integer>> answers = calls.stream().map(pool::submit).toList();
			start.countDown();
			for (Future<Integer> answer : answers) {
				assertEquals(200, answer.get(30, TimeUnit.SECONDS));
			}
		} finally {
			pool.shutdownNow();
		}

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM password_reset_token WHERE user_id = ? AND is_used = false",
				Integer.class, user.userId()));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM password_reset_token WHERE user_id = ?", Integer.class,
				user.userId()));
	}

	/** D4-10: a mailbox proven by the reset opens a PENDING account. */
	@Test
	void reset_pendingEmail_becomesActive() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		jdbc.update("UPDATE users SET user_status = 'PENDING' WHERE id = ?", user.userId());

		assertEquals(204, complete(startReset(user.username())).statusCode());

		assertEquals("ACTIVE", jdbc.queryForObject("SELECT user_status FROM users WHERE id = ?", String.class, user.userId()));
	}

	@Test
	void reset_clearsBothCounters_endsSessions_noSignIn() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		HttpResponse<String> login = api.post(LOGIN, null, null, null, json(Map.of("email", user.username(), "password",
				user.password())));
		assertEquals(200, login.statusCode());
		String session = REFRESH_COOKIE + "=" + AuthV1ApiTestSupport.cookieValue(login.headers().allValues("Set-Cookie"), REFRESH_COOKIE);
		jdbc.update("UPDATE users SET failed_login_attempts = 3, locked_until = ?, reauth_failed_attempts = 3,"
				+ " reauth_locked_until = ? WHERE id = ?", Timestamp.from(AppClock.currentUTC().plus(Duration.ofHours(1))),
				Timestamp.from(AppClock.currentUTC().plus(Duration.ofHours(1))), user.userId());

		HttpResponse<String> done = complete(startReset(user.username()));

		assertEquals(204, done.statusCode(), "status " + done.statusCode());
		assertTrue(done.body().isEmpty(), "no session in the answer");
		Map<String, Object> row = jdbc.queryForMap("SELECT failed_login_attempts, locked_until, reauth_failed_attempts,"
				+ " reauth_locked_until FROM users WHERE id = ?", user.userId());
		assertEquals(0, row.get("failed_login_attempts"));
		assertNull(row.get("locked_until"));
		assertEquals(0, row.get("reauth_failed_attempts"));
		assertNull(row.get("reauth_locked_until"));
		assertEquals(401, api.post(REFRESH, ORIGIN, session, null, null).statusCode(), "the old session ended");
	}

	/** spec 4.7: a token is never rebound by string; another address on the account voids it. */
	@Test
	void reset_afterEmailChange_refused() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		Reset reset = startReset(user.username());
		String moved = "moved-" + UUID.randomUUID() + "@example.com";
		jdbc.update("UPDATE users SET email = ?, email_key = ? WHERE id = ?", moved, moved, user.userId());
		String before = passwordHash(user.userId());

		assertRefused(complete(reset));

		assertEquals(before, passwordHash(user.userId()));
	}

	@Test
	void reset_afterPasswordChange_refused() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		Reset reset = startReset(user.username());
		assertEquals(200, api.post(PASSWORD_CHANGE, null, null, tokenOf(user), json(Map.of("currentPassword", user.password(),
				"newPassword", "Changed@2028", "confirmPassword", "Changed@2028"))).statusCode());
		String before = passwordHash(user.userId());

		assertRefused(complete(reset));

		assertEquals(before, passwordHash(user.userId()));
	}

	/** Rows from before the release carry no version, so they are invalid. */
	@Test
	void reset_oldRowWithoutVersion_refused() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		Reset reset = startReset(user.username());
		jdbc.update("UPDATE password_reset_token SET credential_version = NULL WHERE user_id = ?", user.userId());

		assertRefused(complete(reset));
	}

	/** E-19: a new account on a released address never takes over the token of the old one. */
	@Test
	void reset_afterSoftDeleteReuse_refused() throws Exception {
		Created old = api.createEmailUser("CUSTOMER");
		Reset reset = startReset(old.username());
		jdbc.update("UPDATE users SET deleted_at = ?, user_status = 'DELETED' WHERE id = ?",
				Timestamp.from(AppClock.currentUTC()), old.userId());
		AppUser reused = userRepository.create(AppUser.registerWithEmail(UserId.create(ulidGenerator.generateId()), "New owner",
				Email.create(old.username()), Password.create(passwordEncoder.encode("Other@2028")),
				new HashSet<>(Set.of("CUSTOMER")), AppClock.currentUTC()));
		String before = passwordHash(reused.getId().getValue());

		assertRefused(complete(reset));

		assertEquals(before, passwordHash(reused.getId().getValue()));
	}

	@Test
	void reset_tokenBeforeBind_refused() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		Reset reset = startReset(user.username());
		String next = "next-" + UUID.randomUUID() + "@example.com";
		String bearer = tokenOf(user);
		assertEquals(200, api.post(BIND_SEND, null, null, bearer, json(Map.of("newEmail", next, "currentPassword",
				user.password()))).statusCode());
		assertEquals(200, api.post(BIND_CONFIRM, null, null, bearer, json(Map.of("newEmail", next, "code",
				messages.lastCode(next)))).statusCode());

		assertRefused(complete(reset));
		assertNotNull(passwordHash(user.userId()));
	}

	private static void eventually(java.util.function.BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!condition.getAsBoolean()) {
			assertTrue(System.nanoTime() < deadline, "not reached in time");
			Thread.sleep(50);
		}
	}
}
