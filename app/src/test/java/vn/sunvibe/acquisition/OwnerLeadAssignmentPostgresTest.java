package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * Assignment at submission on PostgreSQL (acquisition-owner-lead-queue Q9-1, Q9-2, Q9-13): leads sent through the
 * public intake form, territories written as rows by JDBC, the real auth-service eligibility check behind the
 * ADR-009 adapter. A new lead goes to the person of its ward only when that person can sign in and holds SALE or
 * SALE_LEADER; otherwise it stays NEW for the leaders. The wards Liên Chiểu and Cẩm Lệ are used by no other test
 * class; every territory, lead and user made here is removed afterwards. Needs Docker.
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
class OwnerLeadAssignmentPostgresTest {

	private static final String TERRITORIES = "/api/v1/staff/sale-territories";
	private static final String LIEN_CHIEU = "20197";
	private static final String CAM_LE = "20260";
	private static final List<String> USED_WARDS = List.of(LIEN_CHIEU, CAM_LE);

	/** Phones 0981xxxxxx, apart from the other acquisition test classes. */
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
	private TestUser sale;
	private TestUser saleLeader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		admin = api.logIn(created(UserStatus.ACTIVE, "ADMIN"));
		sale = created(UserStatus.ACTIVE, "SALE");
		saleLeader = created(UserStatus.ACTIVE, "SALE_LEADER");
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

	// ==================== Q9-1: the ward's person gets the lead ====================

	/** Q9-1, US-1 AC-1, BR-3: the SALE of the ward gets the new lead; the system, not a person, made the assignment. */
	@Test
	void submit_wardWithEligibleSale_createsAssignedLeadWithAutoAssignedActivity() throws Exception {
		territory(LIEN_CHIEU, sale);

		String lead = submit(LIEN_CHIEU);

		assertAutoAssigned(lead, sale);
	}

	/** Q9-1, acquisition-owner-lead-queue C-15: a SALE_LEADER holding a ward gets its leads the same way. */
	@Test
	void submit_wardWithEligibleSaleLeader_createsAssignedLeadWithAutoAssignedActivity() throws Exception {
		territory(CAM_LE, saleLeader);

		String lead = submit(CAM_LE);

		assertAutoAssigned(lead, saleLeader);
	}

	// ==================== Q9-2: nobody who may take it, so NEW ====================

	/** Q9-2, US-1 AC-2, BR-4: a ward nobody looks after leaves the lead NEW, without an assignee. */
	@Test
	void submit_wardWithoutHolder_staysNew() throws Exception {
		String lead = submit(CAM_LE);

		assertStaysNew(lead);
	}

	/** Q9-2, BR-4: the ward's person holds neither SALE nor SALE_LEADER (another role, or none at all). */
	@Test
	void submit_holderWithoutRole_staysNew() throws Exception {
		for (TestUser holder : List.of(created(UserStatus.ACTIVE, "CUSTOMER"), created(UserStatus.ACTIVE, "OWNER"),
				created(UserStatus.ACTIVE))) {
			territory(LIEN_CHIEU, holder);

			String lead = submit(LIEN_CHIEU);

			assertStaysNew(lead);
			jdbc.update("delete from sale_territories where ward_code = ?", LIEN_CHIEU);
		}
	}

	/** Q9-2, BR-4: a locked SALE cannot sign in, so they get no new lead. */
	@Test
	void submit_holderLocked_staysNew() throws Exception {
		territory(LIEN_CHIEU, created(UserStatus.LOCK, "SALE"));

		String lead = submit(LIEN_CHIEU);

		assertStaysNew(lead);
	}

	/** Q9-2, BR-4: a soft-deleted SALE (the row is kept, deleted_at is set) gets no new lead. */
	@Test
	void submit_holderSoftDeleted_staysNew() throws Exception {
		TestUser deleted = created(UserStatus.ACTIVE, "SALE");
		assertEquals(1, jdbc.update("update users set deleted_at = now() where id = ?", deleted.userId()));
		territory(LIEN_CHIEU, deleted);

		String lead = submit(LIEN_CHIEU);

		assertStaysNew(lead);
	}

	// ==================== Q9-13: removing a ward's person does not move leads ====================

	/**
	 * Q9-13, BR-6: once the ward's person is removed (by an ADMIN, through the API) the next leads stay NEW, while the
	 * lead they already got keeps its assignee, status, version and history.
	 */
	@Test
	void submit_afterTerritoryRemoved_staysNewForNewLeadsOnly() throws Exception {
		territory(LIEN_CHIEU, sale);
		String before = submit(LIEN_CHIEU);
		assertAutoAssigned(before, sale);

		Result removed = api.delete(admin, TERRITORIES + "/" + LIEN_CHIEU);
		assertEquals(204, removed.status(), removed.rawBody());
		assertEquals(0, count("select count(*) from sale_territories where ward_code = ?", LIEN_CHIEU));

		String after = submit(LIEN_CHIEU);

		assertStaysNew(after);
		assertAutoAssigned(before, sale);
	}

	// ==================== Helpers ====================

	private TestUser created(UserStatus status, String... roles) {
		TestUser user = api.createUser(status, roles);
		userIds.add(user.userId());
		return user;
	}

	private void territory(String wardCode, TestUser holder) {
		jdbc.update("insert into sale_territories (ward_code, user_id, assigned_by, assigned_at) values (?, ?, ?, now())",
				wardCode, holder.userId(), admin.userId());
	}

	private String submit(String wardCode) throws Exception {
		int n = PHONE_SEQUENCE.getAndIncrement();
		String phone = "0981" + String.format("%06d", n);
		String leadId = AcquisitionApiTestSupport.leadId(jdbc, api.submitLead(wardCode, phone, n + " Tôn Đức Thắng"));
		leadIds.add(leadId);
		return leadId;
	}

	/** ASSIGNED to the holder at version 0, history SUBMITTED then AUTO_ASSIGNED, neither with an actor. */
	private void assertAutoAssigned(String leadId, TestUser holder) {
		Map<String, Object> row = jdbc.queryForMap(
				"select status, assigned_user_id, version from owner_leads where id = ?", leadId);
		assertEquals("ASSIGNED", row.get("status"));
		assertEquals(holder.userId(), row.get("assigned_user_id"));
		assertEquals(0L, ((Number) row.get("version")).longValue());

		List<List<String>> history = history(leadId);
		assertEquals(List.of(
				Arrays.asList("SUBMITTED", null, null, null, null, null),
				Arrays.asList("AUTO_ASSIGNED", null, "NEW", "ASSIGNED", null, holder.userId())), history);
	}

	/** NEW without an assignee, history SUBMITTED only. */
	private void assertStaysNew(String leadId) {
		Map<String, Object> row = jdbc.queryForMap("select status, assigned_user_id from owner_leads where id = ?",
				leadId);
		assertEquals("NEW", row.get("status"));
		assertNull(row.get("assigned_user_id"));
		assertEquals(List.of(Arrays.asList("SUBMITTED", null, null, null, null, null)), history(leadId));
	}

	/** [type, actor, from status, to status, from assignee, to assignee] of each activity, oldest first. */
	private List<List<String>> history(String leadId) {
		return jdbc.query("select type, actor_user_id, from_status, to_status, from_assignee_id, to_assignee_id "
						+ "from owner_lead_activities where lead_id = ? order by occurred_at, id",
				(rs, i) -> Arrays.asList(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6)),
				leadId);
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}
}
