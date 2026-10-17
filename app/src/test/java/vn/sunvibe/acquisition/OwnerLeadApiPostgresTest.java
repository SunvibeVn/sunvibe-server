package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.assertSecurityBody;
import static vn.sunvibe.acquisition.AcquisitionApiTestSupport.fieldNames;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * {@code /api/v1/staff/owner-leads} end to end (acquisition-owner-lead-queue spec 6, 7; Q9-3..Q9-9, Q9-11, Q9-17,
 * W9-18): real logins through the security chain and the role hierarchy, leads sent through the public intake form
 * (so they carry consent, history, duplicate candidates and auto-assignment), the lead services on PostgreSQL migrated
 * by Flyway. Territories are rows written by JDBC: Sơn Trà to sale1, Ngũ Hành Sơn to sale2, An Hải and Hòa Khánh to
 * nobody (their leads stay NEW). Every lead, territory and user made here is removed afterwards. Needs Docker.
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
class OwnerLeadApiPostgresTest {

	private static final String LEADS = "/api/v1/staff/owner-leads";
	private static final String TERRITORIES = "/api/v1/staff/sale-territories";
	private static final String SON_TRA = "20263";
	private static final String NGU_HANH_SON = "20285";
	private static final String AN_HAI = "20275";
	private static final String HOA_KHANH = "20200";
	private static final List<String> SUMMARY_FIELDS = List.of("id", "requestCode", "status", "intent", "contactName",
			"provinceCode", "wardCode", "wardName", "propertyType", "assignedUserId", "nextActionAt",
			"possibleDuplicate", "createdAt", "version");
	private static final Set<String> OPEN_MOVES_OF_ASSIGNED =
			Set.of("CONTACTED", "ON_HOLD", "DUPLICATE", "REJECTED", "WITHDRAWN");
	private static final Set<String> CLOSING = Set.of("DUPLICATE", "REJECTED", "WITHDRAWN");
	private static final Instant NEXT_ACTION = Instant.parse("2026-11-20T03:00:00Z");

	/** Phones 0961xxxxxx, apart from the other acquisition test classes. */
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
	private TestUser customer;
	private TestUser lockedSale;
	private TestUser roleless;
	/** LISTING_REVIEWER, OWNER, CUSTOMER, EMPLOYEE, MANAGER, LEADER: no access at all (Q9-3). */
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
		roleless = created(UserStatus.ACTIVE);

