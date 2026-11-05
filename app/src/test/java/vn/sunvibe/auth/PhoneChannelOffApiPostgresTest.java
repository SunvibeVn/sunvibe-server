package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.external.token.AccessTokenIssuerPort;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationPhoneChallenge;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationPhoneChallengeRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Phone mode {@code off} with no SMS provider (auth-email-access spec 4.5, S-32, S-33, S-37): every phone row of the
 * table answers 403 {@code AUTH_CHANNEL_DISABLED}, for anyone, and nothing happens behind it. A proof issued while the
 * channel was open cannot finish; a session from then lives on.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"app.auth.phone.mode=off",
			"app.sms.provider=none" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhoneChannelOffApiPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String SEND = "/api/v1/auth/registration/phone/send-code";
	private static final String VERIFY = "/api/v1/auth/registration/phone/verify-code";
	private static final String REGISTER = "/api/v1/auth/registration";
	private static final String RESET_SMS = "/api/v1/auth/password-reset/sms";
	private static final String RESET_COMPLETE = "/api/v1/auth/password-reset/complete";
	private static final String ME_SEND = "/api/v1/me/verification/phone/send-code";
	private static final String ME_CONFIRM = "/api/v1/me/verification/phone/confirm-code";
	private static final String ME = "/api/v1/me";
	private static final String REFRESH = "/api/v1/auth/session/refresh";
	private static final String STRONG_PASSWORD = "Sunvibe@2027";
	private static final AtomicLong IDS = new AtomicLong(7_000_000);

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
	private RefreshTokenGenerateUseCase refreshTokens;

	@Autowired
	private RegistrationPhoneChallengeRepository challenges;

	@Autowired
	private PasswordResetTokenDomainRepository resetTokens;

	@Autowired
	private CapturingMessagesConfig messages;

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

	private void assertClosed(HttpResponse<String> response) {
		assertEquals(403, response.statusCode(), "status " + response.statusCode());
		assertEquals("AUTH_CHANNEL_DISABLED", api.body(response).path("code").asString());
	}

	private String json(Map<String, ?> body) throws Exception {
		return api.json().writeValueAsString(body);
	}

	private String accessTokenOf(Created user) {
		return accessTokens.issue(userRepository.findById(UserId.from(user.userId())).orElseThrow());
	}

	@Test
	void off_login_403_noSideEffect() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		assertClosed(api.post(LOGIN, null, null, null, api.loginBody(user.username(), user.password())));
		assertClosed(api.post(LOGIN, null, null, null, api.loginBody(user.username(), "wrong")));

		assertEquals(0, jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
				user.userId()), "no count");
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class,
				user.userId()), "no session");
		assertNull(jdbc.queryForObject("SELECT last_login FROM users WHERE id = ?", Timestamp.class, user.userId()));
	}

	/** The answer says nothing about the number: an unknown one and even an invalid one get the same. */
	@Test
	void login_phoneOff_403_noLookup() throws Exception {
		assertClosed(api.post(LOGIN, null, null, null, api.loginBody("0987000999", "whatever")));
		assertClosed(api.post(LOGIN, null, null, null, api.loginBody("not-a-phone", "whatever")));
	}

	@Test
	void off_registrationSendCode_403_noSideEffect() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();

		assertClosed(api.post(SEND, null, null, null, json(Map.of("phoneNumber", phone))));

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM registration_phone_challenges WHERE phone = ?",
				Integer.class, PhoneNumber.create(phone).key()), "no row");
		assertFalse(messages.hasMessage(PhoneNumber.create(phone).key()), "no SMS");
	}

	/** E-13: a code issued while the channel was open is refused before it is counted or consumed. */
	@Test
	void codeFromOn_cannotFinishInOff() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		long id = seedChallenge(phone, "123456");

		assertClosed(api.post(VERIFY, null, null, null, json(Map.of("phoneNumber", phone, "code", "123456"))));
		assertClosed(api.post(VERIFY, null, null, null, json(Map.of("phoneNumber", phone, "code", "000000"))));

		assertEquals(0, jdbc.queryForObject("SELECT failed_attempts FROM registration_phone_challenges WHERE id = ?",
				Integer.class, id), "not counted");
		assertNull(jdbc.queryForObject("SELECT consumed_at FROM registration_phone_challenges WHERE id = ?",
				Timestamp.class, id), "not consumed");
	}

	@Test
	void tokenFromOn_cannotFinishInOff() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		long id = IDS.incrementAndGet();
		Instant now = AppClock.currentUTC();
		RegistrationPhoneChallenge challenge = RegistrationPhoneChallenge.issue(id, PhoneNumber.create(phone).key(),
				"123456", now, Duration.ofMinutes(10));
		challenge.verify("registration-token-" + id, now, Duration.ofMinutes(15));
		transaction.executeWithoutResult(status -> challenges.save(challenge));

		assertClosed(api.post(REGISTER, null, null, null, json(Map.of("fullName", "Closed Channel", "username", phone,
				"password", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD,
				"registrationToken", "registration-token-" + id))));

		assertNull(jdbc.queryForObject("SELECT registered_at FROM registration_phone_challenges WHERE id = ?",
				Timestamp.class, id), "the token is not used up");
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users WHERE phone_key = ?", Integer.class,
				PhoneNumber.create(phone).key()), "no user");
	}

	@Test
	void off_resetSms_403_noSideEffect() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		assertClosed(api.post(RESET_SMS, null, null, null, json(Map.of("phoneNumber", user.username()))));

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM password_reset_token WHERE user_id = ?", Integer.class,
				user.userId()), "no row");
	}

	/** A PHONE token cannot be known before it is read, so this one check follows the read (A-12). */
	@Test
	void off_resetCompletePhoneToken_403_noSideEffect() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		long id = IDS.incrementAndGet();
		String token = "phone-reset-token-" + id;
		Instant now = AppClock.currentUTC();
		transaction.executeWithoutResult(status -> resetTokens.save(PasswordResetToken.restore(id, PasswordResetToken.hashToken(token),
				user.userId(), VerificationChannel.PHONE, user.username(), now, now.plusSeconds(900), false, null, 0, "123456", 0L)));
		String oldHash = jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, user.userId());

		assertClosed(api.post(RESET_COMPLETE, null, null, null, json(Map.of("resetToken", token, "otpCode", "123456",
				"newPassword", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD))));
		assertClosed(api.post(RESET_COMPLETE, null, null, null, json(Map.of("resetToken", token, "otpCode", "000000",
				"newPassword", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD))));

		assertEquals(0, jdbc.queryForObject("SELECT failed_attempts FROM password_reset_token WHERE id = ?",
				Integer.class, id), "not counted");
		assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT is_used FROM password_reset_token WHERE id = ?",
				Boolean.class, id), "not consumed");
		assertEquals(oldHash, jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, user.userId()));
	}

	@Test
	void off_meVerification_403_noSideEffect() throws Exception {
		Created user = api.createUser(UserStatus.PENDING, "CUSTOMER");
		String token = accessTokenOf(user);

		assertClosed(api.post(ME_SEND, null, null, token, null));
		assertClosed(api.post(ME_CONFIRM, null, null, token, json(Map.of("code", "123456"))));

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM verification_codes WHERE user_id = ?", Integer.class,
				user.userId()), "no row");
	}

	/** S-33: changing the mode ends no session. */
	@Test
	void sessionFromOn_livesInOff() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String secret = transaction.execute(status -> refreshTokens.generateRefreshToken(UserId.from(user.userId()),
				ClientInfo.unknown()));

		assertEquals(200, api.get(ME, accessTokenOf(user)).statusCode(), "GET /me");
		assertEquals(200, api.post(REFRESH, ORIGIN, REFRESH_COOKIE + "=" + secret, null, null).statusCode(), "refresh");
	}

	@Test
	void off_emailRecovery_stillAnswers() throws Exception {
		HttpResponse<String> response = api.post("/api/v1/auth/password-reset/email", null, null, null,
				json(Map.of("email", "nobody@example.com")));

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		assertTrue(response.body().contains("expires"), "the neutral answer");
	}

	private long seedChallenge(String phone, String code) {
		long id = IDS.incrementAndGet();
		transaction.executeWithoutResult(status -> challenges.save(RegistrationPhoneChallenge.issue(id,
				PhoneNumber.create(phone).key(), code, AppClock.currentUTC(), Duration.ofMinutes(10))));
		return id;
	}
}
