package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.assertSecurityBody;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.fieldNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * {@code GET /api/v1/staff/owner-lead-assignees} end to end (acquisition-owner-lead-queue-ui spec 4.1, W11-1, W11-2):
 * real logins through the security chain and the role hierarchy, the use case, the app adapter and the auth query on
 * PostgreSQL migrated by Flyway, so the roles are the seeded SALE and SALE_LEADER rows. Every user made here is removed
 * afterwards. Needs Docker, like the other Postgres tests.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerLeadAssigneesApiPostgresTest {

	private static final String PATH = "/api/v1/staff/owner-lead-assignees";
	/** Vietnamese order: Ánh after An, Đạt after Dũng. */
	private static final List<String> NAME_ORDER = List.of("An", "Ánh", "Bình", "Dũng", "Đạt");

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

	private AcquisitionApiTestSupport api;
	private final List<String> userIds = new ArrayList<>();

	private TestUser saleLeader;
	private TestUser salesManager;
	private TestUser admin;
	private TestUser superAdmin;
	/** Eligible people by full name. */
	private final Map<String, TestUser> eligible = new LinkedHashMap<>();
	private final Map<String, List<String>> eligibleRoles = new LinkedHashMap<>();
	/** People who must not be listed, by reason. */
	private final Map<String, TestUser> ineligible = new LinkedHashMap<>();
	/** SALE, LISTING_REVIEWER, CUSTOMER, OWNER, EMPLOYEE, MANAGER, LEADER: no access to the list. */
	private final Map<String, TestUser> forbidden = new LinkedHashMap<>();

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		saleLeader = loggedIn("SALE_LEADER");
		salesManager = loggedIn("SALES_MANAGER");
		admin = loggedIn("ADMIN");
		superAdmin = loggedIn("SUPER_ADMIN");
		for (String role : List.of("SALE", "LISTING_REVIEWER", "CUSTOMER", "OWNER", "EMPLOYEE", "MANAGER", "LEADER")) {
			forbidden.put(role, loggedIn(role));
		}

		// Created out of order on purpose: the endpoint sorts them by Vietnamese collation
		eligible("Đạt", "SALE");
		eligible("Bình", "SALE_LEADER");
		eligible("An", "SALE");
		eligible("Dũng", "SALE");
		eligible("Ánh", "SALE", "SALE_LEADER");

		ineligible.put("locked", create("Khóa", UserStatus.LOCK, "SALE"));
		ineligible.put("terminated", create("Chấm dứt", UserStatus.TERMINATED, "SALE"));
		TestUser deleted = create("Đã xóa", UserStatus.ACTIVE, "SALE");
		jdbc.update("update users set deleted_at = now() where id = ?", deleted.userId());
		ineligible.put("softDeleted", deleted);
		ineligible.put("roleless", create("Không vai trò", UserStatus.ACTIVE));
		ineligible.put("reviewerOnly", create("Chỉ duyệt tin", UserStatus.ACTIVE, "LISTING_REVIEWER"));
	}

	@AfterAll
	void tearDown() {
		AcquisitionApiTestSupport.deleteUsers(jdbc, userIds);
		api.http().close();
	}

	/**
	 * W11-1, W11-2: SALE_LEADER, SALES_MANAGER, ADMIN and SUPER_ADMIN (through the role hierarchy) get the people who may
	 * hold a lead, in name order, with their assignee roles and nothing else.
	 */
	@Test
	void leadersAndAdminsGetEligibleList() throws Exception {
		for (TestUser caller : List.of(saleLeader, salesManager, admin, superAdmin)) {
			Result result = api.get(caller, PATH);

			assertEquals(200, result.status(), caller + " " + result.rawBody());
			assertTrue(result.body().isArray(), result.rawBody());
			List<String> listed = new ArrayList<>();
			result.body().forEach(item -> listed.add(item.path("userId").asString()));

			// the order assertion below is only meaningful when the directory cap of 500 did not cut the list
			assertTrue(listed.size() < 500, caller + " the list is below the directory cap");
			// the five test people come in the Vietnamese name order
			List<String> expectedOrder = NAME_ORDER.stream().map(name -> eligible.get(name).userId()).toList();
			assertEquals(expectedOrder, listed.stream().filter(expectedOrder::contains).toList(),
					caller + " " + result.rawBody());
			for (Map.Entry<String, TestUser> person : eligible.entrySet()) {
				JsonNode item = item(result, person.getValue().userId());
				assertEquals(person.getKey(), item.path("fullName").asString(), caller.toString());
				List<String> roles = new ArrayList<>();
				item.path("roles").forEach(role -> roles.add(role.asString()));
				assertEquals(eligibleRoles.get(person.getKey()), roles, person.getKey());
			}
			// who may not hold a lead is absent
			for (Map.Entry<String, TestUser> person : ineligible.entrySet()) {
				assertFalse(listed.contains(person.getValue().userId()), person.getKey());
			}
			// the callers hold SALE_LEADER, SALES_MANAGER, ADMIN, SUPER_ADMIN: only the first is an assignee
			assertTrue(listed.contains(saleLeader.userId()), caller.toString());
			assertFalse(listed.contains(salesManager.userId()) || listed.contains(admin.userId())
					|| listed.contains(superAdmin.userId()), caller.toString());
			// exactly userId, fullName, roles: no e-mail, phone or username, whatever the account holds
			result.body().forEach(item ->
					assertEquals(List.of("userId", "fullName", "roles"), fieldNames(item), result.rawBody()));
			for (String key : List.of("email", "phone", "username")) {
				assertFalse(result.rawBody().contains("\"" + key + "\""), key);
			}
			for (TestUser person : eligible.values()) {
				assertFalse(result.rawBody().contains(person.username()), "username leaked");
			}
		}
	}

	/** W11-1: every other role, including a plain SALE, gets the security 403. */
	@Test
	void otherRolesForbidden() throws Exception {
		for (Map.Entry<String, TestUser> caller : forbidden.entrySet()) {
			Result result = api.get(caller.getValue(), PATH);

			assertSecurityBody(result, 403);
			assertEquals(PATH, result.body().path("path").asString(), caller.getKey());
		}
	}

	/** W11-1: no token, the security 401. */
	@Test
	void anonymous401() throws Exception {
		assertSecurityBody(api.get(null, PATH), 401);
	}

	private JsonNode item(Result result, String userId) {
		for (JsonNode item : result.body()) {
			if (userId.equals(item.path("userId").asString())) {
				return item;
			}
		}
		throw new AssertionError("user not listed: " + userId + " in " + result.rawBody());
	}

	private void eligible(String fullName, String... roles) {
		eligible.put(fullName, create(fullName, UserStatus.ACTIVE, roles));
		// the order the contract promises: SALE before SALE_LEADER
		eligibleRoles.put(fullName, Arrays.stream(roles).sorted().toList());
	}

	private TestUser create(String fullName, UserStatus status, String... roles) {
		TestUser user = api.createUser(fullName, status, roles);
		userIds.add(user.userId());
		return user;
	}

	private TestUser loggedIn(String role) throws Exception {
		return api.logIn(create("W11 " + role, UserStatus.ACTIVE, role));
	}
}
