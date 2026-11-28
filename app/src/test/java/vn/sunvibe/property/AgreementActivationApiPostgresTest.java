package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The activation of a confirmed version and its refusal over HTTP on PostgreSQL (brokerage-agreement US-3 AC-1 to AC-5,
 * US-4 AC-1, AC-2; BR-4, BR-6, BR-11, BR-12, BR-16, BR-17; E05-S6 spec 3.1, 3.2, 3.5; criteria AV-1 to AV-5, RF-1, RF-2,
 * NT-1, OW-1, RT-1, EV-1 without a subscriber). The annotations of {@link AgreementSendApiPostgresTest} (those of its base),
 * so they share one context, where sending to owners is on and the lapse and start jobs run every 60 s: every
 * confirmation here is fresh, far inside its 14 days, and every start is today, in the past or two days ahead, so no job
 * changes a row behind a test (E05-S6 plan E05S6-P2, P3). Needs Docker.
 */
class AgreementActivationApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String OWNER_STATEMENT = "brokerage-agreement-2026-11";
	private static final String NUMBER = "HDMG-2026/014";
	private static final String NAME = "Nguyen Van A";
	private static final String TITLE = "Director";
	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final Object ABSENT = new Object();

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0931";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== fixtures: the chain of E05-S2 to E05-S5 over HTTP (plan E05S6-P3) ====================

	private record Chain(String leadId, TestUser owner, String propertyId, String unitId, String agreementId) {
	}

	private TestUser newOwner() throws Exception {
		TestUser owner = owner();
		owners.add(owner.userId());
		return owner;
	}

	/** A house lead of {@code sale} with a completed intake for a new owner, and the Sale's complete draft on its unit. */
	private Chain drafted(LocalDate start, LocalDate end) throws Exception {
		Passed passed = house(unique("Activation Street"));
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

	/** AV-1's period: from today in Vietnam for six months. */
	private Chain confirmed() throws Exception {
		return confirmed(today(), today().plusMonths(6));
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

	private void openDraft(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		Result answer = api.post(sale, AGREEMENTS + "/" + agreementId + "/draft", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	// ==================== HTTP ====================

	private Result activate(TestUser caller, String agreementId, long dataVersion, int versionNo, String statement,
			Object number, Object signedOn, Object name, Object title) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", dataVersion);
		body.put("versionNo", versionNo);
		body.put("statementVersion", statement);
		body.put("documentNumber", number);
		body.put("signedOn", signedOn);
		body.put("sunvibeSignatoryName", name);
		body.put("sunvibeSignatoryTitle", title);
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation", body);
	}

	/** The current data version, version 1, the current statement and the document of these tests, signed today. */
	private Result activate(TestUser caller, String agreementId) throws Exception {
		return activate(caller, agreementId, 1);
	}

	private Result activate(TestUser caller, String agreementId, int versionNo) throws Exception {
		return activate(caller, agreementId, rowVersion(agreementId), versionNo, ActivationStatement.VERSION,
				"  " + NUMBER + " ", today().toString(), NAME, TITLE);
	}

	private Result refuse(TestUser caller, String agreementId, long dataVersion, int versionNo, String type, Object note)
			throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", dataVersion);
		body.put("versionNo", versionNo);
		body.put("reasonType", type);
		if (note != ABSENT) {
			body.put("note", note);
		}
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/activation-refusal", body);
	}

	private Result refuse(TestUser caller, String agreementId) throws Exception {
		return refuse(caller, agreementId, rowVersion(agreementId), 1, "DOCUMENT_DIFFERS", "  Rent differs.  ");
	}

	private JsonNode detail(TestUser caller, String agreementId) throws Exception {
		Result answer = api.get(caller, AGREEMENTS + "/" + agreementId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	// ==================== reading rows ====================

	private Map<String, Object> versionRow(String agreementId, int versionNo) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ? and version_no = ?",
				agreementId, versionNo);
	}

	private Map<String, Object> agreementRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreements where id = ?", agreementId);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	/** The agreement's history as "event version actor", oldest first. */
	private List<String> events(String agreementId) {
		return jdbc.queryForList("select event_type || ' ' || version_no || ' ' || coalesce(actor_user_id, '-') from"
				+ " brokerage_agreement_events where agreement_id = ? order by occurred_at, id", String.class, agreementId);
	}

	private int notices(TestUser owner, String kind) {
		return count("select count(*) from auth_email_notifications where user_id = ? and kind = ?", owner.userId(), kind);
	}

	private int accessRows(String agreementId) {
		return count("select count(*) from brokerage_agreement_access_logs where agreement_id = ?", agreementId);
	}

	private int outboxRows(String unitId) {
		return count("select count(*) from outbox_events where aggregate_id = ?", unitId);
	}

	/** Everything a refused command must leave as it was. */
	private record Written(Map<String, Object> agreement, List<Map<String, Object>> versions, List<String> events,
			int activatedNotices, int outbox) {
	}

	private Written written(Chain chain) {
		return new Written(agreementRow(chain.agreementId()), jdbc.queryForList("select * from"
				+ " brokerage_agreement_versions where agreement_id = ? order by version_no", chain.agreementId()),
				events(chain.agreementId()), notices(chain.owner(), "AGREEMENT_ACTIVATED"), outboxRows(chain.unitId()));
	}

	private static void assertError(Result answer, int status, String code, String errors) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	private static void assertNotFound(Result answer) {
		assertError(answer, 404, "NOT_FOUND", "[]");
		assertEquals("The resource was not found.", answer.body().path("message").asString(), answer.rawBody());
	}

	private static Instant instant(Object value) {
		return ((Timestamp) value).toInstant();
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	// ==================== AV-1, AV-2: activation ====================

	/** AV-1 (US-3 AC-1, AC-2, AC-5; S6-D1, S6-D3, S6-D6, S6-D14, S6-D15): EFFECTIVE, recorded, and then allowed. */
	@Test
	void activate_independentManager_effectiveRecordedAllowed() throws Exception {
		Chain chain = confirmed();
		long before = rowVersion(chain.agreementId());
		Map<String, Object> lead = jdbc.queryForMap("select status, version from owner_leads where id = ?", chain.leadId());
		int activities = count("select count(*) from owner_lead_activities where lead_id = ?", chain.leadId());
		Instant from = now();

		Result answer = activate(manager, chain.agreementId());

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> version = versionRow(chain.agreementId(), 1);
		assertEquals("EFFECTIVE", version.get("status"));
		assertEquals("ACTIVATED", version.get("activation_decision"));
		assertEquals(manager.userId(), version.get("activation_decided_by"));
		Instant decidedAt = instant(version.get("activation_decided_at"));
		assertFalse(decidedAt.isBefore(from.minusSeconds(5)), decidedAt.toString());
		assertEquals(ActivationStatement.VERSION, version.get("activation_statement_version"));
		assertEquals(NUMBER, version.get("signed_document_number"));
		assertEquals(Date.valueOf(today()), version.get("signed_document_date"));
		assertEquals(NAME, version.get("sunvibe_signatory_name"));
		assertEquals(TITLE, version.get("sunvibe_signatory_title"));
		assertNull(version.get("activation_refusal_type"));
		Map<String, Object> agreement = agreementRow(chain.agreementId());
		assertEquals("ACTIVE", agreement.get("status"));
		assertEquals(before + 1, agreement.get("version"));
		List<String> events = events(chain.agreementId());
		assertEquals("ACTIVATED 1 " + manager.userId(), events.getLast());
		assertEquals(1, events.stream().filter(event -> event.startsWith("ACTIVATED ")).count());
		assertEquals(0, accessRows(chain.agreementId()));
		assertEquals(lead, jdbc.queryForMap("select status, version from owner_leads where id = ?", chain.leadId()));
		assertEquals(activities, count("select count(*) from owner_lead_activities where lead_id = ?", chain.leadId()));

		JsonNode advertising = detail(manager, chain.agreementId()).path("advertising");
		assertTrue(advertising.path("allowed").asBoolean(), advertising.toString());
		assertEquals("[]", advertising.path("reasonGroups").toString());
		assertEquals(today().plusMonths(6).toString(), advertising.path("endDate").asString());
		assertEquals(8_000_000L, advertising.path("termsInEffect").path("monthlyRent").path("amount").asLong());
		assertTrue(advertising.path("photoConsent").path("interior").asBoolean(), advertising.toString());
		assertFalse(advertising.path("photoConsent").path("exterior").asBoolean(), advertising.toString());
		assertTrue(advertising.path("pendingVersion").isNull(), advertising.toString());
	}

	/** AV-2 (US-3 AC-5, BR-6; S6-D6, S6-D7): a start ahead activates ACTIVATED and reads NOT_STARTED with its start. */
	@Test
	void activate_startTomorrow_notStartedWithPending() throws Exception {
		LocalDate start = today().plusDays(2);
		Chain chain = confirmed(start, start.plusMonths(6));

		assertEquals(204, activate(manager, chain.agreementId()).status());

		assertEquals("ACTIVATED", versionRow(chain.agreementId(), 1).get("status"));
		assertEquals("ACTIVE", agreementRow(chain.agreementId()).get("status"));
		JsonNode advertising = detail(manager, chain.agreementId()).path("advertising");
		assertFalse(advertising.path("allowed").asBoolean(), advertising.toString());
		assertEquals("[\"NOT_STARTED\"]", advertising.path("reasonGroups").toString());
		assertTrue(advertising.path("termsInEffect").isNull(), advertising.toString());
		JsonNode pending = advertising.path("pendingVersion");
		assertEquals(start.toString(), pending.path("startDate").asString());
		assertEquals(start.atStartOfDay(VIETNAM).toInstant(), Instant.parse(pending.path("startsAt").asString()));
		assertEquals(8_000_000L, pending.path("terms").path("monthlyRent").path("amount").asLong());
	}

	// ==================== AV-3, roles: who may decide ====================

	/**
	 * AV-3 (BR-11, BR-16; S6-D2, S6-D16): the lead's assignee holding SALE_LEADER, an editor of v1, the creator of v1 who
	 * holds a manager role, the sender of v1 who holds one, and a manager after the lead was reassigned to them are not
	 * independent; nothing is written.
	 */
	@Test
	void activate_notIndependent_refusedNothingWritten() throws Exception {
		TestUser creator = survey.user("Activation Creator", "SALE", "SALES_MANAGER");
		TestUser sender = survey.user("Activation Sender", "SALE", "SALE_LEADER");
		Chain chain = drafted(today(), today().plusMonths(6));
		// the leader edits v1 as the assignee, the sender sends it as the assignee, then the Sale holds the lead again
		fixture.checklists().reassign(leader, chain.leadId(), leader);
		saveComplete(leader, chain.agreementId(), today(), today().plusMonths(6));
		fixture.checklists().reassign(leader, chain.leadId(), sender);
		send(sender, chain.agreementId());
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		confirm(chain.owner(), chain.agreementId(), 1);
		Written before = written(chain);

		for (TestUser author : List.of(leader, sender)) {
			assertError(activate(author, chain.agreementId()), 403, "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "[]");
		}
		fixture.checklists().reassign(leader, chain.leadId(), leader);
		assertError(activate(leader, chain.agreementId()), 403, "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "[]");
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		assertEquals(before, written(chain));

		// the creator of version 1, who also holds SALES_MANAGER, and the manager once the lead is theirs
		Passed passed = house(unique("Creator Street"));
		TestUser owner = newOwner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		fixture.checklists().reassign(leader, passed.leadId(), creator);
		Chain byCreator = new Chain(passed.leadId(), owner, propertyId, unit, create(creator, passed.leadId(), unit));
		saveComplete(creator, byCreator.agreementId(), today(), today().plusMonths(6));
		send(creator, byCreator.agreementId());
		confirm(owner, byCreator.agreementId(), 1);
		fixture.checklists().reassign(leader, passed.leadId(), sale);
		Written beforeCreator = written(byCreator);
		assertError(activate(creator, byCreator.agreementId()), 403, "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "[]");
		fixture.checklists().reassign(leader, passed.leadId(), leader);
		assertError(activate(leader, byCreator.agreementId()), 403, "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "[]");
		assertEquals(beforeCreator, written(byCreator));
	}

	/** S6-D1 steps 2 to 4: a SALE, a CUSTOMER and the owner are refused by role; an unknown lead or agreement is 404. */
	@Test
	void activate_roleOrVisibility_forbiddenOrNotFound() throws Exception {
		Chain chain = confirmed();
		TestUser customer = survey.user("Activation Customer", "CUSTOMER");
		Written before = written(chain);

		for (TestUser caller : List.of(sale, customer, chain.owner())) {
			assertError(activate(caller, chain.agreementId()), 403, "FORBIDDEN", "[]");
		}
		Result anonymous = api.send(java.net.http.HttpRequest.newBuilder(api.uri(AGREEMENTS + "/" + chain.agreementId()
						+ "/activation")).header("Content-Type", "application/json")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build());
		assertEquals(401, anonymous.status(), anonymous.rawBody());
		assertEquals(before, written(chain));

		// a basis lead the leader cannot see (acquisition-owner-lead-queue BR-1: a leader sees every existing lead, so the
		// agreement rests on a lead id no viewer finds) answers as an unknown agreement
		String hidden = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "AWAITING_CONFIRMATION",
				"OWNER_CONFIRMED");
		created.add(hidden);
		assertNotFound(activate(leader, hidden, 0L, 1, ActivationStatement.VERSION, NUMBER, today().toString(), NAME, TITLE));
		assertNotFound(activate(leader, "01JUNKNOWN0000000000000000", 0L, 1, ActivationStatement.VERSION, NUMBER,
				today().toString(), NAME, TITLE));
		assertEquals(List.of(), events(hidden));
	}

	// ==================== AV-4, AV-5: refusals of the state, the data and the document ====================

	/** AV-4 (US-3 AC-4; BR-2 to BR-4, BR-11; E-2, E-11): each stop or state answers 409 and writes nothing. */
	@Test
	void activate_stopsStatesAndVersions_conflictNothingWritten() throws Exception {
		Chain held = confirmed();
		Written heldBefore = written(held);
		hold(held.leadId());
		assertError(activate(manager, held.agreementId()), 409, "AGREEMENT_NOT_ELIGIBLE",
				"[{\"field\":\"reasonGroups.BASIS\",\"message\":\"This group stops the agreement.\"}]");
		assertEquals(heldBefore, written(held));

		Chain unitStop = confirmed();
		suspendLead(unitStop.leadId(), unitStop.unitId(), "Room only");
		assertEquals("AGREEMENT_NOT_ELIGIBLE", activate(manager, unitStop.agreementId()).body().path("code").asString());
		assertTrue(activate(manager, unitStop.agreementId()).body().path("errors").toString()
				.contains("reasonGroups.UNIT"));

		Chain coOwner = confirmed();
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
				+ " valid_to, created_at) values (?, ?, ?, 'CO_OWNER', 'UNVERIFIED', ?, null, ?)", ids.generateId(),
				coOwner.propertyId(), ids.generateId(), LocalDate.of(2026, 1, 1), Timestamp.from(now()));
		assertError(activate(manager, coOwner.agreementId()), 409, "AGREEMENT_NOT_ELIGIBLE",
				"[{\"field\":\"reasonGroups.OWNER\",\"message\":\"This group stops the agreement.\"}]");

		Chain staff = confirmed();
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'",
				staff.owner().userId());
		Result staffAnswer = activate(manager, staff.agreementId());
		assertEquals("AGREEMENT_NOT_ELIGIBLE", staffAnswer.body().path("code").asString(), staffAnswer.rawBody());
		assertTrue(staffAnswer.body().path("errors").toString().contains("reasonGroups.OWNER"), staffAnswer.rawBody());
		jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
				staff.owner().userId());

		Chain ended = confirmed(today().minusDays(60), today().minusDays(2));
		assertError(activate(manager, ended.agreementId()), 409, "AGREEMENT_VERSION_PERIOD_ENDED", "[]");
		Chain current = confirmed();
		assertError(activate(manager, current.agreementId(), rowVersion(current.agreementId()), 1, "old", NUMBER,
				today().toString(), NAME, TITLE), 409, "AGREEMENT_STATEMENT_OUTDATED", "[]");
		assertError(activate(manager, current.agreementId(), rowVersion(current.agreementId()) + 1, 1,
				ActivationStatement.VERSION, NUMBER, today().toString(), NAME, TITLE), 409, "CONCURRENT_MODIFICATION",
				"[{\"field\":\"expectedDataVersion\",\"message\":\"This value is out of date.\"}]");
		assertError(activate(manager, current.agreementId(), 2), 409, "AGREEMENT_NOT_AWAITING_ACTIVATION", "[]");
		Written currentBefore = written(current);
		assertEquals(currentBefore, written(current));

		Chain drafting = drafted(today(), today().plusMonths(6));
		assertError(activate(manager, drafting.agreementId()), 409, "AGREEMENT_NOT_AWAITING_ACTIVATION", "[]");
		Chain awaitingOwner = drafted(today(), today().plusMonths(6));
		send(sale, awaitingOwner.agreementId());
		assertError(activate(manager, awaitingOwner.agreementId()), 409, "AGREEMENT_NOT_AWAITING_ACTIVATION", "[]");
		assertEquals(204, activate(manager, current.agreementId()).status());
		assertError(activate(admin, current.agreementId()), 409, "AGREEMENT_NOT_AWAITING_ACTIVATION", "[]");
		assertEquals(1, events(current.agreementId()).stream().filter(event -> event.startsWith("ACTIVATED ")).count());
	}

	/**
	 * Brief section 8 example 12 (D9-18): a unit whose only reason is AVAILABILITY_DIFFERS (the house linked onto an
	 * OCCUPIED unit while the owner said VACANT) is activated, and the answer stays allowed (RT-1's last clause).
	 */
	@Test
	void activate_availabilityDiffersOnly_activated() throws Exception {
		Passed passed = house(unique("Activation Link Street"));
		TestUser owner = newOwner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class,
				passed.leadId());
		String propertyId = property(owner.userId(), WARD, "Hải Châu", address, "HOUSE", address);
		ownership(propertyId, owner.userId());
		String unit = unit(propertyId, "U1", "OCCUPIED");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), propertyId, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		assertEquals("[\"AVAILABILITY_DIFFERS\"]",
				blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("reasons").toString());
		Chain chain = new Chain(passed.leadId(), owner, propertyId, unit, create(sale, passed.leadId(), unit));
		saveComplete(sale, chain.agreementId(), today(), today().plusMonths(6));
		send(sale, chain.agreementId());
		confirm(owner, chain.agreementId(), 1);

		assertEquals(204, activate(manager, chain.agreementId()).status());

		Map<String, Object> version = versionRow(chain.agreementId(), 1);
		assertEquals("EFFECTIVE", version.get("status"));
		assertEquals("ACTIVATED", version.get("activation_decision"));
		assertTrue(advertising(chain).path("allowed").asBoolean());
	}

	/** AV-5 (S6-D1, S6-D3; security.md): each bad document value answers 400 on its field and quotes no value. */
	@Test
	void activate_badDocument_validationFailedNoValueQuoted() throws Exception {
		Chain chain = confirmed();
		Written before = written(chain);
		long dataVersion = rowVersion(chain.agreementId());
		String today = today().toString();
		List<String> bad = List.of("", "   ", "A\u0000B", "A‮B");
		for (String value : bad) {
			assertBadField(activate(manager, chain.agreementId(), dataVersion, 1, ActivationStatement.VERSION, value, today,
					NAME, TITLE), "documentNumber", value);
			assertBadField(activate(manager, chain.agreementId(), dataVersion, 1, ActivationStatement.VERSION, NUMBER,
					today, value, TITLE), "sunvibeSignatoryName", value);
			assertBadField(activate(manager, chain.agreementId(), dataVersion, 1, ActivationStatement.VERSION, NUMBER,
					today, NAME, value), "sunvibeSignatoryTitle", value);
		}
		assertBadField(activate(manager, chain.agreementId(), dataVersion, 1, ActivationStatement.VERSION,
				"N".repeat(51), today, NAME, TITLE), "documentNumber", "N".repeat(51));
		assertBadField(activate(manager, chain.agreementId(), dataVersion, 1, ActivationStatement.VERSION, NUMBER, today,
				"Q".repeat(101), TITLE), "sunvibeSignatoryName", "Q".repeat(101));
		assertBadField(activate(manager, chain.agreementId(), dataVersion, 1, ActivationStatement.VERSION, NUMBER, today,
				NAME, "Q".repeat(101)), "sunvibeSignatoryTitle", "Q".repeat(101));
		for (String date : List.of(today().plusDays(2).toString(), "1999-12-31")) {
			assertBadField(activate(manager, chain.agreementId(), dataVersion, 1, ActivationStatement.VERSION, NUMBER, date,
					NAME, TITLE), "signedOn", date);
		}
		assertEquals(before, written(chain));
	}

	private static void assertBadField(Result answer, String field, String value) {
		assertEquals(400, answer.status(), answer.rawBody());
		assertEquals("VALIDATION_FAILED", answer.body().path("code").asString(), answer.rawBody());
		assertEquals(field, answer.body().path("errors").path(0).path("field").asString(), answer.rawBody());
		if (value.strip().length() > 1) {
			assertFalse(answer.rawBody().contains(value.strip()), answer.rawBody());
		}
	}

	// ==================== RF-1, RF-2: the refusal ====================

	/** RF-1 (US-3 AC-3, E-7; S6-D4, S6-D15): refused with the trimmed note, drafting again, no event, no notice. */
	@Test
	void refuse_documentDiffers_refusedDraftingNoteTrimmed() throws Exception {
		Chain chain = confirmed();
		long before = rowVersion(chain.agreementId());

		Result answer = refuse(manager, chain.agreementId());

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> version = versionRow(chain.agreementId(), 1);
		assertEquals("ACTIVATION_REFUSED", version.get("status"));
		assertEquals("REFUSED", version.get("activation_decision"));
		assertEquals(manager.userId(), version.get("activation_decided_by"));
		assertEquals("DOCUMENT_DIFFERS", version.get("activation_refusal_type"));
		assertEquals("Rent differs.", version.get("activation_refusal_note"));
		assertNull(version.get("signed_document_number"));
		assertEquals("DRAFTING", agreementRow(chain.agreementId()).get("status"));
		assertEquals(before + 1, agreementRow(chain.agreementId()).get("version"));
		assertEquals("ACTIVATION_REFUSED 1 " + manager.userId(), events(chain.agreementId()).getLast());
		assertEquals(0, notices(chain.owner(), "AGREEMENT_ACTIVATED"));
		assertEquals(0, outboxRows(chain.unitId()));
	}

	/** S6-D4 with S6-D1 steps 2 to 4: the refusal's own method rule, 401, and one 404 body. */
	@Test
	void refuse_roleOrVisibility_forbiddenOrNotFound() throws Exception {
		Chain chain = confirmed();
		TestUser customer = survey.user("Refusal Customer", "CUSTOMER");
		Written before = written(chain);

		for (TestUser caller : List.of(sale, customer, chain.owner())) {
			assertError(refuse(caller, chain.agreementId()), 403, "FORBIDDEN", "[]");
		}
		Result anonymous = api.send(java.net.http.HttpRequest.newBuilder(api.uri(AGREEMENTS + "/" + chain.agreementId()
						+ "/activation-refusal")).header("Content-Type", "application/json")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build());
		assertEquals(401, anonymous.status(), anonymous.rawBody());
		String hidden = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "AWAITING_CONFIRMATION",
				"OWNER_CONFIRMED");
		created.add(hidden);
		assertNotFound(refuse(leader, hidden, 0L, 1, "DOCUMENT_DIFFERS", null));
		assertNotFound(refuse(leader, "01JUNKNOWN0000000000000000", 0L, 1, "DOCUMENT_DIFFERS", null));
		assertEquals(before, written(chain));
	}

	/**
	 * S6-D4 with S6-D1 steps 5 to 7: the assignee holding SALE_LEADER, an editor and the sender of v1 are not independent;
	 * a drafting agreement, another version and a stale data version are refused; nothing is written. Then the manager's
	 * refusals with the note absent, null and blank store none, on versions 1, 2 and 3 in turn.
	 */
	@Test
	void refuse_notIndependentOrStale_refusedNothingWritten() throws Exception {
		TestUser sender = survey.user("Refusal Sender", "SALE", "SALE_LEADER");
		Chain chain = drafted(today(), today().plusMonths(6));
		fixture.checklists().reassign(leader, chain.leadId(), leader);
		saveComplete(leader, chain.agreementId(), today(), today().plusMonths(6));
		fixture.checklists().reassign(leader, chain.leadId(), sender);
		send(sender, chain.agreementId());
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		confirm(chain.owner(), chain.agreementId(), 1);
		Written before = written(chain);

		assertError(refuse(leader, chain.agreementId()), 403, "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "[]");
		assertError(refuse(sender, chain.agreementId()), 403, "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "[]");
		fixture.checklists().reassign(leader, chain.leadId(), leader);
		assertError(refuse(leader, chain.agreementId()), 403, "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "[]");
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		assertError(refuse(manager, chain.agreementId(), rowVersion(chain.agreementId()), 2, "DOCUMENT_DIFFERS", null),
				409, "AGREEMENT_NOT_AWAITING_ACTIVATION", "[]");
		assertError(refuse(manager, chain.agreementId(), rowVersion(chain.agreementId()) + 1, 1, "DOCUMENT_DIFFERS", null),
				409, "CONCURRENT_MODIFICATION",
				"[{\"field\":\"expectedDataVersion\",\"message\":\"This value is out of date.\"}]");
		assertEquals(before, written(chain));

		Chain drafting = drafted(today(), today().plusMonths(6));
		Written draftingBefore = written(drafting);
		assertError(refuse(manager, drafting.agreementId()), 409, "AGREEMENT_NOT_AWAITING_ACTIVATION", "[]");
		assertEquals(draftingBefore, written(drafting));

		List<Object> notes = new ArrayList<>(java.util.Arrays.asList(ABSENT, null, "   "));
		for (int versionNo = 1; versionNo <= 3; versionNo++) {
			if (versionNo > 1) {
				openDraft(chain.agreementId());
				saveComplete(sale, chain.agreementId(), today(), today().plusMonths(6));
				send(sale, chain.agreementId());
				confirm(chain.owner(), chain.agreementId(), versionNo);
			}
			Result answer = refuse(manager, chain.agreementId(), rowVersion(chain.agreementId()), versionNo,
					"SIGNER_NOT_OWNER", notes.get(versionNo - 1));
			assertEquals(204, answer.status(), answer.rawBody());
			Map<String, Object> version = versionRow(chain.agreementId(), versionNo);
			assertEquals("REFUSED", version.get("activation_decision"));
			assertNull(version.get("activation_refusal_note"), "version " + versionNo);
		}
	}

	/** RF-1: another type answers with the allowed values; a note too long or with a hidden character answers on note. */
	@Test
	void refuse_badTypeOrNote_validationFailed() throws Exception {
		Chain chain = confirmed();
		Written before = written(chain);
		long dataVersion = rowVersion(chain.agreementId());

		Result other = refuse(manager, chain.agreementId(), dataVersion, 1, "OTHER", null);
		assertError(other, 400, "VALIDATION_FAILED", "[{\"field\":\"reasonType\",\"message\":\"This value is not accepted.\","
				+ "\"allowedValues\":[\"DOCUMENT_DIFFERS\",\"SIGNER_NOT_OWNER\"]}]");
		assertBadField(refuse(manager, chain.agreementId(), dataVersion, 1, "DOCUMENT_DIFFERS", "n".repeat(501)), "note",
				"n".repeat(501));
		assertBadField(refuse(manager, chain.agreementId(), dataVersion, 1, "DOCUMENT_DIFFERS", "Rent‮differs"),
				"note", "Rent‮differs");
		assertEquals(before, written(chain));
	}

	/** RF-2 (US-3 AC-3, PLAN1-D8, S5-D10): after a refusal the Sale opens v2, edits, sends; the owner confirms; M1 activates. */
	@Test
	void refuse_thenNewDraft_version2EditSendConfirmActivate() throws Exception {
		Chain chain = confirmed();
		assertEquals(204, refuse(manager, chain.agreementId()).status());

		openDraft(chain.agreementId());
		assertEquals("DRAFT", versionRow(chain.agreementId(), 2).get("status"));
		assertTrue(detail(manager, chain.agreementId()).path("activationStatement").isNull());
		saveComplete(sale, chain.agreementId(), today(), today().plusMonths(6));
		send(sale, chain.agreementId());
		assertTrue(detail(manager, chain.agreementId()).path("activationStatement").isNull());
		confirm(chain.owner(), chain.agreementId(), 2);

		JsonNode awaiting = detail(manager, chain.agreementId());
		assertEquals(ActivationStatement.VERSION, awaiting.path("activationStatement").path("version").asString());
		assertEquals(ActivationStatement.text(2), awaiting.path("activationStatement").path("text").asString());
		assertTrue(awaiting.path("viewerMayDecideActivation").asBoolean());
		assertEquals(204, activate(manager, chain.agreementId(), 2).status());
		assertEquals("EFFECTIVE", versionRow(chain.agreementId(), 2).get("status"));
		assertEquals("ACTIVE", agreementRow(chain.agreementId()).get("status"));
	}

	// ==================== NT-1, OW-1, RT-1, EV-1 ====================

	/** NT-1 (US-3 AC-5; BR-17; S6-D13): one neutral notice for the owner; none without a verified address; none on refusal. */
	@Test
	void activate_ownerNotice_oneNeutralRowNoneWithoutAddress() throws Exception {
		Chain chain = confirmed();
		assertEquals(204, activate(manager, chain.agreementId()).status());
		assertEquals(1, notices(chain.owner(), "AGREEMENT_ACTIVATED"));

		Chain withoutAddress = confirmed();
		jdbc.update("update users set email_verified_at = null where id = ?", withoutAddress.owner().userId());
		assertEquals(204, activate(manager, withoutAddress.agreementId()).status());
		assertEquals(0, notices(withoutAddress.owner(), "AGREEMENT_ACTIVATED"));
		assertEquals("ACTIVE", agreementRow(withoutAddress.agreementId()).get("status"));

		Chain refused = confirmed();
		assertEquals(204, refuse(manager, refused.agreementId()).status());
		assertEquals(0, notices(refused.owner(), "AGREEMENT_ACTIVATED"));
	}

	/** OW-1 (US-2 AC-1; S6-D19, S6-D21): the owner reads the effective state and activateBy, never the record. */
	@Test
	void ownerRead_afterActivationOrRefusal_activationNullActivateBySet() throws Exception {
		Chain activated = confirmed();
		Instant confirmedAt = instant(versionRow(activated.agreementId(), 1).get("owner_answered_at"));
		assertEquals(204, activate(manager, activated.agreementId()).status());
		Chain refused = confirmed();
		assertEquals(204, refuse(manager, refused.agreementId()).status());

		for (Chain chain : List.of(activated, refused)) {
			Result answer = api.get(chain.owner(), ME);
			assertEquals(200, answer.status(), answer.rawBody());
			JsonNode item = answer.body().path("items").path(0);
			JsonNode version = item.path("versions").path(0);
			assertTrue(version.path("activation").isNull(), answer.rawBody());
			assertFalse(version.path("activateBy").isNull(), answer.rawBody());
			for (String secret : List.of(NUMBER, NAME, TITLE, "Rent differs.")) {
				assertFalse(answer.rawBody().contains(secret), secret);
			}
		}
		JsonNode activatedItem = api.get(activated.owner(), ME).body().path("items").path(0);
		assertEquals("ACTIVE", activatedItem.path("status").asString());
		assertEquals("EFFECTIVE", activatedItem.path("versions").path(0).path("status").asString());
		assertEquals(confirmedAt.plus(java.time.Duration.ofDays(14)),
				Instant.parse(activatedItem.path("versions").path(0).path("activateBy").asString()));
		assertEquals("ACTIVATION_REFUSED", api.get(refused.owner(), ME).body().path("items").path(0).path("versions")
				.path(0).path("status").asString());
	}

	/**
	 * RT-1 (US-4 AC-1, AC-2; BR-12, BR-13; E-4; S6-D7, S6-D8): after an activation each change elsewhere turns the
	 * answer off at the next read with its group and back when undone; AVAILABILITY_DIFFERS changes nothing; nothing of
	 * the other lead is named; each read writes its access row.
	 */
	@Test
	void advertising_changesElsewhere_offAtNextReadAndBack() throws Exception {
		// two houses on one street (as the eligibility test's pair): the first creates the property, the second links
		// onto its one unit; the agreement rests on the first
		String street = unique("Two Leads Activation");
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
		Chain chain = new Chain(first.leadId(), owner, propertyId, unit, create(sale, first.leadId(), unit));
		saveComplete(sale, chain.agreementId(), today(), today().plusMonths(6));
		send(sale, chain.agreementId());
		confirm(owner, chain.agreementId(), 1);
		assertEquals(204, activate(manager, chain.agreementId()).status());
		assertTrue(advertising(chain).path("allowed").asBoolean());
		int reads = accessRows(chain.agreementId());

		hold(chain.leadId());
		assertEquals("[\"BASIS\"]", advertising(chain).path("reasonGroups").toString());
		jdbc.update("update owner_leads set status = 'QUALIFIED', version = version + 1 where id = ?", chain.leadId());
		assertTrue(advertising(chain).path("allowed").asBoolean());

		suspendLead(chain.leadId(), chain.unitId(), "Room only");
		assertEquals("[\"UNIT\"]", advertising(chain).path("reasonGroups").toString());
		release(chain.leadId());
		assertTrue(advertising(chain).path("allowed").asBoolean());

		// the owner given a staff role (E-11, example 10): OWNER, beside what the intake's own readiness then says
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'",
				chain.owner().userId());
		JsonNode staff = advertising(chain);
		assertFalse(staff.path("allowed").asBoolean(), staff.toString());
		assertTrue(staff.path("reasonGroups").toString().contains("OWNER"), staff.toString());
		jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
				chain.owner().userId());
		assertTrue(advertising(chain).path("allowed").asBoolean());

		String declared = jdbc.queryForObject("select owner_id from properties where id = ?", String.class,
				chain.propertyId());
		jdbc.update("update properties set owner_id = ? where id = ?", ids.generateId(), chain.propertyId());
		assertTrue(advertising(chain).path("reasonGroups").toString().contains("OWNER"));
		jdbc.update("update properties set owner_id = ? where id = ?", declared, chain.propertyId());
		assertTrue(advertising(chain).path("allowed").asBoolean());

		// a whole-lead suspension of another lead on the same property stops it as PROPERTY, naming nothing of that lead
		suspendLead(second.leadId(), null, "Data request");
		JsonNode property = advertising(chain);
		assertEquals("[\"PROPERTY\"]", property.path("reasonGroups").toString());
		assertFalse(property.toString().contains(second.leadId()), property.toString());
		release(second.leadId());
		assertTrue(advertising(chain).path("allowed").asBoolean());
		assertEquals(reads + 10, accessRows(chain.agreementId()), "one access row per read");
	}

	private JsonNode advertising(Chain chain) throws Exception {
		return detail(manager, chain.agreementId()).path("advertising");
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	private void release(String leadId) throws Exception {
		String suspensionId = jdbc.queryForObject("select id from inventory_suspensions where lead_id = ?", String.class,
				leadId);
		Result answer = release(manager, leadId, suspensionId, "The request was answered", versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	/**
	 * EV-1 (ADR-014 decision 3), listing-publish E06-S7 spec E06S7-D13, S-6, PB-16: with the production list, which
	 * subscribes listing-unit-advertising to E05's event, every row an activation writes is that consumer's, one per E05
	 * event the activation appends: now and at the end of the end date (its start is today, not ahead: S6-D12).
	 */
	@Test
	void activate_productionList_oneListingRowPerEvent() throws Exception {
		Chain chain = confirmed();

		assertEquals(204, activate(manager, chain.agreementId()).status());

		Map<String, Object> version = versionRow(chain.agreementId(), 1);
		assertEquals("EFFECTIVE", version.get("status"));
		assertEquals("ACTIVATED", version.get("activation_decision"));
		assertEquals(List.of("listing-unit-advertising " + UnitAdvertisingChangedEvent.TYPE,
						"listing-unit-advertising " + UnitAdvertisingChangedEvent.TYPE),
				jdbc.queryForList("select consumer || ' ' || event_type from outbox_events where aggregate_id = ?"
						+ " order by next_attempt_at, id", String.class, chain.unitId()));
		assertEquals(2, count("select count(distinct event_id) from outbox_events where aggregate_id = ?",
				chain.unitId()));
	}
}
