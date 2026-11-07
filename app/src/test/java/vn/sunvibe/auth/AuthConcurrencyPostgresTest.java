package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Session;
import vn.sunvibe.auth.core.application.shared.external.token.AccessTokenIssuerPort;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The races of session issue and of identifiers, over HTTP so open-in-view is in play (auth-email-access 5.4, A-24, S-4,
 * S-5, S-13, S-16, S-17, S-26, S-35). The order is forced: by a pause inside the password encoder, which runs at a known
 * point of each flow, or by a row a test holds; a request that must wait is seen waiting in {@code pg_stat_activity}.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"app.auth.mail.notice-delay=1h" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class, AuthConcurrencyPostgresTest.PausingEncoder.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthConcurrencyPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String REFRESH = "/api/v1/auth/session/refresh";
	private static final String LOGOUT = "/api/v1/auth/session/logout";
	private static final String RESET_SMS = "/api/v1/auth/password-reset/sms";
	private static final String RESET_EMAIL = "/api/v1/auth/password-reset/email";
	private static final String RESET_COMPLETE = "/api/v1/auth/password-reset/complete";
	private static final String PASSWORD_CHANGE = "/api/v1/me/password/change";
	private static final String LOGOUT_ALL = "/api/v1/me/sessions/logout-all";
	private static final String BIND_SEND = "/api/v1/me/email/send-code";
	private static final String BIND_CONFIRM = "/api/v1/me/email/confirm-code";
	private static final String SIGN_UP_SEND = "/api/v1/auth/registration/email/send-code";
	private static final String SIGN_UP_VERIFY = "/api/v1/auth/registration/email/verify-code";
	private static final String SIGN_UP = "/api/v1/auth/registration/email";
	private static final String NEW_PASSWORD = "Sunvibe@2027";

	private static final Pause AFTER_MATCH = new Pause();
	private static final Pause AFTER_ENCODE = new Pause();

	/** One request stops at the armed point until the test lets it go; every other call passes. */
	private static final class Pause {
		private final AtomicReference<Held> armed = new AtomicReference<>();

		Held arm() {
			Held held = new Held();
			armed.set(held);
			return held;
		}

		void passThrough() {
			Held held = armed.getAndSet(null);
			if (held != null) {
				held.reached.countDown();
				try {
					held.released.await(30, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private static final class Held {
		private final CountDownLatch reached = new CountDownLatch(1);
		private final CountDownLatch released = new CountDownLatch(1);

		void awaitReached() throws InterruptedException {
			assertTrue(reached.await(20, TimeUnit.SECONDS), "the request never reached the pause");
		}

		void release() {
			released.countDown();
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class PausingEncoder {
		@Bean
		@Primary
		BCryptPasswordEncoder pausingPasswordEncoder() {
			return new BCryptPasswordEncoder(4) {
				@Override
				protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
					boolean matches = super.matchesNonNull(rawPassword, encodedPassword);
					AFTER_MATCH.passThrough();
					return matches;
				}

				@Override
				protected String encodeNonNullPassword(String rawPassword) {
					String encoded = super.encodeNonNullPassword(rawPassword);
					AFTER_ENCODE.passThrough();
					return encoded;
				}
			};
		}
	}

	/** A row held by the test, as a slow transaction would hold it. */
	private final class RowLock {
		private final Connection connection;
		private boolean released;

		RowLock(String sql, Object... arguments) throws SQLException {
			connection = dataSource.getConnection();
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				for (int i = 0; i < arguments.length; i++) {
					statement.setObject(i + 1, arguments[i]);
				}
				try (ResultSet row = statement.executeQuery()) {
					assertTrue(row.next(), "no row to hold");
				}
			}
		}

		void release() throws SQLException {
			if (!released) {
				released = true;
				connection.rollback();
				connection.setAutoCommit(true);
				connection.close();
			}
		}
	}

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
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	private AuthV1ApiTestSupport api;
	private ExecutorService pool;

	@BeforeAll
	void setUp() {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		pool = Executors.newFixedThreadPool(16);
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		api.close();
	}

	private String json(Map<String, ?> body) throws Exception {
		return api.json().writeValueAsString(body);
	}

	private Future<HttpResponse<String>> async(Callable<HttpResponse<String>> call) {
		return pool.submit(call);
	}

	private static HttpResponse<String> answer(Future<HttpResponse<String>> call) throws Exception {
		return call.get(30, TimeUnit.SECONDS);
	}

	private static int status(Future<HttpResponse<String>> call) throws Exception {
		return answer(call).statusCode();
	}

	/** Waits for a state, not for a time: {@code requests} backends are stuck on a row lock. */
	private void awaitBlocked(int requests) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (blocked() < requests) {
			assertTrue(System.nanoTime() < deadline, "the requests never reached the lock");
			Thread.sleep(10);
		}
	}

	private int blocked() {
		return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
				+ " AND wait_event_type = 'Lock' AND state = 'active'", Integer.class);
	}

	private int liveTokens(Created user) {
		return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND status = 'ACTIVE'", Integer.class,
				user.userId());
	}

	private String hash(Created user) {
		return jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, user.userId());
	}

	private String tokenOf(Created user) {
		return accessTokens.issue(userRepository.findById(UserId.from(user.userId())).orElseThrow());
	}

	private HttpResponse<String> login(Created user) throws Exception {
		return api.post(LOGIN, null, null, null, api.loginBody(user.username(), user.password()));
	}

	private HttpResponse<String> refresh(Session session) throws Exception {
		return api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null);
	}

	private Reset startReset(Created user) throws Exception {
		HttpResponse<String> started = api.post(RESET_SMS, null, null, null, json(Map.of("phoneNumber", user.username())));
		assertEquals(200, started.statusCode());
		return new Reset(api.body(started).path("resetToken").asString(), messages.lastCode(user.username()));
	}

	private HttpResponse<String> complete(String token, String code) throws Exception {
		return api.post(RESET_COMPLETE, null, null, null, json(Map.of("resetToken", token, "otpCode", code, "newPassword",
				NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)));
	}

	private HttpResponse<String> changePassword(Created user, String bearer) throws Exception {
		return api.post(PASSWORD_CHANGE, null, null, bearer, json(Map.of("currentPassword", user.password(), "newPassword",
				NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)));
	}

	private String bindSend(Created user, String bearer, String email) throws Exception {
		assertEquals(200, api.post(BIND_SEND, null, null, bearer, json(Map.of("newEmail", email, "currentPassword",
				user.password()))).statusCode());
		return messages.lastCode(email);
	}

	private HttpResponse<String> bindConfirm(String bearer, String email, String code) throws Exception {
		return api.post(BIND_CONFIRM, null, null, bearer, json(Map.of("newEmail", email, "code", code)));
	}

	private HttpResponse<String> revokeRole(String adminBearer, Created target, String role) throws Exception {
		return api.send(api.request("/api/v1/staff/users/" + target.userId() + "/roles/" + role)
				.header("Authorization", "Bearer " + adminBearer).DELETE());
	}

	private static String freshAddress() {
		return "race-" + UUID.randomUUID() + "@example.com";
	}

	private HttpResponse<String> signUpSend(String email) throws Exception {
		return api.post(SIGN_UP_SEND, null, null, null, json(Map.of("email", email)));
	}

	/** A verified proof of the mailbox, as its registration token. */
	private String proof(String email) throws Exception {
		assertEquals(200, signUpSend(email).statusCode());
		HttpResponse<String> verified = api.post(SIGN_UP_VERIFY, null, null, null, json(Map.of("email", email, "code",
				messages.lastCode(email))));
		assertEquals(200, verified.statusCode(), "status " + verified.statusCode());
		return api.body(verified).path("registrationToken").asString();
	}

	private HttpResponse<String> signUp(String email, String registrationToken) throws Exception {
		return api.post(SIGN_UP, null, null, null, json(Map.of("email", email, "registrationToken", registrationToken,
				"fullName", "Race User", "password", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)));
	}

	private int holdersOf(String email) {
		return jdbc.queryForObject("SELECT count(*) FROM users WHERE email_key = ? AND deleted_at IS NULL", Integer.class, email);
	}

	private void assertRefused(HttpResponse<String> response, int status, String code) {
		assertEquals(status, response.statusCode(), "status " + response.statusCode());
		assertEquals(code, api.body(response).path("code").asString());
	}

	// ==================== S-16: a session cannot be issued on credentials that just changed ====================

	@Test
	void login_vsReset_newHashKept_noUsableSession() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Reset reset = startReset(user);
		Held held = AFTER_MATCH.arm();

		Future<HttpResponse<String>> oldPassword = async(() -> login(user));
		held.awaitReached();
		assertEquals(204, complete(reset.token(), reset.code()).statusCode());
		String afterReset = hash(user);
		held.release();

		assertEquals(401, status(oldPassword));
		assertEquals(0, liveTokens(user), "the old password left no session");
		assertEquals(afterReset, hash(user), "the login wrote the old hash back");
		assertTrue(passwordEncoder.matches(NEW_PASSWORD, afterReset));
	}

	@Test
	void login_vsPasswordChange() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String bearer = tokenOf(user);
		Held held = AFTER_MATCH.arm();

		Future<HttpResponse<String>> oldPassword = async(() -> login(user));
		held.awaitReached();
		assertEquals(200, changePassword(user, bearer).statusCode());
		held.release();

		assertEquals(401, status(oldPassword));
		assertEquals(1, liveTokens(user), "only the session of the change");
	}

	@Test
	void login_vsBind() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String bearer = tokenOf(user);
		String email = freshAddress();
		String code = bindSend(user, bearer, email);
		Held held = AFTER_MATCH.arm();

		Future<HttpResponse<String>> oldVersion = async(() -> login(user));
		held.awaitReached();
		assertEquals(200, bindConfirm(bearer, email, code).statusCode());
		held.release();

		assertEquals(401, status(oldVersion));
		assertEquals(1, liveTokens(user), "only the session of the bind");
	}

	// ==================== S-17: no token outlives the end of its sessions ====================

	@Test
	void refresh_vsReset_bothOrders() throws Exception {
		Created first = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(first);
		Reset reset = startReset(first);
		assertEquals(200, refresh(session).statusCode());
		assertEquals(204, complete(reset.token(), reset.code()).statusCode());
		assertEquals(0, liveTokens(first), "refresh, then reset");

		Created second = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session other = api.logIn(second);
		Reset resetFirst = startReset(second);
		assertEquals(204, complete(resetFirst.token(), resetFirst.code()).statusCode());
		assertEquals(401, refresh(other).statusCode());
		assertEquals(0, liveTokens(second), "reset, then refresh");
	}

	/** The reset holds the row in its encode; the refresh waits for it, then sees its token revoked. */
	@Test
	void refresh_waitingOnUser_whileResetCommits_noLiveToken() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		Reset reset = startReset(user);
		Held held = AFTER_ENCODE.arm();

		Future<HttpResponse<String>> resetDone = async(() -> complete(reset.token(), reset.code()));
		held.awaitReached();
		Future<HttpResponse<String>> refreshed = async(() -> refresh(session));
		awaitBlocked(1);
		held.release();

		assertEquals(204, status(resetDone));
		assertEquals(401, status(refreshed));
		assertEquals(0, liveTokens(user));
	}

	@Test
	void refresh_vsPasswordChange() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		String bearer = tokenOf(user);
		Held held = AFTER_ENCODE.arm();

		Future<HttpResponse<String>> changed = async(() -> changePassword(user, bearer));
		held.awaitReached();
		Future<HttpResponse<String>> refreshed = async(() -> refresh(session));
		awaitBlocked(1);
		held.release();

		assertEquals(200, status(changed));
		assertEquals(401, status(refreshed));
		assertEquals(1, liveTokens(user), "only the session of the change");
	}

	/** The confirm holds the user and waits on its code; the refresh waits on the user. */
	@Test
	void refresh_vsBind() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		String bearer = tokenOf(user);
		String email = freshAddress();
		String code = bindSend(user, bearer, email);
		RowLock proof = new RowLock("SELECT 1 FROM verification_codes WHERE user_id = ? FOR UPDATE", user.userId());
		try {
			Future<HttpResponse<String>> bound = async(() -> bindConfirm(bearer, email, code));
			awaitBlocked(1);
			Future<HttpResponse<String>> refreshed = async(() -> refresh(session));
			awaitBlocked(2);
			proof.release();

			assertEquals(200, status(bound));
			assertEquals(401, status(refreshed));
			assertEquals(1, liveTokens(user), "only the session of the bind");
		} finally {
			proof.release();
		}
	}

	/**
	 * The other order: the refresh holds the user in share mode and waits on its token, the change waits on the user, so it
	 * revokes the token the refresh is about to issue.
	 */
	private void refreshFirst(Created user, Session session, Callable<HttpResponse<String>> change, int changed, int liveAfter)
			throws Exception {
		RowLock token = new RowLock("SELECT 1 FROM refresh_tokens WHERE user_id = ? FOR UPDATE", user.userId());
		try {
			Future<HttpResponse<String>> refreshed = async(() -> refresh(session));
			awaitBlocked(1);
			Future<HttpResponse<String>> done = async(change);
			awaitBlocked(2);
			token.release();

			assertEquals(200, status(refreshed));
			assertEquals(changed, status(done));
			assertEquals(liveAfter, liveTokens(user));
		} finally {
			token.release();
		}
	}

	@Test
	void refreshFirst_vsReset_noLiveToken() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		Reset reset = startReset(user);

		refreshFirst(user, session, () -> complete(reset.token(), reset.code()), 204, 0);
	}

	@Test
	void refreshFirst_vsPasswordChange_onlyTheNewSession() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		String bearer = tokenOf(user);

		refreshFirst(user, session, () -> changePassword(user, bearer), 200, 1);
	}

	@Test
	void refreshFirst_vsBind_onlyTheNewSession() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		String bearer = tokenOf(user);
		String email = freshAddress();
		String code = bindSend(user, bearer, email);

		refreshFirst(user, session, () -> bindConfirm(bearer, email, code), 200, 1);
	}

	/** Spec 5.4: a refresh waits three seconds at most; the 5xx is not fatal to the session, so the cookie stays. */
	@Test
	void refresh_lockTimeout_answers5xx_cookieKept() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		RowLock row = new RowLock("SELECT 1 FROM users WHERE id = ? FOR NO KEY UPDATE", user.userId());
		try {
			HttpResponse<String> refused = refresh(session);

			assertTrue(refused.statusCode() >= 500, "status " + refused.statusCode());
			assertTrue(refused.headers().allValues("Set-Cookie").stream()
					.noneMatch(cookie -> cookie.startsWith(AuthV1ApiTestSupport.REFRESH_COOKIE + "=")), "the cookie is not cleared");
		} finally {
			row.release();
		}

		assertEquals(200, refresh(session).statusCode());
	}

	/** Spec 4.9: a send holds no row through SMTP, so a bind confirm or a lockout never waits for a mail. */
	@Test
	void bindSend_mailInFlight_holdsNoUserLock() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		jdbc.update("UPDATE users SET reauth_failed_attempts = 2 WHERE id = ?", user.userId());
		String bearer = tokenOf(user);
		String email = freshAddress();
		CountDownLatch held = new CountDownLatch(1);
		messages.holdMail(held);
		try {
			Future<HttpResponse<String>> sent = async(() -> api.post(BIND_SEND, null, null, bearer, json(Map.of("newEmail", email,
					"currentPassword", user.password()))));
			messages.awaitMailHeld();

			new RowLock("SELECT 1 FROM users WHERE id = ? FOR NO KEY UPDATE NOWAIT", user.userId()).release();

			held.countDown();
			assertEquals(200, status(sent));
		} finally {
			held.countDown();
			messages.holdMail(null);
		}
	}

	/** The refresh holds the user in share mode and waits on its token; the logout waits on the user. */
	@Test
	void logout_vsRefresh() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		RowLock token = new RowLock("SELECT 1 FROM refresh_tokens WHERE user_id = ? FOR UPDATE", user.userId());
		try {
			Future<HttpResponse<String>> refreshed = async(() -> refresh(session));
			awaitBlocked(1);
			Future<HttpResponse<String>> loggedOut = async(() -> api.post(LOGOUT, ORIGIN, session.refreshCookie(), null, null));
			awaitBlocked(2);
			token.release();

			assertEquals(200, status(refreshed));
			assertEquals(204, status(loggedOut));
			assertEquals(0, liveTokens(user), "the token of the refresh in flight ended with the session");
		} finally {
			token.release();
		}
	}

	/** spec 5.4: the user row first, or a logout and a reset deadlock on the rows of the family. */
	@Test
	void logout_locksTheUserBeforeTheToken() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		RowLock row = new RowLock("SELECT 1 FROM users WHERE id = ? FOR NO KEY UPDATE", user.userId());
		try {
			Future<HttpResponse<String>> loggedOut = async(() -> api.post(LOGOUT, ORIGIN, session.refreshCookie(), null, null));
			awaitBlocked(1);
			new RowLock("SELECT 1 FROM refresh_tokens WHERE user_id = ? FOR UPDATE NOWAIT", user.userId()).release();
			row.release();

			assertEquals(204, status(loggedOut));
			assertEquals(0, liveTokens(user));
		} finally {
			row.release();
		}
	}

	@Test
	void logoutAll_vsRefresh() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(user);
		RowLock token = new RowLock("SELECT 1 FROM refresh_tokens WHERE user_id = ? FOR UPDATE", user.userId());
		try {
			Future<HttpResponse<String>> refreshed = async(() -> refresh(session));
			awaitBlocked(1);
			Future<HttpResponse<String>> loggedOut = async(() -> api.post(LOGOUT_ALL, null, null, session.accessToken(), null));
			awaitBlocked(2);
			token.release();

			assertEquals(200, status(refreshed));
			assertEquals(204, status(loggedOut));
			assertEquals(0, liveTokens(user));
		} finally {
			token.release();
		}
	}

	/** The session issued after the revoke carries the roles of the row, not those the password check read. */
	@Test
	void roleRevoke_vsLogin() throws Exception {
		Created admin = api.createUser(UserStatus.ACTIVE, "ADMIN", "CUSTOMER");
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER", "SALE");
		Held held = AFTER_MATCH.arm();

		Future<HttpResponse<String>> signedIn = async(() -> login(user));
		held.awaitReached();
		assertEquals(204, revokeRole(tokenOf(admin), user, "SALE").statusCode());
		held.release();

		HttpResponse<String> response = answer(signedIn);
		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		List<String> roles = new ArrayList<>();
		api.body(response).path("user").path("roles").forEach(role -> roles.add(role.asString()));
		assertFalse(roles.contains("SALE"), "the session must not carry the revoked role");
	}

	@Test
	void roleRevoke_vsRefresh() throws Exception {
		Created admin = api.createUser(UserStatus.ACTIVE, "ADMIN", "CUSTOMER");
		String adminBearer = tokenOf(admin);
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER", "SALE");
		Session session = api.logIn(user);
		RowLock token = new RowLock("SELECT 1 FROM refresh_tokens WHERE user_id = ? FOR UPDATE", user.userId());
		try {
			Future<HttpResponse<String>> refreshed = async(() -> refresh(session));
			awaitBlocked(1);
			Future<HttpResponse<String>> revoked = async(() -> revokeRole(adminBearer, user, "SALE"));
			awaitBlocked(2);
			token.release();

			assertEquals(200, status(refreshed));
			assertEquals(204, status(revoked));
			assertEquals(0, liveTokens(user), "the revoke ends the session the refresh just rotated");
		} finally {
			token.release();
		}
	}

	@Test
	void reset_twoWrongCodesParallel_bothCounted() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Reset reset = startReset(user);
		String wrong = reset.code().equals("000000") ? "111111" : "000000";
		RowLock token = new RowLock("SELECT 1 FROM password_reset_token WHERE user_id = ? FOR UPDATE", user.userId());
		try {
			Future<HttpResponse<String>> first = async(() -> complete(reset.token(), wrong));
			Future<HttpResponse<String>> second = async(() -> complete(reset.token(), wrong));
			awaitBlocked(2);
			token.release();

			assertRefused(answer(first), 400, "CODE_INCORRECT");
			assertRefused(answer(second), 400, "CODE_INCORRECT");
			assertEquals(2, jdbc.queryForObject("SELECT failed_attempts FROM password_reset_token WHERE user_id = ?", Integer.class,
					user.userId()));
		} finally {
			token.release();
		}
	}

	// ==================== S-4, S-26, S-35: one mailbox or number, one account ====================

	/** Both pass the check before either writes: the unique index decides, and the loser gets the code of the check. */
	@Test
	void complete_parallelTwoProofs_oneUser() throws Exception {
		String email = freshAddress();
		String firstProof = proof(email);
		jdbc.update("UPDATE registration_email_challenges SET created_at = created_at - INTERVAL '2 minutes' WHERE email_key = ?", email);
		String secondProof = proof(email);
		Held held = AFTER_ENCODE.arm();

		Future<HttpResponse<String>> first = async(() -> signUp(email, firstProof));
		held.awaitReached();
		assertEquals(201, signUp(email.toUpperCase(Locale.ROOT), secondProof).statusCode());
		held.release();

		assertRefused(answer(first), 409, "EMAIL_NOT_AVAILABLE");
		assertEquals(1, holdersOf(email));
	}

	@Test
	void complete_vsBind_sameEmail() throws Exception {
		String email = freshAddress();
		String registrationToken = proof(email);
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String bearer = tokenOf(user);
		String code = bindSend(user, bearer, email);
		Held held = AFTER_ENCODE.arm();

		Future<HttpResponse<String>> signedUp = async(() -> signUp(email, registrationToken));
		held.awaitReached();
		assertEquals(200, bindConfirm(bearer, email, code).statusCode());
		held.release();

		assertRefused(answer(signedUp), 409, "EMAIL_NOT_AVAILABLE");
		assertEquals(1, holdersOf(email));
		assertEquals(email, jdbc.queryForObject("SELECT email_key FROM users WHERE id = ?", String.class, user.userId()));
	}

	@Test
	void bind_parallelTwoAccountsOneMailbox_oneWins() throws Exception {
		String email = freshAddress();
		Created loser = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Created winner = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String loserBearer = tokenOf(loser);
		String winnerBearer = tokenOf(winner);
		String loserCode = bindSend(loser, loserBearer, email);
		String winnerCode = bindSend(winner, winnerBearer, email);
		RowLock proof = new RowLock("SELECT 1 FROM verification_codes WHERE user_id = ? FOR UPDATE", loser.userId());
		try {
			Future<HttpResponse<String>> late = async(() -> bindConfirm(loserBearer, email, loserCode));
			awaitBlocked(1);
			assertEquals(200, bindConfirm(winnerBearer, email, winnerCode).statusCode());
			proof.release();

			assertRefused(answer(late), 409, "EMAIL_NOT_AVAILABLE");
			assertEquals(1, holdersOf(email));
			assertEquals(email, jdbc.queryForObject("SELECT email_key FROM users WHERE id = ?", String.class, winner.userId()));
			assertNull(jdbc.queryForObject("SELECT email_key FROM users WHERE id = ?", String.class, loser.userId()));
		} finally {
			proof.release();
		}
	}

	/**
	 * The check of the confirm sees the address free, because the other account has not committed; its write then waits on
	 * the unique index and loses. Spec 4.9: this rolls back, so nothing changes and the code stays until it expires.
	 */
	@Test
	void bind_losesAtTheUniqueIndex_409_nothingChanged() throws Exception {
		String email = freshAddress();
		Created other = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String bearer = tokenOf(user);
		String code = bindSend(user, bearer, email);
		int sessions = liveTokens(user);

		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (PreparedStatement update = connection.prepareStatement("UPDATE users SET email = ?, email_key = ? WHERE id = ?")) {
				update.setString(1, email);
				update.setString(2, email);
				update.setString(3, other.userId());
				assertEquals(1, update.executeUpdate());
			}
			Future<HttpResponse<String>> late = async(() -> bindConfirm(bearer, email, code));
			awaitBlocked(1);
			connection.commit();

			assertRefused(answer(late), 409, "EMAIL_NOT_AVAILABLE");
			connection.setAutoCommit(true);
		}

		assertEquals(1, holdersOf(email));
		assertEquals(sessions, liveTokens(user), "no session");
		assertEquals(0L, jdbc.queryForObject("SELECT credential_version FROM users WHERE id = ?", Long.class, user.userId()));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM auth_email_notifications WHERE user_id = ?", Integer.class,
				user.userId()));
		assertNull(jdbc.queryForObject("SELECT consumed_at FROM verification_codes WHERE user_id = ?", java.sql.Timestamp.class,
				user.userId()), "the rollback keeps the code");
	}

	@Test
	void on_parallelRegistration_oneUser() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		String key = PhoneNumber.create(phone).key();
		assertEquals(200, api.post("/api/v1/auth/registration/phone/send-code", null, null, null,
				json(Map.of("phoneNumber", phone))).statusCode());
		HttpResponse<String> verified = api.post("/api/v1/auth/registration/phone/verify-code", null, null, null,
				json(Map.of("phoneNumber", phone, "code", messages.lastCode(phone))));
		assertEquals(200, verified.statusCode());
		String body = json(Map.of("fullName", "Race User", "username", phone, "password", NEW_PASSWORD, "confirmPassword",
				NEW_PASSWORD, "registrationToken", api.body(verified).path("registrationToken").asString()));
		RowLock challenge = new RowLock("SELECT 1 FROM registration_phone_challenges WHERE phone = ? FOR UPDATE", key);
		try {
			Future<HttpResponse<String>> first = async(() -> api.post("/api/v1/auth/registration", null, null, null, body));
			Future<HttpResponse<String>> second = async(() -> api.post("/api/v1/auth/registration", null, null, null, body));
			awaitBlocked(2);
			challenge.release();

			List<Integer> statuses = new ArrayList<>(List.of(status(first), status(second)));
			statuses.sort(null);
			assertEquals(201, statuses.get(0));
			assertTrue(statuses.get(1) >= 400 && statuses.get(1) < 500, "status " + statuses.get(1));
			assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE phone_key = ?", Integer.class, key));
		} finally {
			challenge.release();
		}
	}

	// ==================== S-13: the lock comes before the throttle ====================

	/** The first send holds the address through its mail; nobody else gets past the lock meanwhile. */
	private void onlyOneSendPasses(String email) throws Exception {
		int before = messages.mailsTo(email);
		CountDownLatch held = new CountDownLatch(1);
		messages.holdMail(held);
		try {
			Future<HttpResponse<String>> first = async(() -> signUpSend(email));
			messages.awaitMailHeld();
			List<Future<HttpResponse<String>>> others = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				others.add(async(() -> signUpSend(email)));
			}
			for (Future<HttpResponse<String>> other : others) {
				int status = status(other);
				assertTrue(status == 429 || status == 503, "status " + status);
			}
			held.countDown();
			assertEquals(200, status(first));
		} finally {
			held.countDown();
			messages.holdMail(null);
		}
		assertEquals(before + 1, messages.mailsTo(email));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM registration_email_challenges WHERE email_key = ?"
				+ " AND consumed_at IS NULL", Integer.class, email));
	}

	@Test
	void send_parallelFirstSend_oneActiveRow_oneMail() throws Exception {
		onlyOneSendPasses(freshAddress());
	}

	@Test
	void send_parallelResend_oneMail() throws Exception {
		String email = freshAddress();
		assertEquals(200, signUpSend(email).statusCode());
		jdbc.update("UPDATE registration_email_challenges SET created_at = created_at - INTERVAL '2 minutes' WHERE email_key = ?", email);

		onlyOneSendPasses(email);
	}

	// ==================== S-5: the longest address fits everywhere ====================

	@Test
	void email254_wholeFlow() throws Exception {
		String email = "a".repeat(64) + "@" + UUID.randomUUID().toString().replace("-", "") + "b".repeat(31) + "." + "c".repeat(63)
				+ "." + "d".repeat(57) + ".com";
		assertEquals(254, email.length());

		assertEquals(201, signUp(email, proof(email)).statusCode());
		assertEquals(200, api.post(LOGIN, null, null, null, json(Map.of("email", email, "password", NEW_PASSWORD))).statusCode());
		assertEquals(200, api.post(RESET_EMAIL, null, null, null, json(Map.of("email", email))).statusCode());
		String token = messages.awaitResetToken(email);
		assertEquals(204, api.post(RESET_COMPLETE, null, null, null, json(Map.of("resetToken", token, "otpCode",
				messages.awaitCode(email), "newPassword", "Sunvibe@2028", "confirmPassword", "Sunvibe@2028"))).statusCode());
		assertEquals(254, jdbc.queryForObject("SELECT length(email_key) FROM users WHERE email_key = ?", Integer.class, email));
	}

	// ==================== no deadlock in any order ====================

	/** Every command takes the user row first, so a mix of them on one account refuses or passes, and never answers 5xx. */
	@Test
	void noDeadlock_mixedLoginRefreshResetBindRevokeLogout() throws Exception {
		Created admin = api.createUser(UserStatus.ACTIVE, "ADMIN", "CUSTOMER");
		String adminBearer = tokenOf(admin);
		for (int round = 0; round < 5; round++) {
			Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER", "SALE");
			Session session = api.logIn(user);
			Session other = api.logIn(user);
			Reset reset = startReset(user);
			String bearer = tokenOf(user);
			String email = freshAddress();
			String code = bindSend(user, bearer, email);

			CountDownLatch start = new CountDownLatch(1);
			List<Callable<HttpResponse<String>>> calls = List.of(
					() -> login(user),
					() -> refresh(session),
					() -> complete(reset.token(), reset.code()),
					() -> bindConfirm(bearer, email, code),
					() -> revokeRole(adminBearer, user, "SALE"),
					() -> api.post(LOGOUT, ORIGIN, other.refreshCookie(), null, null));
			List<Future<HttpResponse<String>>> running = new ArrayList<>();
			for (Callable<HttpResponse<String>> call : calls) {
				running.add(pool.submit(() -> {
					start.await();
					return call.call();
				}));
			}
			start.countDown();

			for (Future<HttpResponse<String>> call : running) {
				int status = status(call);
				assertTrue(status < 500, "status " + status);
			}
		}
	}
}
