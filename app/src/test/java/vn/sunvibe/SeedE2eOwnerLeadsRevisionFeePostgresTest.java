package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.testcontainers.utility.MountableFile;

import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.PostgresContainerConfig.PostgresTestDatabase;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The E2E lead seed and the fee rows of a listing revision (listing-publish E06-S2 spec E06S2-D15, U-5): the seed's
 * revision delete removes them through {@code ON DELETE CASCADE}, so the script needs no edit. The new case beside
 * {@link SeedE2eOwnerLeadsPostgresTest}, which stays untouched (plan E06S2-P1): its annotations (:57-66), the script
 * paths, {@code COUNTS}, {@code SEEDED}, the account and ward constants, {@code LEAD_IDS}, {@code FOREIGN_CHANGE},
 * {@code FOREIGN_CHANGE_AN_HAI}, {@code THANH_KHE} (:68-116), its fields (:119-137), its per-test cleanup
 * {@code removeFixtures} and {@code tearDown} (:139-161), and the helpers {@code count}, {@code printedCounts}, the
 * password accessors, {@code seedAccounts}, {@code accountVariables}, {@code runLeads}, the three {@code execPsql} and
 * {@code randomPassword} (:1218-1230, :1280-1343, :1437-1439) are copied as written, so this class shares its
 * context; the cleanup gains only the delete of this class's fee rows, before their revision. Passwords are random per
 * run and never printed.
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
class SeedE2eOwnerLeadsRevisionFeePostgresTest {

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

	private static final long FOREIGN_CHANGE = 99000000000001L;
	/** A change row another account wrote for ward An Hải; the fixed-id delete of the script must keep it. */
	private static final long FOREIGN_CHANGE_AN_HAI = 99000000000003L;
	private static final String THANH_KHE = "20209";

	/** The rows of this class's case, ids and code no other class uses. */
	private static final String AGREEMENT = "01JE2EREVFEEAGREEMENT00015";
	private static final String VERSION = "01JE2EREVFEEAGRVERSION0015";
	private static final String UNIT = "01JE2EREVFEEUNIT0000000015";
	private static final String LISTING = "01JE2EREVFEELISTING0000015";
	private static final String REVISION = "01JE2EREVFEELISTREV0000015";
	private static final String HISTORY = "01JE2EREVFEELISTHIS0000015";

	@Value("${local.server.port}")
	private int port;

	/** The database of this context on the shared server (PostgresContainerConfig). */
	@Autowired
	private PostgresTestDatabase database;

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
		// the only rows this slice alone writes: the fee rows of this class's revision, before their revision
		jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id = ?", REVISION);
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

	/**
	 * listing-publish E06-S2 spec E06S2-D15, U-5: a fee row under the revision of a listing whose agreement the seed
	 * deletes goes with its revision, so the seed runs again with no edit and leaves no fee row, revision or listing.
	 */
	@Test
	void reseed_afterARevisionFee_clearsItsFeeRows() throws Exception {
		try {
			seedAccounts();
			assertEquals(0, runLeads("local").getExitCode());
			String l15 = LEAD_IDS.get(14);
			Timestamp now = Timestamp.from(Instant.now());
			// A DRAFTING agreement with its version 1 DRAFT on L15, as the neighbour's listing case builds it.
			jdbc.update("INSERT INTO brokerage_agreements (id, unit_id, unit_code, property_id, transaction_type,"
					+ " owner_user_id, basis_lead_id, status, created_by_user_id, created_at, updated_at, version) VALUES (?, ?,"
					+ " 'SV-HOU-999915', 'e2e00000-0000-0000-0000-000000000015', 'LONG_TERM_RENT', ?, ?, 'DRAFTING', ?, ?, ?, 0)",
					AGREEMENT, UNIT, CUSTOMER_ID, l15, SALE_ID, now, now);
			jdbc.update("INSERT INTO brokerage_agreement_versions (id, agreement_id, version_no, status, start_date, end_date,"
					+ " basis_lead_id, basis_intake_id, created_by_user_id, created_at, updated_at) VALUES (?, ?, 1, 'DRAFT',"
					+ " DATE '2027-01-01', DATE '2027-12-31', ?, '01JE2EINTAKE00000000000015', ?, ?, ?)", VERSION, AGREEMENT,
					l15, SALE_ID, now, now);
			// Its listing, revision 1 in DRAFT with an IN_EFFECT terms snapshot and one fee row, and its CREATED row.
			jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
					+ " created_at, updated_at) VALUES (?, 'TD-E2ER-FEEA', ?, 'LONG_TERM_RENT', ?, 'UNPUBLISHED', ?, ?, ?)",
					LISTING, UNIT, AGREEMENT, SALE_ID, now, now);
			jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
					+ " updated_at, terms_source, terms_captured_at, terms_monthly_rent, terms_deposit_basis,"
					+ " terms_deposit_months, terms_minimum_term_months, terms_payment_period_months) VALUES (?, ?, 1,"
					+ " 'DRAFT', ?, ?, ?, 'IN_EFFECT', ?, 8000000, 'MONTHS', 2, 6, 1)", REVISION, LISTING, SALE_ID, now, now,
					now);
			jdbc.update("INSERT INTO listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
					+ " VALUES (?, 1, 'Electricity', 'AMOUNT', 3500, 'PER_KWH')", REVISION);
			jdbc.update("INSERT INTO listing_history (id, listing_id, action, revision_no, actor_user_id, occurred_at)"
					+ " VALUES (?, ?, 'CREATED', 1, ?, ?)", HISTORY, LISTING, SALE_ID, now);
			assertEquals(1, count("SELECT count(*) FROM listing_revision_fees WHERE revision_id = '" + REVISION + "'"));

			ExecResult again = runLeads("local");

			assertEquals(0, again.getExitCode(), "re-run after a revision fee failed: " + again.getStderr());
			assertEquals(SEEDED, printedCounts(again));
			assertEquals(0, count("SELECT count(*) FROM listing_revision_fees WHERE revision_id = '" + REVISION + "'"));
			assertEquals(0, count("SELECT count(*) FROM listing_revisions WHERE listing_id = '" + LISTING + "'"));
			assertEquals(0, count("SELECT count(*) FROM listings WHERE id = '" + LISTING + "'"));
		} finally {
			jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id = ?", REVISION);
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", LISTING);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", LISTING);
			jdbc.update("DELETE FROM listings WHERE id = ?", LISTING);
			jdbc.update("DELETE FROM brokerage_agreement_events WHERE agreement_id = ?", AGREEMENT);
			jdbc.update("DELETE FROM brokerage_agreement_versions WHERE agreement_id = ?", AGREEMENT);
			jdbc.update("DELETE FROM brokerage_agreements WHERE id = ?", AGREEMENT);
		}
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
		database.container().copyFileToContainer(MountableFile.forHostPath(script), inContainer);
		List<String> command = new ArrayList<>();
		command.addAll(List.of("env", "PGPASSWORD=" + database.container().getPassword()));
		if (legacyClientEncoding) {
			command.add("PGCLIENTENCODING=WIN1252");
		}
		command.add("psql");
		command.addAll(connectionArgs);
		command.addAll(List.of("-U", database.container().getUsername(),
				"-d", database.name(), "-v", "ON_ERROR_STOP=1"));
		command.addAll(variables);
		command.addAll(List.of("-f", inContainer));
		return database.container().execInContainer(command.toArray(String[]::new));
	}

	private static String randomPassword() {
		return "Aa1!" + UUID.randomUUID();
	}
}
