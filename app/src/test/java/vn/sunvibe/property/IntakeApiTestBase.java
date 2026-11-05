package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.MediaApiTestBase;
import vn.sunvibe.survey.MediaIntakeFixture;
import vn.sunvibe.survey.MediaTestStore;
import vn.sunvibe.survey.MediaTestSwitches;
import vn.sunvibe.survey.PassedRevisionFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * What the intake API tests share (property-create intake spec 7): real PASSED revisions, the staff roles, an owner account
 * with a verified address, the HTTP calls, and the SQL that seeds rows a test does not build through the API. One context
 * with the survey API tests, so the intake switch is on there. Needs Docker.
 */
abstract class IntakeApiTestBase extends MediaApiTestBase {

	static final String LEADS = SurveyApiTestSupport.LEADS;
	static final String CASES = "/api/v1/staff/inventory-cases";
	static final String CONFIRMATIONS = "/api/v1/me/property-confirmations";
	static final String WARD = SurveyApiTestSupport.WARD;
	static final String STATEMENT = "owner-link-2026-11";
	static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	IdGenerator<String> ids;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	private MediaTestStore store;

	@Autowired
	private MediaTestSwitches switches;

	private final Map<String, String> passwords = new java.util.concurrent.ConcurrentHashMap<>();

	PassedRevisionFixture fixture;
	AcquisitionApiTestSupport api;
	SurveyApiTestSupport survey;
	TestUser sale;
	TestUser otherSale;
	TestUser leader;
	TestUser manager;
	TestUser admin;

	/** Four digits that keep this class's phones apart from the others. */
	abstract String phonePrefix();

