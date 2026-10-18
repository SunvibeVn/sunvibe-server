package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The generated API contract of the v1 auth and me endpoints (auth-browser-session spec §3.11; W10-14), read from
 * {@code /v3/api-docs} on the H2 test profile like {@code OpenApiExportTest}, so no database is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthV1OpenApiTest {

	private static final String SECURITY_ERROR_REF = "#/components/schemas/SecurityError";

	@Value("${local.server.port}")
	private int port;

	private JsonNode contract;

	@BeforeAll
	void readContract() throws Exception {
		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpResponse<String> response = http.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			contract = JsonMapper.builder().build().readTree(response.body());
		}
	}

	/** W10-14 */
	@Test
	void openApi_declaresBearerAuthAndRefreshCookie() {
		JsonNode schemes = contract.path("components").path("securitySchemes");

		JsonNode bearer = schemes.path("bearerAuth");
		assertEquals("http", bearer.path("type").asString());
		assertEquals("bearer", bearer.path("scheme").asString());
		assertEquals("JWT", bearer.path("bearerFormat").asString());

		JsonNode cookie = schemes.path("refreshCookie");
		assertEquals("apiKey", cookie.path("type").asString());
		assertEquals("cookie", cookie.path("in").asString());
		assertEquals("sv_refresh", cookie.path("name").asString());
		assertTrue(cookie.path("description").asString().contains("__Secure-sv_refresh"), "names the prod cookie");

		assertTrue(contract.path("security").isMissingNode() || contract.path("security").isEmpty(),
				"no global security requirement");
	}

	/** W10-14: every /api/v1/me operation requires the Bearer token and documents the chain's 401/403. */
	@Test
	void openApi_mePaths_requireBearerAuth() {
		List<Map.Entry<String, JsonNode>> operations = operationsUnder("/api/v1/me");
		assertEquals(7, operations.size(), "the seven operations of spec §3.1 rows 10-16");

		for (Map.Entry<String, JsonNode> operation : operations) {
			assertEquals(List.of("bearerAuth"), securityNames(operation.getValue()), operation.getKey());
			for (String status : List.of("401", "403")) {
				JsonNode content = operation.getValue().path("responses").path(status).path("content");
				assertFalse(content.isMissingNode() || content.isEmpty(), operation.getKey() + " documents " + status);
				content.forEach(media -> assertEquals(SECURITY_ERROR_REF, media.path("schema").path("$ref").asString(),
						operation.getKey() + " " + status));
			}
		}
	}

	/** W10-14: the public auth operations take no access token; refresh and logout take the refresh cookie. */
	@Test
	void openApi_authPaths_requireNoBearer() {
		List<Map.Entry<String, JsonNode>> operations = operationsUnder("/api/v1/auth");
		assertEquals(9, operations.size(), "the nine operations of spec §3.1 rows 1-9");

		for (Map.Entry<String, JsonNode> operation : operations) {
			List<String> names = securityNames(operation.getValue());
			assertFalse(names.contains("bearerAuth"), operation.getKey());
			if (operation.getKey().contains("/api/v1/auth/session/")) {
				assertEquals(List.of("refreshCookie"), names, operation.getKey());
			} else {
				assertTrue(names.isEmpty(), operation.getKey());
			}
		}
	}

	/** W10-14, spec §3.1 row 6: registration creates an account, 201. */
	@Test
	void openApi_registration_documents201() {
		JsonNode responses = contract.path("paths").path("/api/v1/auth/registration").path("post").path("responses");

		assertTrue(responses.has("201"), "documents 201");
		assertFalse(responses.has("200"), "no 200");
	}

	/**
	 * Spec §3.1 rate-limit column, §3.4, §3.8 (W10-11): every rate-limited operation documents the per-IP 429
	 * {@code RATE_LIMITED} with {@code Retry-After}; the send-code operations name their code limits in the same 429.
	 * Logout, logout-all and GET /api/v1/me have no limit and document no 429.
	 */
	@Test
	void openApi_rateLimitedOperations_document429() {
		List<String> limited = List.of(
				"post /api/v1/auth/login",
				"post /api/v1/auth/session/refresh",
				"post /api/v1/auth/registration/phone/send-code",
				"post /api/v1/auth/registration/phone/verify-code",
				"post /api/v1/auth/registration",
				"post /api/v1/auth/password-reset/email",
				"post /api/v1/auth/password-reset/sms",
				"post /api/v1/auth/password-reset/complete",
				"post /api/v1/me/verification/phone/send-code",
				"post /api/v1/me/verification/phone/confirm-code",
				"post /api/v1/me/verification/email/send-code",
				"post /api/v1/me/verification/email/confirm-code",
				"post /api/v1/me/password/change");
		for (String key : limited) {
			JsonNode tooMany = operation(key).path("responses").path("429");
			assertFalse(tooMany.isMissingNode(), key + " documents 429");
			String description = tooMany.path("description").asString();
			assertTrue(description.contains("RATE_LIMITED"), key + ": " + description);
			assertTrue(tooMany.path("headers").has("Retry-After"), key + " documents Retry-After");
			assertFalse(tooMany.path("content").isMissingNode() || tooMany.path("content").isEmpty(),
					key + " documents the body");
			if (key.endsWith("/send-code")) {
				assertTrue(description.contains("CODE_RESEND_TOO_SOON"), key + ": " + description);
				assertTrue(description.contains("CODE_DAILY_LIMIT"), key + ": " + description);
			}
		}

		for (String key : List.of("post /api/v1/auth/session/logout", "post /api/v1/me/sessions/logout-all",
				"get /api/v1/me")) {
			JsonNode responses = operation(key).path("responses");
			assertFalse(responses.isMissingNode(), key + " exists");
			assertFalse(responses.has("429"), key + " has no rate limit");
		}
	}

	/** W10-60: the legacy auth paths are absent from the contract. */
	@Test
	void openApi_legacyAuthPaths_areAbsent() {
		for (String prefix : List.of("/api/auth", "/api/users/command", "/api/user/query")) {
			assertTrue(operationsUnder(prefix).isEmpty(), prefix + " still documented");
		}
	}

	/** Spec §3.11: every v1 operation carries its own explicit, unique operationId. */
	@Test
	void openApi_v1OperationIds_areUnchanged() {

		Map<String, String> v1 = new java.util.LinkedHashMap<>();
		v1.put("post /api/v1/auth/login", "authLogin");
		v1.put("post /api/v1/auth/session/refresh", "authSessionRefresh");
		v1.put("post /api/v1/auth/session/logout", "authSessionLogout");
		v1.put("post /api/v1/auth/registration/phone/send-code", "authRegistrationSendCode");
		v1.put("post /api/v1/auth/registration/phone/verify-code", "authRegistrationVerifyCode");
		v1.put("post /api/v1/auth/registration", "authRegister");
		v1.put("post /api/v1/auth/password-reset/email", "authPasswordResetByEmail");
		v1.put("post /api/v1/auth/password-reset/sms", "authPasswordResetBySms");
		v1.put("post /api/v1/auth/password-reset/complete", "authPasswordResetComplete");
		v1.put("get /api/v1/me", "getMe");
		v1.put("post /api/v1/me/verification/phone/send-code", "meSendPhoneCode");
		v1.put("post /api/v1/me/verification/phone/confirm-code", "meConfirmPhoneCode");
		v1.put("post /api/v1/me/verification/email/send-code", "meSendEmailCode");
		v1.put("post /api/v1/me/verification/email/confirm-code", "meConfirmEmailCode");
		v1.put("post /api/v1/me/password/change", "meChangePassword");
		v1.put("post /api/v1/me/sessions/logout-all", "meLogoutAll");
		assertEquals(16, v1.size());
		v1.forEach((key, id) -> assertEquals(id, operation(key).path("operationId").asString(), key));
	}

	/** The 401/403 bodies of the security chain reference this schema by name; it must exist in the contract. */
	@Test
	void openApi_securityErrorSchema_exists() {
		assertFalse(contract.path("components").path("schemas").path("SecurityError").isMissingNode(),
				"components.schemas.SecurityError");
	}

	/** The operation for {@code "method path"} (lower-case method). */
	private JsonNode operation(String key) {
		String[] parts = key.split(" ");
		return contract.path("paths").path(parts[1]).path(parts[0]);
	}

	/** Each operation keyed {@code "METHOD path"}, for the paths equal to or under {@code prefix}. */
	private List<Map.Entry<String, JsonNode>> operationsUnder(String prefix) {
		List<Map.Entry<String, JsonNode>> operations = new ArrayList<>();
		for (Map.Entry<String, JsonNode> path : contract.path("paths").properties()) {
			if (path.getKey().equals(prefix) || path.getKey().startsWith(prefix + "/")) {
				for (Map.Entry<String, JsonNode> method : path.getValue().properties()) {
					operations.add(Map.entry(method.getKey().toUpperCase() + " " + path.getKey(), method.getValue()));
				}
			}
		}
		return operations;
	}

	private static List<String> securityNames(JsonNode operation) {
		List<String> names = new ArrayList<>();
		operation.path("security").forEach(requirement -> names.addAll(requirement.propertyNames()));
		return names;
	}
}
