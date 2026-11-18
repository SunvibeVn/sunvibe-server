package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Sending an agreement's draft to its owner over HTTP on PostgreSQL (brokerage-agreement US-1 AC-5; BR-5, BR-7, BR-8,
 * BR-10, BR-16 to BR-18; E05-S4 spec 3.2 to 3.4, SD-1 to SD-5, SD-6 first sentence, SD-8, SD-9, SD-14): the two
 * transitions, the history row and the one owner notice, the step order with its exact refusals, and nothing written on
 * a refusal. The annotations of {@link AgreementDraftApiPostgresTest} (those of its base), so both share one Spring
 * context, where sending to owners is on (E05-S4 plan E05S4-P6). Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class AgreementSendApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String WARD_NAME = "Hải Châu";
	private static final String GROUP_MESSAGE = "This group stops the agreement.";
	private static final String PART_MESSAGE = "This part is missing.";
	private static final List<String> ELEVEN = List.of("photoConsent", "terms.monthlyRent", "terms.deposit",
			"terms.minimumTermMonths", "terms.paymentPeriodMonths", "terms.mandatoryFees", "feePolicy.formula",
			"feePolicy.dueDays", "feePolicy.renewal", "feePolicy.earlyLeaseEnd", "feePolicy.protectionDays");

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0942";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== HTTP ====================

	private Result send(TestUser caller, String agreementId, Object expectedDataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", expectedDataVersion);
		return sendBody(caller, agreementId, body);
	}

	private Result sendBody(TestUser caller, String agreementId, Map<String, Object> body) throws Exception {
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/draft/submission", body);
	}

	private Result put(TestUser caller, String agreementId, Map<String, Object> body) throws Exception {
		return api.put(caller, AGREEMENTS + "/" + agreementId + "/draft", body);
	}

	/** The complete draft with one change made to it. */
	private static Map<String, Object> completeWith(long dataVersion, Consumer<Map<String, Object>> change) {
		Map<String, Object> body = AgreementFeePolicyApiPostgresTest.complete(dataVersion);
		change.accept(body);
		return body;
	}

	@SuppressWarnings("unchecked") // the example body's parts are maps of the test classes
	private static Map<String, Object> part(Map<String, Object> body, String name) {
		return (Map<String, Object>) body.get(name);
	}

	// ==================== fixtures ====================

	private record Done(String leadId, TestUser owner, String propertyId, String unitId, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's draft on its unit, not edited yet. */
	private Done drafted() throws Exception {
		Passed passed = house(unique("Send Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String unit = unitOf(passed.leadId());
		return new Done(passed.leadId(), owner, propertyId, unit, create(sale, passed.leadId(), unit));
	}

	/** {@link #drafted()} with the complete draft saved: the agreement's row version is 1. */
	private Done done() throws Exception {
		Done done = drafted();
		saveComplete(sale, done.agreementId());
		return done;
	}

	private void saveComplete(TestUser caller, String agreementId) throws Exception {
		Result answer = put(caller, agreementId, AgreementFeePolicyApiPostgresTest.complete(rowVersion(agreementId)));
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private String create(TestUser caller, String leadId, String unitId) throws Exception {
		Result answer = api.post(caller, AGREEMENTS, createBody(leadId, unitId));
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		return id;
	}

	private static Map<String, Object> createBody(String leadId, String unitId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		return body;
	}

	private String unitOf(String leadId) throws Exception {
		return blockOk(sale, leadId).path("readiness").path("units").path(0).path("unitId").asString();
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	// ==================== reading rows ====================

	private Map<String, Object> versionRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ?", agreementId);
	}

	private Map<String, Object> agreementRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreements where id = ?", agreementId);
	}

	private List<String> feeRows(String agreementId) {
		return jdbc.queryForList("select f.fee_no || '|' || f.name || '|' || coalesce(f.amount::text, '-')"
				+ " from brokerage_agreement_version_fees f join brokerage_agreement_versions v on v.id = f.version_id"
				+ " where v.agreement_id = ? order by f.fee_no", String.class, agreementId);
	}

	private List<String> tierRows(String agreementId) {
		return jdbc.queryForList("select t.renewal || '|' || t.tier_no || '|' || t.from_months || '|' || t.percent"
				+ " from brokerage_agreement_version_fee_tiers t join brokerage_agreement_versions v on v.id = t.version_id"
				+ " where v.agreement_id = ? order by t.renewal, t.tier_no", String.class, agreementId);
	}

	/** The agreement's history as "event version actor", oldest first. */
	private List<String> events(String agreementId) {
		return jdbc.queryForList("select event_type || ' ' || version_no || ' ' || actor_user_id from"
				+ " brokerage_agreement_events where agreement_id = ? order by occurred_at, id", String.class, agreementId);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private int notices(TestUser owner) {
		return jdbc.queryForObject("select count(*) from auth_email_notifications where user_id = ?"
				+ " and kind = 'AGREEMENT_PENDING'", Integer.class, owner.userId());
	}

	private int accessRows(String agreementId) {
		return AgreementFixture.rows(jdbc, "brokerage_agreement_access_logs", agreementId);
	}

	/** Everything a refused send must leave as it was, the owner's notices included. */
	private record Written(Map<String, Object> agreement, Map<String, Object> version, List<String> fees,
			List<String> tiers, List<String> events, int notices) {
	}

	private Written written(Done done) {
		return new Written(agreementRow(done.agreementId()), versionRow(done.agreementId()), feeRows(done.agreementId()),
				tierRows(done.agreementId()), events(done.agreementId()), notices(done.owner()));
	}

	private static void assertError(Result answer, int status, String code, String errors) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	private static String partErrors(String... parts) {
		return "[" + String.join(",", Arrays.stream(parts)
				.map(part -> "{\"field\":\"" + part + "\",\"message\":\"" + PART_MESSAGE + "\"}").toList()) + "]";
	}

	private static void assertNotFound(Result answer) {
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("The resource was not found.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
	}

	/** A 409 AGREEMENT_NOT_ELIGIBLE with exactly these groups, and nothing written. */
	private void assertNotEligible(Done done, String... groups) throws Exception {
		Written before = written(done);
		String errors = Arrays.stream(groups)
				.map(group -> "{\"field\":\"reasonGroups." + group + "\",\"message\":\"" + GROUP_MESSAGE + "\"}")
				.reduce((left, right) -> left + "," + right).orElse("");

		Result answer = send(sale, done.agreementId(), rowVersion(done.agreementId()));

		assertError(answer, 409, "AGREEMENT_NOT_ELIGIBLE", "[" + errors + "]");
		assertEquals(before, written(done), String.join(",", groups));
	}

	// ==================== SD-1: the send ====================

	@Test
	void send_completeDraft_sentWithOneEventAndOneNotice() throws Exception {
		Done done = done();
		Map<String, Object> agreementBefore = agreementRow(done.agreementId());
		Map<String, Object> versionBefore = versionRow(done.agreementId());
		List<String> feesBefore = feeRows(done.agreementId());
		List<String> tiersBefore = tierRows(done.agreementId());
		Instant start = Instant.now().truncatedTo(ChronoUnit.MICROS);

		Result answer = send(sale, done.agreementId(), 1);

		Instant end = Instant.now();
		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("", answer.response().body());
		Map<String, Object> version = versionRow(done.agreementId());
		assertEquals("AWAITING_OWNER", version.get("status"));
		Instant sentAt = ((Timestamp) version.get("sent_at")).toInstant();
		assertEquals(sentAt, ((Timestamp) version.get("updated_at")).toInstant());
		assertFalse(sentAt.isBefore(start), sentAt + " before " + start);
		assertFalse(sentAt.isAfter(end), sentAt + " after " + end);
		Map<String, Object> agreement = agreementRow(done.agreementId());
		assertEquals("AWAITING_CONFIRMATION", agreement.get("status"));
		assertEquals(2L, rowVersion(done.agreementId()));
		assertEquals(sentAt, ((Timestamp) agreement.get("updated_at")).toInstant());
		for (String column : List.of("unit_id", "unit_code", "property_id", "owner_user_id", "basis_lead_id",
				"transaction_type", "created_by_user_id", "created_at")) {
			assertEquals(agreementBefore.get(column), agreement.get(column), column);
		}
		for (String column : versionBefore.keySet()) {
			if (!List.of("status", "updated_at", "sent_at").contains(column)) {
				assertEquals(versionBefore.get(column), version.get(column), column);
			}
		}
		assertEquals(feesBefore, feeRows(done.agreementId()));
		assertEquals(tiersBefore, tierRows(done.agreementId()));
		assertEquals(List.of("CREATED 1 " + sale.userId(), "DRAFT_EDITED 1 " + sale.userId(),
				"VERSION_SENT 1 " + sale.userId()), events(done.agreementId()));
		List<Map<String, Object>> notices = jdbc.queryForList("select kind, destination from auth_email_notifications"
				+ " where user_id = ? and kind = 'AGREEMENT_PENDING'", done.owner().userId());
		String address = jdbc.queryForObject("select email from users where id = ?", String.class, done.owner().userId());
		assertEquals(List.of(Map.of("kind", "AGREEMENT_PENDING", "destination", address)), notices);
		assertEquals(0, accessRows(done.agreementId()));

		Result detail = api.get(sale, AGREEMENTS + "/" + done.agreementId());
		assertEquals("AWAITING_CONFIRMATION", detail.body().path("status").asString(), detail.rawBody());
		JsonNode read = detail.body().path("versions").path(0);
		assertEquals("AWAITING_OWNER", read.path("status").asString(), detail.rawBody());
		assertEquals(sentAt, Instant.parse(read.path("sentAt").asString()), detail.rawBody());
		assertEquals(2L, detail.body().path("dataVersion").asLong());
	}

	// ==================== SD-2: locked after the send, the unit's slot kept ====================

	@Test
	void send_afterSending_lockedAndSlotKept() throws Exception {
		Done done = done();
		assertEquals(204, send(sale, done.agreementId(), 1).status());
		Written before = written(done);

		assertError(put(sale, done.agreementId(), AgreementFeePolicyApiPostgresTest.complete(2)), 409,
				"AGREEMENT_NOT_DRAFT", "[]");
		assertError(api.get(sale, AGREEMENTS + "/" + done.agreementId() + "/survey-terms"), 409, "AGREEMENT_NOT_DRAFT",
				"[]");
		Result again = send(sale, done.agreementId(), 2);
		assertError(again, 409, "AGREEMENT_NOT_DRAFT", "[]");
		assertEquals("This agreement has no draft to edit.", again.body().path("message").asString());
		assertEquals(before, written(done));
		assertEquals(1, notices(done.owner()));

		assertError(api.post(sale, AGREEMENTS, createBody(done.leadId(), done.unitId())), 409, "AGREEMENT_EXISTS", "[]");
		Result eligibility = api.get(sale, LEADS + "/" + done.leadId() + "/brokerage-eligibility");
		assertEquals(200, eligibility.status(), eligibility.rawBody());
		JsonNode unit = null;
		for (JsonNode candidate : eligibility.body().path("units")) {
			if (done.unitId().equals(candidate.path("unitId").asString())) {
				unit = candidate;
			}
		}
		assertNotNull(unit, eligibility.rawBody());
		assertEquals(true, unit.path("unfinishedAgreement").asBoolean(), eligibility.rawBody());
		assertEquals(done.agreementId(), unit.path("agreementId").asString(), eligibility.rawBody());
	}

	// ==================== SD-3: completeness ====================

	@Test
	void send_incomplete_partsInOrderNothingWritten() throws Exception {
		Done created = drafted();
		Written createdBefore = written(created);

		Result first = send(sale, created.agreementId(), 0);

		assertError(first, 409, "AGREEMENT_VERSION_INCOMPLETE", partErrors(ELEVEN.toArray(String[]::new)));
		assertEquals("This version cannot be sent yet: a part is missing.", first.body().path("message").asString());
		assertEquals(createdBefore, written(created));

		Done done = done();
		List<Consumer<Map<String, Object>>> nulls = List.of(
				body -> body.put("photoConsent", null),
				body -> part(body, "terms").put("monthlyRent", null),
				body -> part(body, "terms").put("deposit", null),
				body -> part(body, "terms").put("minimumTermMonths", null),
				body -> part(body, "terms").put("paymentPeriodMonths", null),
				body -> part(body, "terms").put("mandatoryFees", null),
				body -> part(body, "feePolicy").put("formula", null),
				body -> part(body, "feePolicy").put("dueDays", null),
				body -> part(body, "feePolicy").put("renewal", null),
				body -> part(body, "feePolicy").put("earlyLeaseEnd", null),
				body -> part(body, "feePolicy").put("protectionDays", null));
		for (int index = 0; index < ELEVEN.size(); index++) {
			long dataVersion = rowVersion(done.agreementId());
			assertEquals(204, put(sale, done.agreementId(), completeWith(dataVersion, nulls.get(index))).status());
			Written before = written(done);

			assertError(send(sale, done.agreementId(), dataVersion + 1), 409, "AGREEMENT_VERSION_INCOMPLETE",
					partErrors(ELEVEN.get(index)));

			assertEquals(before, written(done), ELEVEN.get(index));
		}
		assertEquals(0, notices(done.owner()));
		assertEquals(0, notices(created.owner()));
	}

	/** brokerage-agreement BR-8: 0 is a stated value; "free of charge" with its reason is complete. */
	@Test
	void send_zerosAndFree_sent() throws Exception {
		Done zeros = drafted();
		assertEquals(204, put(sale, zeros.agreementId(), completeWith(0, body -> {
			body.put("photoConsent", false);
			part(body, "terms").put("deposit", AgreementDraftApiPostgresTest.deposit("MONTHS", 0, null, null));
			part(body, "terms").put("mandatoryFees", List.of());
			part(body, "feePolicy").put("dueDays", 0);
			part(body, "feePolicy").put("protectionDays", 0);
		})).status());

		Result zeroAnswer = send(sale, zeros.agreementId(), 1);

		assertEquals(204, zeroAnswer.status(), zeroAnswer.rawBody());

		Done free = drafted();
		assertEquals(204, put(sale, free.agreementId(), completeWith(0, body -> {
			part(body, "terms").put("deposit", AgreementDraftApiPostgresTest.deposit("AMOUNT", null, 0, "VND"));
			part(body, "feePolicy").put("formula", AgreementFeePolicyApiPostgresTest.formula("FREE", null, null, null,
					"First unit with Sunvibe"));
		})).status());

		Result freeAnswer = send(sale, free.agreementId(), 1);

		assertEquals(204, freeAnswer.status(), freeAnswer.rawBody());
		assertEquals(1, notices(zeros.owner()));
		assertEquals(1, notices(free.owner()));
	}

	// ==================== SD-4: the E05-S1 check on the draft's own basis ====================

	@Test
	void send_notEligible_groupsNothingWritten() throws Exception {
		Done held = done();
		hold(held.leadId());
		assertNotEligible(held, "BASIS");

		Done staff = done();
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'",
				staff.owner().userId());
		assertNotEligible(staff, "BASIS", "PROPERTY", "OWNER");

		Done coOwned = done();
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state,"
				+ " valid_from, created_at) values (?, ?, ?, 'CO_OWNER', 'UNVERIFIED', ?, ?)", ids.generateId(),
				coOwned.propertyId(), ids.generateId(), LocalDate.of(2026, 1, 1),
				Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
		assertNotEligible(coOwned, "OWNER");

		Done ownerMoved = done();
		jdbc.update("update brokerage_agreements set owner_user_id = ? where id = ?", owner().userId(),
				ownerMoved.agreementId());
		assertNotEligible(ownerMoved, "OWNER");

		Done otherBasis = done();
		jdbc.update("update brokerage_agreement_versions set basis_intake_id = ? where agreement_id = ?",
				ids.generateId(), otherBasis.agreementId());
		assertNotEligible(otherBasis, "BASIS");

		String street = unique("Send Two Leads");
		Passed first = house(street);
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		available(first.leadId(), first.spaceIds(), "VACANT");
		JsonNode result = completed(first.leadId());
		String unit = result.path("units").path(0).path("unitId").asString();
		Passed second = house(street);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), result.path("propertyId").asString(), second.spaceIds(),
				List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		Done linked = new Done(second.leadId(), owner, result.path("propertyId").asString(), unit,
				create(sale, second.leadId(), unit));
		saveComplete(sale, linked.agreementId());
		suspendLead(first.leadId(), null, "Data request");
		assertNotEligible(linked, "PROPERTY");

		String buildingStreet = unique("Send Two Leads Building");
		Passed building = fixture.parts(leader, sale, "WHOLE_BUILDING", buildingStreet, "P101", "P102");
		TestUser buildingOwner = owner();
		owners.add(buildingOwner.userId());
		checked(building, buildingOwner);
		SurveyApiTestSupport.ok(createTarget(building.leadId(), building.spaceIds().get(0)));
		available(building.leadId(), building.spaceIds(), "VACANT");
		String buildingProperty = completed(building.leadId()).path("propertyId").asString();
		Map<String, String> unitBySpaceCode = new LinkedHashMap<>();
		jdbc.query("select space_code, unit_id from inventory_unit_mappings where intake_id = ?",
				row -> {
					unitBySpaceCode.put(row.getString("space_code"), row.getString("unit_id"));
				}, intakeRow(building.leadId()).get("id"));
		Passed rooms = fixture.parts(leader, sale, "WHOLE_BUILDING", buildingStreet, "P101", "P102");
		checked(rooms, buildingOwner);
		SurveyApiTestSupport.ok(linkTarget(rooms.leadId(), buildingProperty, rooms.spaceIds(),
				List.of(unitBySpaceCode.get("P101"), unitBySpaceCode.get("P102"))));
		available(rooms.leadId(), rooms.spaceIds(), "VACANT");
		completed(rooms.leadId());
		String p101 = unitBySpaceCode.get("P101");
		Done room = new Done(rooms.leadId(), buildingOwner, buildingProperty, p101, create(sale, rooms.leadId(), p101));
		saveComplete(sale, room.agreementId());
		suspendLead(building.leadId(), p101, "Room only");
		assertNotEligible(room, "UNIT");
	}

	/** D9-18: an availability that differs from the survey stops nothing. */
	@Test
	void send_availabilityDiffersOnly_sent() throws Exception {
		Passed passed = house(unique("Send Link Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class,
				passed.leadId());
		String propertyId = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(propertyId, owner.userId());
		String unit = unit(propertyId, "U1", "OCCUPIED");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), propertyId, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String id = create(sale, passed.leadId(), unitOf(passed.leadId()));
		saveComplete(sale, id);

		Result answer = send(sale, id, 1);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(1, notices(owner));
	}

	// ==================== SD-5: who sends ====================

	@Test
	void send_callers_asBr16() throws Exception {
		Done done = done();
		TestUser superAdmin = survey.user("Send Super Admin", "SUPER_ADMIN");
		TestUser customer = survey.user("Send Customer", "CUSTOMER");
		TestUser reviewer = survey.user("Send Reviewer", "LISTING_REVIEWER");
		Written before = written(done);

		for (TestUser caller : List.of(leader, manager, admin, superAdmin)) {
			assertError(send(caller, done.agreementId(), 1), 403, "AGREEMENT_NOT_ASSIGNEE", "[]");
		}
		assertNotFound(send(otherSale, done.agreementId(), 1));
		for (TestUser caller : List.of(customer, reviewer, done.owner())) {
			Result answer = send(caller, done.agreementId(), 1);
			assertEquals(403, answer.status(), answer.rawBody());
			assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
		}
		Result anonymous = send(null, done.agreementId(), 1);
		assertEquals(401, anonymous.status(), anonymous.rawBody());
		assertEquals("UNAUTHORIZED", anonymous.body().path("code").asString(), anonymous.rawBody());
		for (String other : List.of("01JUNKNOWN0000000000000000", "not-an-id", done.agreementId().toLowerCase(Locale.ROOT))) {
			assertNotFound(send(sale, other, 1));
		}
		assertEquals(before, written(done));

		assertEquals(204, send(sale, done.agreementId(), 1).status());
		assertEquals("VERSION_SENT 1 " + sale.userId(), events(done.agreementId()).getLast());

		Done byLeader = drafted();
		fixture.checklists().reassign(leader, byLeader.leadId(), leader);
		saveComplete(leader, byLeader.agreementId());
		Result leaderAnswer = send(leader, byLeader.agreementId(), 1);
		assertEquals(204, leaderAnswer.status(), leaderAnswer.rawBody());
		assertEquals("VERSION_SENT 1 " + leader.userId(), events(byLeader.agreementId()).getLast());
	}

	/** brokerage-agreement E-10: a reassignment moves the right at once. */
	@Test
	void send_reassigned_newAssigneeSends() throws Exception {
		Done done = done();
		fixture.checklists().reassign(leader, done.leadId(), otherSale);

		assertNotFound(send(sale, done.agreementId(), 1));
		Result answer = send(otherSale, done.agreementId(), 1);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("VERSION_SENT 1 " + otherSale.userId(), events(done.agreementId()).getLast());
		assertEquals(1, notices(done.owner()));
	}

	/** Spec S4-D2, security.md: no body field sets a status, a version number, an owner or a sending time. */
	@Test
	void send_extraBodyProperties_ignored() throws Exception {
		Done done = done();
		String ownerBefore = (String) agreementRow(done.agreementId()).get("owner_user_id");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", 1);
		body.put("status", "ACTIVE");
		body.put("versionNo", 7);
		body.put("ownerUserId", "01JXOWNER00000000000000000");
		body.put("sentAt", "2020-01-01T00:00:00Z");

		Result answer = sendBody(sale, done.agreementId(), body);

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> agreement = agreementRow(done.agreementId());
		Map<String, Object> version = versionRow(done.agreementId());
		assertEquals("AWAITING_CONFIRMATION", agreement.get("status"));
		assertEquals(ownerBefore, agreement.get("owner_user_id"));
		assertEquals("AWAITING_OWNER", version.get("status"));
		assertEquals(1, version.get("version_no"));
		assertTrue(((Timestamp) version.get("sent_at")).toInstant().isAfter(Instant.parse("2026-01-01T00:00:00Z")));
		assertEquals(1, notices(done.owner()));
	}

	@Test
	void send_badRequest_validationOrMalformed() throws Exception {
		Done done = done();
		Written before = written(done);
		String field = "[{\"field\":\"expectedDataVersion\",\"message\":\"This value is not accepted.\"}]";

		assertError(sendBody(sale, done.agreementId(), new LinkedHashMap<>()), 400, "VALIDATION_FAILED", field);
		assertError(send(sale, done.agreementId(), null), 400, "VALIDATION_FAILED", field);
		assertError(send(sale, done.agreementId(), -1), 400, "VALIDATION_FAILED", field);
		assertError(send(sale, done.agreementId(), "one"), 400, "MALFORMED_REQUEST", "[]");

		assertEquals(before, written(done));
	}

	// ==================== SD-6, first sentence ====================

	@Test
	void send_staleDataVersion_concurrentModification() throws Exception {
		Done done = done();
		Written before = written(done);

		Result answer = send(sale, done.agreementId(), 0);

		assertError(answer, 409, "CONCURRENT_MODIFICATION",
				"[{\"field\":\"expectedDataVersion\",\"message\":\"This value is out of date.\"}]");
		assertEquals(before, written(done));
	}

	// ==================== SD-8: no verified address, no notice ====================

	@Test
	void send_ownerWithoutVerifiedAddress_sentWithoutNotice() throws Exception {
		Done done = done();
		jdbc.update("update users set email_verified_at = null where id = ?", done.owner().userId());

		Result answer = send(sale, done.agreementId(), 1);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("AWAITING_OWNER", versionRow(done.agreementId()).get("status"));
		assertEquals("VERSION_SENT 1 " + sale.userId(), events(done.agreementId()).getLast());
		assertEquals(0, notices(done.owner()));
	}

	// ==================== SD-9: nothing outside brokerage but the notice ====================

	private record Snapshot(String leadStatus, long leadVersion, int activities, int leadAccessLogs, int intakes,
			int suspensions, int inventoryAccessLogs, int intakeEvents, int mappings, int ownerships) {
	}

	private Snapshot snapshot(String leadId, String propertyId) {
		Map<String, Object> lead = jdbc.queryForMap("select status, version from owner_leads where id = ?", leadId);
		return new Snapshot((String) lead.get("status"), ((Number) lead.get("version")).longValue(),
				count("select count(*) from owner_lead_activities where lead_id = ?", leadId),
				count("select count(*) from owner_lead_access_logs where lead_id = ?", leadId),
				count("select count(*) from inventory_intakes where lead_id = ?", leadId),
				count("select count(*) from inventory_suspensions where lead_id = ?", leadId),
				count("select count(*) from inventory_access_logs where lead_id = ?", leadId),
				count("select count(*) from inventory_intake_events where intake_id in"
						+ " (select id from inventory_intakes where lead_id = ?)", leadId),
				count("select count(*) from inventory_unit_mappings where intake_id in"
						+ " (select id from inventory_intakes where lead_id = ?)", leadId),
				count("select count(*) from property_ownerships where property_id = ?", propertyId));
	}

	@Test
	void send_writesNothingOutsideBrokerageButTheNotice() throws Exception {
		Done done = done();
		String other = AgreementFixture.insert(jdbc, ids, ids.generateId(), done.leadId(), "CANCELLED", "CANCELLED");
		created.add(other);
		JsonNode readinessBefore = blockOk(sale, done.leadId()).path("readiness");
		Snapshot before = snapshot(done.leadId(), done.propertyId());
		Map<String, Object> otherAgreement = agreementRow(other);
		Map<String, Object> otherVersion = versionRow(other);
		Map<String, Object> intake = intakeRow(done.leadId());
		Map<String, Object> ownership = jdbc.queryForMap("select * from property_ownerships where property_id = ?",
				done.propertyId());

		Result answer = send(sale, done.agreementId(), 1);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(before, snapshot(done.leadId(), done.propertyId()));
		assertEquals(otherAgreement, agreementRow(other));
		assertEquals(otherVersion, versionRow(other));
		assertEquals(intake, intakeRow(done.leadId()));
		assertEquals(ownership, jdbc.queryForMap("select * from property_ownerships where property_id = ?",
				done.propertyId()));
		assertEquals(0, accessRows(done.agreementId()));
		assertEquals(readinessBefore, blockOk(sale, done.leadId()).path("readiness"));
	}

	// ==================== SD-14: no value in an answer or a log line ====================

	@Test
	void send_refusals_quoteNoValue(CapturedOutput output) throws Exception {
		Map<String, Object> privatePolicy = AgreementFeePolicyApiPostgresTest.policy(
				AgreementFeePolicyApiPostgresTest.formula("FIXED_AMOUNT", null, 123_456_789, "VND", null), 23,
				AgreementFeePolicyApiPostgresTest.formula("PERCENT_OF_RENT", AgreementFeePolicyApiPostgresTest.tiers(1, 287),
						null, null, null),
				AgreementFeePolicyApiPostgresTest.early("PARTIAL_REFUND", 97, 11), 166);
		Map<String, Object> freePolicy = AgreementFeePolicyApiPostgresTest.policy(
				AgreementFeePolicyApiPostgresTest.formula("FREE", null, null, null, "Zebra reason"), 23,
				AgreementFeePolicyApiPostgresTest.formula("NOT_CHARGED", null, null, null, null),
				AgreementFeePolicyApiPostgresTest.early("NO_REFUND", null, null), null);
		int mark = output.getOut().length();

		Done incomplete = drafted();
		assertEquals(204, put(sale, incomplete.agreementId(), completeWith(0, body -> body.put("feePolicy", freePolicy)))
				.status());
		Result incompleteAnswer = send(sale, incomplete.agreementId(), 1);
		assertEquals(409, incompleteAnswer.status(), incompleteAnswer.rawBody());

		Done held = drafted();
		assertEquals(204, put(sale, held.agreementId(), completeWith(0, body -> body.put("feePolicy", privatePolicy)))
				.status());
		hold(held.leadId());
		Result heldAnswer = send(sale, held.agreementId(), 1);
		assertEquals(409, heldAnswer.status(), heldAnswer.rawBody());

		Done sent = drafted();
		assertEquals(204, put(sale, sent.agreementId(), completeWith(0, body -> body.put("feePolicy", privatePolicy)))
				.status());
		Result sentAnswer = send(sale, sent.agreementId(), 1);
		assertEquals(204, sentAnswer.status(), sentAnswer.rawBody());

		String address = jdbc.queryForObject("select email from users where id = ?", String.class, sent.owner().userId());
		// long values only: a short number can appear in a timestamp or a correlation id of its own accord
		List<String> values = List.of("123456789", "Zebra", "8000000", "Electricity", address);
		for (Result answer : List.of(incompleteAnswer, heldAnswer)) {
			for (String value : values) {
				assertFalse(answer.rawBody().contains(value), value + " in " + answer.rawBody());
			}
		}
		String logged = output.getOut().substring(mark);
		for (String value : values) {
			assertFalse(logged.contains(value), value + " in the output");
		}
	}
}
