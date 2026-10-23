package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.survey.SurveyApiTestSupport;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * The E2E fixture script {@code scripts/dev/seed-e2e-owner-leads.sql} (acquisition-owner-lead-queue-ui spec 4.4,
 * W11-11) run with psql inside the test PostgreSQL container, after {@code seed-e2e-accounts.sql}. The decisive proof
 * is that the application itself reads the fixtures: the staff API, called as the seeded leader, sale and admin,
 * shows the twelve leads, their history, the duplicate link, the staff names and the one territory. Passwords are
 * random per run and never printed.
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
class SeedE2eOwnerLeadsPostgresTest {

	private static final Path ACCOUNTS_SCRIPT = Path.of("..", "scripts", "dev", "seed-e2e-accounts.sql");
	private static final Path LEADS_SCRIPT = Path.of("..", "scripts", "dev", "seed-e2e-owner-leads.sql");
	private static final Pattern COUNTS = Pattern.compile("seed-e2e-owner-leads: leads=(\\d+) consents=(\\d+) "
			+ "activities=(\\d+) candidates=(\\d+) territories=(\\d+) territory_changes=(\\d+)");

	private static final String CUSTOMER_ID = "01JE2EACCT0000000000000001";
	private static final String SALE_ID = "01JE2EACCT0000000000000002";
	private static final String LEADER_ID = "01JE2EACCT0000000000000003";
	private static final String ADMIN_ID = "01JE2EACCT0000000000000004";

	private static final String AN_HAI = "20275";
	private static final String HAI_CHAU = "20242";
	/** The fixture leads L1..L12 in order. */
	private static final List<String> LEAD_IDS = List.of(
			"01JE2EHEAD0000000000000001", "01JE2EHEAD0000000000000002", "01JE2EHEAD0000000000000003",
			"01JE2EHEAD0000000000000004", "01JE2EHEAD0000000000000005", "01JE2EHEAD0000000000000006",
			"01JE2EHEAD0000000000000007", "01JE2EHEAD0000000000000008", "01JE2EHEAD0000000000000009",
			"01JE2EHEAD0000000000000010", "01JE2EHEAD0000000000000011", "01JE2EHEAD0000000000000012");
	private static final List<String> CODES = List.of("SV-E2EA-AA2A", "SV-E2EA-AA2B", "SV-E2EA-AA2C", "SV-E2EA-AA2D",
			"SV-E2EA-AA2E", "SV-E2EA-AA2F", "SV-E2EA-AA2G", "SV-E2EA-AA2H", "SV-E2EA-AA2J", "SV-E2EA-AA2K",
			"SV-E2EA-AA2L", "SV-E2EA-AA2M");
	private static final List<String> STATUSES = List.of("NEW", "ASSIGNED", "CONTACTED", "ON_HOLD", "ASSIGNED",
			"REJECTED", "ASSIGNED", "ASSIGNED", "ASSIGNED", "NEW", "CONTACTED", "ASSIGNED");
	/** acquisition-owner-intake BR-3: L1..L12 use the six types and two leads have none. */
	private static final List<String> PROPERTY_TYPES = Arrays.asList("APARTMENT", "HOMESTAY", "CONDOTEL", "HOUSE",
			"WHOLE_BUILDING", null, "APARTMENT", "APARTMENT", null, "VILLA", "APARTMENT", "HOUSE");

	private static final String FOREIGN_LEAD = "01JE2EOTHERLEAD00000000001";
	private static final String FOREIGN_CONSENT = "01JE2EOTHERCONS00000000001";
	private static final long FOREIGN_CHANGE = 99000000000001L;
	/** A change row another account wrote for ward An Hải; the fixed-id delete of the script must keep it. */
	private static final long FOREIGN_CHANGE_AN_HAI = 99000000000003L;
	private static final String THANH_KHE = "20209";
	private static final String OTHER_PERSON = "01JE2EOTHERPERSON0000000A";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private PostgreSQLContainer postgres;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final JsonMapper json = JsonMapper.builder().build();

	private String salePassword;
	private String leaderPassword;
	private String adminPassword;

	@AfterEach
	void removeFixtures() {
		jdbc.update("UPDATE owner_leads SET duplicate_of_lead_id = NULL WHERE duplicate_of_lead_id LIKE '01JE2EHEAD%'");
		SurveyApiTestSupport.deleteSurveyRows(jdbc, LEAD_IDS);
		jdbc.update("DELETE FROM owner_lead_intent_changes WHERE lead_id LIKE '01JE2EHEAD%'");
		jdbc.update("DELETE FROM owner_lead_access_logs WHERE lead_id LIKE '01JE2EHEAD%' OR lead_id LIKE '01JE2EOTHERLEAD%'");
		jdbc.update("DELETE FROM owner_lead_activities WHERE lead_id LIKE '01JE2EHEAD%' OR lead_id LIKE '01JE2EOTHERLEAD%'");
		jdbc.update("DELETE FROM owner_lead_duplicate_candidates WHERE lead_id LIKE '01JE2EHEAD%' "
				+ "OR candidate_lead_id LIKE '01JE2EHEAD%' OR lead_id LIKE '01JE2EOTHERLEAD%'");
		jdbc.update("DELETE FROM owner_leads WHERE id LIKE '01JE2EHEAD%' OR id LIKE '01JE2EOTHERLEAD%'");
		jdbc.update("DELETE FROM consent_records WHERE id LIKE '01JE2EHCNT%' OR id LIKE '01JE2EOTHERCONS%'");
		jdbc.update("DELETE FROM sale_territories WHERE ward_code IN (?, ?, ?)", AN_HAI, HAI_CHAU, THANH_KHE);
		jdbc.update("DELETE FROM sale_territory_changes WHERE id IN (20261018000301, ?, ?)", FOREIGN_CHANGE,
				FOREIGN_CHANGE_AN_HAI);
		jdbc.update("DELETE FROM auth_audit_events WHERE user_id IN (?, ?, ?, ?)", CUSTOMER_ID, SALE_ID, LEADER_ID,
				ADMIN_ID);
		jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", CUSTOMER_ID, SALE_ID, LEADER_ID, ADMIN_ID);
	}

