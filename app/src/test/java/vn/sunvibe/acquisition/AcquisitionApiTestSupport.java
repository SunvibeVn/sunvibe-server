package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.Username;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Shared helpers of the acquisition API tests (end to end: {@code RANDOM_PORT}, {@link HttpClient}, real security
 * chain). The user helpers are copies of those of {@code PropertyCreateCharacterizationTest}: users are saved through
 * the auth domain repository and log in through {@code POST /api/v1/auth/login}, so a staff call carries a real access
 * token. Tokens and passwords are never printed.
 */
final class AcquisitionApiTestSupport {

	static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

	/** Usernames of created users; the 08 prefix keeps them apart from the phones the intake tests submit. */
	private static final AtomicInteger USERNAME_SEQUENCE = new AtomicInteger(1);

	private final int port;
	private final UserDomainRepository userRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	private final IdGenerator<String> ulidGenerator;
	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final JsonMapper json = JsonMapper.builder().build();

	AcquisitionApiTestSupport(int port, UserDomainRepository userRepository, BCryptPasswordEncoder passwordEncoder,
			IdGenerator<String> ulidGenerator) {
		this.port = port;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.ulidGenerator = ulidGenerator;
	}

	/** {@code secret} is the password before {@link #logIn}, the access token after. */
	record TestUser(String userId, String username, String secret) {
		@Override
		public String toString() {
			// Keeps the token out of any assertion message or log line
			return "TestUser[" + username + "]";
		}
	}

	/** One HTTP answer: status, parsed body (missing node when empty), headers, and the raw text for messages. */
	record Result(int status, JsonNode body, HttpResponse<String> response, String rawBody) {

		String header(String name) {
			return response.headers().firstValue(name).orElse(null);
		}
	}

	JsonMapper json() {
		return json;
	}

	HttpClient http() {
		return http;
	}

	URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	/** Same server through the IPv4 loopback address, so the client IP the server sees is known. */
	URI ipv4Uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

	/** Saved through the auth repository; PENDING keeps the phone unverified. The password is kept for logIn. */
	TestUser createUser(UserStatus status, String... roles) {
		String username = "08" + String.format("%08d", USERNAME_SEQUENCE.getAndIncrement());
		String password = UUID.randomUUID().toString();
		Instant now = AppClock.currentUTC();

		AppUser user = AppUser.create(
				UserId.create(ulidGenerator.generateId()),
				"W9 " + String.join(" ", roles),
				Username.create(username),
				null,
				Password.create(passwordEncoder.encode(password)),
				ProfileImage.create(null),
				null,
				new HashSet<>(Set.of(roles)),
				now);
		if (status != UserStatus.PENDING) {
			user.verifyPhone(now);
			user.setUserStatus(status);
		}
		AppUser saved = userRepository.save(user);
		return new TestUser(saved.getId().getValue(), username, password);
	}

	/** The same user with an access token in place of the password. */
	TestUser logIn(TestUser user) throws Exception {
		HttpResponse<String> login = http.send(
				HttpRequest.newBuilder(uri("/api/v1/auth/login"))
						.timeout(REQUEST_TIMEOUT)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(
								json.writeValueAsString(Map.of("username", user.username(), "password", user.secret()))))
						.build(),
				HttpResponse.BodyHandlers.ofString());
		// The login body holds the token, so it is never put in the failure message
		assertEquals(200, login.statusCode(), "login failed for " + user + ", status " + login.statusCode());
		String accessToken = json.readTree(login.body()).path("accessToken").asString();
		assertFalse(accessToken.isBlank(), "login returned no access token for " + user);

