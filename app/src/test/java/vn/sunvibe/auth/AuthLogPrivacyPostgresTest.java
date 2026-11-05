package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.audit.AuthAuditWriter;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * One scripted run of every flow with synthetic identifiers, then nothing may show them: not the log, not an audit subject,
 * not the body of a refusal (auth-email-access 4.10, SV-009). A wrong password and an unknown account are part of the run,
 * because those are the paths that quote what the client typed.
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
@ExtendWith(OutputCaptureExtension.class)
class AuthLogPrivacyPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String STRONG_PASSWORD = "Sunvibe@2027";
	private static final String WRONG_PASSWORD = "Wrong@Pass1";
	private static final String NEW_PASSWORD = "Sunvibe@2028";
	private static final String RESET_COMPLETE = "/api/v1/auth/password-reset/complete";

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
	private AuthAuditWriter auditWriter;

	@Autowired
	private JdbcTemplate jdbc;

	private AuthV1ApiTestSupport api;
	private final List<String> identifiers = new ArrayList<>();
	private final List<String> refusals = new ArrayList<>();
	private String output;

	@BeforeAll
	void runEveryFlow(CapturedOutput captured) throws Exception {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		phoneFlows();
		emailFlows();
		unknownIdentifiers();
		assertTrue(auditWriter.awaitIdle(Duration.ofSeconds(10)));
		output = captured.getAll();
	}

	@AfterAll
	void tearDown() {
		api.close();
	}

	private String json(Map<String, ?> body) throws Exception {
		return api.json().writeValueAsString(body);
	}

	private HttpResponse<String> post(String path, String bearer, Map<String, ?> body) throws Exception {
		HttpResponse<String> response = api.post(path, null, null, bearer, json(body));
		if (response.statusCode() >= 400) {
			refusals.add(response.body());
		}
		return response;
	}

	private String freshPhone() {
		String phone = AuthV1ApiTestSupport.nextPhone();
		identifiers.add(phone);
		identifiers.add(PhoneNumber.create(phone).key());
		return phone;
	}

	private String freshEmail(String label) {
		String email = "privacy-" + label + "-" + UUID.randomUUID() + "@example.com";
		identifiers.add(email);
		return email;
	}

	private void phoneFlows() throws Exception {
		String phone = freshPhone();
		assertEquals(200, post("/api/v1/auth/registration/phone/send-code", null, Map.of("phoneNumber", phone)).statusCode());
		HttpResponse<String> verified = post("/api/v1/auth/registration/phone/verify-code", null,
				Map.of("phoneNumber", phone, "code", messages.lastCode(phone)));
		assertEquals(200, verified.statusCode());
		assertEquals(201, post("/api/v1/auth/registration", null, Map.of("fullName", "Privacy Phone", "username", phone,
				"password", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD,
				"registrationToken", api.body(verified).path("registrationToken").asString())).statusCode());

		post(LOGIN, null, Map.of("username", phone, "password", WRONG_PASSWORD));
		HttpResponse<String> login = post(LOGIN, null, Map.of("username", phone, "password", STRONG_PASSWORD));
		assertEquals(200, login.statusCode());
		String bearer = api.body(login).path("accessToken").asString();
		post("/api/v1/me/verification/phone/send-code", bearer, Map.of());
		post("/api/v1/me/verification/phone/confirm-code", bearer, Map.of("code", messages.lastCode(phone)));

		HttpResponse<String> started = post("/api/v1/auth/password-reset/sms", null, Map.of("phoneNumber", phone));
		assertEquals(200, started.statusCode());
		String token = api.body(started).path("resetToken").asString();
		post(RESET_COMPLETE, null, Map.of("resetToken", token, "otpCode", "000000", "newPassword", NEW_PASSWORD,
				"confirmPassword", NEW_PASSWORD));
		assertEquals(204, post(RESET_COMPLETE, null, Map.of("resetToken", token, "otpCode", messages.lastCode(phone),
				"newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)).statusCode());

		HttpResponse<String> again = post(LOGIN, null, Map.of("username", phone, "password", NEW_PASSWORD));
		assertEquals(200, again.statusCode());
		String signedIn = api.body(again).path("accessToken").asString();
		String bound = freshEmail("bound");
		post("/api/v1/me/email/send-code", signedIn, Map.of("newEmail", bound, "currentPassword", WRONG_PASSWORD));
		assertEquals(200, post("/api/v1/me/email/send-code", signedIn, Map.of("newEmail", bound, "currentPassword", NEW_PASSWORD))
				.statusCode());
		post("/api/v1/me/email/confirm-code", signedIn, Map.of("newEmail", bound, "code", "000000"));
		assertEquals(200, post("/api/v1/me/email/confirm-code", signedIn, Map.of("newEmail", bound, "code",
				messages.lastCode(bound))).statusCode());
	}

	private void emailFlows() throws Exception {
		String email = freshEmail("account");
		assertEquals(200, post("/api/v1/auth/registration/email/send-code", null, Map.of("email", email)).statusCode());
		post("/api/v1/auth/registration/email/verify-code", null, Map.of("email", email, "code", "000000"));
		HttpResponse<String> verified = post("/api/v1/auth/registration/email/verify-code", null,
				Map.of("email", email, "code", messages.lastCode(email)));
		assertEquals(200, verified.statusCode());
		assertEquals(201, post("/api/v1/auth/registration/email", null, Map.of("email", email, "fullName", "Privacy Email",
				"password", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD,
				"registrationToken", api.body(verified).path("registrationToken").asString())).statusCode());

		post(LOGIN, null, Map.of("email", email, "password", WRONG_PASSWORD));
		HttpResponse<String> login = post(LOGIN, null, Map.of("email", email, "password", STRONG_PASSWORD));
		assertEquals(200, login.statusCode());
		String bearer = api.body(login).path("accessToken").asString();
		post("/api/v1/me/password/change", bearer, Map.of("currentPassword", WRONG_PASSWORD, "newPassword", NEW_PASSWORD,
				"confirmPassword", NEW_PASSWORD));
		HttpResponse<String> changed = post("/api/v1/me/password/change", bearer, Map.of("currentPassword", STRONG_PASSWORD,
				"newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD));
		assertEquals(200, changed.statusCode());
		api.post("/api/v1/me/sessions/logout-all", null, null, api.body(changed).path("accessToken").asString(), null);

		assertEquals(200, post("/api/v1/auth/password-reset/email", null, Map.of("email", email)).statusCode());
		String token = messages.awaitResetToken(email);
		assertEquals(204, post(RESET_COMPLETE, null, Map.of("resetToken", token, "otpCode", messages.awaitCode(email),
				"newPassword", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD)).statusCode());
	}

	private void unknownIdentifiers() throws Exception {
		post(LOGIN, null, Map.of("email", freshEmail("unknown"), "password", WRONG_PASSWORD));
		post(LOGIN, null, Map.of("username", freshPhone(), "password", WRONG_PASSWORD));
		post("/api/v1/auth/password-reset/email", null, Map.of("email", freshEmail("unknown-reset")));
		post("/api/v1/auth/password-reset/sms", null, Map.of("phoneNumber", freshPhone()));
		String refused = "privacy.refused" + UUID.randomUUID();
		identifiers.add(refused);
		post("/api/v1/auth/password-reset/email", null, Map.of("email", refused));
		post(LOGIN, null, Map.of("email", refused, "password", WRONG_PASSWORD));
	}

	@Test
	void logs_noPhoneNoEmail_acrossFlows() {
		assertFalse(identifiers.isEmpty());
		assertFalse(output.isEmpty(), "the log was not captured");
		assertTrue(output.contains("Login"), "the log was not captured");
		for (String identifier : identifiers) {
			assertFalse(output.contains(identifier), "an identifier in the log");
		}
	}

	@Test
	void audit_subjectMasked_everyEvent() {
		List<String> subjects = jdbc.queryForList("SELECT subject FROM auth_audit_events WHERE subject IS NOT NULL", String.class);

		assertFalse(subjects.isEmpty(), "the run wrote no subject");
		for (String subject : subjects) {
			assertTrue(subject.contains("*"), "a subject without a mask");
			for (String identifier : identifiers) {
				assertFalse(subject.contains(identifier), "an identifier in an audit subject");
			}
		}
	}

	@Test
	void exceptions_quoteNoIdentifier() {
		assertFalse(refusals.isEmpty(), "the run was refused nowhere");
		for (String refusal : refusals) {
			for (String identifier : identifiers) {
				assertFalse(refusal.contains(identifier), "an identifier in a refusal");
			}
		}
	}
}