	@AfterAll
	void tearDown() {
		http.close();
	}

	/** W11-11: twelve leads, one territory; a second run of both files changes no count and no id (idempotent). */
	@Test
	void seed_runTwice_twelveLeadsOneTerritory_sameCountsTheSecondTime() throws Exception {
		seedAccounts();
		ExecResult first = runLeads("local");
		assertEquals(0, first.getExitCode(), "first run failed: " + first.getStderr());
		assertEquals(List.of(12, 12, 25, 1, 1, 1), printedCounts(first));
		List<String> before = stableSnapshot();

		seedAccounts();
		ExecResult second = runLeads("local");
		assertEquals(0, second.getExitCode(), "second run failed: " + second.getStderr());
		assertEquals(List.of(12, 12, 25, 1, 1, 1), printedCounts(second));

		assertEquals(12, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
		assertEquals(12, count("SELECT count(*) FROM consent_records WHERE id LIKE '01JE2EHCNT%'"));
		assertEquals(12, count("SELECT count(*) FROM owner_lead_activities WHERE type = 'SUBMITTED' "
				+ "AND lead_id LIKE '01JE2EHEAD%' AND actor_user_id IS NULL"));
		assertEquals(9, count("SELECT count(*) FROM owner_lead_activities WHERE type = 'ASSIGNED' "
				+ "AND lead_id LIKE '01JE2EHEAD%' AND actor_user_id = '" + LEADER_ID + "'"));
		assertEquals(25, count("SELECT count(*) FROM owner_lead_activities WHERE lead_id LIKE '01JE2EHEAD%'"));
		assertEquals(1, count("SELECT count(*) FROM owner_lead_duplicate_candidates "
				+ "WHERE lead_id = '" + LEAD_IDS.get(7) + "' AND candidate_lead_id = '" + LEAD_IDS.get(6) + "'"));
		assertEquals(1, count("SELECT count(*) FROM sale_territories WHERE user_id = '" + SALE_ID + "'"));
		assertEquals(SALE_ID, jdbc.queryForObject("SELECT user_id FROM sale_territories WHERE ward_code = ?",
				String.class, AN_HAI));
		assertEquals(1, count("SELECT count(*) FROM sale_territory_changes WHERE ward_code = '" + AN_HAI + "'"));
		assertEquals(List.of(), jdbc.queryForList("SELECT 1 FROM owner_leads WHERE id LIKE '01JE2EHEAD%' "
				+ "AND request_code NOT LIKE 'SV-E2EA-%'"));
		assertEquals(before, stableSnapshot(), "the same leads, codes, statuses, holders and versions");
		assertEquals(LEAD_IDS, jdbc.queryForList("SELECT id FROM owner_leads WHERE id LIKE '01JE2EHEAD%' ORDER BY id",
				String.class));
		assertEquals(CODES, jdbc.queryForList("SELECT request_code FROM owner_leads WHERE id LIKE '01JE2EHEAD%' "
				+ "ORDER BY id", String.class));
		assertEquals(STATUSES, jdbc.queryForList("SELECT status FROM owner_leads WHERE id LIKE '01JE2EHEAD%' "
				+ "ORDER BY id", String.class));
		assertEquals(PROPERTY_TYPES, jdbc.queryForList("SELECT property_type FROM owner_leads "
				+ "WHERE id LIKE '01JE2EHEAD%' ORDER BY id", String.class));
	}

	/** W11-11: any target but {@code local} (or none) is refused before the first change. */
	@Test
	void seed_targetNotLocal_isRefusedAndWritesNothing() throws Exception {
		seedAccounts();

		ExecResult prod = runLeads("prod");
		assertNotEquals(0, prod.getExitCode(), "target=prod must fail");
		assertTrue(prod.getStderr().contains("target=local"), "the refusal names the fix");

		ExecResult missing = execPsql(LEADS_SCRIPT, List.of());
		assertNotEquals(0, missing.getExitCode(), "no target must fail");
		assertTrue(missing.getStderr().contains("target=local"), "the refusal names the fix");

		assertNothingSeeded();
	}

	/** The fixtures point at the account seed's users: without them the script stops with a clear message. */
	@Test
	void seed_withoutTheAccountsSeed_isRefusedAndWritesNothing() throws Exception {
		ExecResult result = runLeads("local");

		assertNotEquals(0, result.getExitCode(), "missing seed users must fail");
		assertTrue(result.getStderr().contains("seed-e2e-accounts.sql first"), "the refusal names the fix");
		assertNothingSeeded();
	}

	/** The accounts seed refuses a phone held by another account (W11-11); the fixtures then cannot be seeded. */
	@Test
	void seed_accountsPhoneTakenByAnotherAccount_isRefusedAndWritesNothing() throws Exception {
		String otherId = "01JE2EOTHER000000000000003";
		jdbc.update("INSERT INTO users (id, full_name, username, password, user_status, failed_login_attempts, "
				+ "created_date, last_update) VALUES (?, 'Someone else', '0900000103', 'not-a-hash', 'ACTIVE', 0, "
				+ "now(), now())", otherId);
		try {
			ExecResult accounts = execPsql(ACCOUNTS_SCRIPT, accountVariables("local", salePassword(), leaderPassword()));
			assertNotEquals(0, accounts.getExitCode(), "a phone of another account must fail");
			assertTrue(accounts.getStderr().contains("already belongs to another account"), "the refusal names it");

			ExecResult leads = runLeads("local");
			assertNotEquals(0, leads.getExitCode(), "the fixtures need the seed users");
			assertNothingSeeded();
			assertEquals("Someone else", jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class,
					otherId));
		} finally {
			jdbc.update("DELETE FROM users WHERE id = ?", otherId);
		}
	}

