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
import java.math.BigDecimal;
import java.sql.Timestamp;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.survey.ChecklistApiTestSupport;
import vn.sunvibe.survey.SurveyApiTestSupport;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;

/**
 * The E2E fixture script {@code scripts/dev/seed-e2e-owner-leads.sql} (acquisition-owner-lead-queue-ui spec 4.4,
 * W11-11) run with psql inside the test PostgreSQL container, after {@code seed-e2e-accounts.sql}. The decisive proof
 * is that the application itself reads the fixtures: the staff API, called as the seeded leader, sale and admin,
 * shows the fifteen leads, their history, the duplicate link, the staff names and the one territory. Passwords are
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
			+ "activities=(\\d+) candidates=(\\d+) territories=(\\d+) territory_changes=(\\d+) "
			+ "appointments=(\\d+)");
	/**
	 * The printed counts of a complete run: 15 leads and consents, 40 History rows (L14 and L15 each add SUBMITTED,
	 * ASSIGNED, STATUS_CHANGED, SURVEY_BOOKED and SURVEY_VISITED to the 30 of L1..L13), the visited appointments of L13,
	 * L14 and L15.
	 */
	private static final List<Integer> SEEDED = List.of(15, 15, 40, 1, 1, 1, 3);

	private static final String CUSTOMER_ID = "01JE2EACCT0000000000000001";
	private static final String SALE_ID = "01JE2EACCT0000000000000002";
	private static final String LEADER_ID = "01JE2EACCT0000000000000003";
	private static final String ADMIN_ID = "01JE2EACCT0000000000000004";

	private static final String AN_HAI = "20275";
	private static final String HAI_CHAU = "20242";
	/** The fixture leads L1..L15 in order. */
	private static final List<String> LEAD_IDS = List.of(
			"01JE2EHEAD0000000000000001", "01JE2EHEAD0000000000000002", "01JE2EHEAD0000000000000003",
			"01JE2EHEAD0000000000000004", "01JE2EHEAD0000000000000005", "01JE2EHEAD0000000000000006",
			"01JE2EHEAD0000000000000007", "01JE2EHEAD0000000000000008", "01JE2EHEAD0000000000000009",
			"01JE2EHEAD0000000000000010", "01JE2EHEAD0000000000000011", "01JE2EHEAD0000000000000012",
			"01JE2EHEAD0000000000000013", "01JE2EHEAD0000000000000014", "01JE2EHEAD0000000000000015");
	/** L13's visited appointment (survey-checklist plan PD-7). */
	private static final String L13_VISIT = "01JE2ESVAPPT00000000000013";
	/** L14's visited appointment, for the survey-media E2E flows (survey-media spec 6.4). */
	private static final String L14_VISIT = "01JE2ESVAPPT00000000000014";
	/** L15's visited appointment, for the review flow (survey-checklist submission plan Task A15). */
	private static final String L15_VISIT = "01JE2ESVAPPT00000000000015";
	private static final List<String> CODES = List.of("SV-E2EA-AA2A", "SV-E2EA-AA2B", "SV-E2EA-AA2C", "SV-E2EA-AA2D",
			"SV-E2EA-AA2E", "SV-E2EA-AA2F", "SV-E2EA-AA2G", "SV-E2EA-AA2H", "SV-E2EA-AA2J", "SV-E2EA-AA2K",
			"SV-E2EA-AA2L", "SV-E2EA-AA2M", "SV-E2EA-AA2N", "SV-E2EA-AA2P", "SV-E2EA-AA2Q");
	private static final List<String> STATUSES = List.of("NEW", "ASSIGNED", "CONTACTED", "ON_HOLD", "ASSIGNED",
			"REJECTED", "ASSIGNED", "ASSIGNED", "ASSIGNED", "NEW", "CONTACTED", "ASSIGNED", "SURVEY_SCHEDULED",
			"SURVEY_SCHEDULED", "SURVEY_SCHEDULED");
	/** acquisition-owner-intake BR-3: L1..L15 use the six types and two leads have none. */
	private static final List<String> PROPERTY_TYPES = Arrays.asList("APARTMENT", "HOMESTAY", "CONDOTEL", "HOUSE",
			"WHOLE_BUILDING", null, "APARTMENT", "APARTMENT", null, "VILLA", "APARTMENT", "HOUSE", "HOUSE", "HOUSE",
			"HOUSE");

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

	/** W11-11: fifteen leads, one territory; a second run of both files changes no count and no id (idempotent). */
	@Test
	void seed_runTwice_fifteenLeadsOneTerritory_sameCountsTheSecondTime() throws Exception {
		seedAccounts();
		ExecResult first = runLeads("local");
		assertEquals(0, first.getExitCode(), "first run failed: " + first.getStderr());
		assertEquals(SEEDED, printedCounts(first));
		List<String> before = stableSnapshot();

		seedAccounts();
		ExecResult second = runLeads("local");
		assertEquals(0, second.getExitCode(), "second run failed: " + second.getStderr());
		assertEquals(SEEDED, printedCounts(second));

		assertEquals(15, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
		assertEquals(15, count("SELECT count(*) FROM consent_records WHERE id LIKE '01JE2EHCNT%'"));
		assertEquals(15, count("SELECT count(*) FROM owner_lead_activities WHERE type = 'SUBMITTED' "
				+ "AND lead_id LIKE '01JE2EHEAD%' AND actor_user_id IS NULL"));
		assertEquals(12, count("SELECT count(*) FROM owner_lead_activities WHERE type = 'ASSIGNED' "
				+ "AND lead_id LIKE '01JE2EHEAD%' AND actor_user_id = '" + LEADER_ID + "'"));
		assertEquals(40, count("SELECT count(*) FROM owner_lead_activities WHERE lead_id LIKE '01JE2EHEAD%'"));
		assertEquals(List.of("SUBMITTED", "ASSIGNED", "STATUS_CHANGED", "SURVEY_BOOKED", "SURVEY_VISITED"),
				jdbc.queryForList("SELECT type FROM owner_lead_activities WHERE lead_id = ? ORDER BY occurred_at, id",
						String.class, LEAD_IDS.get(13)));
		assertEquals(List.of(20261018000014L, 20261018000114L, 20261018000214L, 20261018000701L, 20261018000702L),
				jdbc.queryForList("SELECT id FROM owner_lead_activities WHERE lead_id = ? ORDER BY occurred_at, id",
						Long.class, LEAD_IDS.get(13)));
		assertEquals(List.of("SUBMITTED", "ASSIGNED", "STATUS_CHANGED", "SURVEY_BOOKED", "SURVEY_VISITED"),
				jdbc.queryForList("SELECT type FROM owner_lead_activities WHERE lead_id = ? ORDER BY occurred_at, id",
						String.class, LEAD_IDS.get(12)));
		assertEquals(List.of("VISITED/OWN/" + SALE_ID + "/" + SALE_ID), jdbc.queryForList("SELECT status || '/' || "
				+ "visit_record_kind || '/' || performed_by || '/' || visit_recorded_by FROM survey_appointments "
				+ "WHERE lead_id = ?", String.class, LEAD_IDS.get(12)));
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
		assertEquals(15, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
		assertEquals(16, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%' OR id = '" + FOREIGN_LEAD
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
		assertEquals(15, count("SELECT count(*) FROM owner_leads WHERE id LIKE '01JE2EHEAD%'"));
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
				"SV-E2EA-AA2J", "SV-E2EA-AA2L", "SV-E2EA-AA2M", "SV-E2EA-AA2N", "SV-E2EA-AA2P", "SV-E2EA-AA2Q");
		for (String code : CODES) {
			assertEquals(ofSale.contains(code) ? 1 : 0,
					get(sale, "/api/v1/staff/owner-leads?scope=mine&size=5&requestCode=" + code).path("items").size(),
					code + (ofSale.contains(code) ? " is in the queue of E2E Sale" : " is not in the queue of E2E Sale"));
		}

		// the detail calls wrote access logs; the next run deletes them with the fixture leads and still succeeds
		assertTrue(count("SELECT count(*) FROM owner_lead_access_logs WHERE lead_id LIKE '01JE2EHEAD%'") > 0);
		ExecResult again = runLeads("local");
		assertEquals(0, again.getExitCode(), "re-run after the API read failed: " + again.getStderr());
		assertEquals(SEEDED, printedCounts(again));
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
		assertEquals(SEEDED, printedCounts(again));
		for (String table : List.of("survey_access_logs", "survey_resumptions", "owner_lead_intent_changes")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE lead_id LIKE '01JE2EHEAD%'"), table);
		}
		assertEquals(List.of(L13_VISIT, L14_VISIT, L15_VISIT), jdbc.queryForList("SELECT id FROM survey_appointments WHERE lead_id"
				+ " LIKE '01JE2EHEAD%' ORDER BY id", String.class), "only the fixture visits of L13, L14 and L15");
		assertEquals(0, count("SELECT count(*) FROM survey_appointment_revisions r LEFT JOIN survey_appointments a "
				+ "ON a.id = r.appointment_id WHERE a.id IS NULL"), "no revision left without its appointment");
		assertEquals(0, count("SELECT count(*) FROM survey_appointment_events e LEFT JOIN survey_appointments a "
				+ "ON a.id = e.appointment_id WHERE a.id IS NULL"), "no event left without its appointment");
		assertEquals(List.of("CONTACTED", "LONG_TERM_RENT", "1"), jdbc.queryForList("SELECT unnest(ARRAY[status, "
				+ "intent, intent_cycle::text]) FROM owner_leads WHERE id = ?", String.class, l11));
		assertEquals(List.of("ASSIGNED", "SALE", "1"), jdbc.queryForList("SELECT unnest(ARRAY[status, intent, "
				+ "intent_cycle::text]) FROM owner_leads WHERE id = ?", String.class, l12));
	}


	/**
	 * survey-checklist: E2E Sale starts a checklist on L13 (the performer, so no intake), saves G1 and G3, the lead goes
	 * to E2E Trưởng nhóm who takes the draft over, and E2E Sale's read of it is refused with a security event; the next
	 * run removes every checklist row of the fixtures and L13 can be started again.
	 */
	@Test
	void reseed_afterChecklistFlows_removesChecklistRows() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		String sale = accessToken("0900000102", salePassword());
		String leader = accessToken("0900000103", leaderPassword());
		String l13 = LEAD_IDS.get(12);

		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L13_VISIT + "/checklists",
				nullIntake()));
		String checklistId = jdbc.queryForObject("SELECT id FROM survey_checklists WHERE lead_id = ?", String.class,
				l13);
		String checklistPath = "/api/v1/staff/survey-checklists/" + checklistId;
		ObjectNode g1 = ChecklistApiTestSupport.input(get(sale, checklistPath), "identification");
		g1.put("rentalScope", "WHOLE");
		ObjectNode space = ((ArrayNode) g1.path("spaces")).addObject();
		space.put("spaceId", "01JE2ESPACE000000000000001");
		space.put("code", "P1");
		space.put("location", "Tầng trệt");
		space.putNull("description");
		assertEquals(200, put(sale, checklistPath + "/identification", saveBody(0, g1)));
		ObjectNode g3 = ChecklistApiTestSupport.input(get(sale, checklistPath), "dimensions");
		ObjectNode area = (ObjectNode) g3.path("spaces").path(0).path("area");
		area.put("state", "VALUE");
		area.put("value", new BigDecimal("40.5"));
		area.put("source", "MEASURED");
		assertEquals(200, put(sale, checklistPath + "/dimensions", saveBody(0, g3)));
		assertEquals(200, post(leader, "/api/v1/staff/owner-leads/" + l13 + "/assign", Map.of("assigneeUserId",
				LEADER_ID, "expectedVersion", leadVersion(l13))));
		Map<String, Object> takeover = new LinkedHashMap<>();
		takeover.put("expectedVersion", jdbc.queryForObject("SELECT version FROM survey_checklists WHERE id = ?",
				Long.class, checklistId));
		takeover.put("reason", "Trưởng nhóm nhận lại");
		takeover.put("basis", Map.of("confirmedOn", java.time.LocalDate.now(SurveyTimeRules.ZONE).toString(),
				"text", "Đã trao đổi với nhân viên"));
		assertEquals(201, post(leader, checklistPath + "/takeovers", takeover));
		assertEquals(404, status(sale, checklistPath));
		assertEquals(1, count("SELECT count(*) FROM survey_security_events WHERE lead_id = '" + l13 + "'"));

		ExecResult again = runLeads("local");

		assertEquals(0, again.getExitCode(), "re-run after the checklist flows failed: " + again.getStderr());
		assertEquals(SEEDED, printedCounts(again));
		for (String table : List.of("survey_checklists", "survey_security_events", "survey_access_logs")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE lead_id LIKE '01JE2EHEAD%'"), table);
		}
		for (String table : List.of("survey_checklist_groups", "survey_checklist_takeovers",
				"survey_checklist_saves")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE checklist_id = '" + checklistId + "'"),
					table);
		}
		assertEquals(SALE_ID, jdbc.queryForObject("SELECT assigned_user_id FROM owner_leads WHERE id = ?",
				String.class, l13));
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L13_VISIT + "/checklists",
				nullIntake()));
	}

	/**
	 * survey-checklist submission plan Task A15: the insert-only rows of a review on L15 (a revision with its submission,
	 * frozen photo, decision, mark and a carried selection) never stop a re-run, and L15 can be started again.
	 */
	@Test
	void reseed_afterAReviewFlow_clearsItsRows() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		String sale = accessToken("0900000102", salePassword());
		String l15 = LEAD_IDS.get(14);
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L15_VISIT + "/checklists", nullIntake()));
		String first = jdbc.queryForObject("SELECT id FROM survey_checklists WHERE lead_id = ?", String.class, l15);
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("UPDATE survey_checklists SET status = 'NEEDS_CHANGES', submitted_at = ?, submitted_by_user_id = ?,"
				+ " decided_at = ? WHERE id = ?", now, SALE_ID, now, first);
		String second = "01JE2EREVISION000000000002";
		jdbc.update("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " kind, template_code, template_version, status, holder_user_id, created_by, created_at, version,"
				+ " previous_checklist_id) SELECT ?, lead_id, 2, visit_appointment_id, intent_cycle, kind, template_code,"
				+ " template_version, 'DRAFT', holder_user_id, created_by, created_at, 0, id FROM survey_checklists"
				+ " WHERE id = ?", second, first);
		String photo = "01JE2EPHOTO000000000000001";
		jdbc.update("INSERT INTO survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " VALUES (?, 1, 1, ?)", first, now);
		jdbc.update("INSERT INTO survey_media (id, checklist_id, upload_key, media_no, version, category,"
				+ " uploaded_by_user_id, created_at, declared_size_bytes, source_sha256, status) VALUES (?, ?,"
				+ " '01JE2EUPKEY000000000000004', 1, 0, 'EXTERIOR_ACCESS', ?, ?, 1000, ?, 'AWAITING_CONTENT')", photo,
				first, SALE_ID, now, "a".repeat(64));
		jdbc.update("INSERT INTO survey_checklist_submissions (checklist_id, submitted_by_user_id, submitted_at,"
				+ " template_code, template_version, kind, lead_intent, intent_cycle, visit_appointment_id,"
				+ " visit_performed_by, visit_arrived_at, visit_recorded_by, visit_recorded_at, rental_scope, spaces,"
				+ " group_versions, media_policy, content_sha256) SELECT id, ?, ?, template_code, template_version, kind,"
				+ " 'LONG_TERM_RENT', intent_cycle, visit_appointment_id, ?, ?, ?, ?, 'WHOLE', '[]', '{}',"
				+ " 'MEDIA_V1_NO_PERMISSION', ? FROM survey_checklists WHERE id = ?", SALE_ID, now, SALE_ID, now, SALE_ID,
				now, "a".repeat(64), first);
		jdbc.update("INSERT INTO survey_checklist_evidence (checklist_id, media_id, category, full_sha256) VALUES"
				+ " (?, ?, 'EXTERIOR_ACCESS', ?)", first, photo, "a".repeat(64));
		jdbc.update("INSERT INTO survey_checklist_decisions (checklist_id, outcome, needs_changes_kind, reason,"
				+ " reviewer_user_id, decided_at, lead_status_before, lead_status_after) VALUES (?, 'NEEDS_CHANGES',"
				+ " 'DOCUMENTS_ONLY', 'Thiếu giấy tờ', ?, ?, 'SURVEY_SCHEDULED', 'SURVEY_SCHEDULED')", first, LEADER_ID, now);
		jdbc.update("INSERT INTO survey_checklist_evidence_rejections (checklist_id, media_id, reason) VALUES (?, ?,"
				+ " 'Ảnh mờ')", first, photo);
		jdbc.update("INSERT INTO survey_checklist_media_selections (checklist_id, media_id, carried_from_checklist_id,"
				+ " selected_at) VALUES (?, ?, ?, ?)", second, photo, first, now);

		ExecResult again = runLeads("local");

		assertEquals(0, again.getExitCode(), "re-run after a review failed: " + again.getStderr());
		assertEquals(SEEDED, printedCounts(again));
		for (String table : List.of("survey_checklist_evidence_rejections", "survey_checklist_evidence",
				"survey_checklist_media_selections")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE media_id = '" + photo + "'"), table);
		}
		for (String table : List.of("survey_checklist_decisions", "survey_checklist_submissions")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE checklist_id = '" + first + "'"), table);
		}
		assertEquals(0, count("SELECT count(*) FROM survey_checklists WHERE lead_id = '" + l15 + "'"));
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L15_VISIT + "/checklists", nullIntake()));
	}

	/** property-create requalification plan Task A6: the row of a mark on L15 never stops a re-run. */
	@Test
	void reseed_afterARequalification_clearsItsRows() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		String sale = accessToken("0900000102", salePassword());
		String l15 = LEAD_IDS.get(14);
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L15_VISIT + "/checklists", nullIntake()));
		String checklist = jdbc.queryForObject("SELECT id FROM survey_checklists WHERE lead_id = ?", String.class, l15);
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("UPDATE survey_checklists SET status = 'PASSED', submitted_at = ?, submitted_by_user_id = ?,"
				+ " decided_at = ?, requalified_at = ? WHERE id = ?", now, SALE_ID, now, now, checklist);
		jdbc.update("INSERT INTO survey_checklist_requalifications (checklist_id, reason_kind, reason, event_source, scope,"
				+ " basis_date, marked_by_user_id, marked_at, lead_status_before, lead_status_after) VALUES (?, 'WRONG_ASSET',"
				+ " 'Sai tài sản', 'Chủ nhà báo', 'Toàn bộ', CURRENT_DATE, ?, ?, 'QUALIFIED', 'CONTACTED')", checklist,
				LEADER_ID, now);

		ExecResult again = runLeads("local");

		assertEquals(0, again.getExitCode(), "re-run after a requalification failed: " + again.getStderr());
		assertEquals(SEEDED, printedCounts(again));
		assertEquals(0, count("SELECT count(*) FROM survey_checklist_requalifications WHERE checklist_id = '" + checklist
				+ "'"));
		assertEquals(0, count("SELECT count(*) FROM survey_checklists WHERE lead_id = '" + l15 + "'"));
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L15_VISIT + "/checklists", nullIntake()));
	}

	/**
	 * property-create intake plan Task A17: what an inventory-intake run leaves on L15 goes with the next seed, the property
	 * it created included, so the next CREATE opens no case against a leftover.
	 */
	@Test
	void reseed_afterCompletion_clearsRows() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		String sale = accessToken("0900000102", salePassword());
		String l15 = LEAD_IDS.get(14);
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L15_VISIT + "/checklists", nullIntake()));
		String checklist = jdbc.queryForObject("SELECT id FROM survey_checklists WHERE lead_id = ?", String.class, l15);
		Timestamp now = Timestamp.from(Instant.now());
		String intake = "01JE2EINTAKE00000000000015";
		String request = "01JE2EREQUEST0000000000015";
		String proof = "01JE2EPROOF000000000000015";
		String property = "e2e00000-0000-0000-0000-000000000015";
		String unit = "01JE2EUNIT0000000000000015";
		jdbc.update("INSERT INTO inventory_intakes (id, intake_no, lead_id, intent_cycle, status, source_checklist_id, source_sha256,"
				+ " rental_scope, asset_type, ward_code, address_norm, selection, created_at, updated_at) VALUES (?, 1, ?, 1, 'DRAFT',"
				+ " ?, ?, 'WHOLE', 'HOUSE', ?, 'an hai', '[]', ?, ?)", intake, l15, checklist, "a".repeat(64), AN_HAI, now, now);
		jdbc.update("INSERT INTO inventory_owner_requests (id, intake_id, code_hash, expires_at, created_by_user_id, created_at)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", request, intake, "b".repeat(64), now, SALE_ID, now);
		jdbc.update("INSERT INTO inventory_owner_proofs (id, intake_id, request_id, lead_id, intent_cycle, checklist_id, user_id,"
				+ " scope_hash, statement_version, confirmed_at, owner_check, sign_in_losses) VALUES (?, ?, ?, ?, 1, ?, ?, ?,"
				+ " 'owner-link-2026-11', ?, 'DONE', 0)", proof, intake, request, l15, checklist, CUSTOMER_ID, "c".repeat(64), now);
		jdbc.update("INSERT INTO properties (id, property_code, title, street, ward, city, owner_id, created_at, updated_at, created_by,"
				+ " updated_by, asset_type, rental_layout, intake_id, ward_code, address_norm) VALUES (?, 'SV-HOU-999915', 'Whole house, An"
				+ " Hải', 'An Hải', 'An Hải', 'Đà Nẵng', ?, ?, ?, 'seed', 'seed', 'HOUSE', 'WHOLE', ?, ?, 'an hai')", property, CUSTOMER_ID,
				now, now, intake, AN_HAI);
		jdbc.update("INSERT INTO units (id, property_id, unit_code, bedrooms, bathrooms, area_sqm, availability_state, created_at,"
				+ " updated_at) VALUES (?, ?, 'SV-HOU-999915', 1, 1, 30, 'AVAILABLE', ?, ?)", unit, property, now, now);
		jdbc.update("INSERT INTO property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
				+ " created_at) VALUES (?, ?, ?, 'OWNER', 'UNVERIFIED', CURRENT_DATE, ?)", "01JE2EOWNERSHIP00000000015", property,
				CUSTOMER_ID, now);
		jdbc.update("INSERT INTO inventory_unit_mappings (intake_id, space_id, space_code, unit_id, canonical) VALUES (?, 'S', 'A1',"
				+ " ?, true)", intake, unit);
		jdbc.update("UPDATE inventory_intakes SET status = 'COMPLETED', result_property_id = ?, proof_id = ?, completed_at = ?"
				+ " WHERE id = ?", property, proof, now, intake);
		jdbc.update("INSERT INTO inventory_intake_events (intake_id, actor_user_id, action, occurred_at) VALUES (?, ?, 'COMPLETION', ?)",
				intake, SALE_ID, now);
		jdbc.update("INSERT INTO inventory_suspensions (id, lead_id, intake_id, reason, created_by_user_id, created_at) VALUES"
				+ " ('01JE2ESUSPEND0000000000015', ?, ?, 'Held', ?, ?)", l15, intake, ADMIN_ID, now);
		jdbc.update("INSERT INTO inventory_duplicate_cases (id, intake_id, candidate_property_id, pair_key, scope_hash, signal, status,"
				+ " created_at) VALUES ('01JE2ECASE0000000000000015', ?, ?, 'P:e2e', ?, 'ADDRESS', 'OPEN', ?)", intake, property,
				"c".repeat(64), now);
		jdbc.update("INSERT INTO inventory_access_logs (user_id, lead_id, intake_id, action, accessed_at) VALUES (?, ?, ?, 'BLOCK_READ', ?)",
				SALE_ID, l15, intake, now);

		ExecResult again = runLeads("local");

		assertEquals(0, again.getExitCode(), "re-run after an intake failed: " + again.getStderr());
		assertEquals(SEEDED, printedCounts(again));
		for (String table : List.of("inventory_intakes", "inventory_suspensions", "inventory_access_logs")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE lead_id = '" + l15 + "'"), table);
		}
		for (String table : List.of("inventory_owner_requests", "inventory_owner_proofs", "inventory_unit_mappings",
				"inventory_intake_events", "inventory_duplicate_cases")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE intake_id = '" + intake + "'"), table);
		}
		assertEquals(0, count("SELECT count(*) FROM properties WHERE id = '" + property + "'"));
		assertEquals(0, count("SELECT count(*) FROM units WHERE id = '" + unit + "'"));
		assertEquals(0, count("SELECT count(*) FROM property_ownerships WHERE property_id = '" + property + "'"));

		ExecResult third = runLeads("local");

		assertEquals(0, third.getExitCode(), "a second run failed: " + third.getStderr());
		assertEquals(SEEDED, printedCounts(third));
	}

	/**
	 * survey-media spec 6.4 (plan Task A23): after the media flows on L14 (a checklist started through the API, photos
	 * in the shapes create, content, removal and an ADMIN hold leave, their set, job and variants, the budget and slot
	 * rows of the fixture users), the next run removes every one of them and L14 can be started again.
	 */
	@Test
	void reseed_afterMediaFlows_removesMediaRows() throws Exception {
		seedAccounts();
		assertEquals(0, runLeads("local").getExitCode());
		String sale = accessToken("0900000102", salePassword());
		String l14 = LEAD_IDS.get(13);
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L14_VISIT + "/checklists", nullIntake()));
		String checklistId = jdbc.queryForObject("SELECT id FROM survey_checklists WHERE lead_id = ?", String.class,
				l14);
		jdbc.update("INSERT INTO survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " VALUES (?, 4, 3, now())", checklistId);
		String[] ids = {"01JE2EMEDIA000000000000001", "01JE2EMEDIA000000000000002", "01JE2EMEDIA000000000000003"};
		jdbc.update("INSERT INTO survey_media (id, checklist_id, upload_key, media_no, version, category,"
				+ " uploaded_by_user_id, created_at, declared_size_bytes, source_sha256, status) VALUES"
				+ " (?, ?, '01JE2EUPKEY000000000000001', 1, 0, 'EXTERIOR_ACCESS', ?, now(), 1000, ?, 'AWAITING_CONTENT')",
				ids[0], checklistId, SALE_ID, "a".repeat(64));
		jdbc.update("INSERT INTO survey_media (id, checklist_id, upload_key, media_no, version, category,"
				+ " uploaded_by_user_id, created_at, declared_size_bytes, source_sha256, status, content_received_at,"
				+ " source_storage_key, held_at, held_by_user_id, hold_reason) VALUES (?, ?,"
				+ " '01JE2EUPKEY000000000000002', 2, 0, 'DETAIL', ?, now(), 1000, ?, 'PROCESSING', now(), ?, now(), ?,"
				+ " 'SECURITY_INCIDENT')", ids[1], checklistId, SALE_ID, "b".repeat(64),
				"quarantine/" + ids[1] + "/1", ADMIN_ID);
		jdbc.update("INSERT INTO survey_media (id, checklist_id, upload_key, media_no, version, category,"
				+ " uploaded_by_user_id, created_at, declared_size_bytes, source_sha256, status, content_received_at,"
				+ " source_storage_key, source_deleted_at, source_format, ready_at, finished_at, removed_at,"
				+ " removed_by_user_id, removal_kind, content_delete_requested_at) VALUES (?, ?,"
				+ " '01JE2EUPKEY000000000000003', 3, 0, 'DETAIL', ?, now(), 1000, ?, 'READY', now(), ?, now(), 'JPEG',"
				+ " now(), now(), now(), ?, 'HOLDER', now())", ids[2], checklistId, SALE_ID, "c".repeat(64),
				"quarantine/" + ids[2] + "/1", SALE_ID);
		jdbc.update("INSERT INTO survey_media_variants (media_id, variant, attempt, storage_key, sha256, size_bytes,"
				+ " width, height, processor_version, created_at) VALUES (?, 'FULL', 1, ?, ?, 3, 400, 300, 'test-9',"
				+ " now())", ids[2], "media/" + ids[2] + "/1/FULL", "d".repeat(64));
		jdbc.update("INSERT INTO survey_media_jobs (media_id, kind, state, attempts, limit_hits, due_at, created_at,"
				+ " updated_at) VALUES (?, 'PROCESS', 'PENDING', 0, 0, now(), now(), now()), (?, 'DELETE_CONTENT',"
				+ " 'PENDING', 0, 0, now(), now(), now())", ids[1], ids[2]);
		jdbc.update("INSERT INTO survey_media_read_budgets (user_id, hour_start, kind, read_count) VALUES"
				+ " (?, date_trunc('hour', now()), 'LIST', 3), (?, date_trunc('hour', now()), 'FULL', 1)", SALE_ID,
				ADMIN_ID);
		jdbc.update("INSERT INTO survey_media_body_slots (user_id, instance_id, taken_at, expires_at) VALUES"
				+ " (?, 'e2e-seed-test', now(), now() + interval '1 minute')", SALE_ID);

		ExecResult again = runLeads("local");

		assertEquals(0, again.getExitCode(), "re-run after the media flows failed: " + again.getStderr());
		assertEquals(SEEDED, printedCounts(again));
		for (String table : List.of("survey_media_jobs", "survey_media_variants")) {
			assertEquals(0, count("SELECT count(*) FROM " + table + " WHERE media_id LIKE '01JE2EMEDIA%'"), table);
		}
		assertEquals(0, count("SELECT count(*) FROM survey_media WHERE id LIKE '01JE2EMEDIA%'"));
		assertEquals(0, count("SELECT count(*) FROM survey_media_sets WHERE checklist_id = '" + checklistId + "'"));
		assertEquals(0, count("SELECT count(*) FROM survey_checklists WHERE lead_id LIKE '01JE2EHEAD%'"));
		assertEquals(0, count("SELECT count(*) FROM survey_checklist_takeovers WHERE checklist_id = '" + checklistId
				+ "'"));
		assertEquals(0, count("SELECT count(*) FROM survey_media_read_budgets WHERE user_id IN ('" + SALE_ID + "', '"
				+ LEADER_ID + "', '" + ADMIN_ID + "')"));
		assertEquals(0, count("SELECT count(*) FROM survey_media_body_slots WHERE user_id IN ('" + SALE_ID + "', '"
				+ LEADER_ID + "', '" + ADMIN_ID + "')"));
		assertEquals(201, post(sale, "/api/v1/staff/survey-appointments/" + L14_VISIT + "/checklists", nullIntake()));
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

	/** PUT of the body as JSON; returns the status (the body holds survey data and is never printed). */
	private int put(String accessToken, String path, Object body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	/** GET; returns only the status. */
	private int status(String accessToken, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(30))
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private static Map<String, Object> nullIntake() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("intake", null);
		return body;
	}

	private static Map<String, Object> saveBody(long expectedVersion, ObjectNode content) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", expectedVersion);
		body.put("correction", null);
		body.put("content", content);
		return body;
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
