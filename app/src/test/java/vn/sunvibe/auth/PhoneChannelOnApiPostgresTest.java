package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

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
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Phone mode {@code on} (auth-email-access S-35, S-36, D4-9): one number however it is written, and a verified email
 * with an unproven phone does not open the account to whoever holds that number.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhoneChannelOnApiPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String SEND = "/api/v1/auth/registration/phone/send-code";
	private static final String VERIFY = "/api/v1/auth/registration/phone/verify-code";
	private static final String REGISTER = "/api/v1/auth/registration";
	private static final String RESET_SMS = "/api/v1/auth/password-reset/sms";
	private static final String STRONG_PASSWORD = "Sunvibe@2027";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private CapturingMessagesConfig messages;

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

	private static String international(String national) {
		return "+84 " + national.substring(1, 4) + " " + national.substring(4, 7) + " " + national.substring(7);
	}

	private static String countryCode(String national) {
		return "84" + national.substring(1);
	}

	@Test
	void on_variantsReachOneAccount() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		for (String variant : List.of(user.username(), international(user.username()), countryCode(user.username()))) {
			HttpResponse<String> response = api.post(LOGIN, null, null, null, api.loginBody(variant, user.password()));

			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			assertEquals(user.userId(), api.body(response).path("user").path("userId").asString());
		}
	}

	@Test
	void on_reRegisterByVariant_refused() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		HttpResponse<String> response = api.post(SEND, null, null, null,
				json(Map.of("phoneNumber", international(user.username()))));

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		assertEquals("VALIDATION_FAILED", api.body(response).path("code").asString());
	}

	/** Sign-up by a variant ends on the one key, with the national form stored for display. */
	@Test
	void on_registerByVariant_storesKeyAndNational() throws Exception {
		String national = AuthV1ApiTestSupport.nextPhone();
		String variant = international(national);
		String key = PhoneNumber.create(national).key();
		assertEquals(200, api.post(SEND, null, null, null, json(Map.of("phoneNumber", variant))).statusCode());
		HttpResponse<String> verified = api.post(VERIFY, null, null, null,
				json(Map.of("phoneNumber", countryCode(national), "code", messages.lastCode(key))));
		assertEquals(200, verified.statusCode(), "status " + verified.statusCode());

		HttpResponse<String> registered = api.post(REGISTER, null, null, null, json(Map.of("fullName", "Variant User",
				"username", national, "password", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD,
				"registrationToken", api.body(verified).path("registrationToken").asString())));

		assertEquals(201, registered.statusCode(), "status " + registered.statusCode());
		assertEquals(national, jdbc.queryForObject("SELECT username FROM users WHERE phone_key = ?", String.class, key));
	}

	/** D4-9: whoever holds the number has proven nothing, and the account has a mailbox that was proven. */
	@Test
	void on_verifiedEmailUnverifiedPhone_noLogin_noSmsReset() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String email = "bound-" + user.userId().toLowerCase() + "@example.com";
		jdbc.update("UPDATE users SET email = ?, email_key = ?, email_verified_at = ?, phone_verified_at = NULL"
				+ " WHERE id = ?", email, email, Timestamp.from(AppClock.currentUTC()), user.userId());

		HttpResponse<String> login = api.post(LOGIN, null, null, null, api.loginBody(user.username(), user.password()));
		api.post(RESET_SMS, null, null, null, json(Map.of("phoneNumber", user.username())));

		assertEquals(401, login.statusCode(), "status " + login.statusCode());
		assertEquals(0, jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
				user.userId()), "not counted");
		assertFalse(messages.hasMessage(PhoneNumber.create(user.username()).key()), "no SMS");
	}

	/** S-15: failed attempts and the temporary lock belong to the account, whichever channel they came through. */
	@Test
	void login_failuresSharedAcrossChannels() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String email = "both-" + user.userId().toLowerCase() + "@example.com";
		jdbc.update("UPDATE users SET email = ?, email_key = ?, email_verified_at = ? WHERE id = ?", email, email,
				Timestamp.from(AppClock.currentUTC()), user.userId());

		for (int i = 0; i < 2; i++) {
			assertEquals(401, api.post(LOGIN, null, null, null, api.loginBody(user.username(), "wrong")).statusCode());
			assertEquals(401, api.post(LOGIN, null, null, null,
					json(Map.of("email", email, "password", "wrong"))).statusCode());
		}
		HttpResponse<String> fifth = api.post(LOGIN, null, null, null, api.loginBody(user.username(), "wrong"));

		assertEquals(423, fifth.statusCode(), "status " + fifth.statusCode());
		assertEquals(423, api.post(LOGIN, null, null, null,
				json(Map.of("email", email, "password", user.password()))).statusCode(), "the lock is shared too");
	}

	@Test
	void on_refusedUsername_400() throws Exception {
		HttpResponse<String> response = api.post(LOGIN, null, null, null, api.loginBody("0123456789", "whatever"));

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		assertEquals("VALIDATION_FAILED", api.body(response).path("code").asString());
		assertEquals("username", api.body(response).path("errors").get(0).path("field").asString());
		assertTrue(api.body(response).path("errors").size() == 1);
	}
}