		return new TestUser(user.userId(), user.username(), accessToken);
	}

	/** Sends the request and parses the answer; a body that is not JSON is kept only as raw text. */
	Result send(HttpRequest request) throws Exception {
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		String raw = response.body();
		JsonNode parsed;
		try {
			parsed = raw == null || raw.isBlank() ? json.missingNode() : json.readTree(raw);
		} catch (RuntimeException notJson) {
			parsed = json.missingNode();
		}
		return new Result(response.statusCode(), parsed, response, "status " + response.statusCode() + ", body " + raw);
	}

	/** A request to the path, with the caller's bearer token when a caller is given. */
	HttpRequest.Builder request(TestUser caller, String path) {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(REQUEST_TIMEOUT);
		if (caller != null) {
			request.header("Authorization", "Bearer " + caller.secret());
		}
		return request;
	}

	// ==================== Staff calls (acquisition-owner-lead-queue spec 6) ====================

	/** GET as the caller ({@code null}: without a token). */
	Result get(TestUser caller, String path) throws Exception {
		return send(request(caller, path).GET().build());
	}

	/** POST of the body as JSON, as the caller ({@code null}: without a token). */
	Result post(TestUser caller, String path, Object body) throws Exception {
		return send(request(caller, path)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
				.build());
	}

	/** PUT of the body as JSON, as the caller ({@code null}: without a token). */
	Result put(TestUser caller, String path, Object body) throws Exception {
		return send(request(caller, path)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
				.build());
	}

	/** DELETE as the caller ({@code null}: without a token). */
	Result delete(TestUser caller, String path) throws Exception {
		return send(request(caller, path).DELETE().build());
	}

	/**
	 * Sends a valid owner intake for the ward of Đà Nẵng through the public endpoint, as an owner would, so the lead
	 * gets its consent, its SUBMITTED activity, its duplicate candidates and its auto-assignment like any other.
	 * The address must differ between calls with the same phone, or the 5-minute repeat rule returns the first lead.
	 *
	 * @return the request code of the lead
	 */
	String submitLead(String wardCode, String phone, String addressDetail) throws Exception {
		Result result = send(HttpRequest.newBuilder(uri("/api/v1/public/owner-intake-requests"))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						json.writeValueAsString(intakeBody(wardCode, phone, addressDetail)), StandardCharsets.UTF_8))
				.build());
		assertEquals(202, result.status(), result.rawBody());
		return result.body().path("requestCode").asString();
	}

	/** The example body of acquisition-owner-intake spec 6.3 for the ward, phone and address. */
	static Map<String, Object> intakeBody(String wardCode, String phone, String addressDetail) {
		Map<String, Object> price = new LinkedHashMap<>();
		price.put("amount", 15_000_000);
		price.put("currency", "VND");
		price.put("unit", "MONTH");
		Map<String, Object> consent = new LinkedHashMap<>();
		consent.put("accepted", true);
		consent.put("noticeVersion", "owner-intake-2026-10");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("contactName", "Nguyễn Thị Lan");
		body.put("phone", phone);
		body.put("email", "lan@example.com");
		body.put("intent", "LONG_TERM_RENT");
		body.put("provinceCode", "48");
		body.put("wardCode", wardCode);
		body.put("addressDetail", addressDetail);
		body.put("latitude", 16.0678);
		body.put("longitude", 108.2208);
		body.put("propertyType", "TWO_BEDROOM");
		body.put("askingPrice", price);
		body.put("availableFrom", "2026-11-01");
		body.put("notes", "Có nội thất");
		body.put("preferredCallbackTime", "Buổi tối");
		body.put("consent", consent);
		return body;
	}

	/** The field names of a JSON object, in order. */
	static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.propertyNames().forEach(names::add);
		return names;
	}

	/** The lead id of a request code. */
	static String leadId(JdbcTemplate jdbc, String requestCode) {
		return jdbc.queryForObject("select id from owner_leads where request_code = ?", String.class, requestCode);
	}

	/** Deletes the leads with every row that points to them (history, logs, candidates, keys) and their consents. */
	static void deleteLeads(JdbcTemplate jdbc, Collection<String> leadIds) {
		for (String id : leadIds) {
			jdbc.update("delete from owner_intake_idempotency_keys where lead_id = ?", id);
			jdbc.update("delete from owner_lead_duplicate_candidates where lead_id = ? or candidate_lead_id = ?", id, id);
			jdbc.update("delete from owner_lead_activities where lead_id = ?", id);
			jdbc.update("delete from owner_lead_access_logs where lead_id = ?", id);
			jdbc.update("update owner_leads set duplicate_of_lead_id = null where id = ? or duplicate_of_lead_id = ?",
					id, id);
		}
		for (String id : leadIds) {
			String consentId = jdbc.queryForList("select consent_record_id from owner_leads where id = ?", String.class, id)
					.stream().findFirst().orElse(null);
			jdbc.update("delete from owner_leads where id = ?", id);
			if (consentId != null) {
				jdbc.update("delete from consent_records where id = ?", consentId);
			}
		}
	}

	/** Deletes the users (their roles and refresh tokens cascade) and the audit rows their logins wrote. */
	static void deleteUsers(JdbcTemplate jdbc, Collection<String> userIds) {
		for (String id : userIds) {
			jdbc.update("delete from auth_audit_events where user_id = ?", id);
			jdbc.update("delete from users where id = ?", id);
		}
	}

	/** The body of the security chain's 401 and 403 (ADR-010 leaves it as it was): exactly error, message, path. */
	static void assertSecurityBody(Result result, int status) {
		assertEquals(status, result.status(), result.rawBody());
		assertEquals(List.of("error", "message", "path"), fieldNames(result.body()), result.rawBody());
	}
}
