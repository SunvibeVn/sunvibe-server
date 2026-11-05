package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Session;
import vn.sunvibe.auth.core.application.usecase.auth.EmailNotificationDeliveryUseCase;
import vn.sunvibe.auth.core.application.usecase.external.token.AccessTokenIssuerPort;
import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.EmailNotificationRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Bind and replace of the account email end to end on PostgreSQL (auth-email-access 4.9, S-21..S-27, S-44). The job that
 * delivers notices is held back here, so a test sees the row it wrote.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"app.auth.mail.notice-delay=1h" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeEmailBindingApiPostgresTest {

	private static final String SEND = "/api/v1/me/email/send-code";
	private static final String CONFIRM = "/api/v1/me/email/confirm-code";
	private static final String PASSWORD_CHANGE = "/api/v1/me/password/change";
	private static final String REFRESH = "/api/v1/auth/session/refresh";
	private static final String ME = "/api/v1/me";
	private static final String LOGIN = "/api/v1/auth/login";
	private static final String NEW_PASSWORD = "Sunvibe@2027";
	private static final AtomicInteger IDS = new AtomicInteger(1);

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
	private EmailNotificationRepository notifications;

	@Autowired
	private EmailNotificationDeliveryUseCase delivery;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	private AuthV1ApiTestSupport api;

	@BeforeAll
	void setUp() {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
	}

	@AfterAll
	void tearDown() {
		api.close();
	}

	private static String freshAddress() {
		return "bind-" + UUID.randomUUID() + "@example.com";
	}

	private String json(Map<String, ?> body) throws Exception {
		return api.json().writeValueAsString(body);
	}

	private HttpResponse<String> send(String token, String email, String password) throws Exception {
		return api.post(SEND, null, null, token, json(Map.of("newEmail", email, "currentPassword", password)));
	}

	private HttpResponse<String> confirm(String token, String email, String code) throws Exception {
		return api.post(CONFIRM, null, null, token, json(Map.of("newEmail", email, "code", code)));
	}

	private HttpResponse<String> bind(Created user, String token, String email) throws Exception {
		assertEquals(200, send(token, email, user.password()).statusCode());
		return confirm(token, email, messages.lastCode(email));
	}

	private void assertCode(HttpResponse<String> response, int status, String code) {
		assertEquals(status, response.statusCode(), "status " + response.statusCode());
		assertEquals(code, api.body(response).path("code").asString());
	}

	private String tokenOf(Created user) {
		return accessTokens.issue(userRepository.findById(UserId.from(user.userId())).orElseThrow());
	}

	private int reauthFailures(Created user) {
		return jdbc.queryForObject("SELECT reauth_failed_attempts FROM users WHERE id = ?", Integer.class, user.userId());
	}

	private List<String> roles(String userId) {
		return jdbc.queryForList("SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?"
				+ " ORDER BY r.name", String.class, userId);
	}

	@Test
	void confirm_keepsIdRolesOwnershipActors() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER", "SALE");
		String email = freshAddress();

		HttpResponse<String> bound = bind(user, tokenOf(user), email);

		assertEquals(200, bound.statusCode(), "status " + bound.statusCode());
		assertEquals(user.userId(), api.body(bound).path("user").path("userId").asString());
		assertEquals(List.of("CUSTOMER", "SALE"), roles(user.userId()));
		assertEquals(email, jdbc.queryForObject("SELECT email_key FROM users WHERE id = ?", String.class, user.userId()));
		assertEquals(1L, jdbc.queryForObject("SELECT credential_version FROM users WHERE id = ?", Long.class, user.userId()));
	}

	@Test
	void confirm_pendingBecomesActive_rolesKept() throws Exception {
		Created user = api.createUser(UserStatus.PENDING, "CUSTOMER");

		assertEquals(200, bind(user, tokenOf(user), freshAddress()).statusCode());

		assertEquals("ACTIVE", jdbc.queryForObject("SELECT user_status FROM users WHERE id = ?", String.class, user.userId()));
		assertEquals(List.of("CUSTOMER"), roles(user.userId()));
	}

	/** S-24: every other session ends, this device gets a new one, and access tokens of the old version die. */
	@Test
	void confirm_endsOtherSessions_newCookie() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session before = api.logIn(user);

		HttpResponse<String> bound = bind(user, before.accessToken(), freshAddress());

		assertEquals(200, bound.statusCode(), "status " + bound.statusCode());
		String cookie = AuthV1ApiTestSupport.cookieValue(bound.headers().allValues("Set-Cookie"), REFRESH_COOKIE);
		assertNotNull(cookie);
		assertNotEquals(before.refreshCookie(), REFRESH_COOKIE + "=" + cookie);
		assertEquals(401, api.post(REFRESH, ORIGIN, before.refreshCookie(), null, null).statusCode(), "the old session ended");
		assertEquals(401, api.get(ME, before.accessToken()).statusCode(), "the old access token died");
		assertEquals(200, api.get(ME, api.body(bound).path("accessToken").asString()).statusCode());
		assertEquals(200, api.post(REFRESH, ORIGIN, REFRESH_COOKIE + "=" + cookie, null, null).statusCode());
	}

	/** The bound address signs in, and the account keeps its phone. */
	@Test
	void confirm_addressSignsIn() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String email = freshAddress();
		bind(user, tokenOf(user), email);

		assertEquals(200, api.post(LOGIN, null, null, null,
				json(Map.of("email", email, "password", user.password()))).statusCode());
	}

	@Test
	void confirm_otherAccount_409_nothingChanged_codeUsed() throws Exception {
		Created owner = api.createEmailUser("CUSTOMER");
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String token = tokenOf(user);

		assertEquals(200, send(token, owner.username(), user.password()).statusCode(), "the same 200 before the proof");
		String code = messages.lastCode(owner.username());
		assertCode(confirm(token, owner.username(), code), 409, "EMAIL_NOT_AVAILABLE");

		assertEquals(0L, jdbc.queryForObject("SELECT credential_version FROM users WHERE id = ?", Long.class, user.userId()));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ? AND email IS NOT NULL", Integer.class,
				user.userId()));
		assertCode(confirm(token, owner.username(), code), 400, "CODE_EXPIRED");
	}

	@Test
	void confirm_fiveWrongCodes_burned() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String token = tokenOf(user);
		String email = freshAddress();
		assertEquals(200, send(token, email, user.password()).statusCode());
		String code = messages.lastCode(email);
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 4; i++) {
			assertCode(confirm(token, email, wrong), 400, "CODE_INCORRECT");
		}
		assertCode(confirm(token, email, wrong), 400, "CODE_ATTEMPTS_EXCEEDED");

		assertCode(confirm(token, email, code), 400, "CODE_EXPIRED");
		assertEquals(5, jdbc.queryForObject("SELECT failed_attempts FROM verification_codes WHERE user_id = ?", Integer.class,
				user.userId()));
	}

	/** security.md: every field has a bound, so a body cannot make the server hash what it pleases. */
	@Test
	void send_overlongPassword_400_notCounted() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		assertCode(send(tokenOf(user), freshAddress(), "x".repeat(300)), 400, "VALIDATION_FAILED");

		assertEquals(0, reauthFailures(user));
	}

	/** The two paths that ask for the password again answer alike. */
	@Test
	void passwordChange_overlongPassword_400_notCounted() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		assertCode(api.post(PASSWORD_CHANGE, null, null, tokenOf(user), json(Map.of("currentPassword", "x".repeat(300),
				"newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD))), 400, "VALIDATION_FAILED");

		assertEquals(0, reauthFailures(user));
	}

	/** S-41: a row without a key still blocks an equivalent address, at sign-up and at a bind. */
	@Test
	void afterBackfill_keylessEquivalentBlocksRegistrationAndBind() throws Exception {
		String email = freshAddress();
		Timestamp now = Timestamp.from(AppClock.currentUTC());
		jdbc.update("INSERT INTO users (id, full_name, email, password, kyc_status, user_status, created_date, last_update)"
				+ " VALUES (?, 'Keyless row', ?, 'hash', 'UNVERIFIED', 'ACTIVE', ?, ?)", ulidGenerator.generateId(),
				email.toUpperCase(), now, now);
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		assertCode(bind(user, tokenOf(user), email), 409, "EMAIL_NOT_AVAILABLE");

		jdbc.update("UPDATE registration_email_challenges SET consumed_at = ? WHERE email_key = ?",
				Timestamp.from(AppClock.currentUTC()), email);
		assertEquals(200, api.post("/api/v1/auth/registration/email/send-code", null, null, null,
				json(Map.of("email", email))).statusCode());
		assertCode(api.post("/api/v1/auth/registration/email/verify-code", null, null, null, json(Map.of("email", email,
				"code", messages.lastCode(email)))), 409, "EMAIL_NOT_AVAILABLE");
	}

	@Test
	void send_wrongPassword_400_counted() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		HttpResponse<String> response = send(tokenOf(user), freshAddress(), "Wrong@Pass1");

		assertCode(response, 400, "VALIDATION_FAILED");
		assertEquals("currentPassword", api.body(response).path("errors").get(0).path("field").asString());
		assertEquals(1, reauthFailures(user));
		assertEquals(0, jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, user.userId()));
	}

	@Test
	void send_atLimit_423() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String token = tokenOf(user);
		for (int i = 0; i < 4; i++) {
			assertEquals(400, send(token, freshAddress(), "Wrong@Pass1").statusCode());
		}

		HttpResponse<String> fifth = send(token, freshAddress(), "Wrong@Pass1");

		assertEquals(423, fifth.statusCode(), "status " + fifth.statusCode());
		assertTrue(fifth.headers().firstValue("Retry-After").isPresent());
		assertEquals(423, send(token, freshAddress(), user.password()).statusCode(), "the right password waits too");
	}

	@Test
	void send_correctPassword_clearsCounter() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String token = tokenOf(user);
		send(token, freshAddress(), "Wrong@Pass1");

		assertEquals(200, send(token, freshAddress(), user.password()).statusCode());

		assertEquals(0, reauthFailures(user));
	}

	@Test
	void send_loginLock_doesNotStopBind() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String token = tokenOf(user);
		jdbc.update("UPDATE users SET failed_login_attempts = 5, locked_until = ? WHERE id = ?",
				Timestamp.from(AppClock.currentUTC().plus(Duration.ofHours(1))), user.userId());

		assertEquals(200, bind(user, token, freshAddress()).statusCode());
	}

	@Test
	void send_sessionWithoutPassword_refused() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		assertCode(api.post(SEND, null, null, tokenOf(user), json(Map.of("newEmail", freshAddress()))), 400, "VALIDATION_FAILED");
	}

	@Test
	void send_withoutBearer_401() throws Exception {
		assertEquals(401, api.post(SEND, null, null, null,
				json(Map.of("newEmail", freshAddress(), "currentPassword", "x"))).statusCode());
	}

	@Test
	void send_mailFails_503_noRow() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		messages.mailDown(true);
		try {
			assertCode(send(tokenOf(user), freshAddress(), user.password()), 503, "EMAIL_DELIVERY_FAILED");
		} finally {
			messages.mailDown(false);
		}

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM verification_codes WHERE user_id = ?", Integer.class,
				user.userId()));
	}

	/** S-44: a stolen access token cannot guess the password without limit through this path either. */
	@Test
	void passwordChange_wrongPassword_counted_refusedAtLimit() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String token = tokenOf(user);
		String wrong = json(Map.of("currentPassword", "Wrong@Pass1", "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD));

		for (int i = 0; i < 4; i++) {
			assertCode(api.post(PASSWORD_CHANGE, null, null, token, wrong), 400, "VALIDATION_FAILED");
		}
		HttpResponse<String> fifth = api.post(PASSWORD_CHANGE, null, null, token, wrong);

		assertEquals(423, fifth.statusCode(), "status " + fifth.statusCode());
		assertEquals(5, reauthFailures(user));
		assertEquals(423, api.post(PASSWORD_CHANGE, null, null, token, json(Map.of("currentPassword", user.password(),
				"newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD))).statusCode());
	}

	@Test
	void replace_writesOneNotice_firstBindNone() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String first = freshAddress();
		String second = freshAddress();
		HttpResponse<String> bound = bind(user, tokenOf(user), first);
		assertEquals(0, noticeRows(user));
		pastTheCooldown(user);

		assertEquals(200, bind(user, api.body(bound).path("accessToken").asString(), second).statusCode());

		assertEquals(1, noticeRows(user));
		assertEquals(first, jdbc.queryForObject("SELECT destination FROM auth_email_notifications WHERE user_id = ?",
				String.class, user.userId()));
	}

	/** spec 5.6: the notice never undoes the binding; it is retried, then given up, and then the old address is gone. */
	@Test
	void notice_senderFails_bindingKept_retries_thenGivesUp() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String first = freshAddress();
		String second = freshAddress();
		HttpResponse<String> bound = bind(user, tokenOf(user), first);
		pastTheCooldown(user);
		assertEquals(200, bind(user, api.body(bound).path("accessToken").asString(), second).statusCode());

		messages.mailDown(true);
		try {
			for (int attempt = 1; attempt <= 5; attempt++) {
				delivery.deliverDue();
				assertEquals(attempt, jdbc.queryForObject("SELECT attempts FROM auth_email_notifications WHERE user_id = ?",
						Integer.class, user.userId()));
				jdbc.update("UPDATE auth_email_notifications SET next_attempt_at = ? WHERE user_id = ?",
						Timestamp.from(AppClock.currentUTC().minusSeconds(5)), user.userId());
			}
			delivery.deliverDue();
		} finally {
			messages.mailDown(false);
		}

		assertEquals(0, noticeRows(user));
		assertEquals(second, jdbc.queryForObject("SELECT email_key FROM users WHERE id = ?", String.class, user.userId()));
	}

	/** spec 5.6: a row taken by one worker is skipped by the other, also while the first has not committed. */
	@Test
	void notice_twoWorkers_oneSend() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		long id = IDS.incrementAndGet() + 8_000_000L;
		java.time.Instant now = AppClock.currentUTC();
		transaction.executeWithoutResult(status -> notifications.save(
				EmailNotification.emailChanged(id, user.userId(), "old-" + id + "@example.com", now.minusSeconds(5))));
		CountDownLatch claimed = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Thread first = new Thread(() -> transaction.executeWithoutResult(status -> {
			notifications.claimDue(now, 20, now.plusSeconds(300));
			claimed.countDown();
			await(release);
		}));
		first.start();
		assertTrue(claimed.await(10, TimeUnit.SECONDS));

		List<EmailNotification> second = transaction.execute(status -> notifications.claimDue(now, 20, now.plusSeconds(300)));
		release.countDown();
		first.join(10_000);

		assertFalse(second.stream().anyMatch(n -> n.getId() == id), "the row of the first worker was skipped");
		assertTrue(transaction.execute(status -> notifications.claimDue(now, 20, now.plusSeconds(300))).stream()
				.noneMatch(n -> n.getId() == id), "after its commit the row is no longer due");
	}

	/** S-27, D4-4: bind is the only way to make an email verified. */
	@Test
	void verificationEmailPaths_gone() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String token = tokenOf(user);

		assertEquals(404, api.post("/api/v1/me/verification/email/send-code", null, null, token, null).statusCode());
		assertEquals(404, api.post("/api/v1/me/verification/email/confirm-code", null, null, token,
				json(Map.of("code", "123456"))).statusCode());
	}

	/** Moves the codes of the user back, as the resend cooldown would. */
	private void pastTheCooldown(Created user) {
		jdbc.update("UPDATE verification_codes SET created_at = created_at - INTERVAL '2 minutes' WHERE user_id = ?",
				user.userId());
	}

	private int noticeRows(Created user) {
		return jdbc.queryForObject("SELECT count(*) FROM auth_email_notifications WHERE user_id = ?", Integer.class, user.userId());
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(10, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
