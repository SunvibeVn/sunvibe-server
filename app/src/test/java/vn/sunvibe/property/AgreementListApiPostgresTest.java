package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.AgreementListFixture;
import vn.sunvibe.brokerage.AgreementListFixture.Version;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The staff agreement list over HTTP on PostgreSQL (brokerage-agreement US-4 AC-1, AC-3; BR-5, BR-6, BR-12, BR-16, BR-17;
 * E05-S7 spec S7-D1 to S7-D6, S7-D13; criteria LS-1 to LS-6). Agreements are made through the API chain as
 * {@link AgreementActivationApiPostgresTest} makes them, or by {@link AgreementListFixture} where a stored state is all a
 * case needs. A caller who sees every lead shares the database with other classes, so each case makes its agreements
 * just before it reads, finds them on the first page of 50 of the default order, newest first, and asserts only about
 * them. The annotations of its base, so it shares the context of the other agreement API tests. Needs Docker.
 */
class AgreementListApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String OWNER_STATEMENT = "brokerage-agreement-2026-11";
	private static final String SEVEN = "[\"DRAFTING\",\"AWAITING_CONFIRMATION\",\"ACTIVE\",\"SUSPENDED\",\"EXPIRED\","
			+ "\"TERMINATED\",\"CANCELLED\"]";

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0929";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== HTTP ====================

	private Result list(TestUser caller, String query) throws Exception {
		return api.get(caller, AGREEMENTS + query);
	}

	/** The list, which must answer 200. */
	private JsonNode listOk(TestUser caller, String query) throws Exception {
		Result answer = list(caller, query);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	private static List<String> ids(JsonNode page) {
		List<String> ids = new ArrayList<>();
		page.path("items").forEach(item -> ids.add(item.path("agreementId").asString()));
		return ids;
	}

	/** The row of the agreement on the caller's first page of 50; it must be there. */
	private JsonNode rowOf(TestUser caller, String agreementId) throws Exception {
		for (JsonNode item : listOk(caller, "?size=50").path("items")) {
			if (agreementId.equals(item.path("agreementId").asString())) {
				return item;
			}
		}
		throw new AssertionError(agreementId + " not on the first page of " + caller);
	}

	private JsonNode detail(TestUser caller, String agreementId) throws Exception {
		Result answer = api.get(caller, AGREEMENTS + "/" + agreementId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	private static void assertValidationFailed(Result answer, String field, String allowedValuesOrNull) {
		assertEquals(400, answer.status(), answer.rawBody());
		assertEquals("VALIDATION_FAILED", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("Validation failed.", answer.body().path("message").asString(), answer.rawBody());
		JsonNode errors = answer.body().path("errors");
		assertEquals(1, errors.size(), answer.rawBody());
		assertEquals(field, errors.path(0).path("field").asString(), answer.rawBody());
		assertEquals("This value is not accepted.", errors.path(0).path("message").asString(), answer.rawBody());
		if (allowedValuesOrNull == null) {
			assertTrue(errors.path(0).path("allowedValues").isMissingNode(), answer.rawBody());
		} else {
			assertEquals(allowedValuesOrNull, errors.path(0).path("allowedValues").toString(), answer.rawBody());
		}
	}

	private static List<String> sortedKeys(JsonNode node) {
		List<String> keys = new ArrayList<>();
		node.properties().forEach(entry -> keys.add(entry.getKey()));
		return keys.stream().sorted().toList();
	}

	// ==================== fixtures ====================

	/** A fixture agreement on the lead, created now, in the state, with one version in the state over the period. */
	private String fixture(String leadId, String status, String versionStatus, LocalDate start, LocalDate end) {
		return fixture(leadId, status, Instant.now().truncatedTo(ChronoUnit.MICROS),
				new Version(1, versionStatus, start, end));
	}

	private String fixture(String leadId, String status, Instant createdAt, Version... versions) {
		String id = AgreementListFixture.insert(jdbc, ids, leadId, "P-LIST", status, createdAt, versions);
		created.add(id);
		return id;
	}

	private String drafting(String leadId) {
		return fixture(leadId, "DRAFTING", "DRAFT", LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
	}

	private record Chain(String leadId, TestUser owner, String propertyId, String unitId, String agreementId) {
	}

	private TestUser newOwner() throws Exception {
		TestUser owner = owner();
		owners.add(owner.userId());
		return owner;
	}

	/** A house lead of {@code sale} with a completed intake for a new owner, and the Sale's complete draft on its unit. */
	private Chain drafted(LocalDate start, LocalDate end) throws Exception {
		Passed passed = house(unique("List Street"));
		TestUser owner = newOwner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		Chain chain = new Chain(passed.leadId(), owner, propertyId, unit, create(sale, passed.leadId(), unit));
		saveComplete(sale, chain.agreementId(), start, end);
		return chain;
	}

	/** {@link #drafted} sent by the Sale and confirmed by its owner now: version 1 awaits activation. */
	private Chain confirmed(LocalDate start, LocalDate end) throws Exception {
		Chain chain = drafted(start, end);
		send(sale, chain.agreementId());
		confirm(chain.owner(), chain.agreementId(), 1);
		return chain;
	}

	/** {@link #confirmed} activated by the manager. */
	private Chain activated(LocalDate start, LocalDate end) throws Exception {
		Chain chain = confirmed(start, end);
		Result answer = activate(manager, chain.agreementId());
		assertEquals(204, answer.status(), answer.rawBody());
		return chain;
	}

	private String create(TestUser drafter, String leadId, String unitId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(drafter, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		return id;
	}

	private void saveComplete(TestUser editor, String agreementId, LocalDate start, LocalDate end) throws Exception {
		Map<String, Object> body = AgreementFeePolicyApiPostgresTest.complete(rowVersion(agreementId));
		body.put("startDate", start.toString());
		body.put("endDate", end.toString());
		Result answer = api.put(editor, AGREEMENTS + "/" + agreementId + "/draft", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private void send(TestUser sender, String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		Result answer = api.post(sender, AGREEMENTS + "/" + agreementId + "/draft/submission", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private void confirm(TestUser owner, String agreementId, int versionNo) throws Exception {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("agreementId", agreementId);
		item.put("versionNo", versionNo);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", passwordOf(owner));
		body.put("statementVersion", OWNER_STATEMENT);
		body.put("versions", List.of(item));
		Result answer = api.post(owner, ME + "/confirmations", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private Result activate(TestUser caller, String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		body.put("versionNo", 1);
		body.put("statementVersion", ActivationStatement.VERSION);
		body.put("documentNumber", "HDMG-2026/014");
		body.put("signedOn", today().toString());
		body.put("sunvibeSignatoryName", "Nguyen Van A");
		body.put("sunvibeSignatoryTitle", "Director");
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation", body);
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	/** The access-log rows of the agreement as "user action", oldest first. */
	private List<String> accessRows(String agreementId) {
		return jdbc.queryForList("select user_id || ' ' || action from brokerage_agreement_access_logs"
				+ " where agreement_id = ? order by id", String.class, agreementId);
	}

	// ==================== LS-1: who lists what ====================

	/** LS-1 (BR-16): a leader, a manager, an admin and a super admin list the agreements of every Sale's leads. */
	@Test
	void list_managersAndAdmins_everyAgreement() throws Exception {
		TestUser superAdmin = survey.user("List Super Admin", "SUPER_ADMIN");
		String ofSale = drafting(survey.lead(leader, sale, "ASSIGNED"));
		String ofOther = drafting(survey.lead(leader, otherSale, "ASSIGNED"));

		for (TestUser reader : List.of(leader, manager, admin, superAdmin)) {
			List<String> listed = ids(listOk(reader, "?size=50"));

			assertTrue(listed.containsAll(List.of(ofSale, ofOther)), reader + " " + listed);
		}
	}

	/** LS-1 (BR-16, IDOR): a Sale lists the agreements of the leads assigned to them now, never another Sale's. */
	@Test
	void list_sale_onlyAgreementsOfLeadsAssignedNow() throws Exception {
		String ofSale = drafting(survey.lead(leader, sale, "ASSIGNED"));
		String ofOther = drafting(survey.lead(leader, otherSale, "ASSIGNED"));

		List<String> bySale = ids(listOk(sale, "?size=50"));
		List<String> byOther = ids(listOk(otherSale, "?size=50"));

		assertTrue(bySale.contains(ofSale), bySale.toString());
		assertFalse(bySale.contains(ofOther), bySale.toString());
		assertTrue(byOther.contains(ofOther), byOther.toString());
		assertFalse(byOther.contains(ofSale), byOther.toString());
	}

	/** LS-1, Review Focus 5 (BR-16, E-10): after a reassignment the former Sale loses the row, the new Sale gains it. */
	@Test
	void list_afterReassignment_formerSaleLosesNewSaleGains() throws Exception {
		String leadId = survey.lead(leader, sale, "ASSIGNED");
		String agreementId = drafting(leadId);
		assertTrue(ids(listOk(sale, "?size=50")).contains(agreementId));

		fixture.checklists().reassign(leader, leadId, otherSale);

		assertFalse(ids(listOk(sale, "?size=50")).contains(agreementId));
		assertTrue(ids(listOk(otherSale, "?size=50")).contains(agreementId));
	}

	/** LS-1 (S7-D2, fail closed): a Sale with no assigned lead gets an empty page. */
	@Test
	void list_saleWithoutLeads_emptyPage() throws Exception {
		TestUser lone = survey.user("List Sale Without Leads", "SALE");
		drafting(survey.lead(leader, sale, "ASSIGNED"));

		JsonNode page = listOk(lone, "");

		assertEquals("[]", page.path("items").toString());
		assertEquals(0, page.path("totalElements").asLong());
		assertEquals(0, page.path("page").asInt());
		assertEquals(20, page.path("size").asInt());
	}

	@Test
	void list_customerRole_forbidden() throws Exception {
		TestUser customer = survey.user("List Customer", "CUSTOMER");

		Result answer = list(customer, "");

		assertEquals(403, answer.status(), answer.rawBody());
		assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
	}

	@Test
	void list_noToken_unauthorized() throws Exception {
		Result answer = list(null, "");

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
	}

	// ==================== LS-2: the state filter and the state at the read ====================

	/** LS-2 (S7-D3): the stored states asked, one, several or none (every state). */
	@Test
	void list_statusFilter_storedStatesOneSeveralNone() throws Exception {
		TestUser lone = survey.user("List State Sale", "SALE");
		String leadId = survey.lead(leader, lone, "ASSIGNED");
		String drafting = drafting(leadId);
		String active = fixture(leadId, "ACTIVE", "EFFECTIVE", today().minusDays(30), today().plusDays(150));
		String suspended = fixture(leadId, "SUSPENDED", "EFFECTIVE", today().minusDays(30), today().plusDays(150));

		assertEquals(List.of(active), ids(listOk(lone, "?status=ACTIVE")));
		assertEquals(List.of(suspended, active), ids(listOk(lone, "?status=ACTIVE&status=SUSPENDED")));
		JsonNode all = listOk(lone, "");
		assertEquals(List.of(suspended, active, drafting), ids(all));
		assertEquals(3, all.path("totalElements").asLong());
		assertEquals("ACTIVE", listOk(lone, "?status=ACTIVE").path("items").path(0).path("status").asString());
	}

	/** LS-2 (S7-D13): an unknown state code answers 400 with one entry status and the seven codes. */
	@Test
	void list_unknownStatus_validationFailedWithAllowedValues() throws Exception {
		for (String query : List.of("?status=ENDED", "?status=ACTIVE&status=active")) {
			Result answer = list(manager, query);

			assertValidationFailed(answer, "status", SEVEN);
			assertFalse(answer.rawBody().contains("\"ENDED\"") || answer.rawBody().contains("\"active\""),
					answer.rawBody());
		}
	}

	/**
	 * LS-2 (S7-D3): an agreement awaiting confirmation whose 14 days of activation have passed reads DRAFTING and its
	 * version LAPSED at the read, whether or not the lapse job has stored it yet.
	 */
	@Test
	void list_lapseDueNotStored_rowDraftingVersionLapsed() throws Exception {
		String leadId = survey.lead(leader, sale, "ASSIGNED");
		String id = fixture(leadId, "AWAITING_CONFIRMATION", "AWAITING_OWNER", LocalDate.parse("2027-01-01"),
				LocalDate.parse("2027-12-31"));
		Instant confirmedAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(15, ChronoUnit.DAYS);
		jdbc.update("update brokerage_agreement_versions set status = 'OWNER_CONFIRMED', sent_at = ?, owner_answer ="
				+ " 'CONFIRMED', owner_answered_by = ?, owner_answered_at = ?, owner_statement_version = ?"
				+ " where agreement_id = ?", Timestamp.from(confirmedAt.minus(1, ChronoUnit.DAYS)),
				AgreementFixture.FIXTURE_USER, Timestamp.from(confirmedAt), OWNER_STATEMENT, id);

		JsonNode row = rowOf(sale, id);

		assertEquals("DRAFTING", row.path("status").asString(), row.toString());
		assertEquals(1, row.path("latestVersionNo").asInt());
		assertEquals("LAPSED", row.path("latestVersionStatus").asString(), row.toString());
		assertEquals("[\"NOT_IN_EFFECT\"]", row.path("advertising").path("reasonGroups").toString());
	}

	// ==================== LS-3: ends within the next 30 days ====================

	/**
	 * LS-3 (S7-D4, plan E05S7-P3): ACTIVE agreements whose effective version ends today and today + 29 in Vietnam, in
	 * that order; one ending today + 30 is not listed. The Vietnam date is read before the fixtures and after the read,
	 * and the case runs again once when they differ.
	 */
	@Test
	void list_endsWithin30Days_soonestFirstInEffectOnly() throws Exception {
		for (int attempt = 0; attempt < 2; attempt++) {
			LocalDate today = today();
			TestUser lone = survey.user("List Ending Sale " + attempt, "SALE");
			String leadId = survey.lead(leader, lone, "ASSIGNED");
			String endsToday = fixture(leadId, "ACTIVE", "EFFECTIVE", today.minusDays(60), today);
			String endsLast = fixture(leadId, "ACTIVE", "EFFECTIVE", today.minusDays(60), today.plusDays(29));
			fixture(leadId, "ACTIVE", "EFFECTIVE", today.minusDays(60), today.plusDays(30));

			JsonNode page = listOk(lone, "?endsWithin30Days=true");

			if (!today.equals(today())) {
				continue;
			}
			assertEquals(List.of(endsToday, endsLast), ids(page));
			assertEquals(2, page.path("totalElements").asLong());
			assertEquals(today.toString(), page.path("items").path(0).path("advertising").path("endDate").asString());
			assertEquals(3, ids(listOk(lone, "")).size());
			return;
		}
		throw new AssertionError("the read crossed Vietnam midnight twice");
	}

	// ==================== LS-4: each row's advertising answer equals the detail's ====================

	/**
	 * LS-4 (US-4 AC-1, AC-3; BR-12; E-4; examples 3, 10; S7-D5): drafting, activated with its start ahead, allowed, past
	 * its end, stopped by a whole-lead suspension of another lead of the property, and with an owner who holds a staff
	 * role: each row's allowed, groups, end date and end equal the detail's advertising, and its start date the detail's
	 * pending version's start date or null.
	 */
	@Test
	void list_rowAdvertising_equalsTheDetailAtTheSameClock() throws Exception {
		Chain drafting = drafted(today(), today().plusMonths(6));
		assertRowEqualsDetail(drafting.agreementId(), "[\"NOT_IN_EFFECT\"]");

		LocalDate start = today().plusDays(2);
		Chain notStarted = activated(start, start.plusMonths(6));
		JsonNode notStartedRow = assertRowEqualsDetail(notStarted.agreementId(), "[\"NOT_STARTED\"]");
		assertEquals(start.toString(), notStartedRow.path("advertising").path("startDate").asString());

		Chain allowed = activated(today(), today().plusMonths(6));
		JsonNode allowedRow = assertRowEqualsDetail(allowed.agreementId(), "[]");
		assertTrue(allowedRow.path("advertising").path("allowed").asBoolean(), allowedRow.toString());
		assertEquals(today().plusMonths(6).toString(), allowedRow.path("advertising").path("endDate").asString());

		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'",
				allowed.owner().userId());
		try {
			JsonNode ownerRow = assertRowEqualsDetail(allowed.agreementId(), null);
			assertTrue(ownerRow.path("advertising").path("reasonGroups").toString().contains("OWNER"), ownerRow.toString());
		} finally {
			jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
					allowed.owner().userId());
		}

		Chain ended = activated(today(), today().plusMonths(6));
		jdbc.update("update brokerage_agreement_versions set start_date = ?, end_date = ? where agreement_id = ?",
				Date.valueOf(today().minusDays(200)), Date.valueOf(today().minusDays(1)), ended.agreementId());
		JsonNode endedRow = assertRowEqualsDetail(ended.agreementId(), "[\"ENDED\"]");
		assertEquals(today().minusDays(1).toString(), endedRow.path("advertising").path("endDate").asString());

		// two houses on one street: the first creates the property, the second links onto its one unit; a whole-lead
		// suspension of the second stops the first's agreement as PROPERTY, naming nothing of that lead
		String street = unique("Two Leads List");
		Passed first = house(street);
		TestUser owner = newOwner();
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		available(first.leadId(), first.spaceIds(), "VACANT");
		JsonNode result = completed(first.leadId());
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		Passed second = house(street);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		String onProperty = create(sale, first.leadId(), unit);
		saveComplete(sale, onProperty, today(), today().plusMonths(6));
		send(sale, onProperty);
		confirm(owner, onProperty, 1);
		assertEquals(204, activate(manager, onProperty).status());
		suspendLead(second.leadId(), null, "Data request");
		JsonNode propertyRow = assertRowEqualsDetail(onProperty, "[\"PROPERTY\"]");
		assertFalse(propertyRow.toString().contains(second.leadId()), propertyRow.toString());
	}

	/**
	 * The row's five advertising values against the detail's, read by the manager one after the other; checks the
	 * groups when given, and answers the row.
	 */
	private JsonNode assertRowEqualsDetail(String agreementId, String groupsOrNull) throws Exception {
		JsonNode row = rowOf(manager, agreementId);
		JsonNode advertising = detail(manager, agreementId).path("advertising");
		JsonNode listed = row.path("advertising");
		assertEquals(advertising.path("allowed"), listed.path("allowed"), row.toString());
		assertEquals(advertising.path("reasonGroups"), listed.path("reasonGroups"), row.toString());
		assertEquals(advertising.path("endDate"), listed.path("endDate"), row.toString());
		assertEquals(advertising.path("endsAt"), listed.path("endsAt"), row.toString());
		JsonNode pending = advertising.path("pendingVersion");
		assertEquals(pending.isNull() ? NullNode.getInstance() : pending.path("startDate"), listed.path("startDate"),
				row.toString());
		if (groupsOrNull != null) {
			assertEquals(groupsOrNull, listed.path("reasonGroups").toString(), row.toString());
		}
		return row;
	}

	// ==================== LS-5: the fields, and nothing written ====================

	/** LS-5 (BR-17, S7-D5): the page, the item and its advertising have exactly the contract's keys. */
	@Test
	void list_itemKeys_exactlyTheContractFields() throws Exception {
		TestUser lone = survey.user("List Keys Sale", "SALE");
		String id = drafting(survey.lead(leader, lone, "ASSIGNED"));

		JsonNode page = listOk(lone, "");

		assertEquals(List.of("items", "page", "size", "totalElements"), sortedKeys(page));
		JsonNode item = page.path("items").path(0);
		assertEquals(id, item.path("agreementId").asString());
		assertEquals(List.of("advertising", "agreementId", "createdAt", "latestVersionNo", "latestVersionStatus", "status",
				"transactionType", "unitCode"), sortedKeys(item));
		assertEquals(List.of("allowed", "endDate", "endsAt", "reasonGroups", "startDate"),
				sortedKeys(item.path("advertising")));
		assertEquals("P-LIST", item.path("unitCode").asString());
		assertEquals("LONG_TERM_RENT", item.path("transactionType").asString());
		assertEquals("DRAFT", item.path("latestVersionStatus").asString());
		for (String secret : List.of(AgreementFixture.FIXTURE_USER, AgreementFixture.FIXTURE_INTAKE, "P-FIXTURE")) {
			assertFalse(page.toString().contains(secret), secret + " in " + page);
		}
	}

	/** LS-5 (BR-17, S7-D6): a list read writes no access-log, history or outbox row. */
	@Test
	void list_read_writesNoAccessHistoryOrOutboxRow() throws Exception {
		TestUser lone = survey.user("List Silent Sale", "SALE");
		String leadId = survey.lead(leader, lone, "ASSIGNED");
		List<String> mine = List.of(drafting(leadId),
				fixture(leadId, "ACTIVE", "EFFECTIVE", today().minusDays(30), today().plusDays(150)));
		int byLone = count("select count(*) from brokerage_agreement_access_logs where user_id = ?", lone.userId());
		int byManager = count("select count(*) from brokerage_agreement_access_logs where user_id = ?", manager.userId());
		int outbox = count("select count(*) from outbox_events");

		assertEquals(2, listOk(lone, "").path("items").size());
		listOk(manager, "?size=50");
		listOk(lone, "?status=ACTIVE&endsWithin30Days=true");

		for (String id : mine) {
			assertEquals(List.of(), accessRows(id), id);
			assertEquals(0, count("select count(*) from brokerage_agreement_events where agreement_id = ?", id), id);
		}
		assertEquals(byLone, count("select count(*) from brokerage_agreement_access_logs where user_id = ?", lone.userId()));
		assertEquals(byManager, count("select count(*) from brokerage_agreement_access_logs where user_id = ?",
				manager.userId()));
		assertEquals(outbox, count("select count(*) from outbox_events"));
	}

	/** LS-5 (PLAN1-D1, S7-D6): the detail of a listed agreement, opened by the same caller, writes one DETAIL_READ row. */
	@Test
	void list_thenDetail_oneDetailReadRowForThatAgreementAndUser() throws Exception {
		Chain chain = drafted(today(), today().plusMonths(6));

		rowOf(sale, chain.agreementId());
		assertEquals(List.of(), accessRows(chain.agreementId()));
		detail(sale, chain.agreementId());

		assertEquals(List.of(sale.userId() + " DETAIL_READ"), accessRows(chain.agreementId()));
	}

	// ==================== LS-6: paging and the parameters ====================

	/** LS-6 (S7-D13): a page or size out of its bounds answers 400 with one entry on it and no allowed values. */
	@Test
	void list_pageAndSizeOutOfBounds_validationFailedOneEntry() throws Exception {
		assertValidationFailed(list(manager, "?page=-1"), "page", null);
		assertValidationFailed(list(manager, "?page=10000"), "page", null);
		assertValidationFailed(list(manager, "?size=0"), "size", null);
		assertValidationFailed(list(manager, "?size=51"), "size", null);
		assertEquals(200, list(manager, "?page=9999&size=50").status());
		assertEquals(200, list(manager, "?size=1").status());
	}

	/** LS-6 (S7-D13): a value of the wrong type answers 400 naming its parameter, never 500 or MALFORMED_REQUEST. */
	@Test
	void list_wrongTypes_validationFailedNamingTheParameterNoValue() throws Exception {
		Map<String, String> sent = new LinkedHashMap<>();
		sent.put("?page=x", "page");
		sent.put("?size=x", "size");
		sent.put("?page=99999999999", "page");
		sent.put("?endsWithin30Days=maybe", "endsWithin30Days");
		for (Map.Entry<String, String> query : sent.entrySet()) {
			Result answer = list(manager, query.getKey());

			assertValidationFailed(answer, query.getValue(), null);
			String value = query.getKey().substring(query.getKey().indexOf('=') + 1);
			assertFalse(answer.rawBody().contains(value), query.getKey() + " " + answer.rawBody());
		}
	}

	/** LS-6: an empty page reads as page 0. */
	@Test
	void list_emptyPage_readsPageZero() throws Exception {
		JsonNode page = listOk(manager, "?page=&size=5");

		assertEquals(0, page.path("page").asInt());
		assertEquals(5, page.path("size").asInt());
	}

	/** LS-6 (S7-D1): the count of every match, and creation time newest first then id, page by page. */
	@Test
	void list_paging_totalAndDefaultOrder() throws Exception {
		TestUser lone = survey.user("List Paging Sale", "SALE");
		String leadId = survey.lead(leader, lone, "ASSIGNED");
		Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Version draft = new Version(1, "DRAFT", LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
		String oldest = fixture(leadId, "DRAFTING", base.minusSeconds(20), draft);
		String middle = fixture(leadId, "DRAFTING", base.minusSeconds(10), draft);
		String newest = fixture(leadId, "DRAFTING", base, draft);

		JsonNode first = listOk(lone, "?page=0&size=2");
		JsonNode second = listOk(lone, "?page=1&size=2");

		assertEquals(List.of(newest, middle), ids(first));
		assertEquals(List.of(oldest), ids(second));
		assertEquals(3, first.path("totalElements").asLong());
		assertEquals(3, second.path("totalElements").asLong());
		assertEquals(1, second.path("page").asInt());
		assertEquals(2, second.path("size").asInt());
	}

	/** LS-6: a page past the end answers no item and the count. */
	@Test
	void list_pagePastTheEnd_noItemAndTheCount() throws Exception {
		TestUser lone = survey.user("List Past Sale", "SALE");
		String leadId = survey.lead(leader, lone, "ASSIGNED");
		drafting(leadId);
		drafting(leadId);

		JsonNode page = listOk(lone, "?page=5&size=2");

		assertEquals("[]", page.path("items").toString());
		assertEquals(2, page.path("totalElements").asLong());
		assertEquals(5, page.path("page").asInt());
	}
}
