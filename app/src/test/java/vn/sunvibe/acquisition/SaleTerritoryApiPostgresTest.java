package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.assertSecurityBody;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * {@code /api/v1/staff/sale-territories} end to end (acquisition-owner-lead-queue spec 6; Q9-3, Q9-12, Q9-13, W9-18):
 * real logins through the security chain and the role hierarchy, the territory service and the catalog adapter on
 * PostgreSQL migrated by Flyway. Each test leaves no territory, change row, lead or user behind, so the intake tests
 * (which expect their wards to have nobody) are unaffected. Needs Docker, like the other Postgres tests.
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
class SaleTerritoryApiPostgresTest {

	private static final String PATH = "/api/v1/staff/sale-territories";
	private static final String HOA_CUONG = "20257";
	private static final String THANH_KHE = "20209";
	private static final String AN_KHE = "20305";
	private static final String HOA_XUAN = "20314";
	private static final List<String> USED_WARDS = List.of(HOA_CUONG, THANH_KHE, AN_KHE, HOA_XUAN);

	/** Phones 0971xxxxxx, apart from the other acquisition test classes. */
	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);

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
	private final List<String> leadIds = new ArrayList<>();

	private TestUser admin;
	private TestUser superAdmin;
	private TestUser sale1;
	private TestUser sale2;
	private TestUser saleLeader;
	private TestUser customer;
	private TestUser lockedSale;
	private TestUser roleless;
	/** Every logged-in role that is not ADMIN or SUPER_ADMIN (Q9-3). */
	private final Map<String, TestUser> nonAdmins = new HashMap<>();

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		admin = loggedIn("ADMIN");
		superAdmin = loggedIn("SUPER_ADMIN");
		sale1 = loggedIn("SALE");
		sale2 = loggedIn("SALE");
		saleLeader = loggedIn("SALE_LEADER");
		nonAdmins.put("SALE", sale1);
		nonAdmins.put("SALE_LEADER", saleLeader);
		for (String role : List.of("SALES_MANAGER", "LISTING_REVIEWER", "OWNER", "CUSTOMER", "EMPLOYEE", "MANAGER",
				"LEADER")) {
			nonAdmins.put(role, loggedIn(role));
		}
		customer = nonAdmins.get("CUSTOMER");
		lockedSale = created(UserStatus.LOCK, "SALE");
		roleless = created(UserStatus.ACTIVE);
	}

	@AfterEach
	void removeTerritoriesAndLeads() {
		for (String ward : USED_WARDS) {
			jdbc.update("delete from sale_territories where ward_code = ?", ward);
			jdbc.update("delete from sale_territory_changes where ward_code = ?", ward);
		}
		AcquisitionApiTestSupport.deleteLeads(jdbc, leadIds);
		leadIds.clear();
	}

	@AfterAll
	void tearDown() {
		AcquisitionApiTestSupport.deleteUsers(jdbc, userIds);
		api.http().close();
	}

	// ==================== Q9-12: list ====================

	/** Q9-12: every ward of the province, sorted by name; only the assigned ones carry a person and a time. */
	@Test
	void get_territories_listsAllSixteenWardsAssignedOrNot() throws Exception {
		assertEquals(200, assign(admin, HOA_CUONG, sale1).status());
		assertEquals(200, assign(admin, THANH_KHE, sale2).status());

		Result result = api.get(admin, PATH + "?provinceCode=48");

		assertEquals(200, result.status(), result.rawBody());
		JsonNode list = result.body();
		assertEquals(16, list.size(), result.rawBody());
		List<String> names = new ArrayList<>();
		for (JsonNode territory : list) {
			names.add(territory.path("wardName").asString());
			assertEquals(List.of("wardCode", "wardName", "wardType", "userId", "assignedAt"),
					AcquisitionApiTestSupport.fieldNames(territory));
			String ward = territory.path("wardCode").asString();
			if (ward.equals(HOA_CUONG) || ward.equals(THANH_KHE)) {
				assertEquals(ward.equals(HOA_CUONG) ? sale1.userId() : sale2.userId(),
						territory.path("userId").asString());
				assertFalse(territory.path("assignedAt").isNull(), ward);
			} else {
				assertTrue(territory.path("userId").isNull(), ward);
				assertTrue(territory.path("assignedAt").isNull(), ward);
			}
		}
		List<String> sorted = new ArrayList<>(names);
		sorted.sort(Collator.getInstance(Locale.forLanguageTag("vi")));
		assertEquals(sorted, names);
		assertTrue(names.contains("Hòa Cường") && names.contains("Hoàng Sa"), names.toString());
	}

	@Test
	void get_territories_unknownProvince_returns404_missingProvince_returns400() throws Exception {
		Result unknown = api.get(admin, PATH + "?provinceCode=99");
		assertEquals(404, unknown.status(), unknown.rawBody());
		assertEquals("NOT_FOUND", unknown.body().path("code").asString());
		assertEquals(0, unknown.body().path("errors").size());

		Result missing = api.get(admin, PATH);
		assertValidationError(missing, "provinceCode");
	}

	// ==================== Q9-12: assign, replace, unassign ====================

	/** Q9-12: one person per ward; each change of person is one change row; the same person again is a no-op. */
	@Test
	void put_territory_assignsReplacesAndWritesChangeRows() throws Exception {
		Result first = assign(admin, AN_KHE, sale1);
		assertEquals(200, first.status(), first.rawBody());
		assertEquals(AN_KHE, first.body().path("wardCode").asString());
		assertEquals("An Khê", first.body().path("wardName").asString());
		assertEquals("WARD", first.body().path("wardType").asString());
		assertEquals(sale1.userId(), first.body().path("userId").asString());
		assertFalse(first.body().path("assignedAt").isNull());

		Result second = assign(admin, AN_KHE, sale2);
		assertEquals(200, second.status(), second.rawBody());
		assertEquals(sale2.userId(), second.body().path("userId").asString());

		assertEquals(List.of(sale2.userId()), holders(AN_KHE));
		assertEquals(List.of(List.of("null", sale1.userId(), admin.userId()),
				List.of(sale1.userId(), sale2.userId(), admin.userId())), changes(AN_KHE));
		assertEquals(admin.userId(), jdbc.queryForObject(
				"select assigned_by from sale_territories where ward_code = ?", String.class, AN_KHE));

		Result same = assign(admin, AN_KHE, sale2);
		assertEquals(200, same.status(), same.rawBody());
		assertEquals(sale2.userId(), same.body().path("userId").asString());
		assertEquals(2, changes(AN_KHE).size());
	}

	/** acquisition-owner-lead-queue C-14: a SALE_LEADER can look after wards too. */
	@Test
	void put_territory_saleLeaderIsAllowed() throws Exception {
		Result result = assign(admin, HOA_XUAN, saleLeader);

		assertEquals(200, result.status(), result.rawBody());
		assertEquals(List.of(saleLeader.userId()), holders(HOA_XUAN));
	}

	/**
	 * Q9-12, acquisition-owner-lead-queue E-4: only someone who can sign in and holds SALE or SALE_LEADER; nothing is
	 * stored otherwise.
	 */
	@Test
	void put_ineligibleUser_returns422OnUserId() throws Exception {
		for (String userId : List.of(customer.userId(), lockedSale.userId(), roleless.userId(),
				ulidGenerator.generateId())) {
			Result result = api.put(admin, PATH + "/" + AN_KHE, Map.of("userId", userId));

			assertEquals(422, result.status(), result.rawBody());
			assertEquals("INELIGIBLE_ASSIGNEE", result.body().path("code").asString());
			assertEquals(1, result.body().path("errors").size());
			assertEquals("userId", result.body().path("errors").get(0).path("field").asString());
			assertFalse(result.rawBody().contains(userId), "the id is never echoed");
		}
		assertEquals(List.of(), holders(AN_KHE));
		assertEquals(0, changes(AN_KHE).size());
	}

	@Test
	void put_unknownWard_returns404() throws Exception {
		Result result = assign(admin, "00000", sale1);

		assertEquals(404, result.status(), result.rawBody());
		assertEquals("NOT_FOUND", result.body().path("code").asString());
		assertEquals(0, result.body().path("errors").size());
		assertEquals(0, count("select count(*) from sale_territories where ward_code = '00000'"));
	}

	@Test
	void put_blankUserId_returns400OnUserId() throws Exception {
		assertValidationError(api.put(admin, PATH + "/" + AN_KHE, Map.of("userId", "  ")), "userId");
		assertValidationError(api.put(admin, PATH + "/" + AN_KHE, Map.of()), "userId");
		assertEquals(List.of(), holders(AN_KHE));
	}

	/** User ids are ULIDs of 26 characters (users.id): a huge one is refused on the field before any lookup. */
	@Test
	void put_userIdOf1Mb_returns400OnUserId() throws Exception {
		Result result = api.put(admin, PATH + "/" + AN_KHE, Map.of("userId", "x".repeat(1_000_000)));

		assertValidationError(result, "userId");
		assertFalse(result.rawBody().contains("xxxxxxxxxx"), "the value is not echoed");
		assertEquals(List.of(), holders(AN_KHE));
	}

	/** Ward codes are at most 10 characters (ward_code VARCHAR(10)): a longer one is a 400, not a lookup. */
	@Test
	void putAndDelete_wardCodeOver10Chars_return400OnWardCode() throws Exception {
		String longWard = "12345678901";

		assertValidationError(api.put(admin, PATH + "/" + longWard, Map.of("userId", sale1.userId())), "wardCode");
		assertValidationError(api.delete(admin, PATH + "/" + longWard), "wardCode");
		assertEquals(0, count("select count(*) from sale_territory_changes where ward_code = ?", longWard));
	}

	/** Q9-12: DELETE leaves the ward without a person and records who did it. */
	@Test
	void delete_territory_unassignsAndWritesChange() throws Exception {
		assertEquals(200, assign(admin, AN_KHE, sale1).status());

		Result result = api.delete(admin, PATH + "/" + AN_KHE);

		assertEquals(204, result.status(), result.rawBody());
		assertEquals(List.of(), holders(AN_KHE));
		assertEquals(List.of(List.of("null", sale1.userId(), admin.userId()),
				List.of(sale1.userId(), "null", admin.userId())), changes(AN_KHE));
	}

	@Test
	void delete_territoryNobodyHolds_returns204WithoutChangeRow() throws Exception {
		Result result = api.delete(admin, PATH + "/" + AN_KHE);

		assertEquals(204, result.status(), result.rawBody());
		assertEquals(0, changes(AN_KHE).size());
	}

	@Test
	void delete_unknownWard_returns404() throws Exception {
		Result result = api.delete(admin, PATH + "/00000");

		assertEquals(404, result.status(), result.rawBody());
		assertEquals("NOT_FOUND", result.body().path("code").asString());
	}

	// ==================== Q9-3: who may call ====================

	/** Q9-3: every role but ADMIN (and SUPER_ADMIN) gets the security 403 on every call, with valid input. */
	@Test
	void territories_nonAdminRoles_return403OnEveryCall() throws Exception {
		assertEquals(200, assign(admin, AN_KHE, sale1).status());

		for (Map.Entry<String, TestUser> entry : nonAdmins.entrySet()) {
			TestUser caller = entry.getValue();
			assertSecurityBody(api.get(caller, PATH + "?provinceCode=48"), 403);
			assertSecurityBody(assign(caller, HOA_XUAN, sale1), 403);
			assertSecurityBody(api.delete(caller, PATH + "/" + AN_KHE), 403);
		}
		assertEquals(List.of(), holders(HOA_XUAN));
		assertEquals(List.of(sale1.userId()), holders(AN_KHE));
		assertEquals(1, changes(AN_KHE).size());
	}

	/** SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy. */
	@Test
	void territories_superAdmin_actsAsAdmin() throws Exception {
		assertEquals(200, api.get(superAdmin, PATH + "?provinceCode=48").status());

		Result put = assign(superAdmin, AN_KHE, sale1);
		assertEquals(200, put.status(), put.rawBody());
		Result delete = api.delete(superAdmin, PATH + "/" + AN_KHE);
		assertEquals(204, delete.status(), delete.rawBody());

		assertEquals(List.of(List.of("null", sale1.userId(), superAdmin.userId()),
				List.of(sale1.userId(), "null", superAdmin.userId())), changes(AN_KHE));
	}

	/** W9-18: nothing here is public. */
	@Test
	void territories_withoutToken_return401() throws Exception {
		assertSecurityBody(api.get(null, PATH + "?provinceCode=48"), 401);
		assertSecurityBody(api.put(null, PATH + "/" + AN_KHE, Map.of("userId", sale1.userId())), 401);
		assertSecurityBody(api.delete(null, PATH + "/" + AN_KHE), 401);
		assertEquals(List.of(), holders(AN_KHE));
	}

	// ==================== Q9-13: existing leads stay ====================

	/** Q9-13: a new person for the ward gets the new leads only; the lead sent before keeps its assignee. */
	@Test
	void put_territory_changeOfPerson_doesNotMoveExistingLeads() throws Exception {
		assertEquals(200, assign(admin, AN_KHE, sale1).status());
		String before = submit(AN_KHE);
		assertEquals(sale1.userId(), assignee(before));

		assertEquals(200, assign(admin, AN_KHE, sale2).status());

		assertEquals(sale1.userId(), assignee(before));
		assertEquals("ASSIGNED", jdbc.queryForObject("select status from owner_leads where id = ?", String.class,
				before));
		String after = submit(AN_KHE);
		assertEquals(sale2.userId(), assignee(after));
	}

	// ==================== Helpers ====================

	private TestUser loggedIn(String role) throws Exception {
		return api.logIn(created(UserStatus.ACTIVE, role));
	}

	private TestUser created(UserStatus status, String... roles) {
		TestUser user = api.createUser(status, roles);
		userIds.add(user.userId());
		return user;
	}

	private Result assign(TestUser caller, String wardCode, TestUser holder) throws Exception {
		return api.put(caller, PATH + "/" + wardCode, Map.of("userId", holder.userId()));
	}

	/** The person in the ward's row (one at most, the table's key is the ward). */
	private List<String> holders(String wardCode) {
		return jdbc.queryForList("select user_id from sale_territories where ward_code = ?", String.class, wardCode);
	}

	/** [old, new, actor] of each change row of the ward, oldest first; "null" for nobody. */
	private List<List<String>> changes(String wardCode) {
		return jdbc.query("select old_user_id, new_user_id, actor_user_id from sale_territory_changes "
						+ "where ward_code = ? order by occurred_at, id",
				(row, i) -> List.of(String.valueOf(row.getString(1)), String.valueOf(row.getString(2)),
						row.getString(3)),
				wardCode);
	}

	private String submit(String wardCode) throws Exception {
		int n = PHONE_SEQUENCE.getAndIncrement();
		String phone = "0971" + String.format("%06d", n);
		String leadId = AcquisitionApiTestSupport.leadId(jdbc, api.submitLead(wardCode, phone, n + " Lê Duẩn"));
		leadIds.add(leadId);
		return leadId;
	}

	private String assignee(String leadId) {
		return jdbc.queryForObject("select assigned_user_id from owner_leads where id = ?", String.class, leadId);
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}

	private static void assertValidationError(Result result, String field) {
		assertEquals(400, result.status(), result.rawBody());
		assertEquals("VALIDATION_FAILED", result.body().path("code").asString(), result.rawBody());
		List<String> fields = new ArrayList<>();
		result.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		assertEquals(List.of(field), fields, result.rawBody());
	}
}