	@BeforeAll
	void setUpIntake() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ids);
		fixture = new PassedRevisionFixture(api, jdbc, ids, store, phonePrefix());
		survey = fixture.survey();
		sale = survey.user("Intake Sale", "SALE");
		otherSale = survey.user("Intake Other Sale", "SALE");
		leader = survey.user("Intake Leader", "SALE_LEADER");
		manager = survey.user("Intake Manager", "SALES_MANAGER");
		admin = survey.user("Intake Admin", "ADMIN");
	}

	@BeforeEach
	void openUploads() {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
	}

	@AfterEach
	void resetUploads() {
		store.reset();
		switches.reset();
	}

	@AfterAll
	void tearDownIntake() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
		deleteIntakesOfGoneLeads();
	}

	/** The cleanup above deletes the leads; their intake rows must not reach the next class of this shared context. */
	private void deleteIntakesOfGoneLeads() {
		String gone = "(select i.id from inventory_intakes i where not exists (select 1 from owner_leads l where l.id = i.lead_id))";
		String goneLead = "lead_id not in (select id from owner_leads)";
		jdbc.update("delete from inventory_access_logs where " + goneLead);
		jdbc.update("delete from inventory_suspensions where " + goneLead);
		jdbc.update("delete from inventory_intake_events where intake_id in " + gone);
		jdbc.update("delete from inventory_unit_mappings where intake_id in " + gone);
		jdbc.update("delete from inventory_duplicate_cases where intake_id in " + gone + " or candidate_intake_id in " + gone
				+ " or proceeding_intake_id in " + gone);
		jdbc.update("update inventory_intakes set status = 'ABANDONED', proof_id = null where id in " + gone);
		jdbc.update("delete from inventory_owner_proofs where intake_id in " + gone);
		jdbc.update("delete from inventory_owner_requests where intake_id in " + gone);
		jdbc.update("delete from inventory_intakes where " + goneLead);
	}

	// ==================== fixtures ====================

	/** A signed-in account with a verified address: who says they own the asset. */
	TestUser owner() throws Exception {
		return owner(UserStatus.ACTIVE);
	}

	TestUser owner(UserStatus status) throws Exception {
		TestUser created = api.createUser("Intake Owner", UserStatus.ACTIVE, "CUSTOMER");
		String address = address(created);
		jdbc.update("update users set email = ?, email_key = ?, email_verified_at = ? where id = ?", address, address,
				Timestamp.from(Instant.now()), created.userId());
		passwords.put(created.userId(), created.secret());
		TestUser signedIn = api.logIn(created);
		jdbc.update("update users set user_status = ? where id = ?", status.name(), created.userId());
		return signedIn;
	}

	String passwordOf(TestUser user) {
		return passwords.get(user.userId());
	}

	/** A request the assignee made: what the Sale reads out by phone. */
	record Issued(String requestId, String code) {
	}

	Result requestOwner(TestUser caller, String leadId, String email, Long expectedVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ownerEmail", email);
		body.put("expectedVersion", expectedVersion);
		return api.post(caller, base(leadId) + "/owner-confirmation", body);
	}

	/** The assignee asks this address to confirm; the answer must be 200. */
	Issued issued(String leadId, String email) throws Exception {
		Result answer = requestOwner(sale, leadId, email, versionOf(sale, leadId));
		SurveyApiTestSupport.ok(answer);
		return new Issued(answer.body().path("requestId").asString(), answer.body().path("code").asString());
	}

	Result details(TestUser owner, String requestId, String code) throws Exception {
		return api.post(owner, CONFIRMATIONS + "/" + requestId + "/details", Map.of("code", code));
	}

	Result confirm(TestUser owner, String requestId, String code, String password, String statement) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", code);
		body.put("currentPassword", password);
		body.put("statementVersion", statement);
		return api.post(owner, CONFIRMATIONS + "/" + requestId + "/confirm", body);
	}

	/** The account confirms with everything right; the answer must be 204. */
	void confirmed(TestUser owner, Issued issued) throws Exception {
		Result answer = confirm(owner, issued.requestId(), issued.code(), passwordOf(owner), STATEMENT);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	Result decline(TestUser owner, String requestId, String code) throws Exception {
		return api.post(owner, CONFIRMATIONS + "/" + requestId + "/decline", Map.of("code", code));
	}

	Result withdraw(TestUser owner, String requestId, String password) throws Exception {
		return api.post(owner, CONFIRMATIONS + "/" + requestId + "/withdrawal", Map.of("currentPassword", password));
	}

	String proofIdOf(String leadId) {
		return jdbc.queryForObject("select id from inventory_owner_proofs where lead_id = ? and withdrawn_at is null"
				+ " and voided_at is null order by confirmed_at desc limit 1", String.class, leadId);
	}

	Result context(TestUser caller, String leadId) throws Exception {
		return api.get(caller, base(leadId) + "/owner-check-context");
	}

	Result startCheck(TestUser caller, String leadId, String proofId, String basis, Long expectedVersion) throws Exception {
		return api.post(caller, base(leadId) + "/owner-check/start", proofBody(proofId, basis, expectedVersion));
	}

	Result refuse(TestUser caller, String leadId, String proofId, String basis, Long expectedVersion) throws Exception {
		return api.post(caller, base(leadId) + "/owner-check/refusal", proofBody(proofId, basis, expectedVersion));
	}

	private static Map<String, Object> proofBody(String proofId, String basis, Long expectedVersion) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("proofId", proofId);
		body.put("basis", basis);
		body.put("expectedVersion", expectedVersion);
		return body;
	}

	/** The manager starts the check of the lead's proof; the answer must be 200. Answers the phrase. */
	String started(TestUser checker, String leadId) throws Exception {
		Result answer = startCheck(checker, leadId, proofIdOf(leadId), "Called the number on the lead", versionOf(checker, leadId));
		SurveyApiTestSupport.ok(answer);
		return answer.body().path("phrase").asString();
	}

	Result enter(TestUser owner, String requestId, String phrase) throws Exception {
		return api.post(owner, CONFIRMATIONS + "/" + requestId + "/check", Map.of("phrase", phrase));
	}

	Result suspend(TestUser caller, String leadId, String unitIdOrNull, String reason, Long expectedVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("unitId", unitIdOrNull);
		body.put("reason", reason);
		body.put("expectedVersion", expectedVersion);
		return api.post(caller, base(leadId) + "/suspensions", body);
	}

	Result release(TestUser caller, String leadId, String suspensionId, String reason, Long expectedVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("releaseReason", reason);
		body.put("expectedVersion", expectedVersion);
		return api.post(caller, base(leadId) + "/suspensions/" + suspensionId + "/release", body);
	}

	/** Selection, confirmation and the owner check done: what search, target and completion need. */
	Issued checked(Passed passed, TestUser owner) throws Exception {
		Issued issued = proved(passed, owner);
		Result entry = enter(owner, issued.requestId(), started(manager, passed.leadId()));
		assertEquals(204, entry.status(), entry.rawBody());
		return issued;
	}

	Result search(TestUser caller, String leadId, String query) throws Exception {
		return api.get(caller, base(leadId) + "/inventory-search?" + query);
	}

	Result createTarget(String leadId, String canonicalSpaceIdOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("mode", "CREATE");
		body.put("canonicalSpaceId", canonicalSpaceIdOrNull);
		body.put("expectedVersion", versionOf(sale, leadId));
		return api.put(sale, base(leadId) + "/target", body);
	}

	/** A link of the spaces onto the units, in order. */
	Result linkTarget(String leadId, String propertyId, List<String> spaceIds, List<String> unitIds) throws Exception {
		List<Map<String, Object>> mappings = new java.util.ArrayList<>();
		for (int i = 0; i < spaceIds.size(); i++) {
			mappings.add(Map.of("spaceId", spaceIds.get(i), "unitId", unitIds.get(i)));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("mode", "LINK");
		body.put("propertyId", propertyId);
		body.put("mappings", mappings);
		body.put("expectedVersion", versionOf(sale, leadId));
		return api.put(sale, base(leadId) + "/target", body);
	}

	static LocalDate today() {
		return LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
	}

	static Map<String, Object> availabilityItem(String spaceId, String state, LocalDate contactedOn, String note) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("spaceId", spaceId);
		item.put("state", state);
		item.put("contactedOn", contactedOn == null ? null : contactedOn.toString());
		item.put("note", note);
		return item;
	}

	Result availability(String leadId, List<Map<String, Object>> items) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("items", items);
		body.put("expectedVersion", versionOf(sale, leadId));
		return api.put(sale, base(leadId) + "/availability", body);
	}

	/** Every space confirmed in this state today; the answer must be 200. */
	void available(String leadId, List<String> spaceIds, String state) throws Exception {
		SurveyApiTestSupport.ok(availability(leadId, spaceIds.stream()
				.map(id -> availabilityItem(id, state, today(), "Owner said so on the phone")).toList()));
	}

	Result preview(TestUser caller, String leadId) throws Exception {
		return api.get(caller, base(leadId) + "/preview");
	}

	/** The preview, which must answer 200. */
	JsonNode previewOk(String leadId) throws Exception {
		Result answer = preview(sale, leadId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	Result complete(TestUser caller, String leadId, Long expectedVersion, String previewHash) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", expectedVersion);
		body.put("previewHash", previewHash);
		return api.post(caller, base(leadId) + "/completion", body);
	}

	/** The assignee completes on the current preview; the answer must be 200. */
	JsonNode completed(String leadId) throws Exception {
		Result answer = complete(sale, leadId, versionOf(sale, leadId), previewOk(leadId).path("previewHash").asString());
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	/** A surveyed house taken through a create, by this owner: a result to work on. */
	JsonNode createdHouse(Passed passed, TestUser owner) throws Exception {
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return completed(passed.leadId());
	}

	/** Selection, request and confirmation of the lead's spaces by this owner. */
	Issued proved(Passed passed, TestUser owner) throws Exception {
		return proved(passed.leadId(), passed.spaceIds(), owner);
	}

	Issued proved(String leadId, List<String> spaceIds, TestUser owner) throws Exception {
		selected(leadId, spaceIds);
		Issued issued = issued(leadId, address(owner));
		confirmed(owner, issued);
		return issued;
	}

	static String address(TestUser user) {
		return "owner." + user.userId().toLowerCase() + "@example.com";
	}

	Passed house(String address) throws Exception {
		return fixture.whole(leader, sale, "HOUSE", address);
	}

	String unique(String text) {
		return SEQUENCE.incrementAndGet() + " " + text + " " + phonePrefix();
	}

	/** A property as the legacy create leaves it, or as a completed intake does when {@code intakeId} is given. */
	String property(String ownerId, String wardCode, String wardName, String street, String assetType, String addressNorm) {
		String id = ids.generateId();
		String code = "WT-INT-" + String.format("%06d", SEQUENCE.incrementAndGet());
		LocalDateTime now = LocalDateTime.of(2026, 11, 6, 9, 0);
		jdbc.update("insert into properties (id, property_code, title, street, ward, district, city, property_type, bedrooms,"
						+ " bathrooms, square_meters, monthly_price, deposit_price, min_rent_months, status, owner_id, created_at,"
						+ " updated_at, created_by, updated_by, asset_type, ward_code, address_norm) values (?, ?, 'Legacy', ?, ?,"
						+ " 'Hải Châu', 'Đà Nẵng', 'STUDIO', 1, 1, 30, 5000000, 5000000, 6, 'AVAILABLE', ?, ?, ?, 'seed', 'seed', ?,"
						+ " ?, ?)", id, code, street, wardName, ownerId, now, now, assetType, wardCode, addressNorm);
		return id;
	}

	/** A unit of a property; the first one of a legacy property carries its code. */
	String unit(String propertyId, String code, String state) {
		String id = ids.generateId();
		Instant now = Instant.parse("2026-11-06T02:00:00Z");
		jdbc.update("insert into units (id, property_id, unit_code, bedrooms, bathrooms, area_sqm, availability_state,"
						+ " created_at, updated_at) values (?, ?, ?, 1, 1, 30, ?, ?, ?)", id, propertyId, code, state,
				Timestamp.from(now), Timestamp.from(now));
		return id;
	}

	void ownership(String propertyId, String userId) {
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
						+ " created_at) values (?, ?, ?, 'OWNER', 'UNVERIFIED', ?, ?)", ids.generateId(), propertyId, userId,
				LocalDate.of(2026, 1, 1), Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
	}

	// ==================== HTTP ====================

	static String base(String leadId) {
		return LEADS + "/" + leadId + "/inventory-intake";
	}

	Result block(TestUser caller, String leadId) throws Exception {
		return api.get(caller, base(leadId));
	}

	/** The block, which must answer 200. */
	JsonNode blockOk(TestUser caller, String leadId) throws Exception {
		Result block = block(caller, leadId);
		assertEquals(200, block.status(), block.rawBody());
		return block.body();
	}

	/** The expectedVersion a client sends: the block's, null with no current row. */
	Long versionOf(TestUser caller, String leadId) throws Exception {
		JsonNode version = blockOk(caller, leadId).path("version");
		return version.isNull() ? null : version.asLong();
	}

	Result select(TestUser caller, String leadId, List<String> spaceIds, Long expectedVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("spaceIds", spaceIds);
		body.put("expectedVersion", expectedVersion);
		return api.put(caller, base(leadId) + "/selection", body);
	}

	/** The assignee selects these spaces; the answer must be 200. */
	JsonNode selected(String leadId, List<String> spaceIds) throws Exception {
		Result answer = select(sale, leadId, spaceIds, versionOf(sale, leadId));
		SurveyApiTestSupport.ok(answer);
		return answer.body();
	}

	static void assertRefused(int status, String code, Result answer) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
	}

	static Set<String> reasons(JsonNode block) {
		Set<String> reasons = new java.util.TreeSet<>();
		block.path("gate").path("reasons").forEach(reason -> reasons.add(reason.asString()));
		return reasons;
	}

	int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	Map<String, Object> intakeRow(String leadId) {
		return jdbc.queryForMap("select * from inventory_intakes where lead_id = ? order by intake_no desc limit 1", leadId);
	}

	void hold(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "ON_HOLD");
		body.put("expectedVersion", survey.version(leadId));
		body.put("note", "Owner abroad");
		SurveyApiTestSupport.ok(api.post(leader, LEADS + "/" + leadId + "/status-changes", body));
	}

	void close(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "WITHDRAWN");
		body.put("expectedVersion", survey.version(leadId));
		body.put("reason", "Owner sold the house");
		SurveyApiTestSupport.ok(api.post(leader, LEADS + "/" + leadId + "/status-changes", body));
	}
}