	/** Only the fixture rows are deleted and inserted: a foreign lead, territory and change survive two runs. */
	@Test
	void seed_foreignRows_areNeverTouched() throws Exception {
		seedAccounts();
		insertForeignRows();
		List<String> foreignBefore = foreignSnapshot();

		assertEquals(0, runLeads("local").getExitCode());
		assertEquals(0, runLeads("local").getExitCode());

		assertEquals(foreignBefore, foreignSnapshot(), "the foreign rows are unchanged");
		assertEquals(12, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
		assertEquals(13, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%' OR id = '" + FOREIGN_LEAD
				+ "'"));
		assertEquals(2, count("SELECT count(*) FROM sale_territories WHERE ward_code IN ('" + AN_HAI + "', '" + HAI_CHAU
				+ "')"), "the foreign ward and the fixture ward");
	}

	/** A request code already used by another lead is refused before any change; the other lead stays. */
	@Test
	void seed_requestCodeUsedByAnotherLead_isRefusedAndWritesNothing() throws Exception {
		seedAccounts();
		insertForeignRows();
		jdbc.update("UPDATE owner_leads SET request_code = 'SV-E2EA-AA2A' WHERE id = ?", FOREIGN_LEAD);
		List<String> foreignBefore = foreignSnapshot();

		ExecResult result = runLeads("local");

		assertNotEquals(0, result.getExitCode(), "a taken request code must fail");
		assertTrue(result.getStderr().contains("request codes"), "the refusal names the cause");
		assertEquals(foreignBefore, foreignSnapshot(), "the foreign rows are unchanged");
		assertEquals(0, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
	}

	/** A ward held by a real person is never taken over: refused before any change, the holder stays. */
	@Test
	void seed_territoryWardHeldByAnotherPerson_isRefusedAndWritesNothing() throws Exception {
		seedAccounts();
		jdbc.update("INSERT INTO sale_territories (ward_code, user_id, assigned_by, assigned_at) "
				+ "VALUES (?, '01JE2EOTHERPERSON0000000A', '01JE2EOTHERPERSON0000000B', now())", AN_HAI);
		String before = jdbc.queryForObject("SELECT t::text FROM sale_territories t WHERE ward_code = ?",
				String.class, AN_HAI);

		ExecResult result = runLeads("local");

		assertNotEquals(0, result.getExitCode(), "a ward of a real person must fail");
		assertTrue(result.getStderr().contains("(20275)"), "the refusal names the ward");
		assertEquals(before, jdbc.queryForObject("SELECT t::text FROM sale_territories t WHERE ward_code = ?",
				String.class, AN_HAI));
		assertEquals(0, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
		assertEquals(0, count("SELECT count(*) FROM consent_records WHERE id LIKE '01JE2EHCNT%'"));
	}

	/**
	 * psql on Windows starts with the console code page as client encoding; the scripts hold UTF-8 names, so each
	 * pins {@code \encoding UTF8}. Here the client encoding is forced to WIN1252 from the environment, as on such a
	 * console, and the names must still be stored right.
	 */
	@Test
	void seed_clientEncodingWin1252InTheEnvironment_stillStoresTheUtf8Names() throws Exception {
		ExecResult accounts = execPsql(ACCOUNTS_SCRIPT, accountVariables("local", salePassword(), leaderPassword()),
				true);
		assertEquals(0, accounts.getExitCode(), "accounts seed failed: " + accounts.getStderr());
		ExecResult leads = execPsql(LEADS_SCRIPT, List.of("-v", "target=local"), true);
		assertEquals(0, leads.getExitCode(), "fixture seed failed: " + leads.getStderr());

		assertEquals("E2E Trưởng nhóm", jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class,
				LEADER_ID));
		assertEquals("E2E Admin", jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class,
				ADMIN_ID));
		assertEquals("Lê Thị Hạnh", jdbc.queryForObject("SELECT contact_name FROM owner_leads WHERE id = ?",
				String.class, LEAD_IDS.get(2)));
		assertEquals("Chủ nhà không còn nhu cầu", jdbc.queryForObject(
				"SELECT close_reason FROM owner_leads WHERE id = ?", String.class, LEAD_IDS.get(5)));
		assertEquals("Đã gọi lần đầu, hẹn gọi lại.", jdbc.queryForObject(
				"SELECT note FROM owner_lead_activities WHERE id = 20261018000203", String.class));
		assertEquals("An Hải", jdbc.queryForObject("SELECT ward_name FROM owner_leads WHERE id = ?", String.class,
				LEAD_IDS.get(1)));
	}

