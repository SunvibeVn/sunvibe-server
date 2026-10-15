package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.Username;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

/**
 * Role admin API {@code /api/v1/staff/users/{userId}/roles} end to end on PostgreSQL (wave W6, W6-9; auth-roles
 * brief US-4, US-5, BR-9..BR-11, E-4..E-7), plus self-registration roles (US-1, BR-3) and the SUPER_ADMIN role
 * hierarchy on a method rule and on the HTTP rule {@code /features/admin/**} (US-5, BR-4).
 * Q-5 = 403 for a self change, Q-6 = revoking OWNER allowed, Q-7 = no roles left allowed.
 * <p>
 * The login rate limit is off and registration does not need a phone token, so this class gets its own context
 * (and database). Tokens and passwords are never printed.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"app.security.registration.require-phone-verification=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAdminApiTest {

	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final String UNKNOWN_USER_ID = "01NOSUCHUSER00000000000000";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final JsonMapper json = JsonMapper.builder().build();

	private Caller superAdmin;
	private Caller admin;
	private Caller sale;
	private Caller tenant;

	private record Caller(String userId, String username, String accessToken, String refreshCookie) {
		@Override
		public String toString() {
			return "Caller[" + username + "]";
		}
	}

	private record Result(int status, JsonNode body, String rawBody) {
	}

	@BeforeAll
	void logIn() throws Exception {
		superAdmin = logIn(createUser(UserStatus.ACTIVE, "SUPER_ADMIN"));
		admin = logIn(createUser(UserStatus.ACTIVE, "ADMIN"));
		sale = logIn(createUser(UserStatus.ACTIVE, "SALE"));
		tenant = logIn(createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER"));
	}

	@AfterAll
	void closeHttpClient() {
		http.close();
	}

	// ==================== GET ====================

	@Test
	void getRoles_admin_returns200WithSortedRoles() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER").userId();

		Result result = send("GET", admin, rolesPath(target));

		assertEquals(200, result.status(), result.rawBody());
		assertEquals(target, result.body().path("userId").asString());
		assertEquals(List.of("CUSTOMER", "TENANT"), strings(result.body().path("roles")));
	}

	/** US-5, BR-4 (W6-4): SUPER_ADMIN passes the ADMIN method rule through the role hierarchy. */
	@Test
	void getRoles_superAdmin_returns200() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		assertEquals(200, send("GET", superAdmin, rolesPath(target)).status());
	}

	/** E-7 */
	@Test
	void getRoles_unknownUser_returns404() throws Exception {
		Result result = send("GET", admin, rolesPath(UNKNOWN_USER_ID));

		assertEquals(404, result.status(), result.rawBody());
		assertFalse(result.rawBody().contains(UNKNOWN_USER_ID), "the id is not echoed");
	}

	/** E-4, T-021: a SALE or TENANT caller cannot use the role API. */
	@Test
	void getRoles_saleOrTenant_returns403() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		assertForbidden(send("GET", sale, rolesPath(target)));
		assertForbidden(send("GET", tenant, rolesPath(target)));
	}

	@Test
	void getRoles_withoutToken_returns401() throws Exception {
		assertEquals(401, send("GET", null, rolesPath(UNKNOWN_USER_ID)).status());
	}

	// ==================== PUT (grant) ====================

	/** US-4 AC-1, AC-4: ADMIN grants a new role; audited with actor, target and role. */
	@Test
	void grant_adminGrantsSale_returns204StoresAndAudits() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		Result result = send("PUT", admin, rolePath(target, "SALE"));

		assertEquals(204, result.status(), result.rawBody());
		assertEquals(Set.of("CUSTOMER", "SALE"), dbRoles(target));
		assertEquals(List.of("role=SALE;actorUserId=" + admin.userId()), auditDetails("ROLE_GRANTED", target));
	}

	/** US-4 AC-3: idempotent, the repeat is not audited again. */
	@Test
	void grant_roleAlreadyHeld_returns204AndAuditsOnce() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		assertEquals(204, send("PUT", admin, rolePath(target, "OWNER")).status());
		assertEquals(204, send("PUT", admin, rolePath(target, "OWNER")).status());

		assertEquals(Set.of("CUSTOMER", "OWNER"), dbRoles(target));
		assertEquals(1, auditDetails("ROLE_GRANTED", target).size());
	}

	/** US-4 AC-2, E-4 */
	@Test
	void grant_adminGrantsAdmin_returns403() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		assertForbidden(send("PUT", admin, rolePath(target, "ADMIN")));
		assertEquals(Set.of("CUSTOMER"), dbRoles(target));
	}

	/** US-4 AC-2 */
	@Test
	void grant_superAdminGrantsAdmin_returns204() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		assertEquals(204, send("PUT", superAdmin, rolePath(target, "ADMIN")).status());
		assertEquals(Set.of("CUSTOMER", "ADMIN"), dbRoles(target));
	}

	/** BR-9, E-5, Q-5 */
	@Test
	void grant_toSelf_returns403() throws Exception {
		assertForbidden(send("PUT", admin, rolePath(admin.userId(), "SALE")));
		assertEquals(Set.of("ADMIN"), dbRoles(admin.userId()));
	}

	/** E-6: SUPER_ADMIN, old roles and unknown names -> 400 on field role. */
	@Test
	void grant_roleNotGrantable_returns400() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		for (String role : List.of("SUPER_ADMIN", "LANDLORD", "KING")) {
			Result result = send("PUT", superAdmin, rolePath(target, role));

			assertEquals(400, result.status(), role + ": " + result.rawBody());
			assertEquals("Validation failed.", result.body().path("message").asString());
			assertEquals("role", result.body().path("errors").get(0).path("field").asString());
		}
		assertEquals(Set.of("CUSTOMER"), dbRoles(target));
	}

	/** E-7 */
	@Test
	void grant_unknownUser_returns404() throws Exception {
		assertEquals(404, send("PUT", admin, rolePath(UNKNOWN_USER_ID, "SALE")).status());
	}

	/** BR-10, E-7 */
	@Test
	void grant_lockedUser_returns422() throws Exception {
		String target = createUser(UserStatus.LOCK, "CUSTOMER").userId();

		Result result = send("PUT", admin, rolePath(target, "SALE"));

		assertEquals(422, result.status(), result.rawBody());
		assertEquals("userId", result.body().path("errors").get(0).path("field").asString());
		assertFalse(result.rawBody().contains(target), "the id is not echoed");
		assertEquals(Set.of("CUSTOMER"), dbRoles(target));
	}

	/** E-4, T-021 */
	@Test
	void grant_saleOrTenantCaller_returns403() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "CUSTOMER").userId();

		assertForbidden(send("PUT", sale, rolePath(target, "SALE")));
		assertForbidden(send("PUT", tenant, rolePath(target, "OWNER")));
		assertEquals(Set.of("CUSTOMER"), dbRoles(target));
	}

	// ==================== DELETE (revoke) ====================

	/** US-4 AC-4, AC-5, BR-11: revoke ends every session of the target; the old refresh token is refused. */
	@Test
	void revoke_heldRole_returns204AuditsAndEndsTheTargetsSessions() throws Exception {
		Caller target = logIn(createUser(UserStatus.ACTIVE, "CUSTOMER", "SALE"));

		Result result = send("DELETE", admin, rolePath(target.userId(), "SALE"));

		assertEquals(204, result.status(), result.rawBody());
		assertEquals(Set.of("CUSTOMER"), dbRoles(target.userId()));
		assertEquals(List.of("role=SALE;actorUserId=" + admin.userId()), auditDetails("ROLE_REVOKED", target.userId()));
		assertEquals(List.of("ROLE_REVOKED"), jdbc.queryForList(
				"SELECT DISTINCT revoke_reason FROM refresh_tokens WHERE user_id = ?", String.class, target.userId()));
		assertEquals(401, refresh(target).status(), "the refresh token issued before the revoke is dead");
	}

	/** Owner clarification Q-8: an ADMIN cannot change a SUPER_ADMIN's roles, so cannot end their sessions. */
	@Test
	void revoke_adminOnASuperAdminAccount_returns403AndKeepsTheirSessions() throws Exception {
		Caller otherSuperAdmin = logIn(createUser(UserStatus.ACTIVE, "SUPER_ADMIN", "CUSTOMER"));

		assertForbidden(send("DELETE", admin, rolePath(otherSuperAdmin.userId(), "CUSTOMER")));
		assertForbidden(send("PUT", admin, rolePath(otherSuperAdmin.userId(), "SALE")));

		assertEquals(Set.of("SUPER_ADMIN", "CUSTOMER"), dbRoles(otherSuperAdmin.userId()));
		assertEquals(200, refresh(otherSuperAdmin).status());
	}

	/** Q-8: another ADMIN account is changed only by SUPER_ADMIN. */
	@Test
	void grant_onAnotherAdminAccount_adminGets403_superAdminGets204() throws Exception {
		String otherAdmin = createUser(UserStatus.ACTIVE, "ADMIN").userId();

		assertForbidden(send("PUT", admin, rolePath(otherAdmin, "LISTING_REVIEWER")));
		assertEquals(204, send("PUT", superAdmin, rolePath(otherAdmin, "LISTING_REVIEWER")).status());
		assertEquals(Set.of("ADMIN", "LISTING_REVIEWER"), dbRoles(otherAdmin));
	}

	/** US-4 AC-3: nothing to revoke -> 204, no audit, sessions kept. */
	@Test
	void revoke_roleNotHeld_returns204AndKeepsSessions() throws Exception {
		Caller target = logIn(createUser(UserStatus.ACTIVE, "CUSTOMER"));

		assertEquals(204, send("DELETE", admin, rolePath(target.userId(), "SALE")).status());

		assertTrue(auditDetails("ROLE_REVOKED", target.userId()).isEmpty());
		assertEquals(200, refresh(target).status());
	}

	@Test
	void revoke_adminRevokesAdmin_returns403() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "ADMIN").userId();

		assertForbidden(send("DELETE", admin, rolePath(target, "ADMIN")));
		assertEquals(Set.of("ADMIN"), dbRoles(target));
	}

	/** BR-11 with auth BR-3: the revoked role stops working at once, even on an access token issued before. */
	@Test
	void revoke_superAdminRevokesAdmin_returns204AndTheOldAccessTokenLosesIt() throws Exception {
		Caller target = logIn(createUser(UserStatus.ACTIVE, "ADMIN"));
		assertEquals(200, send("GET", target, rolesPath(admin.userId())).status());

		assertEquals(204, send("DELETE", superAdmin, rolePath(target.userId(), "ADMIN")).status());

		assertTrue(dbRoles(target.userId()).isEmpty(), "Q-7: an account may end with no roles");
		assertForbidden(send("GET", target, rolesPath(admin.userId())));
	}

	@Test
	void revoke_fromSelf_returns403() throws Exception {
		assertForbidden(send("DELETE", superAdmin, rolePath(superAdmin.userId(), "ADMIN")));
	}

	@Test
	void revoke_oldRole_returns400() throws Exception {
		String target = createUser(UserStatus.ACTIVE, "TENANT").userId();

		assertEquals(400, send("DELETE", superAdmin, rolePath(target, "TENANT")).status());
		assertEquals(Set.of("TENANT"), dbRoles(target));
	}

	@Test
	void revoke_unknownUser_returns404() throws Exception {
		assertEquals(404, send("DELETE", admin, rolePath(UNKNOWN_USER_ID, "SALE")).status());
	}

	// ==================== registration and hierarchy ====================

	/** US-1 AC-1, BR-3 (W6-3) */
	@Test
	void register_newAccount_getsExactlyTenantAndCustomer() throws Exception {
		String username = nextPhone();
		// StrongPassword: letters, digits and @$!%*?& only, upper, lower, digit and symbol
		String password = "Aa1!" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(uri("/api/users/command/register"))
						.timeout(REQUEST_TIMEOUT)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
								"fullName", "W6 Registered", "username", username,
								"password", password, "confirmPassword", password))))
						.build(),
				HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		String userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", String.class, username);
		assertEquals(Set.of("TENANT", "CUSTOMER"), dbRoles(userId));
	}

	/** US-5, BR-4 (W6-4): SUPER_ADMIN passes the HTTP rule hasRole(ADMIN) on /features/admin/**; TENANT does not. */
	@Test
	void adminFeaturePath_superAdminPassesTheHttpRule_tenantDoesNot() throws Exception {
		int superAdminStatus = send("POST", superAdmin, "/features/admin/w6-check").status();

		assertNotEquals(401, superAdminStatus);
		assertNotEquals(403, superAdminStatus);
		assertForbidden(send("POST", tenant, "/features/admin/w6-check"));
	}

	// ==================== Helpers ====================

	private static String rolesPath(String userId) {
		return "/api/v1/staff/users/" + userId + "/roles";
	}

	private static String rolePath(String userId, String role) {
		return rolesPath(userId) + "/" + role;
	}

	private void assertForbidden(Result result) {
		assertEquals(403, result.status(), result.rawBody());
		assertEquals("Forbidden", result.body().path("error").asString());
	}

	private Set<String> dbRoles(String userId) {
		return Set.copyOf(jdbc.queryForList(
				"SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?", String.class,
				userId));
	}

	private List<String> auditDetails(String type, String userId) {
		return jdbc.queryForList("SELECT details FROM auth_audit_events WHERE event_type = ? AND user_id = ?",
				String.class, type, userId);
	}

	private static String nextPhone() {
		return "08" + String.format("%08d", PHONE_SEQUENCE.getAndIncrement());
	}

	private record Created(String userId, String username, String password) {
	}

	private Created createUser(UserStatus status, String... roles) {
		String username = nextPhone();
		String password = UUID.randomUUID().toString();
		Instant now = AppClock.currentUTC();
		AppUser user = AppUser.create(UserId.create(ulidGenerator.generateId()), "W6 user", Username.create(username),
				null, Password.create(passwordEncoder.encode(password)), ProfileImage.create(null), null,
				new HashSet<>(Set.of(roles)), now);
		user.verifyPhone(now);
		user.setUserStatus(status);
		return new Created(userRepository.save(user).getId().getValue(), username, password);
	}

	private Caller logIn(Created user) throws Exception {
		HttpResponse<String> login = http.send(
				HttpRequest.newBuilder(uri("/api/auth/login"))
						.timeout(REQUEST_TIMEOUT)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
								Map.of("username", user.username(), "password", user.password()))))
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertEquals(200, login.statusCode(), "login failed, status " + login.statusCode());
		String accessToken = json.readTree(login.body()).path("result").path("accessToken").asString();
		String refreshCookie = login.headers().allValues("Set-Cookie").stream()
				.filter(c -> c.startsWith("cookie-jwt-rf="))
				.map(c -> c.substring(0, c.indexOf(';')))
				.findFirst().orElseThrow();
		return new Caller(user.userId(), user.username(), accessToken, refreshCookie);
	}

	private Result refresh(Caller caller) throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(uri("/api/auth/refresh-token"))
						.timeout(REQUEST_TIMEOUT)
						.header("Cookie", caller.refreshCookie())
						.POST(HttpRequest.BodyPublishers.noBody())
						.build(),
				HttpResponse.BodyHandlers.ofString());
		return new Result(response.statusCode(), json.missingNode(), "status " + response.statusCode());
	}

	private Result send(String method, Caller caller, String path) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(REQUEST_TIMEOUT)
				.method(method, HttpRequest.BodyPublishers.noBody());
		if (caller != null) {
			request.header("Authorization", "Bearer " + caller.accessToken());
		}
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		String raw = response.body();
		JsonNode parsed = raw == null || raw.isBlank() ? json.missingNode() : json.readTree(raw);
		return new Result(response.statusCode(), parsed, "status " + response.statusCode() + ", body " + raw);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asString()));
		return values;
	}
}