		territory(SON_TRA, sale1);
		territory(NGU_HANH_SON, sale2);
	}

	@AfterAll
	void tearDown() {
		jdbc.update("delete from sale_territories where ward_code in (?, ?)", SON_TRA, NGU_HANH_SON);
		AcquisitionApiTestSupport.deleteLeads(jdbc, leadIds);
		AcquisitionApiTestSupport.deleteUsers(jdbc, userIds);
		api.http().close();
	}

	// ==================== Q9-3: list and who may call ====================

	/** Q9-3: a SALE's list holds only the leads assigned to them, with or without scope=mine. */
	@Test
	void list_sale_mine_returnsOnlyOwnLeads() throws Exception {
		String own = submit(SON_TRA);
		String other = submit(NGU_HANH_SON);
		String unassigned = submit(AN_HAI);

		for (String query : List.of("?size=100", "?scope=mine&size=100")) {
			Result result = api.get(sale1, LEADS + query);

			assertEquals(200, result.status(), result.rawBody());
			List<String> ids = ids(result);
			assertTrue(ids.contains(own), query);
			assertFalse(ids.contains(other), query);
			assertFalse(ids.contains(unassigned), query);
			result.body().path("items").forEach(item ->
					assertEquals(sale1.userId(), item.path("assignedUserId").asString()));
		}
	}

	/**
	 * Q9-3, acquisition-owner-lead-queue US-2 AC-1: a SALE asking for the unassigned leads or the whole queue gets the
	 * security 403.
	 */
	@Test
	void list_sale_unassignedOrAll_returns403() throws Exception {
		assertSecurityBody(api.get(sale1, LEADS + "?scope=unassigned"), 403);
		assertSecurityBody(api.get(sale1, LEADS + "?scope=all"), 403);
	}

	/** Q9-3: SALE_LEADER, SALES_MANAGER and ADMIN see every lead; unassigned means status NEW only. */
	@Test
	void list_leaders_andAdmin_seeAllAndUnassigned() throws Exception {
		String ofSale1 = submit(SON_TRA);
		String ofSale2 = submit(NGU_HANH_SON);
		String unassigned = submit(AN_HAI);

		for (TestUser caller : List.of(saleLeader, salesManager, admin)) {
			List<String> all = ids(okList(caller, "?scope=all&size=100"));
			assertTrue(all.containsAll(List.of(ofSale1, ofSale2, unassigned)), caller.toString());

			Result open = okList(caller, "?scope=unassigned&size=100");
			List<String> unassignedIds = ids(open);
			assertTrue(unassignedIds.contains(unassigned), caller.toString());
			assertFalse(unassignedIds.contains(ofSale1) || unassignedIds.contains(ofSale2), caller.toString());
			open.body().path("items").forEach(item -> assertEquals("NEW", item.path("status").asString()));

			List<String> mine = ids(okList(caller, "?size=100"));
			assertFalse(mine.contains(ofSale1) || mine.contains(ofSale2) || mine.contains(unassigned),
					caller.toString());
		}
	}

	/** SUPER_ADMIN passes every staff rule through the role hierarchy and sees what ADMIN sees. */
	@Test
	void list_superAdmin_actsAsAdmin() throws Exception {
		String ofSale1 = submit(SON_TRA);
		String unassigned = submit(AN_HAI);

		assertTrue(ids(okList(superAdmin, "?scope=all&size=100")).containsAll(List.of(ofSale1, unassigned)));
		assertTrue(ids(okList(superAdmin, "?scope=unassigned&size=100")).contains(unassigned));
		assertEquals(200, api.get(superAdmin, LEADS + "/" + ofSale1).status());
		Result assigned = api.post(superAdmin, LEADS + "/" + unassigned + "/assign",
				Map.of("assigneeUserId", sale1.userId(), "expectedVersion", version(unassigned)));
		assertEquals(200, assigned.status(), assigned.rawBody());
		assertEquals("ASSIGNED", assigned.body().path("status").asString());
	}

	/** Q9-3: the other roles get the security 403 on every call, with valid input; nothing is read or changed. */
	@Test
	void list_otherRoles_return403_onEveryOwnerLeadsCall() throws Exception {
		String lead = submit(SON_TRA);

		for (Map.Entry<String, TestUser> entry : otherRoles.entrySet()) {
			TestUser caller = entry.getValue();
			assertSecurityBody(api.get(caller, LEADS), 403);
			assertSecurityBody(api.get(caller, LEADS + "/" + lead), 403);
			assertSecurityBody(api.post(caller, LEADS + "/" + lead + "/assign",
					Map.of("assigneeUserId", sale2.userId(), "expectedVersion", 0)), 403);
			assertSecurityBody(api.post(caller, LEADS + "/" + lead + "/status-changes",
					Map.of("toStatus", "CONTACTED", "expectedVersion", 0)), 403);
			assertSecurityBody(api.post(caller, LEADS + "/" + lead + "/activities", Map.of("note", "Đã gọi")),
					403);
		}

		assertEquals("ASSIGNED", status(lead));
		assertEquals(sale1.userId(), assignee(lead));
		assertEquals(0L, version(lead));
		assertEquals(0, accessLogs(lead));
		assertEquals(2, activityCount(lead));
	}

	/** W9-18: the list is not public. */
	@Test
	void list_withoutToken_returns401() throws Exception {
		assertSecurityBody(api.get(null, LEADS), 401);
	}

	/** Spec 6: 400 VALIDATION_FAILED on the query parameter that is wrong. */
	@Test
	void list_badSizePageScopeStatus_return400OnThatParameter() throws Exception {
		assertValidationError(api.get(saleLeader, LEADS + "?size=0"), "size");
		assertValidationError(api.get(saleLeader, LEADS + "?size=101"), "size");
		assertValidationError(api.get(saleLeader, LEADS + "?page=-1"), "page");
		assertValidationError(api.get(saleLeader, LEADS + "?scope=x"), "scope");
		assertValidationError(api.get(saleLeader, LEADS + "?status=NOPE"), "status");
	}

	/** acquisition-owner-lead-queue C-15: list items carry the contact name and ward, never contact data. */
	@Test
	void list_summary_hasNoPhoneEmailAddressOrCoordinates() throws Exception {
		String phone = nextPhone();
		String address = PHONE_SEQUENCE.get() + " Võ Nguyên Giáp, căn B-0907";
		String lead = submit(SON_TRA, phone, address);

		Result result = okList(sale1, "?size=100");

		JsonNode item = item(result, lead);
		assertEquals(SUMMARY_FIELDS, fieldNames(item));
		assertEquals("Nguyễn Thị Lan", item.path("contactName").asString());
		assertEquals(SON_TRA, item.path("wardCode").asString());
		assertEquals("Sơn Trà", item.path("wardName").asString());
		String raw = result.rawBody();
		for (String hidden : List.of(phone.replace(" ", ""), phone.replace(" ", "").substring(1), "lan@example.com",
				"Võ Nguyên Giáp", "16.0678", "108.2208")) {
			assertFalse(raw.contains(hidden), "the list holds " + hidden);
		}
	}

	/** Spec 6: newest first, then id; page, size and totalElements describe the whole filtered list. */
	@Test
	void list_pagingAndOrder_newestFirst() throws Exception {
		String query = "?scope=all&wardCode=" + HOA_KHANH;
		long before = okList(saleLeader, query + "&size=1").body().path("totalElements").asLong();
		String first = submit(HOA_KHANH);
		String second = submit(HOA_KHANH);
		String third = submit(HOA_KHANH);

		Result page0 = okList(saleLeader, query + "&size=2&page=0");
		Result page1 = okList(saleLeader, query + "&size=2&page=1");

		assertEquals(List.of("items", "page", "size", "totalElements"), fieldNames(page0.body()));
		assertEquals(List.of(third, second), ids(page0));
		assertEquals(0, page0.body().path("page").asInt());
		assertEquals(2, page0.body().path("size").asInt());
		assertEquals(before + 3, page0.body().path("totalElements").asLong());
		assertEquals(first, ids(page1).get(0));
		assertEquals(1, page1.body().path("page").asInt());
		Instant newest = Instant.parse(page0.body().path("items").get(0).path("createdAt").asString());
		Instant next = Instant.parse(page0.body().path("items").get(1).path("createdAt").asString());
		assertFalse(newest.isBefore(next));
	}

	// ==================== Q9-11: detail and access log ====================

	/** Q9-11: every detail GET writes one access-log row; the list writes none; the detail holds no hash. */
	@Test
	void detail_forAssignedSale_returnsFullDetailAndWritesAccessLogRow() throws Exception {
		String phone = nextPhone();
		String lead = submit(SON_TRA, phone, PHONE_SEQUENCE.get() + " Trần Hưng Đạo");
		okList(sale1, "?size=100");
		okList(saleLeader, "?scope=all&size=100");
		assertEquals(0, accessLogs(lead));

		Result result = api.get(sale1, LEADS + "/" + lead);

		assertEquals(200, result.status(), result.rawBody());
		List<Map<String, Object>> logs = jdbc.queryForList(
				"select user_id, accessed_at from owner_lead_access_logs where lead_id = ?", lead);
		assertEquals(1, logs.size());
		assertEquals(sale1.userId(), logs.get(0).get("user_id"));
		Instant accessedAt = ((Timestamp) logs.get(0).get("accessed_at")).toInstant();
		assertTrue(accessedAt.isAfter(Instant.now().minus(5, ChronoUnit.MINUTES)), accessedAt.toString());
		assertEquals(200, api.get(sale1, LEADS + "/" + lead).status());
		assertEquals(2, accessLogs(lead));

		JsonNode detail = result.body();
		assertEquals(lead, detail.path("id").asString());
		assertEquals("ASSIGNED", detail.path("status").asString());
		assertEquals(sale1.userId(), detail.path("assignedUserId").asString());
		assertEquals("+84" + phone.replace(" ", "").substring(1), detail.path("phone").asString());
		assertEquals("lan@example.com", detail.path("email").asString());
		assertEquals(0, detail.path("latitude").decimalValue().compareTo(new java.math.BigDecimal("16.0678")));
		assertEquals(15_000_000L, detail.path("askingPrice").path("amount").asLong());
		assertEquals("VND", detail.path("askingPrice").path("currency").asString());
		assertEquals("MONTH", detail.path("askingPrice").path("unit").asString());
		assertEquals("WEB", detail.path("source").asString());
		assertTrue(detail.path("duplicateOfLeadId").isNull());
		assertTrue(detail.path("closeReason").isNull());
		assertEquals(0, detail.path("duplicateCandidates").size());
		assertEquals(List.of("noticeVersion", "acceptedAt"), fieldNames(detail.path("consent")));
		assertEquals("owner-intake-2026-10", detail.path("consent").path("noticeVersion").asString());
		assertTrue(fieldNames(detail).containsAll(SUMMARY_FIELDS), fieldNames(detail).toString());
		assertTrue(fieldNames(detail).containsAll(List.of("phone", "email", "addressDetail", "latitude", "longitude",
				"askingPrice", "availableFrom", "notes", "preferredCallbackTime", "source", "duplicateOfLeadId",
				"closeReason", "consent", "duplicateCandidates", "activities")), fieldNames(detail).toString());
		String raw = result.rawBody().toLowerCase(java.util.Locale.ROOT);
		assertFalse(raw.contains("snapshot") || raw.contains("hash"), result.rawBody());

		JsonNode activities = detail.path("activities");
		assertEquals(List.of("SUBMITTED", "AUTO_ASSIGNED"), types(activities));
		assertTrue(activities.get(0).path("actorUserId").isNull());
		assertEquals(sale1.userId(), activities.get(1).path("toAssigneeId").asString());
		assertFalse(Instant.parse(activities.get(1).path("occurredAt").asString())
				.isBefore(Instant.parse(activities.get(0).path("occurredAt").asString())));
	}

	/**
	 * Q9-4: a SALE asking for another person's lead gets exactly the answer of an id that does not exist (status and
	 * body, but the correlation id), on detail, status change and note; the hidden attempt is not access-logged.
	 */
	@Test
	void detail_statusChange_activities_onAnotherSalesLead_return404WithTheMissingIdBody() throws Exception {
		String hidden = submit(SON_TRA);
		String missing = ulidGenerator.generateId();
		Map<String, Object> statusChange = Map.of("toStatus", "CONTACTED", "expectedVersion", 0);
		Map<String, Object> note = Map.of("note", "Đã gọi");

		List<Result[]> pairs = List.of(
				new Result[] { api.get(sale2, LEADS + "/" + hidden), api.get(sale2, LEADS + "/" + missing) },
				new Result[] { api.post(sale2, LEADS + "/" + hidden + "/status-changes", statusChange),
						api.post(sale2, LEADS + "/" + missing + "/status-changes", statusChange) },
				new Result[] { api.post(sale2, LEADS + "/" + hidden + "/activities", note),
						api.post(sale2, LEADS + "/" + missing + "/activities", note) });

		for (Result[] pair : pairs) {
			Result onHidden = pair[0];
			Result onMissing = pair[1];
			assertEquals(404, onHidden.status(), onHidden.rawBody());
			assertEquals(onMissing.status(), onHidden.status());
			assertEquals("NOT_FOUND", onHidden.body().path("code").asString());
			assertEquals(0, onHidden.body().path("errors").size());
			assertEquals(withoutCorrelationId(onMissing.body()), withoutCorrelationId(onHidden.body()));
			assertFalse(onHidden.rawBody().contains(hidden), "the hidden id is never echoed");
		}
		assertEquals(0, accessLogs(hidden));
		assertEquals(0, count("select count(*) from owner_lead_access_logs where user_id = ? and lead_id = ?",
				sale2.userId(), hidden));
		assertEquals("ASSIGNED", status(hidden));
		assertEquals(0L, version(hidden));
		assertEquals(2, activityCount(hidden));
	}

	// ==================== Q9-17: duplicates the viewer can see ====================

	/**
	 * Q9-17: candidates and the lead a duplicate repeats are shown only when the viewer can see them; closing as a
	 * duplicate of a hidden, a duplicate, the same or an unknown lead is one and the same 400.
	 */
	@Test
	void candidates_saleSeesOnlyVisibleOnes() throws Exception {
		String phone = nextPhone();
		String a = submit(SON_TRA, phone, PHONE_SEQUENCE.get() + " Hoàng Sa, căn 1");
		String b = submit(NGU_HANH_SON, phone, PHONE_SEQUENCE.get() + " Hoàng Sa, căn 2");
		assertEquals(sale2.userId(), assignee(b));

		JsonNode aForSale1 = okDetail(sale1, a);
		assertFalse(aForSale1.path("possibleDuplicate").asBoolean());
		assertEquals(0, aForSale1.path("duplicateCandidates").size());
		assertFalse(item(okList(sale1, "?size=100"), a).path("possibleDuplicate").asBoolean());
		JsonNode bForSale2 = okDetail(sale2, b);
		assertFalse(bForSale2.path("possibleDuplicate").asBoolean());
		assertEquals(0, bForSale2.path("duplicateCandidates").size());

		JsonNode aForLeader = okDetail(saleLeader, a);
		assertTrue(aForLeader.path("possibleDuplicate").asBoolean());
		assertEquals(List.of(b), candidateIds(aForLeader));
		assertEquals(List.of("id", "requestCode", "status", "createdAt"),
				fieldNames(aForLeader.path("duplicateCandidates").get(0)));
		assertEquals(List.of(a), candidateIds(okDetail(saleLeader, b)));
		assertTrue(item(okList(saleLeader, "?scope=all&size=100&wardCode=" + SON_TRA), a)
				.path("possibleDuplicate").asBoolean());

		Result closedB = api.post(saleLeader, LEADS + "/" + b + "/status-changes", Map.of("toStatus", "DUPLICATE",
				"expectedVersion", version(b), "reason", "Cùng chủ nhà", "duplicateOfLeadId", a));
		assertEquals(200, closedB.status(), closedB.rawBody());
		assertTrue(okDetail(sale2, b).path("duplicateOfLeadId").isNull());
		assertEquals(a, okDetail(saleLeader, b).path("duplicateOfLeadId").asString());

		String hiddenOpen = submit(NGU_HANH_SON);
		String ownDuplicate = submit(SON_TRA);
		Result closedOwn = api.post(sale1, LEADS + "/" + ownDuplicate + "/status-changes", Map.of("toStatus",
				"DUPLICATE", "expectedVersion", version(ownDuplicate), "reason", "Gửi hai lần", "duplicateOfLeadId", a));
		assertEquals(200, closedOwn.status(), closedOwn.rawBody());

		Set<String> messages = new HashSet<>();
		for (String target : List.of(hiddenOpen, ownDuplicate, a, ulidGenerator.generateId(), "not-a-lead-id")) {
			Result result = api.post(sale1, LEADS + "/" + a + "/status-changes", Map.of("toStatus", "DUPLICATE",
					"expectedVersion", version(a), "reason", "Trùng", "duplicateOfLeadId", target));
			assertValidationError(result, "duplicateOfLeadId");
			assertFalse(result.rawBody().contains(target), "the target id is never echoed");
			messages.add(result.body().path("errors").get(0).path("message").asString());
		}
		assertEquals(1, messages.size(), messages.toString());
		assertEquals("ASSIGNED", status(a));
	}

	// ==================== Q9-5, Q9-6: assignment ====================

	/**
	 * Q9-5, Q9-6: a NEW lead becomes ASSIGNED with one ASSIGNED activity; reassigning removes the old person's
	 * access at once (detail, list, status change and note) while the new one sees the next action and the notes.
	 */
	@Test
	void assign_leader_newLeadBecomesAssigned_andReassignRevokesTheOldPerson() throws Exception {
		String lead = submit(AN_HAI);
		JsonNode fresh = okDetail(saleLeader, lead);
		assertEquals("NEW", fresh.path("status").asString());
		assertTrue(fresh.path("assignedUserId").isNull());
		long v0 = fresh.path("version").asLong();

		Result assigned = api.post(saleLeader, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", sale1.userId(), "expectedVersion", v0));
		assertEquals(200, assigned.status(), assigned.rawBody());
		assertEquals(SUMMARY_FIELDS, fieldNames(assigned.body()));
		assertEquals("ASSIGNED", assigned.body().path("status").asString());
		assertEquals(sale1.userId(), assigned.body().path("assignedUserId").asString());
		assertEquals(v0 + 1, assigned.body().path("version").asLong());
		JsonNode history = okDetail(saleLeader, lead).path("activities");
		assertEquals(List.of("SUBMITTED", "ASSIGNED"), types(history));
		JsonNode assignment = history.get(1);
		assertEquals("NEW", assignment.path("fromStatus").asString());
		assertEquals("ASSIGNED", assignment.path("toStatus").asString());
		assertTrue(assignment.path("fromAssigneeId").isNull());
		assertEquals(sale1.userId(), assignment.path("toAssigneeId").asString());
		assertEquals(saleLeader.userId(), assignment.path("actorUserId").asString());

		Result planned = api.post(sale1, LEADS + "/" + lead + "/activities",
				Map.of("note", "Hẹn gọi lại", "nextActionAt", NEXT_ACTION.toString()));
		assertEquals(201, planned.status(), planned.rawBody());
		Result plain = api.post(sale1, LEADS + "/" + lead + "/activities", Map.of("note", "Chủ nhà đi vắng"));
		assertEquals(201, plain.status(), plain.rawBody());
		long beforeReassign = plain.body().path("lead").path("version").asLong();
		assertEquals(v0 + 2, beforeReassign);

		Result reassigned = api.post(salesManager, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", sale2.userId(), "expectedVersion", beforeReassign));
		assertEquals(200, reassigned.status(), reassigned.rawBody());
		assertEquals(sale2.userId(), reassigned.body().path("assignedUserId").asString());
		assertEquals("ASSIGNED", reassigned.body().path("status").asString());
		assertEquals(beforeReassign + 1, reassigned.body().path("version").asLong());

		assertEquals(404, api.get(sale1, LEADS + "/" + lead).status());
		assertFalse(ids(okList(sale1, "?size=100")).contains(lead));
		assertEquals(404, api.post(sale1, LEADS + "/" + lead + "/status-changes",
				Map.of("toStatus", "CONTACTED", "expectedVersion", beforeReassign + 1)).status());
		assertEquals(404, api.post(sale1, LEADS + "/" + lead + "/activities", Map.of("note", "Còn tôi")).status());

		JsonNode forSale2 = okDetail(sale2, lead);
		assertEquals(NEXT_ACTION, Instant.parse(forSale2.path("nextActionAt").asString()));
		JsonNode activities = forSale2.path("activities");
		assertEquals(List.of("SUBMITTED", "ASSIGNED", "NOTE_ADDED", "NOTE_ADDED", "REASSIGNED"), types(activities));
		assertEquals("Hẹn gọi lại", activities.get(2).path("note").asString());
		assertEquals("Chủ nhà đi vắng", activities.get(3).path("note").asString());
		JsonNode reassignment = activities.get(4);
		assertEquals(sale1.userId(), reassignment.path("fromAssigneeId").asString());
		assertEquals(sale2.userId(), reassignment.path("toAssigneeId").asString());
		assertEquals(salesManager.userId(), reassignment.path("actorUserId").asString());
		assertTrue(ids(okList(sale2, "?size=100")).contains(lead));
	}

	/**
	 * Q9-6, acquisition-owner-lead-queue E-4: a user without SALE or SALE_LEADER, a locked one or an unknown id is a
	 * 422; the lead stays.
	 */
	@Test
	void assign_toUserWithoutSaleRoleOrLocked_returns422OnAssigneeUserId() throws Exception {
		String lead = submit(AN_HAI);
		long version = version(lead);

		for (String userId : List.of(customer.userId(), lockedSale.userId(), roleless.userId(),
				ulidGenerator.generateId())) {
			Result result = api.post(saleLeader, LEADS + "/" + lead + "/assign",
					Map.of("assigneeUserId", userId, "expectedVersion", version));

			assertEquals(422, result.status(), result.rawBody());
			assertEquals("INELIGIBLE_ASSIGNEE", result.body().path("code").asString());
			assertEquals(1, result.body().path("errors").size());
			assertEquals("assigneeUserId", result.body().path("errors").get(0).path("field").asString());
			assertFalse(result.rawBody().contains(userId), "the id is never echoed");
		}
		assertEquals("NEW", status(lead));
		assertEquals(version, version(lead));
		assertEquals(1, activityCount(lead));
	}

	/** Q9-6: a closed lead cannot be assigned: 409 on status with no allowed status. */
	@Test
	void assign_closedLead_returns409StatusWithEmptyAllowed() throws Exception {
		String lead = submit(AN_HAI);
		Result closed = api.post(saleLeader, LEADS + "/" + lead + "/status-changes",
				Map.of("toStatus", "REJECTED", "expectedVersion", version(lead), "reason", "Không phải chủ nhà"));
		assertEquals(200, closed.status(), closed.rawBody());
		long version = closed.body().path("version").asLong();

		Result result = api.post(saleLeader, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", sale1.userId(), "expectedVersion", version));

		assertTransitionError(result, "status", Set.of());
		assertEquals("REJECTED", status(lead));
		assertEquals(version, version(lead));
	}

	/** Q9-6: a SALE cannot hand out leads, not even their own. */
	@Test
	void assign_asSale_returns403() throws Exception {
		String lead = submit(SON_TRA);

		assertSecurityBody(api.post(sale1, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", sale2.userId(), "expectedVersion", 0)), 403);

		assertEquals(sale1.userId(), assignee(lead));
		assertEquals(0L, version(lead));
	}

	/** Spec 6: assigning the current assignee changes nothing: 200, same version, no activity. */
	@Test
	void assign_sameAssignee_returns200AndVersionUnchanged() throws Exception {
		String lead = submit(SON_TRA);
		long version = version(lead);

		Result result = api.post(admin, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", sale1.userId(), "expectedVersion", version));

		assertEquals(200, result.status(), result.rawBody());
		assertEquals(version, result.body().path("version").asLong());
		assertEquals(sale1.userId(), result.body().path("assignedUserId").asString());
		assertEquals(version, version(lead));
		assertEquals(2, activityCount(lead));
	}

	/** Q9-10, acquisition-owner-lead-queue BR-9: a version that is not the current one is refused; nothing changes. */
	@Test
	void assign_staleExpectedVersion_returns409ConcurrentModification() throws Exception {
		String lead = submit(AN_HAI);
		long version = version(lead);

		Result result = api.post(saleLeader, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", sale1.userId(), "expectedVersion", version + 5));

		assertEquals(409, result.status(), result.rawBody());
		assertEquals("CONCURRENT_MODIFICATION", result.body().path("code").asString());
		assertEquals(List.of("expectedVersion"), errorFields(result));
		assertEquals("NEW", status(lead));
		assertEquals(version, version(lead));
	}

	@Test
	void assign_invalidBody_returns400OnAssigneeUserIdOrExpectedVersion() throws Exception {
		String lead = submit(AN_HAI);
		String path = LEADS + "/" + lead + "/assign";

		assertValidationError(api.post(saleLeader, path, Map.of("expectedVersion", 0)), "assigneeUserId");
		assertValidationError(api.post(saleLeader, path, Map.of("assigneeUserId", "  ", "expectedVersion", 0)),
				"assigneeUserId");
		assertValidationError(api.post(saleLeader, path, Map.of("assigneeUserId", sale1.userId())),
				"expectedVersion");
		assertValidationError(api.post(saleLeader, path,
				Map.of("assigneeUserId", sale1.userId(), "expectedVersion", -1)), "expectedVersion");
		assertEquals("NEW", status(lead));
	}

	/** User ids are ULIDs of 26 characters (users.id): a huge one is refused on the field before any lookup. */
	@Test
	void assign_assigneeUserIdOf1Mb_returns400OnAssigneeUserId() throws Exception {
		String lead = submit(AN_HAI);

		Result result = api.post(saleLeader, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", "x".repeat(1_000_000), "expectedVersion", 0));

		assertValidationError(result, "assigneeUserId");
		assertFalse(result.rawBody().contains("xxxxxxxxxx"), "the value is not echoed");
		assertEquals("NEW", status(lead));
	}

	@Test
	void list_wardCodeOver10Chars_returns400OnWardCode() throws Exception {
		assertEquals(200, api.get(saleLeader, LEADS + "?scope=all&wardCode=1234567890").status());
		assertValidationError(api.get(saleLeader, LEADS + "?scope=all&wardCode=12345678901"), "wardCode");
	}

	/** expectedVersion is never negative, as on the assign endpoint. */
	@Test
	void statusChange_negativeExpectedVersion_returns400OnExpectedVersion() throws Exception {
		String lead = submit(SON_TRA);

		assertValidationError(api.post(sale1, LEADS + "/" + lead + "/status-changes",
				Map.of("toStatus", "CONTACTED", "expectedVersion", -1)), "expectedVersion");
		assertEquals("ASSIGNED", status(lead));
	}

	/**
	 * A next action time outside what the database can store (before 1970 or from year 10000 on) is a 400 on
	 * nextActionAt on both endpoints that take it, never a 500.
	 */
	@Test
	void statusChangeAndNote_nextActionAtOutOfStorableRange_return400OnNextActionAt() throws Exception {
		String lead = submit(SON_TRA);

		for (String value : List.of("+1000000-01-01T00:00:00Z", "+10000-01-01T00:00:00Z", "1969-12-31T23:59:59Z")) {
			assertValidationError(api.post(sale1, LEADS + "/" + lead + "/status-changes",
					Map.of("toStatus", "CONTACTED", "expectedVersion", 0, "nextActionAt", value)), "nextActionAt");
			assertValidationError(api.post(sale1, LEADS + "/" + lead + "/activities",
					Map.of("note", "Hẹn khảo sát", "nextActionAt", value)), "nextActionAt");
		}

		assertEquals("ASSIGNED", status(lead));
		assertEquals(0L, version(lead));
		assertEquals(2, activityCount(lead));
	}

	/**
	 * acquisition-owner-lead-queue C-17: an over-long duplicate target gets exactly the field and message of an unknown
	 * one, so the length rule tells nothing either.
	 */
	@Test
	void statusChange_duplicateOfLeadIdOf1Mb_returnsTheSame400AsAnUnknownTarget() throws Exception {
		String lead = submit(SON_TRA);
		String path = LEADS + "/" + lead + "/status-changes";

		Result unknown = api.post(sale1, path, Map.of("toStatus", "DUPLICATE", "expectedVersion", 0, "reason", "Trùng",
				"duplicateOfLeadId", "01JZZZZZZZZZZZZZZZZZZZZZZZ"));
		Result huge = api.post(sale1, path, Map.of("toStatus", "DUPLICATE", "expectedVersion", 0, "reason", "Trùng",
				"duplicateOfLeadId", "x".repeat(1_000_000)));

		assertValidationError(unknown, "duplicateOfLeadId");
		assertValidationError(huge, "duplicateOfLeadId");
		assertEquals(withoutCorrelationId(unknown.body()), withoutCorrelationId(huge.body()));
		assertEquals("ASSIGNED", status(lead));
	}

	// ==================== Q9-7, Q9-8: status changes ====================

	/** Q9-7: ASSIGNED -> CONTACTED -> ON_HOLD -> CONTACTED, each with the new version and a STATUS_CHANGED entry. */
	@Test
	void statusChange_allowedTransitions_succeedWithActivities() throws Exception {
		String lead = submit(SON_TRA);
		long version = version(lead);

		for (String to : List.of("CONTACTED", "ON_HOLD", "CONTACTED")) {
			Result result = api.post(sale1, LEADS + "/" + lead + "/status-changes",
					Map.of("toStatus", to, "expectedVersion", version));

			assertEquals(200, result.status(), result.rawBody());
			assertEquals(SUMMARY_FIELDS, fieldNames(result.body()));
			assertEquals(to, result.body().path("status").asString());
			assertEquals(version + 1, result.body().path("version").asLong());
			version = result.body().path("version").asLong();
		}

		JsonNode activities = okDetail(sale1, lead).path("activities");
		assertEquals(List.of("SUBMITTED", "AUTO_ASSIGNED", "STATUS_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED"),
				types(activities));
		List<String> moves = new ArrayList<>();
		for (int i = 2; i < 5; i++) {
			JsonNode activity = activities.get(i);
			moves.add(activity.path("fromStatus").asString() + ">" + activity.path("toStatus").asString());
			assertEquals(sale1.userId(), activity.path("actorUserId").asString());
		}
		assertEquals(List.of("ASSIGNED>CONTACTED", "CONTACTED>ON_HOLD", "ON_HOLD>CONTACTED"), moves);
	}

	/**
	 * Q9-7: a move outside acquisition module brief BR-3 is 409 on toStatus listing exactly the statuses allowed now.
	 */
	@Test
	void statusChange_forbiddenPairs_return409WithAllowedValues() throws Exception {
		String assigned = submit(SON_TRA);
		String fresh = submit(AN_HAI);

		assertTransitionError(api.post(sale1, LEADS + "/" + assigned + "/status-changes",
				Map.of("toStatus", "NEW", "expectedVersion", 0)), "toStatus", OPEN_MOVES_OF_ASSIGNED);
		assertTransitionError(api.post(sale1, LEADS + "/" + assigned + "/status-changes",
				Map.of("toStatus", "QUALIFIED", "expectedVersion", 0)), "toStatus", OPEN_MOVES_OF_ASSIGNED);
		long freshVersion = version(fresh);
		assertTransitionError(api.post(saleLeader, LEADS + "/" + fresh + "/status-changes",
				Map.of("toStatus", "CONTACTED", "expectedVersion", freshVersion)), "toStatus", CLOSING);
		assertTransitionError(api.post(saleLeader, LEADS + "/" + fresh + "/status-changes",
				Map.of("toStatus", "ASSIGNED", "expectedVersion", freshVersion)), "toStatus", CLOSING);

		assertEquals("ASSIGNED", status(assigned));
		assertEquals(0L, version(assigned));
		assertEquals("NEW", status(fresh));
		assertEquals(freshVersion, version(fresh));
	}

	/**
	 * Q9-7, acquisition-owner-lead-queue E-2: a closed lead refuses every status change (nothing allowed) and every
	 * note (field status).
	 */
	@Test
	void statusChange_closedLead_rejectsEveryChangeAndNote() throws Exception {
		String lead = submit(SON_TRA);
		String other = submit(SON_TRA);
		Result closed = api.post(sale1, LEADS + "/" + lead + "/status-changes",
				Map.of("toStatus", "WITHDRAWN", "expectedVersion", 0, "reason", "Chủ nhà rút lại"));
		assertEquals(200, closed.status(), closed.rawBody());
		long version = closed.body().path("version").asLong();
		int activities = activityCount(lead);

		for (String to : List.of("NEW", "ASSIGNED", "CONTACTED", "ON_HOLD", "QUALIFIED", "REJECTED", "WITHDRAWN",
				"DUPLICATE")) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("toStatus", to);
			body.put("expectedVersion", version);
			body.put("reason", "Thử lại");
			body.put("duplicateOfLeadId", other);
			assertTransitionError(api.post(sale1, LEADS + "/" + lead + "/status-changes", body), "toStatus", Set.of());
		}
		assertTransitionError(api.post(sale1, LEADS + "/" + lead + "/activities", Map.of("note", "Gọi lại")),
				"status", Set.of());
		assertTransitionError(api.post(sale1, LEADS + "/" + lead + "/activities",
				Map.of("note", "Gọi lại", "nextActionAt", NEXT_ACTION.toString())), "status", Set.of());

		assertEquals("WITHDRAWN", status(lead));
		assertEquals(version, version(lead));
		assertEquals(activities, activityCount(lead));
	}

	/**
	 * Q9-8, acquisition-owner-lead-queue E-3: closing needs a reason of 1..500 characters, DUPLICATE also the lead it
	 * repeats; closing clears the next action.
	 */
	@Test
	void statusChange_closing_requiresReasonAndDuplicateOf() throws Exception {
		String lead = submit(SON_TRA);
		Result planned = api.post(sale1, LEADS + "/" + lead + "/activities",
				Map.of("note", "Hẹn khảo sát", "nextActionAt", NEXT_ACTION.toString()));
		assertEquals(201, planned.status(), planned.rawBody());
		long version = planned.body().path("lead").path("version").asLong();
		String path = LEADS + "/" + lead + "/status-changes";

		assertValidationError(api.post(sale1, path, Map.of("toStatus", "REJECTED", "expectedVersion", version)),
				"reason");
		assertValidationError(api.post(sale1, path,
				Map.of("toStatus", "REJECTED", "expectedVersion", version, "reason", "   ")), "reason");
		assertValidationError(api.post(sale1, path,
				Map.of("toStatus", "REJECTED", "expectedVersion", version, "reason", "a".repeat(501))), "reason");
		assertValidationError(api.post(sale1, path,
				Map.of("toStatus", "DUPLICATE", "expectedVersion", version, "reason", "Trùng")), "duplicateOfLeadId");
		assertValidationError(api.post(sale1, path, Map.of("toStatus", "DUPLICATE", "expectedVersion", version,
				"reason", "Trùng", "duplicateOfLeadId", "")), "duplicateOfLeadId");
		assertEquals("ASSIGNED", status(lead));
		assertEquals(version, version(lead));

		Result result = api.post(sale1, path, Map.of("toStatus", "REJECTED", "expectedVersion", version,
				"reason", "  Chủ nhà không muốn cho thuê  "));

		assertEquals(200, result.status(), result.rawBody());
		assertEquals("REJECTED", result.body().path("status").asString());
		assertTrue(result.body().path("nextActionAt").isNull());
		Map<String, Object> row = jdbc.queryForMap("select close_reason, next_action_at from owner_leads where id = ?",
				lead);
		assertEquals("Chủ nhà không muốn cho thuê", row.get("close_reason"));
		assertEquals(null, row.get("next_action_at"));
		JsonNode detail = okDetail(sale1, lead);
		assertEquals("Chủ nhà không muốn cho thuê", detail.path("closeReason").asString());
		JsonNode last = detail.path("activities").get(detail.path("activities").size() - 1);
		assertEquals("STATUS_CHANGED", last.path("type").asString());
		assertEquals("REJECTED", last.path("toStatus").asString());
		assertTrue(last.path("nextActionAt").isNull());
	}

	// ==================== Q9-9: notes ====================

	/** Q9-9: a note alone adds a NOTE_ADDED entry and leaves the lead row, and its version, as they were. */
	@Test
	void note_withoutNextAction_addsActivityAndKeepsVersion() throws Exception {
		String lead = submit(SON_TRA);

		Result result = api.post(sale1, LEADS + "/" + lead + "/activities", Map.of("note", "  Đã gọi, hẹn chiều  "));

		assertEquals(201, result.status(), result.rawBody());
		assertEquals(List.of("activity", "lead"), fieldNames(result.body()));
		JsonNode activity = result.body().path("activity");
		assertEquals("NOTE_ADDED", activity.path("type").asString());
		assertEquals("Đã gọi, hẹn chiều", activity.path("note").asString());
		assertEquals(sale1.userId(), activity.path("actorUserId").asString());
		assertTrue(activity.path("nextActionAt").isNull());
		assertNotNull(activity.path("occurredAt").asString());
		assertEquals(SUMMARY_FIELDS, fieldNames(result.body().path("lead")));
		assertEquals(0L, result.body().path("lead").path("version").asLong());
		assertTrue(result.body().path("lead").path("nextActionAt").isNull());
		assertEquals(0L, version(lead));
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type = 'NOTE_ADDED'",
				lead));
	}

	/**
	 * Q9-9, acquisition-owner-lead-queue BR-8: a note with a next action time also sets it on the lead, which gets a
	 * new version.
	 */
	@Test
	void note_withNextAction_updatesNextActionAtAndBumpsVersion() throws Exception {
		String lead = submit(SON_TRA);

		Result result = api.post(sale1, LEADS + "/" + lead + "/activities",
				Map.of("note", "Hẹn khảo sát", "nextActionAt", NEXT_ACTION.toString()));

		assertEquals(201, result.status(), result.rawBody());
		assertEquals(NEXT_ACTION, Instant.parse(result.body().path("activity").path("nextActionAt").asString()));
		JsonNode summary = result.body().path("lead");
		assertEquals(1L, summary.path("version").asLong());
		assertEquals(NEXT_ACTION, Instant.parse(summary.path("nextActionAt").asString()));
		assertEquals(1L, version(lead));
		assertEquals(NEXT_ACTION, ((Timestamp) jdbc.queryForObject(
				"select next_action_at from owner_leads where id = ?", Timestamp.class, lead)).toInstant());
	}

	@Test
	void note_blank_returns400OnNote() throws Exception {
		String lead = submit(SON_TRA);

		assertValidationError(api.post(sale1, LEADS + "/" + lead + "/activities", Map.of("note", "   ")), "note");
		assertValidationError(api.post(sale1, LEADS + "/" + lead + "/activities", Map.of()), "note");
		assertValidationError(api.post(sale1, LEADS + "/" + lead + "/activities", Map.of("note", "a".repeat(2001))),
				"note");

		assertEquals(2, activityCount(lead));
	}

	// ==================== W9-18: nothing is public ====================

	/** W9-18: all eight staff paths answer 401 without a token, before any input is looked at. */
	@Test
	void anyEndpoint_withoutToken_returns401() throws Exception {
		String lead = submit(SON_TRA);

		assertSecurityBody(api.get(null, LEADS), 401);
		assertSecurityBody(api.get(null, LEADS + "/" + lead), 401);
		assertSecurityBody(api.post(null, LEADS + "/" + lead + "/assign",
				Map.of("assigneeUserId", sale2.userId(), "expectedVersion", 0)), 401);
		assertSecurityBody(api.post(null, LEADS + "/" + lead + "/status-changes",
				Map.of("toStatus", "CONTACTED", "expectedVersion", 0)), 401);
		assertSecurityBody(api.post(null, LEADS + "/" + lead + "/activities", Map.of("note", "Đã gọi")), 401);
		assertSecurityBody(api.get(null, TERRITORIES + "?provinceCode=48"), 401);
		assertSecurityBody(api.put(null, TERRITORIES + "/" + AN_HAI, Map.of("userId", sale1.userId())), 401);
		assertSecurityBody(api.delete(null, TERRITORIES + "/" + SON_TRA), 401);

		assertEquals(0, accessLogs(lead));
		assertEquals(2, activityCount(lead));
		assertEquals(List.of(sale1.userId()), jdbc.queryForList(
				"select user_id from sale_territories where ward_code = ?", String.class, SON_TRA));
		assertEquals(0, count("select count(*) from sale_territories where ward_code = ?", AN_HAI));
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

	private void territory(String wardCode, TestUser holder) {
		jdbc.update("insert into sale_territories (ward_code, user_id, assigned_by, assigned_at) values (?, ?, ?, now())",
				wardCode, holder.userId(), holder.userId());
	}

	private static String nextPhone() {
		String digits = "0961" + String.format("%06d", PHONE_SEQUENCE.getAndIncrement());
		return digits.substring(0, 4) + " " + digits.substring(4, 7) + " " + digits.substring(7);
	}

	private String submit(String wardCode) throws Exception {
		String phone = nextPhone();
		return submit(wardCode, phone, PHONE_SEQUENCE.get() + " Nguyễn Văn Linh");
	}

	private String submit(String wardCode, String phone, String address) throws Exception {
		String leadId = AcquisitionApiTestSupport.leadId(jdbc, api.submitLead(wardCode, phone, address));
		leadIds.add(leadId);
		return leadId;
	}

	private Result okList(TestUser caller, String query) throws Exception {
		Result result = api.get(caller, LEADS + query);
		assertEquals(200, result.status(), result.rawBody());
		return result;
	}

	private JsonNode okDetail(TestUser caller, String leadId) throws Exception {
		Result result = api.get(caller, LEADS + "/" + leadId);
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}

	private static List<String> ids(Result page) {
		List<String> ids = new ArrayList<>();
		page.body().path("items").forEach(item -> ids.add(item.path("id").asString()));
		return ids;
	}

	private static JsonNode item(Result page, String leadId) {
		for (JsonNode item : page.body().path("items")) {
			if (item.path("id").asString().equals(leadId)) {
				return item;
			}
		}
		throw new AssertionError("lead " + leadId + " is not in the page");
	}

	private static List<String> candidateIds(JsonNode detail) {
		List<String> ids = new ArrayList<>();
		detail.path("duplicateCandidates").forEach(candidate -> ids.add(candidate.path("id").asString()));
		return ids;
	}

	private static List<String> types(JsonNode activities) {
		List<String> types = new ArrayList<>();
		activities.forEach(activity -> types.add(activity.path("type").asString()));
		return types;
	}

	private static JsonNode withoutCorrelationId(JsonNode body) {
		ObjectNode copy = ((ObjectNode) body).deepCopy();
		copy.remove("correlationId");
		return copy;
	}

	private static List<String> errorFields(Result result) {
		List<String> fields = new ArrayList<>();
		result.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		return fields;
	}

	private String status(String leadId) {
		return jdbc.queryForObject("select status from owner_leads where id = ?", String.class, leadId);
	}

	private String assignee(String leadId) {
		return jdbc.queryForObject("select assigned_user_id from owner_leads where id = ?", String.class, leadId);
	}

	private long version(String leadId) {
		Long version = jdbc.queryForObject("select version from owner_leads where id = ?", Long.class, leadId);
		return version == null ? -1 : version;
	}

	private int accessLogs(String leadId) {
		return count("select count(*) from owner_lead_access_logs where lead_id = ?", leadId);
	}

	private int activityCount(String leadId) {
		return count("select count(*) from owner_lead_activities where lead_id = ?", leadId);
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}

	private static void assertValidationError(Result result, String field) {
		assertEquals(400, result.status(), result.rawBody());
		assertEquals("VALIDATION_FAILED", result.body().path("code").asString(), result.rawBody());
		assertEquals(List.of(field), errorFields(result), result.rawBody());
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
	}

	/**
	 * 409 INVALID_STATE_TRANSITION with one error on the field, listing exactly the allowed statuses. The messages
	 * are fixed sentences that never name the requested status.
	 */
	private static void assertTransitionError(Result result, String field, Set<String> allowed) {
		assertEquals(409, result.status(), result.rawBody());
		assertEquals("INVALID_STATE_TRANSITION", result.body().path("code").asString(), result.rawBody());
		assertEquals(List.of(field), errorFields(result), result.rawBody());
		JsonNode allowedValues = result.body().path("errors").get(0).path("allowedValues");
		assertTrue(allowedValues.isArray(), result.rawBody());
		Set<String> values = new HashSet<>();
		allowedValues.forEach(value -> values.add(value.asString()));
		assertEquals(allowed.size(), allowedValues.size(), result.rawBody());
		assertEquals(allowed, values, result.rawBody());
		String messages = result.body().path("message").asString() + " "
				+ result.body().path("errors").get(0).path("message").asString();
		for (String status : List.of("NEW", "ASSIGNED", "CONTACTED", "ON_HOLD", "QUALIFIED", "DUPLICATE", "REJECTED",
				"WITHDRAWN")) {
			assertFalse(messages.contains(status), messages);
		}
	}
}