	/**
	 * Both scripts refuse a server that is not on this machine (a DATABASE_URL that points at a shared database): the
	 * psql in the container connects over TCP to the container's own non-loopback address. The Unix socket and the
	 * loopback address, which every other test and a developer's machine use, are accepted.
	 */
	@Test
	void seed_serverNotOnThisMachine_isRefusedBeforeAnyChange() throws Exception {
		String address = containerAddress();
		List<String> overTcp = List.of("-h", address);

		ExecResult accounts = execPsql(ACCOUNTS_SCRIPT, accountVariables("local", salePassword(), leaderPassword()),
				false, overTcp);
		assertNotEquals(0, accounts.getExitCode(), "the accounts seed must refuse a non-local host");
		assertTrue(accounts.getStderr().contains("the server is not on this machine"), "the refusal names the cause");
		assertEquals(0, count("SELECT count(*) FROM users WHERE id IN ('" + CUSTOMER_ID + "', '" + SALE_ID + "', '"
				+ LEADER_ID + "', '" + ADMIN_ID + "')"), "no account written");
		assertEquals(0, count("SELECT count(*) FROM user_roles WHERE user_id IN ('" + SALE_ID + "', '" + LEADER_ID
				+ "', '" + ADMIN_ID + "')"));

		seedAccounts();
		ExecResult leads = execPsql(LEADS_SCRIPT, List.of("-v", "target=local"), false, overTcp);
		assertNotEquals(0, leads.getExitCode(), "the fixture seed must refuse a non-local host");
		assertTrue(leads.getStderr().contains("the server is not on this machine"), "the refusal names the cause");
		assertNothingSeeded();

		ExecResult loopback = execPsql(LEADS_SCRIPT, List.of("-v", "target=local"), false, List.of("-h", "127.0.0.1"));
		assertEquals(0, loopback.getExitCode(), "the loopback address is local: " + loopback.getStderr());
		assertEquals(12, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
	}

	/** A lead outside the fixture that names a fixture lead as its original is refused; nothing changes. */
	@Test
	void seed_foreignLeadMarkedAsDuplicateOfAFixtureLead_isRefusedAndWritesNothing() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		insertForeignRows();
		jdbc.update("UPDATE owner_leads SET duplicate_of_lead_id = ? WHERE id = ?", LEAD_IDS.get(6), FOREIGN_LEAD);
		List<String> foreignBefore = foreignSnapshot();
		List<String> fixturesBefore = fixtureSnapshot();

		ExecResult result = runLeads("local");

		assertNotEquals(0, result.getExitCode(), "a foreign duplicate_of must fail");
		assertTrue(result.getStderr().contains("marked as a duplicate of a fixture lead"), "the refusal names it");
		assertEquals(foreignBefore, foreignSnapshot(), "the foreign rows are unchanged");
		assertEquals(fixturesBefore, fixtureSnapshot(), "the fixture rows are unchanged (rolled back)");
	}

	/** A duplicate-candidate row of a lead outside the fixture that points at a fixture lead is refused, not deleted. */
	@Test
	void seed_foreignCandidateRowPointingAtAFixtureLead_isRefusedAndKept() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		insertForeignRows();
		jdbc.update("INSERT INTO owner_lead_duplicate_candidates (lead_id, candidate_lead_id, detected_at) "
				+ "VALUES (?, ?, now())", FOREIGN_LEAD, LEAD_IDS.get(6));
		List<String> foreignBefore = foreignSnapshot();
		List<String> fixturesBefore = fixtureSnapshot();

		ExecResult result = runLeads("local");

