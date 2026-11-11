package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.assertSecurityBody;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.fieldNames;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * {@code POST /api/v1/staff/owner-leads/assignments} end to end (acquisition-owner-lead-bulk-assign spec 4.1; AC-1, AC-2,
 * AC-4 to AC-9): real logins through the security chain and the role hierarchy, leads sent through the public intake
 * form in Bà Nà, a ward nobody holds (so they stay NEW until a leader gives them out), the lead services on PostgreSQL
 * migrated by Flyway. The context set is that of {@code OwnerLeadApiPostgresTest}, copied as written, so both classes
 * share one Spring context. Every lead and user made here is removed afterwards. Needs Docker.
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
class OwnerLeadBulkAssignApiPostgresTest {

	private static final String LEADS = "/api/v1/staff/owner-leads";
	private static final String ASSIGNMENTS = LEADS + "/assignments";
	private static final String BA_NA = "20308";
	private static final Instant NEXT_ACTION = Instant.parse("2027-01-15T03:00:00Z");
	private static final String SECRET = "SECRETVALUE";

	/** Phones 0999xxxxxx, apart from the other test classes. */
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

	private TestUser sale1;
	private TestUser sale2;
	private TestUser saleLeader;
	private TestUser salesManager;
	private TestUser admin;
	private TestUser superAdmin;
	private TestUser lockedSale;
	private TestUser deletedSale;
	private TestUser customer;
	/** LISTING_REVIEWER, OWNER, CUSTOMER, EMPLOYEE, MANAGER, LEADER: no access to the lead queue at all. */
	private final Map<String, TestUser> otherRoles = new LinkedHashMap<>();

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		sale1 = loggedIn("SALE");
		sale2 = loggedIn("SALE");
		saleLeader = loggedIn("SALE_LEADER");
		salesManager = loggedIn("SALES_MANAGER");
		admin = loggedIn("ADMIN");
		superAdmin = loggedIn("SUPER_ADMIN");
		for (String role : List.of("LISTING_REVIEWER", "OWNER", "CUSTOMER", "EMPLOYEE", "MANAGER", "LEADER")) {
			otherRoles.put(role, loggedIn(role));
		}
		customer = otherRoles.get("CUSTOMER");
		lockedSale = created(UserStatus.LOCK, "SALE");
		deletedSale = created(UserStatus.ACTIVE, "SALE");
		jdbc.update("update users set deleted_at = now() where id = ?", deletedSale.userId());
	}

	@AfterAll
	void tearDown() {
		AcquisitionApiTestSupport.deleteLeads(jdbc, leadIds);
		AcquisitionApiTestSupport.deleteUsers(jdbc, userIds);
		api.http().close();
	}

	// ==================== AC-1: who may call ====================

	/**
	 * acquisition-owner-lead-bulk-assign AC-1, BR-1: SALE_LEADER, SALES_MANAGER, ADMIN and SUPER_ADMIN (role hierarchy)
	 * assign; a SALE and every other role get the security 403 and a caller without a token the 401, each with a valid
	 * body naming a real lead, which keeps its status, assignee, version and History.
	 */
	@Test
	void assignLeads_roles_onlyLeadersManagersAndAdminAssign() throws Exception {
		String unknown = ulidGenerator.generateId();
		for (TestUser caller : List.of(saleLeader, salesManager, admin, superAdmin)) {
			Result result = api.post(caller, ASSIGNMENTS, body(sale2, null, item(unknown, 0)));

			assertEquals(List.of("NOT_FOUND"), outcomes(result, List.of(unknown)), caller.toString());
		}

		String lead = submit();
		LeadState before = state(lead);
		Map<String, Object> valid = body(sale2, "Chuyển lead", item(lead, before.version()));

		assertSecurityBody(api.post(sale1, ASSIGNMENTS, valid), 403);
		for (TestUser caller : otherRoles.values()) {
			assertSecurityBody(api.post(caller, ASSIGNMENTS, valid), 403);
		}
		assertSecurityBody(api.post(null, ASSIGNMENTS, valid), 401);

		assertEquals(before, state(lead));
		assertEquals("NEW", before.status());
		assertNull(before.assignee());
	}

	// ==================== AC-2, AC-8, AC-9: what each assigned lead gets ====================

	/**
	 * acquisition-owner-lead-bulk-assign AC-2, AC-8, AC-9, BR-4: twin leads, one of each pair given out by the single
	 * assignment and the other by the bulk one, to the same person with the same note, get History rows equal in every
	 * column but id, lead and time; the lead row moves the same way, the earlier History and the next action stay, no
	 * access-log row is written, and the previous holder loses the reassigned lead at once.
	 */
	@Test
	void assignLeads_newAndAssignedLeads_eachWritesWhatOneAssignmentWrites() throws Exception {
		String note = "  Chuyển lead khi Minh nghỉ phép  ";
		String newSingle = submit();
		String newBulk = submit();
		String heldSingle = heldBy(sale1);
		String heldBulk = heldBy(sale1);
		for (String held : List.of(heldSingle, heldBulk)) {
			Result planned = api.post(sale1, LEADS + "/" + held + "/activities",
					Map.of("note", "Hẹn gọi lại", "nextActionAt", NEXT_ACTION.toString()));
			assertEquals(201, planned.status(), planned.rawBody());
		}
		Map<String, LeadState> before = new LinkedHashMap<>();
		Map<String, List<Map<String, Object>>> historyBefore = new LinkedHashMap<>();
		for (String lead : List.of(newSingle, newBulk, heldSingle, heldBulk)) {
			before.put(lead, state(lead));
			historyBefore.put(lead, history(lead));
		}

		for (String single : List.of(newSingle, heldSingle)) {
			Result assigned = api.post(saleLeader, LEADS + "/" + single + "/assign", Map.of("assigneeUserId",
					sale2.userId(), "expectedVersion", before.get(single).version(), "note", note));
			assertEquals(200, assigned.status(), assigned.rawBody());
		}
		Result bulk = api.post(saleLeader, ASSIGNMENTS, body(sale2, note,
				item(newBulk, before.get(newBulk).version()), item(heldBulk, before.get(heldBulk).version())));

		assertEquals(List.of("ASSIGNED", "ASSIGNED"), outcomes(bulk, List.of(newBulk, heldBulk)));
		for (String[] pair : List.of(new String[] { newSingle, newBulk }, new String[] { heldSingle, heldBulk })) {
			assertEquals(lastRowWithoutIdentity(pair[0]), lastRowWithoutIdentity(pair[1]));
		}
		for (String lead : List.of(newBulk, heldBulk)) {
			LeadState after = state(lead);
			assertEquals("ASSIGNED", after.status());
			assertEquals(sale2.userId(), after.assignee());
			assertEquals(before.get(lead).version() + 1, after.version());
			assertEquals(before.get(lead).nextActionAt(), after.nextActionAt());
			List<Map<String, Object>> rows = history(lead);
			assertEquals(historyBefore.get(lead).size() + 1, rows.size());
			assertEquals(historyBefore.get(lead), rows.subList(0, rows.size() - 1));
			assertEquals(0, accessLogs(lead));
		}
		Map<String, Object> assignedRow = lastRowWithoutIdentity(newBulk);
		assertEquals("ASSIGNED", assignedRow.get("type"));
		assertEquals("NEW", assignedRow.get("from_status"));
		assertEquals("ASSIGNED", assignedRow.get("to_status"));
		assertEquals(sale2.userId(), assignedRow.get("to_assignee_id"));
		assertEquals(saleLeader.userId(), assignedRow.get("actor_user_id"));
		assertEquals("Chuyển lead khi Minh nghỉ phép", assignedRow.get("note"));
		Map<String, Object> reassignedRow = lastRowWithoutIdentity(heldBulk);
		assertEquals("REASSIGNED", reassignedRow.get("type"));
		assertEquals(sale1.userId(), reassignedRow.get("from_assignee_id"));
		assertEquals(sale2.userId(), reassignedRow.get("to_assignee_id"));
		assertEquals("Chuyển lead khi Minh nghỉ phép", reassignedRow.get("note"));
		assertNull(reassignedRow.get("reference_id"));
		assertFalse(bulk.rawBody().contains("Chuyển lead"), "the note is not in the answer");

		long version = state(heldBulk).version();
		assertEquals(404, api.get(sale1, LEADS + "/" + heldBulk).status());
		assertEquals(404, api.post(sale1, LEADS + "/" + heldBulk + "/status-changes",
				Map.of("toStatus", "CONTACTED", "expectedVersion", version)).status());
		assertEquals(404, api.post(sale1, LEADS + "/" + heldBulk + "/activities", Map.of("note", "Còn tôi")).status());
		Result mine = api.get(sale1, LEADS + "?size=100");
		assertEquals(200, mine.status(), mine.rawBody());
		assertFalse(listedIds(mine).contains(heldBulk));
		assertEquals(version, state(heldBulk).version());
	}

	// ==================== AC-4, AC-5: one outcome per lead ====================

	/**
	 * acquisition-owner-lead-bulk-assign AC-4, BR-5, BR-7: one result per item in request order, each naming the id as
	 * sent: an unknown id and a non-ULID id NOT_FOUND, a closed lead CLOSED, a lead the assignee holds ALREADY_ASSIGNED
	 * even with a wrong version, another lead with a wrong version STALE, the last ASSIGNED. Only that one changes.
	 */
	@Test
	void assignLeads_mixedLeads_outcomesInRequestOrderAndOnlyAssignedLeadsChange() throws Exception {
		String unknown = ulidGenerator.generateId();
		String rejected = submit();
		Result closed = api.post(saleLeader, LEADS + "/" + rejected + "/status-changes", Map.of("toStatus", "REJECTED",
				"expectedVersion", state(rejected).version(), "reason", "Không phải chủ nhà"));
		assertEquals(200, closed.status(), closed.rawBody());
		String heldByAssignee = heldBy(sale2);
		String ofSale1 = heldBy(sale1);
		String fresh = submit();
		Map<String, LeadState> before = new LinkedHashMap<>();
		for (String lead : List.of(rejected, heldByAssignee, ofSale1, fresh)) {
			before.put(lead, state(lead));
		}

		Result result = api.post(saleLeader, ASSIGNMENTS, body(sale2, null, item(unknown, 0), item("x", 0),
				item(rejected, before.get(rejected).version()), item(heldByAssignee, before.get(heldByAssignee).version() + 3),
				item(ofSale1, before.get(ofSale1).version() + 3), item(fresh, before.get(fresh).version())));

		assertEquals(List.of("NOT_FOUND", "NOT_FOUND", "CLOSED", "ALREADY_ASSIGNED", "STALE", "ASSIGNED"),
				outcomes(result, List.of(unknown, "x", rejected, heldByAssignee, ofSale1, fresh)));
		for (String lead : List.of(rejected, heldByAssignee, ofSale1)) {
			assertEquals(before.get(lead), state(lead), lead);
		}
		LeadState assigned = state(fresh);
		assertEquals("ASSIGNED", assigned.status());
		assertEquals(sale2.userId(), assigned.assignee());
		assertEquals(before.get(fresh).version() + 1, assigned.version());
		assertEquals(before.get(fresh).history() + 1, assigned.history());
	}

	/** acquisition-owner-lead-bulk-assign AC-5: the same request again reports ALREADY_ASSIGNED and writes nothing. */
	@Test
	void assignLeads_sameRequestAgain_reportsAlreadyAssignedAndWritesNothing() throws Exception {
		String first = submit();
		String second = submit();
		Map<String, Object> request = body(sale2, "Giao lại", item(first, state(first).version()),
				item(second, state(second).version()));
		assertEquals(List.of("ASSIGNED", "ASSIGNED"),
				outcomes(api.post(saleLeader, ASSIGNMENTS, request), List.of(first, second)));
		LeadState firstAfter = state(first);
		LeadState secondAfter = state(second);

		Result again = api.post(saleLeader, ASSIGNMENTS, request);

		assertEquals(List.of("ALREADY_ASSIGNED", "ALREADY_ASSIGNED"), outcomes(again, List.of(first, second)));
		assertEquals(firstAfter, state(first));
		assertEquals(secondAfter, state(second));
	}

	// ==================== AC-6, AC-7: refusals of the whole request ====================

	/**
	 * acquisition-owner-lead-bulk-assign AC-6, BR-9, D-4: each bad body answers 400 VALIDATION_FAILED on exactly the
	 * field spec 4.1 names (bean validation in Spring's bracket form, a value of the wrong JSON type on its dotted path),
	 * never echoing the value; a body that is not JSON is MALFORMED_REQUEST. The real lead of each body stays as it was.
	 */
	@Test
	void assignLeads_invalidBody_returns400OnTheFieldAndChangesNothing() throws Exception {
		String lead = submit();
		LeadState before = state(lead);
		long version = before.version();
		List<Map<String, Object>> twentyOne = new ArrayList<>();
		twentyOne.add(item(lead, version));
		for (int i = 0; i < 20; i++) {
			twentyOne.add(item(ulidGenerator.generateId(), 0));
		}
		List<Object> nullItem = new ArrayList<>();
		nullItem.add(null);
		nullItem.add(item(lead, version));
		Map<String, Object> noLeadId = new LinkedHashMap<>();
		noLeadId.put("expectedVersion", version);
		Map<String, Object> noVersion = new LinkedHashMap<>();
		noVersion.put("leadId", lead);
		String longId = (SECRET + "X".repeat(26)).substring(0, 27);

		Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
		cases.put("leads", withLeads(sale2.userId(), null, null));
		cases.put("leads ", withLeads(sale2.userId(), null, List.of()));
		cases.put("leads  ", withLeads(sale2.userId(), null, twentyOne));
		cases.put("leads   ", withLeads(sale2.userId(), null, List.of(item(lead, version), item(lead, version))));
		cases.put("leads    ", withLeads(sale2.userId(), null, SECRET));
		cases.put("leads[0]", withLeads(sale2.userId(), null, nullItem));
		cases.put("leads[0].leadId", withLeads(sale2.userId(), null, List.of(noLeadId, item(lead, version))));
		cases.put("leads[0].leadId ", withLeads(sale2.userId(), null, List.of(item("  ", 0), item(lead, version))));
		cases.put("leads[0].leadId  ", withLeads(sale2.userId(), null, List.of(item(longId, 0), item(lead, version))));
		cases.put("leads.0.leadId", withLeads(sale2.userId(), null, List.of(
				Map.of("leadId", Map.of("a", SECRET), "expectedVersion", 0), item(lead, version))));
		cases.put("leads[0].expectedVersion", withLeads(sale2.userId(), null, List.of(noVersion)));
		cases.put("leads[0].expectedVersion ", withLeads(sale2.userId(), null, List.of(item(lead, -1))));
		cases.put("leads.0.expectedVersion", withLeads(sale2.userId(), null,
				List.of(Map.of("leadId", lead, "expectedVersion", SECRET))));
		cases.put("assigneeUserId", withLeads(null, null, List.of(item(lead, version))));
		cases.put("assigneeUserId ", withLeads("  ", null, List.of(item(lead, version))));
		cases.put("assigneeUserId  ", withLeads(longId, null, List.of(item(lead, version))));
		cases.put("note", withLeads(sale2.userId(), SECRET + "n".repeat(2001 - SECRET.length()),
				List.of(item(lead, version))));

		for (Map.Entry<String, Map<String, Object>> entry : cases.entrySet()) {
			Result result = api.post(saleLeader, ASSIGNMENTS, entry.getValue());

			String field = entry.getKey().strip();
			assertEquals(400, result.status(), field + ": " + result.rawBody());
			assertEquals("VALIDATION_FAILED", result.body().path("code").asString(), field + ": " + result.rawBody());
			assertEquals(List.of(field), errorFields(result), field + ": " + result.rawBody());
			assertFalse(result.rawBody().contains(SECRET), field + ": the value is not echoed");
			assertFalse(result.rawBody().contains(lead), field + ": the lead id is not echoed");
		}
		Result malformed = api.send(api.request(saleLeader, ASSIGNMENTS)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"assigneeUserId\": \"" + SECRET + "\", \"leads\": [",
						StandardCharsets.UTF_8))
				.build());
		assertEquals(400, malformed.status(), malformed.rawBody());
		assertEquals("MALFORMED_REQUEST", malformed.body().path("code").asString(), malformed.rawBody());
		assertFalse(malformed.rawBody().contains(SECRET));

		assertEquals(before, state(lead));
	}

	/**
	 * acquisition-owner-lead-bulk-assign AC-7, BR-9: an unknown, deleted, locked, SALES_MANAGER-only or CUSTOMER
	 * assignee is one 422 INELIGIBLE_ASSIGNEE on assigneeUserId for the whole request, checked once, also when every lead
	 * of the request already belongs to that person; nothing changes.
	 */
	@Test
	void assignLeads_ineligibleAssignee_returns422EvenWhenEveryLeadIsTheirs() throws Exception {
		String lead = submit();
		LeadState before = state(lead);

		for (String userId : List.of(ulidGenerator.generateId(), deletedSale.userId(), lockedSale.userId(),
				salesManager.userId(), customer.userId())) {
			Result result = api.post(saleLeader, ASSIGNMENTS, withLeads(userId, null, List.of(item(lead,
					before.version()))));

			assertIneligible(result, userId);
		}
		assertEquals(before, state(lead));

		TestUser leaving = loggedIn("SALE");
		String first = heldBy(leaving);
		String second = heldBy(leaving);
		LeadState firstBefore = state(first);
		LeadState secondBefore = state(second);
		jdbc.update("update users set deleted_at = now() where id = ?", leaving.userId());

		Result theirs = api.post(saleLeader, ASSIGNMENTS, body(leaving, null, item(first, firstBefore.version()),
				item(second, secondBefore.version())));

		assertIneligible(theirs, leaving.userId());
		assertEquals(firstBefore, state(first));
		assertEquals(secondBefore, state(second));
	}

	// ==================== BR-3: the largest action ====================

	/**
	 * acquisition-owner-lead-bulk-assign BR-3, plan P-3: twenty NEW leads go to one person in one call. The time of this
	 * test in the JUnit report is the measurement of spec R-1.
	 */
	@Test
	void assignLeads_twentyLeads_allAssignedInOneCall() throws Exception {
		List<String> twenty = new ArrayList<>();
		List<Map<String, Object>> items = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			String lead = submit();
			twenty.add(lead);
			items.add(item(lead, state(lead).version()));
		}

		Result result = api.post(saleLeader, ASSIGNMENTS, withLeads(sale2.userId(), null, items));

		assertEquals(java.util.Collections.nCopies(20, "ASSIGNED"), outcomes(result, twenty));
		for (String lead : twenty) {
			assertEquals("ASSIGNED", state(lead).status());
			assertEquals(sale2.userId(), state(lead).assignee());
			assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type = 'ASSIGNED'",
					lead));
		}
	}

	// ==================== spec 4.1: no GET ====================

	/** Spec 4.1: GET of the path is the lead detail of the id "assignments", which answers like any unknown id. */
	@Test
	void getAssignmentsPath_answersTheLeadNotFoundBody() throws Exception {
		Result assignments = api.get(saleLeader, ASSIGNMENTS);
		Result missing = api.get(saleLeader, LEADS + "/" + ulidGenerator.generateId());

		assertEquals(404, assignments.status(), assignments.rawBody());
		assertEquals("NOT_FOUND", assignments.body().path("code").asString());
		assertEquals(withoutCorrelationId(missing.body()), withoutCorrelationId(assignments.body()));
	}

	// ==================== Helpers ====================

	/** What a bulk assignment may change on a lead, and its History count. */
	private record LeadState(String status, String assignee, long version, int history, Timestamp nextActionAt) {
	}

	private LeadState state(String leadId) {
		return jdbc.queryForObject("select l.status, l.assigned_user_id, l.version, l.next_action_at, "
				+ "(select count(*) from owner_lead_activities a where a.lead_id = l.id) as history "
				+ "from owner_leads l where l.id = ?",
				(rs, row) -> new LeadState(rs.getString("status"), rs.getString("assigned_user_id"), rs.getLong("version"),
						rs.getInt("history"), rs.getTimestamp("next_action_at")),
				leadId);
	}

	/** The lead's History rows, oldest first, every column. */
	private List<Map<String, Object>> history(String leadId) {
		return jdbc.queryForList("select * from owner_lead_activities where lead_id = ? order by occurred_at, id", leadId);
	}

	/** The newest History row without the columns that differ between twin leads: id, lead and time. */
	private Map<String, Object> lastRowWithoutIdentity(String leadId) {
		List<Map<String, Object>> rows = history(leadId);
		Map<String, Object> row = new LinkedHashMap<>(rows.get(rows.size() - 1));
		row.remove("id");
		row.remove("lead_id");
		row.remove("occurred_at");
		return row;
	}

	private static Map<String, Object> body(TestUser assignee, String note, Map<?, ?>... items) {
		return withLeads(assignee.userId(), note, Arrays.asList(items));
	}

	/** A request body; a null assignee or leads leaves the key out, a null note too. */
	private static Map<String, Object> withLeads(String assigneeUserId, String note, Object leads) {
		Map<String, Object> body = new LinkedHashMap<>();
		if (assigneeUserId != null) {
			body.put("assigneeUserId", assigneeUserId);
		}
		if (note != null) {
			body.put("note", note);
		}
		if (leads != null) {
			body.put("leads", leads);
		}
		return body;
	}

	private static Map<String, Object> item(String leadId, long expectedVersion) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("leadId", leadId);
		item.put("expectedVersion", expectedVersion);
		return item;
	}

	/**
	 * A 200 holding only {@code results}, one item per sent id in the same order, each with only leadId (as sent) and
	 * outcome (acquisition-owner-lead-bulk-assign D-3); returns the outcomes.
	 */
	private static List<String> outcomes(Result result, List<String> sentIds) {
		assertEquals(200, result.status(), result.rawBody());
		assertEquals(List.of("results"), fieldNames(result.body()), result.rawBody());
		List<String> ids = new ArrayList<>();
		List<String> outcomes = new ArrayList<>();
		for (JsonNode item : result.body().path("results")) {
			assertEquals(List.of("leadId", "outcome"), fieldNames(item), result.rawBody());
			ids.add(item.path("leadId").asString());
			outcomes.add(item.path("outcome").asString());
		}
		assertEquals(sentIds, ids, result.rawBody());
		return outcomes;
	}

	private static void assertIneligible(Result result, String userId) {
		assertEquals(422, result.status(), result.rawBody());
		assertEquals("INELIGIBLE_ASSIGNEE", result.body().path("code").asString(), result.rawBody());
		assertEquals(List.of("assigneeUserId"), errorFields(result), result.rawBody());
		assertFalse(result.rawBody().contains(userId), "the id is never echoed");
	}

	private static List<String> errorFields(Result result) {
		List<String> fields = new ArrayList<>();
		result.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		return fields;
	}

	private static List<String> listedIds(Result page) {
		List<String> ids = new ArrayList<>();
		page.body().path("items").forEach(item -> ids.add(item.path("id").asString()));
		return ids;
	}

	private static JsonNode withoutCorrelationId(JsonNode body) {
		ObjectNode copy = ((ObjectNode) body).deepCopy();
		copy.remove("correlationId");
		return copy;
	}

	private TestUser loggedIn(String role) throws Exception {
		return api.logIn(created(UserStatus.ACTIVE, role));
	}

	private TestUser created(UserStatus status, String... roles) {
		TestUser user = api.createUser(status, roles);
		userIds.add(user.userId());
		return user;
	}

	/** A NEW lead: Bà Nà has nobody. */
	private String submit() throws Exception {
		int n = PHONE_SEQUENCE.getAndIncrement();
		String phone = "0999" + String.format("%06d", n);
		String leadId = AcquisitionApiTestSupport.leadId(jdbc, api.submitLead(BA_NA, phone, n + " Hùng Vương"));
		leadIds.add(leadId);
		return leadId;
	}

	/** A lead given to the person by the single assignment. */
	private String heldBy(TestUser holder) throws Exception {
		String lead = submit();
		Result assigned = api.post(saleLeader, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", holder.userId(), "expectedVersion", state(lead).version()));
		assertEquals(200, assigned.status(), assigned.rawBody());
		return lead;
	}

	private int accessLogs(String leadId) {
		return count("select count(*) from owner_lead_access_logs where lead_id = ?", leadId);
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}
}