		assertNotEquals(0, result.getExitCode(), "a foreign candidate row must fail");
		assertTrue(result.getStderr().contains("duplicate link"), "the refusal names the cause");
		assertEquals(1, count("SELECT count(*) FROM owner_lead_duplicate_candidates WHERE lead_id = '"
				+ FOREIGN_LEAD + "' AND candidate_lead_id = '" + LEAD_IDS.get(6) + "'"), "the row is kept");
		assertEquals(foreignBefore, foreignSnapshot(), "the foreign rows are unchanged");
		assertEquals(fixturesBefore, fixtureSnapshot(), "the fixture rows are unchanged (rolled back)");
	}

	/**
	 * Every ward but An Hải is free after seeding: a ward an E2E account holds (left by an interrupted admin run) is
	 * freed, a ward held by anyone else is left alone.
	 */
	@Test
	void seed_otherWardsOfTheE2eAccounts_areFreed_otherHoldersKept() throws Exception {
		seedAccounts();
		jdbc.update("INSERT INTO sale_territories (ward_code, user_id, assigned_by, assigned_at) "
				+ "VALUES (?, ?, ?, now())", HAI_CHAU, SALE_ID, ADMIN_ID);
		jdbc.update("INSERT INTO sale_territories (ward_code, user_id, assigned_by, assigned_at) "
				+ "VALUES (?, ?, ?, now())", THANH_KHE, OTHER_PERSON, OTHER_PERSON);
		String otherBefore = jdbc.queryForObject("SELECT t::text FROM sale_territories t WHERE ward_code = ?",
				String.class, THANH_KHE);

		ExecResult result = runLeads("local");

		assertEquals(0, result.getExitCode(), "seed failed: " + result.getStderr());
		assertEquals(0, count("SELECT count(*) FROM sale_territories WHERE ward_code = '" + HAI_CHAU + "'"),
				"the ward an E2E account held is free");
		assertEquals(otherBefore, jdbc.queryForObject("SELECT t::text FROM sale_territories t WHERE ward_code = ?",
				String.class, THANH_KHE), "the other holder is untouched");
		assertEquals(SALE_ID, jdbc.queryForObject("SELECT user_id FROM sale_territories WHERE ward_code = ?",
				String.class, AN_HAI));
	}

	/**
	 * The application reads the fixtures: the staff API as the seeded leader, sale and admin shows the twelve leads,
	 * their history and names, the duplicate link, the assignee list and the territory; a lead opened by the API
	 * (access log) does not stop the next run.
	 */
	@Test
	void seed_theApplicationReadsTheFixtures() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		String leader = accessToken("0900000103", leaderPassword());
		String admin = accessToken("0900000104", adminPassword());
		String sale = accessToken("0900000102", salePassword());

		// every fixture, looked up by its request code as the leader (the whole queue may hold other leads)
		Map<String, JsonNode> byCode = new LinkedHashMap<>();
		for (String code : CODES) {
			JsonNode found = get(leader, "/api/v1/staff/owner-leads?scope=all&size=5&requestCode=" + code).path("items");
			assertEquals(1, found.size(), code + " is listed once");
			byCode.put(code, found.get(0));
		}
		for (int i = 0; i < CODES.size(); i++) {
			JsonNode item = byCode.get(CODES.get(i));
			assertEquals(LEAD_IDS.get(i), item.path("id").asString());
			assertEquals(STATUSES.get(i), item.path("status").asString(), CODES.get(i));
		}
		assertEquals("E2E Sale", byCode.get("SV-E2EA-AA2B").path("assignedUserName").asString());
		assertEquals(SALE_ID, byCode.get("SV-E2EA-AA2B").path("assignedUserId").asString());
		assertEquals("E2E Trưởng nhóm", byCode.get("SV-E2EA-AA2E").path("assignedUserName").asString());
		assertTrue(byCode.get("SV-E2EA-AA2A").path("assignedUserId").isNull());
		assertTrue(byCode.get("SV-E2EA-AA2K").path("assignedUserId").isNull());
		assertTrue(byCode.get("SV-E2EA-AA2G").path("possibleDuplicate").asBoolean());
		assertTrue(byCode.get("SV-E2EA-AA2H").path("possibleDuplicate").asBoolean());
		assertFalse(byCode.get("SV-E2EA-AA2B").path("possibleDuplicate").asBoolean());
		assertTrue(Instant.parse(byCode.get("SV-E2EA-AA2C").path("nextActionAt").asString()).isBefore(Instant.now()),
				"L3 next action is in the past");
		assertEquals("An Hải", byCode.get("SV-E2EA-AA2B").path("wardName").asString());

		// the two NEW leads are in the unassigned queue (looked up by code, whatever else the queue holds)
		for (String code : List.of("SV-E2EA-AA2A", "SV-E2EA-AA2K")) {
			assertEquals(1, get(leader, "/api/v1/staff/owner-leads?scope=unassigned&size=5&requestCode=" + code)
					.path("items").size(), code + " is unassigned");
		}

		// L3: the history the application reads, with names
		JsonNode l3 = get(leader, "/api/v1/staff/owner-leads/" + LEAD_IDS.get(2));
		assertEquals("CONTACTED", l3.path("status").asString());
		assertEquals(2, l3.path("version").asLong());
		JsonNode activities = l3.path("activities");
		List<String> types = new ArrayList<>();
		activities.forEach(activity -> types.add(activity.path("type").asString()));
		assertEquals(List.of("SUBMITTED", "ASSIGNED", "STATUS_CHANGED"), types);
		assertTrue(activities.get(0).path("actorUserId").isNull());
		assertEquals(LEADER_ID, activities.get(1).path("actorUserId").asString());
		assertEquals("E2E Trưởng nhóm", activities.get(1).path("actorName").asString());
		assertEquals(SALE_ID, activities.get(1).path("toAssigneeId").asString());
		assertEquals("E2E Sale", activities.get(1).path("toAssigneeName").asString());
		assertEquals("E2E Sale", activities.get(2).path("actorName").asString());
		assertEquals("CONTACTED", activities.get(2).path("toStatus").asString());
		assertEquals("Lê Thị Hạnh", l3.path("contactName").asString());
		assertEquals("owner-intake-2026-10", l3.path("consent").path("noticeVersion").asString());

		// L8: the possible duplicate link to L7; L9 without coordinates; L6 closed with a reason
		JsonNode l8 = get(leader, "/api/v1/staff/owner-leads/" + LEAD_IDS.get(7));
		assertEquals(List.of(LEAD_IDS.get(6)), idsOf(l8.path("duplicateCandidates")));
		JsonNode l9 = get(leader, "/api/v1/staff/owner-leads/" + LEAD_IDS.get(8));
		assertTrue(l9.path("latitude").isNull() && l9.path("longitude").isNull());
		JsonNode l10 = get(leader, "/api/v1/staff/owner-leads/" + LEAD_IDS.get(9));
		assertFalse(l10.path("latitude").isNull());
		JsonNode l6 = get(leader, "/api/v1/staff/owner-leads/" + LEAD_IDS.get(5));
		assertEquals("REJECTED", l6.path("status").asString());
		assertEquals("Chủ nhà không còn nhu cầu", l6.path("closeReason").asString());

		// the people who may hold a lead
		JsonNode assignees = get(leader, "/api/v1/staff/owner-lead-assignees");
		Map<String, String> names = new LinkedHashMap<>();
		assignees.forEach(item -> names.put(item.path("userId").asString(), item.path("fullName").asString()));
		assertEquals("E2E Sale", names.get(SALE_ID));
		assertEquals("E2E Trưởng nhóm", names.get(LEADER_ID));

		// the territory list as the admin: An Hải first (Vietnamese collator), held by E2E Sale; no other ward is held
		// by an E2E account
		JsonNode territories = get(admin, "/api/v1/staff/sale-territories?provinceCode=48");
		assertEquals(16, territories.size(), "the 16 wards of province 48");
		assertEquals(AN_HAI, territories.get(0).path("wardCode").asString());
		assertEquals("An Hải", territories.get(0).path("wardName").asString());
		assertEquals(SALE_ID, territories.get(0).path("userId").asString());
		assertEquals("E2E Sale", territories.get(0).path("userName").asString());
		for (int i = 1; i < territories.size(); i++) {
			assertFalse(territories.get(i).path("userId").asString().startsWith("01JE2EACCT"),
					territories.get(i).path("wardName").asString());
		}

		// E2E Sale sees exactly the fixtures assigned to it (looked up by code, whatever else its queue holds)
		List<String> ofSale = List.of("SV-E2EA-AA2B", "SV-E2EA-AA2C", "SV-E2EA-AA2D", "SV-E2EA-AA2G", "SV-E2EA-AA2H",
				"SV-E2EA-AA2J", "SV-E2EA-AA2L", "SV-E2EA-AA2M");
		for (String code : CODES) {
			assertEquals(ofSale.contains(code) ? 1 : 0,
					get(sale, "/api/v1/staff/owner-leads?scope=mine&size=5&requestCode=" + code).path("items").size(),
					code + (ofSale.contains(code) ? " is in the queue of E2E Sale" : " is not in the queue of E2E Sale"));
		}

		// the detail calls wrote access logs; the next run deletes them with the fixture leads and still succeeds
		assertTrue(count("SELECT count(*) FROM owner_lead_access_logs WHERE lead_id LIKE '01JE2EHEAD%'") > 0);
		ExecResult again = runLeads("local");
		assertEquals(0, again.getExitCode(), "re-run after the API read failed: " + again.getStderr());
		assertEquals(List.of(12, 12, 25, 1, 1, 1), printedCounts(again));
		assertEquals(0, count("SELECT count(*) FROM owner_lead_access_logs WHERE lead_id LIKE '01JE2EHEAD%'"));
	}

	/**
	 * survey-schedule: E2E Sale books a survey on L11 and changes the requirement of L12 through the API; the next run
	 * removes every survey and requirement-change row of the fixtures and puts L11 and L12 back as they were.
	 */
	@Test
	void reseed_afterSurveyFlows_removesSurveyRows() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		String sale = accessToken("0900000102", salePassword());
		String l11 = LEAD_IDS.get(10);
		String l12 = LEAD_IDS.get(11);
		Instant start = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS).plus(Duration.ofHours(25));

		assertEquals(201, post(sale, "/api/v1/staff/owner-leads/" + l11 + "/survey-appointments", Map.of(
				"expectedLeadVersion", leadVersion(l11), "startAt", start.toString())));
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("expectedVersion", leadVersion(l12));
		change.put("newIntent", "LONG_TERM_RENT");
		change.put("askingPrice", null);
		change.put("reason", "Chủ nhà muốn cho thuê dài hạn");
		change.put("basis", Map.of("confirmedOn", java.time.LocalDate.now(SurveyTimeRules.ZONE).toString(),
				"text", "Chủ nhà nói qua điện thoại"));
		assertEquals(200, post(sale, "/api/v1/staff/owner-leads/" + l12 + "/intent-changes", change));
		get(sale, "/api/v1/staff/owner-leads/" + l11 + "/survey");
		assertEquals("SURVEY_SCHEDULED", jdbc.queryForObject("SELECT status FROM owner_leads WHERE id = ?",
				String.class, l11));

		ExecResult again = runLeads("local");

		assertEquals(0, again.getExitCode(), "re-run after the survey flows failed: " + again.getStderr());
		assertEquals(List.of(12, 12, 25, 1, 1, 1), printedCounts(again));
		for (String table : List.of("survey_appointments", "survey_access_logs", "survey_resumptions",
				"owner_lead_intent_changes")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE lead_id LIKE '01JE2EHEAD%'"), table);
		}
		assertEquals(0, count("SELECT count(*) FROM survey_appointment_revisions r LEFT JOIN survey_appointments a "
				+ "ON a.id = r.appointment_id WHERE a.id IS NULL"), "no revision left without its appointment");
		assertEquals(0, count("SELECT count(*) FROM survey_appointment_events e LEFT JOIN survey_appointments a "
				+ "ON a.id = e.appointment_id WHERE a.id IS NULL"), "no event left without its appointment");
		assertEquals(List.of("CONTACTED", "LONG_TERM_RENT", "1"), jdbc.queryForList("SELECT unnest(ARRAY[status, "
				+ "intent, intent_cycle::text]) FROM owner_leads WHERE id = ?", String.class, l11));
		assertEquals(List.of("ASSIGNED", "SALE", "1"), jdbc.queryForList("SELECT unnest(ARRAY[status, intent, "
				+ "intent_cycle::text]) FROM owner_leads WHERE id = ?", String.class, l12));
	}

	// ==================== helpers ====================

	private void assertNothingSeeded() {
		assertEquals(0, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
		assertEquals(0, count("SELECT count(*) FROM consent_records WHERE id LIKE '01JE2EHCNT%'"));
		assertEquals(0, count("SELECT count(*) FROM owner_lead_activities WHERE lead_id LIKE '01JE2EHEAD%'"));
		assertEquals(0, count("SELECT count(*) FROM owner_lead_duplicate_candidates WHERE lead_id LIKE '01JE2EHEAD%'"));
		assertEquals(0, count("SELECT count(*) FROM sale_territories WHERE ward_code = '" + AN_HAI + "'"));
		assertEquals(0, count("SELECT count(*) FROM sale_territory_changes WHERE id = 20261018000301"));
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private List<Integer> printedCounts(ExecResult result) {
		Matcher matcher = COUNTS.matcher(result.getStdout());
		assertTrue(matcher.find(), "the script prints the counts, stdout: " + result.getStdout());
		List<Integer> counts = new ArrayList<>();
		for (int group = 1; group <= matcher.groupCount(); group++) {
			counts.add(Integer.parseInt(matcher.group(group)));
		}
		return counts;
	}

	private List<String> fixtureSnapshot() {
		return jdbc.queryForList("SELECT t::text FROM owner_leads t WHERE id LIKE '01JE2EHEAD%' ORDER BY id",
				String.class);
	}

	/** The fixture leads without their time columns: what must be the same after a re-run. */
	private List<String> stableSnapshot() {
		return jdbc.queryForList("SELECT ROW(id, request_code, status, intent, contact_name, "
				+ "phone_normalized, ward_code, assigned_user_id, close_reason, duplicate_of_lead_id, version)::text "
				+ "FROM owner_leads WHERE id LIKE '01JE2EHEAD%' ORDER BY id", String.class);
	}

	private List<String> foreignSnapshot() {
		List<String> rows = new ArrayList<>();
		rows.addAll(jdbc.queryForList("SELECT t::text FROM owner_leads t WHERE id = ?", String.class, FOREIGN_LEAD));
		rows.addAll(jdbc.queryForList("SELECT t::text FROM consent_records t WHERE id = ?", String.class,
				FOREIGN_CONSENT));
		rows.addAll(jdbc.queryForList("SELECT t::text FROM owner_lead_activities t WHERE lead_id = ? ORDER BY id",
				String.class, FOREIGN_LEAD));
		rows.addAll(jdbc.queryForList("SELECT t::text FROM sale_territories t WHERE ward_code = ?", String.class,
				HAI_CHAU));
		rows.addAll(jdbc.queryForList("SELECT t::text FROM sale_territory_changes t WHERE id IN (?, ?) ORDER BY id",
				String.class, FOREIGN_CHANGE, FOREIGN_CHANGE_AN_HAI));
		return rows;
	}

	/** A lead with the same phone as L7, its consent and history, and a territory on another ward. */
	private void insertForeignRows() {
		jdbc.update("INSERT INTO consent_records (id, notice_version, purpose, channel, accepted_at) "
				+ "VALUES (?, 'owner-intake-2026-10', 'OWNER_INTAKE_CONTACT', 'WEB', now())", FOREIGN_CONSENT);
		jdbc.update("INSERT INTO owner_leads (id, request_code, status, intent, source, contact_name, "
				+ "phone_normalized, province_code, province_name, ward_code, ward_name, address_detail, address_key, "
				+ "consent_record_id, submission_snapshot, assigned_user_id, created_at, updated_at, version) "
				+ "VALUES (?, 'SV-ZZZZ-AA2A', 'ASSIGNED', 'SALE', 'WEB', 'Foreign Owner', '+84912000007', '48', "
				+ "'Đà Nẵng', ?, 'Hải Châu', '1 Foreign Street', '1 foreign street', ?, '{}', "
				+ "'01JE2EOTHERPERSON0000000A', now(), now(), 3)", FOREIGN_LEAD, HAI_CHAU, FOREIGN_CONSENT);
		jdbc.update("INSERT INTO owner_lead_activities (id, lead_id, type, occurred_at) "
				+ "VALUES (99000000000002, ?, 'SUBMITTED', now())", FOREIGN_LEAD);
		jdbc.update("INSERT INTO sale_territories (ward_code, user_id, assigned_by, assigned_at) "
				+ "VALUES (?, '01JE2EOTHERPERSON0000000A', '01JE2EOTHERPERSON0000000B', now())", HAI_CHAU);
		jdbc.update("INSERT INTO sale_territory_changes (id, ward_code, old_user_id, new_user_id, actor_user_id, "
				+ "occurred_at) VALUES (?, ?, NULL, '01JE2EOTHERPERSON0000000A', '01JE2EOTHERPERSON0000000B', now())",
				FOREIGN_CHANGE, HAI_CHAU);
		jdbc.update("INSERT INTO sale_territory_changes (id, ward_code, old_user_id, new_user_id, actor_user_id, "
				+ "occurred_at) VALUES (?, ?, NULL, '01JE2EOTHERPERSON0000000A', '01JE2EOTHERPERSON0000000B', now())",
				FOREIGN_CHANGE_AN_HAI, AN_HAI);
	}

	private String salePassword() {
		if (salePassword == null) {
			salePassword = randomPassword();
		}
		return salePassword;
	}

	private String leaderPassword() {
		if (leaderPassword == null) {
			leaderPassword = randomPassword();
		}
		return leaderPassword;
	}

	private String adminPassword() {
		if (adminPassword == null) {
			adminPassword = randomPassword();
		}
		return adminPassword;
	}

	private void seedAccounts() throws Exception {
		ExecResult result = execPsql(ACCOUNTS_SCRIPT, accountVariables("local", salePassword(), leaderPassword()));
		assertEquals(0, result.getExitCode(), "accounts seed failed: " + result.getStderr());
	}

	private List<String> accountVariables(String target, String sale, String leader) {
		return List.of("-v", "target=" + target, "-v", "customer_password=" + randomPassword(),
				"-v", "sale_password=" + sale, "-v", "leader_password=" + leader,
				"-v", "admin_password=" + adminPassword());
	}

	private ExecResult runLeads(String target) throws Exception {
		return execPsql(LEADS_SCRIPT, List.of("-v", "target=" + target));
	}

	private ExecResult execPsql(Path script, List<String> variables) throws Exception {
		return execPsql(script, variables, false);
	}

	/** {@code legacyClientEncoding}: psql starts with client_encoding WIN1252, like a Windows console code page. */
	private ExecResult execPsql(Path script, List<String> variables, boolean legacyClientEncoding) throws Exception {
		return execPsql(script, variables, legacyClientEncoding, List.of());
	}

	/** {@code connectionArgs}: extra psql arguments such as {@code -h <address>} (TCP instead of the Unix socket). */
	private ExecResult execPsql(Path script, List<String> variables, boolean legacyClientEncoding,
			List<String> connectionArgs) throws Exception {
		assertTrue(Files.isRegularFile(script), "seed script missing: " + script.toAbsolutePath().normalize());
		String inContainer = "/tmp/" + script.getFileName();
		postgres.copyFileToContainer(MountableFile.forHostPath(script), inContainer);
		List<String> command = new ArrayList<>();
		command.addAll(List.of("env", "PGPASSWORD=" + postgres.getPassword()));
		if (legacyClientEncoding) {
			command.add("PGCLIENTENCODING=WIN1252");
		}
		command.add("psql");
		command.addAll(connectionArgs);
		command.addAll(List.of("-U", postgres.getUsername(),
				"-d", postgres.getDatabaseName(), "-v", "ON_ERROR_STOP=1"));
		command.addAll(variables);
		command.addAll(List.of("-f", inContainer));
		return postgres.execInContainer(command.toArray(String[]::new));
	}

	/** The container's own address on the Docker network: reachable over TCP and not a loopback address. */
	private String containerAddress() throws Exception {
		ExecResult result = postgres.execInContainer("hostname", "-i");
		assertEquals(0, result.getExitCode(), "hostname -i failed: " + result.getStderr());
		String address = result.getStdout().trim().split("\s+")[0];
		assertFalse(address.isEmpty() || address.startsWith("127.") || address.equals("::1"), address);
		return address;
	}

	private String accessToken(String username, String password) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						json.writeValueAsString(Map.of("username", username, "password", password))))
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		// the body holds the token: never part of a message
		assertEquals(200, response.statusCode(), "login failed for " + username + ", status " + response.statusCode());
		String token = json.readTree(response.body()).path("accessToken").asString();
		assertFalse(token.isBlank(), "no access token for " + username);
		return token;
	}

	private JsonNode get(String accessToken, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		// the body holds names and phones: only the status goes into the message
		assertEquals(200, response.statusCode(), path + " answered " + response.statusCode());
		return json.readTree(response.body());
	}

	/** POST of the body as JSON; returns the status (the body holds lead data and is never printed). */
	private int post(String accessToken, String path, Object body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private long leadVersion(String leadId) {
		return jdbc.queryForObject("SELECT version FROM owner_leads WHERE id = ?", Long.class, leadId);
	}

	private static List<String> idsOf(JsonNode array) {
		List<String> ids = new ArrayList<>();
		array.forEach(node -> ids.add(node.path("id").asString()));
		return ids;
	}

	private static String randomPassword() {
		return "Aa1!" + UUID.randomUUID();
	}
}
