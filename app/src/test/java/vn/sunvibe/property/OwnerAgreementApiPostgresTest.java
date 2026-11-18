package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The owner's agreements over HTTP on PostgreSQL (brokerage-agreement US-2 AC-1 to AC-5; BR-3, BR-4, BR-10, BR-16,
 * BR-17; E05-S5 spec 3.1 to 3.3, OR-1, OR-2, OC-1 to OC-6, RJ-1): the owner's read, the confirmation with the password
 * and the statement, the rejection, and the one answer every other caller gets. The annotations of
 * {@link AgreementSendApiPostgresTest} (those of its base), so both share one context, where sending to owners is on and
 * the lapse job runs: every version here is sent at the JVM's now or at most 7 days less one hour ago, never due
 * (E05-S5 spec S5-D19, plan E05S5-P2). Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class OwnerAgreementApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String STATEMENT_VERSION = "brokerage-agreement-2026-11";
	private static final String WARD_NAME = "Hải Châu";
	private static final String GROUP_MESSAGE = "This group stops the agreement.";
	private static final String EMPTY_LIST = "{\"items\":[],\"more\":false}";

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0939";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== HTTP ====================

	private Result list(TestUser caller) throws Exception {
		return api.get(caller, ME);
	}

	private Result confirm(TestUser caller, String password, String statement, Object... agreementAndVersion)
			throws Exception {
		List<Map<String, Object>> versions = new ArrayList<>();
		for (int index = 0; index < agreementAndVersion.length; index += 2) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("agreementId", agreementAndVersion[index]);
			item.put("versionNo", agreementAndVersion[index + 1]);
			versions.add(item);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", password);
		body.put("statementVersion", statement);
		body.put("versions", versions);
		return api.post(caller, ME + "/confirmations", body);
	}

	/** The owner confirms version 1 of each agreement with the right password and statement. */
	private Result confirmed(Done done, String... agreementIds) throws Exception {
		List<Object> items = new ArrayList<>();
		for (String id : agreementIds) {
			items.add(id);
			items.add(1);
		}
		return confirm(done.owner(), passwordOf(done.owner()), STATEMENT_VERSION, items.toArray());
	}

	private Result reject(TestUser caller, String agreementId, int versionNo, String reasonOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("versionNo", versionNo);
		body.put("reason", reasonOrNull);
		return api.post(caller, ME + "/" + agreementId + "/rejection", body);
	}

	// ==================== fixtures (as AgreementSendApiPostgresTest builds them) ====================

	private record Done(String leadId, TestUser owner, String propertyId, String unitId, String agreementId) {
	}

	private TestUser newOwner() throws Exception {
		TestUser owner = owner();
		owners.add(owner.userId());
		return owner;
	}

	/** A house lead of {@code sale} with a completed intake for this owner, and the Sale's complete draft on its unit. */
	private Done done(TestUser owner) throws Exception {
		Passed passed = house(unique("Owner Answer Street"));
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String unit = unitOf(passed.leadId());
		Done done = new Done(passed.leadId(), owner, propertyId, unit, create(passed.leadId(), unit));
		saveComplete(done.agreementId());
		return done;
	}

	/** {@link #done(TestUser)} sent to the owner by the Sale: version 1 AWAITING_OWNER, sent at the JVM's now. */
	private Done sent(TestUser owner) throws Exception {
		Done done = done(owner);
		send(done.agreementId());
		return done;
	}

	private Done sent() throws Exception {
		return sent(newOwner());
	}

	private void send(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		Result answer = api.post(sale, AGREEMENTS + "/" + agreementId + "/draft/submission", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private void saveComplete(String agreementId) throws Exception {
		Result answer = api.put(sale, AGREEMENTS + "/" + agreementId + "/draft",
				AgreementFeePolicyApiPostgresTest.complete(rowVersion(agreementId)));
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private String create(String leadId, String unitId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", leadId);
		body.put("unitId", unitId);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		return id;
	}

	private String unitOf(String leadId) throws Exception {
		return blockOk(sale, leadId).path("readiness").path("units").path(0).path("unitId").asString();
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	/** Moves the sending time of the agreement's version 1, as an older sending would have stored it. */
	private void sentAgo(String agreementId, Duration ago) {
		jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ? and version_no = 1",
				Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS).minus(ago)), agreementId);
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

	private int ownerReads(String agreementId) {
		return jdbc.queryForObject("select count(*) from brokerage_agreement_access_logs where agreement_id = ?"
				+ " and action = 'OWNER_DETAIL_READ'", Integer.class, agreementId);
	}

	private int notices(TestUser owner) {
		return jdbc.queryForObject("select count(*) from auth_email_notifications where user_id = ?"
				+ " and kind = 'AGREEMENT_PENDING'", Integer.class, owner.userId());
	}

	private int reauthFailures(TestUser user) {
		return jdbc.queryForObject("select reauth_failed_attempts from users where id = ?", Integer.class, user.userId());
	}

	/** Everything a refused command must leave as it was. */
	private record Written(Map<String, Object> agreement, Map<String, Object> version, List<String> events) {
	}

	private Written written(String agreementId) {
		return new Written(agreementRow(agreementId), versionRow(agreementId, 1), events(agreementId));
	}

	private static void assertError(Result answer, int status, String code, String errors) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	/** US-2 AC-1 (spec S5-D18): the body of an unknown id, whatever the caller and the reason. */
	private static void assertNotFound(Result answer) {
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("The resource was not found.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
	}

	private static List<String> sortedKeys(JsonNode node) {
		TreeSet<String> keys = new TreeSet<>();
		node.properties().forEach(entry -> keys.add(entry.getKey()));
		return List.copyOf(keys);
	}

	private static String textOrNull(JsonNode node) {
		return node.isNull() || node.isMissingNode() ? null : node.asString();
	}

	private static JsonNode item(Result list, String agreementId) {
		for (JsonNode item : list.body().path("items")) {
			if (agreementId.equals(item.path("agreementId").asString())) {
				return item;
			}
		}
		throw new AssertionError(agreementId + " not in " + list.rawBody());
	}

	// ==================== OR-1, OR-2: the owner's read ====================

	@Test
	void list_owner_sentAgreementsInFullAndOneAccessRowEach() throws Exception {
		TestUser owner = newOwner();
		Done first = sent(owner);
		Done second = sent(owner);

		Result answer = list(owner);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(false, answer.body().path("more").asBoolean());
		assertEquals(List.of(second.agreementId(), first.agreementId()), List.of(
				answer.body().path("items").path(0).path("agreementId").asString(),
				answer.body().path("items").path(1).path("agreementId").asString()), answer.rawBody());
		for (Done done : List.of(first, second)) {
			JsonNode item = item(answer, done.agreementId());
			assertEquals(List.of("agreementId", "statement", "status", "street", "transactionType", "unitCode",
					"unitLocation", "versions", "wardName"), sortedKeys(item));
			assertEquals("AWAITING_CONFIRMATION", item.path("status").asString());
			assertEquals("LONG_TERM_RENT", item.path("transactionType").asString());
			Map<String, Object> unit = jdbc.queryForMap("select location_label from units where id = ?", done.unitId());
			Map<String, Object> property = jdbc.queryForMap("select street, ward from properties where id = ?",
					done.propertyId());
			String unitCode = (String) agreementRow(done.agreementId()).get("unit_code");
			assertEquals(unitCode, textOrNull(item.path("unitCode")));
			assertEquals(unit.get("location_label"), textOrNull(item.path("unitLocation")));
			assertEquals(property.get("street"), textOrNull(item.path("street")));
			assertEquals(property.get("ward"), textOrNull(item.path("wardName")));
			assertEquals(1, item.path("versions").size());
			JsonNode version = item.path("versions").path(0);
			assertEquals(List.of("activateBy", "activation", "advertisingScope", "answerBy", "createdAt", "endDate",
					"exteriorPhotoConsent", "feePolicy", "ownerAnsweredAt", "ownerRejectionReason", "photoConsent",
					"sentAt", "startDate", "status", "terms", "versionNo"), sortedKeys(version));
			assertEquals("AWAITING_OWNER", version.path("status").asString());
			assertEquals(Instant.parse(version.path("sentAt").asString()).plus(Duration.ofDays(7)),
					Instant.parse(version.path("answerBy").asString()));
			assertTrue(version.path("ownerAnsweredAt").isNull(), item.toString());
			assertTrue(version.path("ownerRejectionReason").isNull(), item.toString());
			assertEquals(8_000_000L, version.path("terms").path("monthlyRent").path("amount").asLong(), item.toString());
			assertFalse(version.path("feePolicy").path("formula").isNull(), item.toString());
			assertEquals(STATEMENT_VERSION, item.path("statement").path("version").asString());
			String text = item.path("statement").path("text").asString();
			assertEquals(AgreementStatement.text(1, unitCode), text);
			assertTrue(text.startsWith("Version 1 of the brokerage agreement for "), text);
			assertEquals(1, ownerReads(done.agreementId()));
		}
		List<String> readers = jdbc.queryForList("select distinct user_id from brokerage_agreement_access_logs where"
				+ " agreement_id in (?, ?)", String.class, first.agreementId(), second.agreementId());
		assertEquals(List.of(owner.userId()), readers);
	}

	/** OR-1: a draft is never answered, and an agreement with no sent version is not listed. */
	@Test
	void list_draftOrUnsent_notAnswered() throws Exception {
		TestUser owner = newOwner();
		Done unsent = done(owner);
		Done rejected = sent(owner);
		assertEquals(204, reject(owner, rejected.agreementId(), 1, null).status());
		Map<String, Object> first = versionRow(rejected.agreementId(), 1);
		jdbc.update("insert into brokerage_agreement_versions (id, agreement_id, version_no, status, start_date, end_date,"
						+ " basis_lead_id, basis_intake_id, created_by_user_id, created_at, updated_at) values (?, ?, 2, 'DRAFT',"
						+ " ?, ?, ?, ?, ?, ?, ?)", ids.generateId(), rejected.agreementId(), first.get("start_date"),
				first.get("end_date"), first.get("basis_lead_id"), first.get("basis_intake_id"), sale.userId(),
				first.get("created_at"), first.get("created_at"));

		Result answer = list(owner);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(1, answer.body().path("items").size(), answer.rawBody());
		JsonNode item = item(answer, rejected.agreementId());
		assertEquals("DRAFTING", item.path("status").asString());
		assertEquals(1, item.path("versions").size());
		assertEquals("OWNER_REJECTED", item.path("versions").path(0).path("status").asString());
		assertTrue(item.path("statement").isNull(), item.toString());
		assertEquals(0, ownerReads(unsent.agreementId()));
	}

	/** OR-2, US-2 AC-1: every other caller gets what an owner with nothing gets, and no access row is written. */
	@Test
	void list_otherCallers_emptyNoAccessRow() throws Exception {
		Done done = sent();
		TestUser stranger = newOwner();

		for (TestUser caller : List.of(stranger, sale, admin)) {
			Result answer = list(caller);
			assertEquals(200, answer.status(), answer.rawBody());
			assertEquals(EMPTY_LIST, answer.response().body());
		}
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'",
				done.owner().userId());
		Result answer = list(done.owner());
		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(EMPTY_LIST, answer.response().body());
		assertEquals(0, ownerReads(done.agreementId()));
	}

	@Test
	void list_noToken_401() throws Exception {
		Result answer = list(null);

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
	}

	/** OR-2: 51 agreements sent to the owner → the 50 newest sendings and more. */
	@Test
	void list_fiftyOneSent_fiftyNewestFirstAndMore() throws Exception {
		TestUser owner = newOwner();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		List<String> newestFirst = new ArrayList<>();
		for (int minutes = 1; minutes <= 51; minutes++) {
			String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(),
					"AWAITING_CONFIRMATION", "AWAITING_OWNER");
			created.add(agreementId);
			jdbc.update("update brokerage_agreements set owner_user_id = ? where id = ?", owner.userId(), agreementId);
			jdbc.update("update brokerage_agreement_versions set sent_at = ? where agreement_id = ?",
					Timestamp.from(now.minus(minutes, ChronoUnit.MINUTES)), agreementId);
			newestFirst.add(agreementId);
		}

		Result answer = list(owner);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(true, answer.body().path("more").asBoolean());
		List<String> listed = new ArrayList<>();
		answer.body().path("items").forEach(item -> listed.add(item.path("agreementId").asString()));
		assertEquals(newestFirst.subList(0, 50), listed);
		JsonNode first = answer.body().path("items").path(0);
		assertTrue(first.path("unitLocation").isNull() && first.path("street").isNull(), first.toString());
		assertEquals(0, ownerReads(newestFirst.get(50)));
		assertEquals(1, ownerReads(newestFirst.get(0)));
	}

	// ==================== OC-1 to OC-6: the confirmation ====================

	@Test
	void confirm_rightPasswordAndStatement_recorded() throws Exception {
		Done done = sent();
		Map<String, Object> before = versionRow(done.agreementId(), 1);
		long rowVersion = rowVersion(done.agreementId());
		long leadVersion = survey.version(done.leadId());
		Instant start = Instant.now().truncatedTo(ChronoUnit.MICROS);

		Result answer = confirmed(done, done.agreementId());

		Instant end = Instant.now();
		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> version = versionRow(done.agreementId(), 1);
		assertEquals("OWNER_CONFIRMED", version.get("status"));
		assertEquals("CONFIRMED", version.get("owner_answer"));
		assertEquals(done.owner().userId(), version.get("owner_answered_by"));
		assertEquals(STATEMENT_VERSION, version.get("owner_statement_version"));
		assertNull(version.get("owner_rejection_reason"));
		Instant answeredAt = ((Timestamp) version.get("owner_answered_at")).toInstant();
		assertFalse(answeredAt.isBefore(start) || answeredAt.isAfter(end), answeredAt.toString());
		for (String column : before.keySet()) {
			if (!List.of("status", "updated_at", "owner_answer", "owner_answered_by", "owner_answered_at",
					"owner_statement_version").contains(column)) {
				assertEquals(before.get(column), version.get(column), column);
			}
		}
		assertEquals("AWAITING_CONFIRMATION", agreementRow(done.agreementId()).get("status"));
		assertEquals(rowVersion + 1, rowVersion(done.agreementId()));
		assertEquals("OWNER_CONFIRMED 1 " + done.owner().userId(), events(done.agreementId()).getLast());
		assertEquals(1, notices(done.owner()));
		assertEquals(leadVersion, survey.version(done.leadId()));

		Result detail = api.get(sale, AGREEMENTS + "/" + done.agreementId());
		JsonNode read = detail.body().path("versions").path(0);
		assertEquals("OWNER_CONFIRMED", read.path("status").asString(), detail.rawBody());
		assertEquals(answeredAt, Instant.parse(read.path("ownerAnsweredAt").asString()), detail.rawBody());
	}

	/** OC-2, US-2 AC-4: one request, each version recorded on its own. */
	@Test
	void confirm_twoAgreements_eachOnItsOwn() throws Exception {
		TestUser owner = newOwner();
		Done first = sent(owner);
		Done second = sent(owner);

		Result answer = confirmed(first, first.agreementId(), second.agreementId());

		assertEquals(204, answer.status(), answer.rawBody());
		for (Done done : List.of(first, second)) {
			assertEquals("OWNER_CONFIRMED", versionRow(done.agreementId(), 1).get("status"));
			assertEquals(owner.userId(), versionRow(done.agreementId(), 1).get("owner_answered_by"));
			assertEquals(1, events(done.agreementId()).stream().filter(event -> event.startsWith("OWNER_CONFIRMED 1 "))
					.count());
		}
	}

	/** OC-3: a wrong password counts on auth's counter and writes nothing. */
	@Test
	void confirm_wrongPassword_400CountedNothingWritten() throws Exception {
		Done done = sent();
		Written before = written(done.agreementId());
		int failures = reauthFailures(done.owner());

		Result answer = confirm(done.owner(), "wrong-password", STATEMENT_VERSION, done.agreementId(), 1);

		assertError(answer, 400, "VALIDATION_FAILED",
				"[{\"field\":\"currentPassword\",\"message\":\"This value is not accepted.\"}]");
		assertEquals(failures + 1, reauthFailures(done.owner()));
		assertEquals(before, written(done.agreementId()));
	}

	/** OC-3: the fifth wrong password locks the re-authentication; the right one then answers 423. */
	@Test
	void confirm_fifthWrongThenRight_423() throws Exception {
		Done done = sent();

		for (int attempt = 1; attempt <= 4; attempt++) {
			assertEquals(400, confirm(done.owner(), "wrong-password", STATEMENT_VERSION, done.agreementId(), 1).status());
		}
		assertError(confirm(done.owner(), "wrong-password", STATEMENT_VERSION, done.agreementId(), 1), 423,
				"ACCOUNT_LOCKED", "[]");
		Result right = confirmed(done, done.agreementId());

		assertError(right, 423, "ACCOUNT_LOCKED", "[]");
		assertEquals("Too many wrong passwords. Try again later.", right.body().path("message").asString());
		assertEquals("AWAITING_OWNER", versionRow(done.agreementId(), 1).get("status"));
	}

	/** OC-4, US-2 AC-5: the basis, the stops and the owner are checked again; each refusal names its group. */
	@Test
	void confirm_stops_notEligibleNothingWritten() throws Exception {
		Done held = sent();
		hold(held.leadId());
		assertNotEligible(held, held.owner(), "BASIS");

		Done coOwned = sent();
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state,"
				+ " valid_from, created_at) values (?, ?, ?, 'CO_OWNER', 'UNVERIFIED', ?, ?)", ids.generateId(),
				coOwned.propertyId(), ids.generateId(), LocalDate.of(2026, 1, 1),
				Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
		assertNotEligible(coOwned, coOwned.owner(), "OWNER");

		Done ownerMoved = sent();
		TestUser otherOwner = newOwner();
		jdbc.update("update brokerage_agreements set owner_user_id = ? where id = ?", otherOwner.userId(),
				ownerMoved.agreementId());
		assertNotEligible(ownerMoved, otherOwner, "OWNER");

		Done otherBasis = sent();
		jdbc.update("update brokerage_agreement_versions set basis_intake_id = ? where agreement_id = ?",
				ids.generateId(), otherBasis.agreementId());
		assertNotEligible(otherBasis, otherBasis.owner(), "BASIS");

		String street = unique("Owner Two Leads");
		Passed first = house(street);
		TestUser owner = newOwner();
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
				create(second.leadId(), unit));
		saveComplete(linked.agreementId());
		send(linked.agreementId());
		suspendLead(first.leadId(), null, "Data request");
		assertNotEligible(linked, owner, "PROPERTY");

		String buildingStreet = unique("Owner Two Leads Building");
		Passed building = fixture.parts(leader, sale, "WHOLE_BUILDING", buildingStreet, "P101", "P102");
		TestUser buildingOwner = newOwner();
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
		Done room = new Done(rooms.leadId(), buildingOwner, buildingProperty, p101, create(rooms.leadId(), p101));
		saveComplete(room.agreementId());
		send(room.agreementId());
		suspendLead(building.leadId(), p101, "Room only");
		assertNotEligible(room, buildingOwner, "UNIT");
	}

	/** A 409 AGREEMENT_NOT_ELIGIBLE on item 0 with exactly these groups, and nothing written. */
	private void assertNotEligible(Done done, TestUser caller, String... groups) throws Exception {
		Written before = written(done.agreementId());
		List<String> entries = new ArrayList<>();
		for (String group : groups) {
			entries.add("{\"field\":\"versions[0].reasonGroups." + group + "\",\"message\":\"" + GROUP_MESSAGE + "\"}");
		}

		Result answer = confirm(caller, passwordOf(caller), STATEMENT_VERSION, done.agreementId(), 1);

		assertError(answer, 409, "AGREEMENT_NOT_ELIGIBLE", "[" + String.join(",", entries) + "]");
		assertEquals(before, written(done.agreementId()), String.join(",", groups));
	}

	/** D9-18: an availability that differs from the survey stops nothing. */
	@Test
	void confirm_availabilityDiffersOnly_confirmed() throws Exception {
		Passed passed = house(unique("Owner Link Street"));
		TestUser owner = newOwner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class,
				passed.leadId());
		String propertyId = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(propertyId, owner.userId());
		String unit = unit(propertyId, "U1", "OCCUPIED");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), propertyId, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String id = create(passed.leadId(), unitOf(passed.leadId()));
		saveComplete(id);
		send(id);

		Result answer = confirm(owner, passwordOf(owner), STATEMENT_VERSION, id, 1);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("OWNER_CONFIRMED", versionRow(id, 1).get("status"));
	}

	/** OC-5, S5-D18: one not-found for every caller and id that is not the caller's sent version; no counter moved. */
	@Test
	void confirm_notTheCallersOrUnknown_404SameBodyNoCounter() throws Exception {
		Done done = sent();
		Done unsent = done(done.owner());
		TestUser stranger = newOwner();
		Written before = written(done.agreementId());
		int failures = reauthFailures(done.owner());

		assertNotFound(confirm(stranger, passwordOf(stranger), STATEMENT_VERSION, done.agreementId(), 1));
		for (TestUser staff : List.of(sale, admin)) {
			assertNotFound(confirm(staff, "any-password", STATEMENT_VERSION, done.agreementId(), 1));
		}
		for (Object[] item : List.of(new Object[]{"01JUNKNOWN0000000000000000", 1}, new Object[]{"not-an-id", 1},
				new Object[]{done.agreementId().toLowerCase(Locale.ROOT), 1}, new Object[]{done.agreementId(), 2},
				new Object[]{unsent.agreementId(), 1})) {
			assertNotFound(confirm(done.owner(), "wrong-password", STATEMENT_VERSION, item));
		}

		assertEquals(failures, reauthFailures(done.owner()));
		assertEquals(0, reauthFailures(stranger));
		assertEquals(before, written(done.agreementId()));
	}

	@Test
	void confirm_outdatedStatement_409NoCounter() throws Exception {
		Done done = sent();
		Written before = written(done.agreementId());

		Result answer = confirm(done.owner(), "wrong-password", "old", done.agreementId(), 1);

		assertError(answer, 409, "AGREEMENT_STATEMENT_OUTDATED", "[]");
		assertEquals("The confirmation text has changed. Read it again.", answer.body().path("message").asString());
		assertEquals(0, reauthFailures(done.owner()));
		assertEquals(before, written(done.agreementId()));
	}

	/** OC-5, spec 3.2: the body's shape answers first, for every caller. */
	@Test
	void confirm_badShape_400() throws Exception {
		Done done = sent();
		String password = passwordOf(done.owner());
		Object[] twentyOne = new Object[42];
		for (int index = 0; index < 21; index++) {
			twentyOne[index * 2] = String.format("01JAGREEMENT%014d", index);
			twentyOne[index * 2 + 1] = 1;
		}

		assertError(confirm(done.owner(), password, STATEMENT_VERSION, done.agreementId(), 1, done.agreementId(), 2), 400,
				"VALIDATION_FAILED", "[{\"field\":\"versions\",\"message\":\"This value is not accepted.\"}]");
		assertError(confirm(done.owner(), password, STATEMENT_VERSION, twentyOne), 400, "VALIDATION_FAILED",
				"[{\"field\":\"versions\",\"message\":\"This value is not accepted.\"}]");
		assertError(confirm(done.owner(), password, STATEMENT_VERSION), 400, "VALIDATION_FAILED",
				"[{\"field\":\"versions\",\"message\":\"This value is not accepted.\"}]");
		assertError(confirm(done.owner(), null, STATEMENT_VERSION, done.agreementId(), 1), 400, "VALIDATION_FAILED",
				"[{\"field\":\"currentPassword\",\"message\":\"This value is not accepted.\"}]");
		assertError(confirm(done.owner(), password, STATEMENT_VERSION, done.agreementId(), 0), 400, "VALIDATION_FAILED",
				"[{\"field\":\"versions[0].versionNo\",\"message\":\"This value is not accepted.\"}]");
		assertError(confirm(sale, password, STATEMENT_VERSION, done.agreementId(), 1, done.agreementId(), 1), 400,
				"VALIDATION_FAILED", "[{\"field\":\"versions\",\"message\":\"This value is not accepted.\"}]");
		Result malformed = api.send(api.request(done.owner(), ME + "/confirmations")
				.header("Content-Type", "application/json")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"versions\": [")).build());
		assertError(malformed, 400, "MALFORMED_REQUEST", "[]");
		assertEquals(0, reauthFailures(done.owner()));
		assertEquals("AWAITING_OWNER", versionRow(done.agreementId(), 1).get("status"));
	}

	/** OC-6: a version sent 7 days less one hour ago may still be confirmed. */
	@Test
	void confirm_sentAlmostSevenDaysAgo_confirmed() throws Exception {
		Done done = sent();
		sentAgo(done.agreementId(), Duration.ofDays(7).minusHours(1));

		Result answer = confirmed(done, done.agreementId());

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("OWNER_CONFIRMED", versionRow(done.agreementId(), 1).get("status"));
	}

	/** OC-6, brokerage-agreement BR-5: an answer is given once and never changes. */
	@Test
	void confirm_answeredVersion_409AnswerKept() throws Exception {
		Done done = sent();
		assertEquals(204, confirmed(done, done.agreementId()).status());
		Written confirmedRow = written(done.agreementId());

		assertError(confirmed(done, done.agreementId()), 409, "AGREEMENT_NOT_AWAITING_OWNER",
				"[{\"field\":\"versions[0]\",\"message\":\"This version cannot be answered now.\"}]");
		assertError(reject(done.owner(), done.agreementId(), 1, "Changed my mind"), 409, "AGREEMENT_NOT_AWAITING_OWNER",
				"[]");
		assertEquals(confirmedRow, written(done.agreementId()));

		Done declined = sent();
		assertEquals(204, reject(declined.owner(), declined.agreementId(), 1, "No").status());
		assertError(confirmed(declined, declined.agreementId()), 409, "AGREEMENT_NOT_AWAITING_OWNER",
				"[{\"field\":\"versions[0]\",\"message\":\"This version cannot be answered now.\"}]");
		assertEquals("No", versionRow(declined.agreementId(), 1).get("owner_rejection_reason"));
	}

	// ==================== RJ-1: the rejection ====================

	@Test
	void reject_reasonTrimmed_draftingAgainNoPassword() throws Exception {
		Done done = sent();
		int failures = reauthFailures(done.owner());

		Result answer = reject(done.owner(), done.agreementId(), 1, "  The rent is too low.  ");

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> version = versionRow(done.agreementId(), 1);
		assertEquals("OWNER_REJECTED", version.get("status"));
		assertEquals("REJECTED", version.get("owner_answer"));
		assertEquals(done.owner().userId(), version.get("owner_answered_by"));
		assertEquals("The rent is too low.", version.get("owner_rejection_reason"));
		assertNull(version.get("owner_statement_version"));
		assertEquals("DRAFTING", agreementRow(done.agreementId()).get("status"));
		assertEquals("OWNER_REJECTED 1 " + done.owner().userId(), events(done.agreementId()).getLast());
		assertEquals(failures, reauthFailures(done.owner()));
		assertEquals(1, notices(done.owner()));

		Result detail = api.get(sale, AGREEMENTS + "/" + done.agreementId());
		assertEquals("DRAFTING", detail.body().path("status").asString(), detail.rawBody());
		assertEquals("The rent is too low.",
				detail.body().path("versions").path(0).path("ownerRejectionReason").asString(), detail.rawBody());
	}

	@Test
	void reject_noOrBlankReason_noneStored() throws Exception {
		TestUser owner = newOwner();
		Done absent = sent(owner);
		Done nulled = sent(owner);
		Done blank = sent(owner);
		Map<String, Object> versionOnly = new LinkedHashMap<>();
		versionOnly.put("versionNo", 1);

		assertEquals(204, api.post(owner, ME + "/" + absent.agreementId() + "/rejection", versionOnly).status());
		assertEquals(204, reject(owner, nulled.agreementId(), 1, null).status());
		assertEquals(204, reject(owner, blank.agreementId(), 1, "   ").status());

		for (Done done : List.of(absent, nulled, blank)) {
			Map<String, Object> version = versionRow(done.agreementId(), 1);
			assertEquals("REJECTED", version.get("owner_answer"), done.agreementId());
			assertNull(version.get("owner_rejection_reason"), done.agreementId());
		}
	}

	@Test
	void reject_badReason_400() throws Exception {
		Done done = sent();
		Written before = written(done.agreementId());

		for (String reason : List.of("a".repeat(501), "Too\u0000low", "Too‮low")) {
			assertError(reject(done.owner(), done.agreementId(), 1, reason), 400, "VALIDATION_FAILED",
					"[{\"field\":\"reason\",\"message\":\"This value is not accepted.\"}]");
			assertError(reject(sale, "01JUNKNOWN0000000000000000", 1, reason), 400, "VALIDATION_FAILED",
					"[{\"field\":\"reason\",\"message\":\"This value is not accepted.\"}]");
		}
		assertError(reject(done.owner(), done.agreementId(), 0, null), 400, "VALIDATION_FAILED",
				"[{\"field\":\"versionNo\",\"message\":\"This value is not accepted.\"}]");
		assertEquals(before, written(done.agreementId()));
	}

	@Test
	void reject_otherCallersOrAnswered_404Or409() throws Exception {
		Done done = sent();
		TestUser stranger = newOwner();
		Written before = written(done.agreementId());

		assertNotFound(reject(stranger, done.agreementId(), 1, null));
		assertNotFound(reject(sale, done.agreementId(), 1, null));
		assertNotFound(reject(admin, done.agreementId(), 1, null));
		assertNotFound(reject(done.owner(), "01JUNKNOWN0000000000000000", 1, null));
		assertNotFound(reject(done.owner(), "not-an-id", 1, null));
		assertNotFound(reject(done.owner(), done.agreementId(), 2, null));
		assertEquals(before, written(done.agreementId()));

		assertEquals(204, reject(done.owner(), done.agreementId(), 1, null).status());
		assertError(reject(done.owner(), done.agreementId(), 1, null), 409, "AGREEMENT_NOT_AWAITING_OWNER", "[]");
		assertEquals("This version is not waiting for your answer.",
				reject(done.owner(), done.agreementId(), 1, null).body().path("message").asString());
	}

	/** RJ-1, brokerage-agreement BR-17: no answer and no log line quotes the reason or the password. */
	@Test
	void reject_bodiesAndLogs_quoteNoReason(CapturedOutput output) throws Exception {
		Done done = sent();
		String reason = "Secret reason " + phonePrefix() + " not for logs";

		Result refused = reject(done.owner(), done.agreementId(), 1, reason + "‮");
		Result wrong = confirm(done.owner(), "Secret-password-" + phonePrefix(), STATEMENT_VERSION, done.agreementId(), 1);
		Result accepted = reject(done.owner(), done.agreementId(), 1, reason);
		Result again = reject(done.owner(), done.agreementId(), 1, reason);

		assertEquals(400, refused.status(), refused.rawBody());
		assertEquals(400, wrong.status(), wrong.rawBody());
		assertEquals(204, accepted.status(), accepted.rawBody());
		assertEquals(409, again.status(), again.rawBody());
		for (Result answer : List.of(refused, wrong, accepted, again)) {
			assertFalse(answer.rawBody().contains("Secret"), answer.rawBody());
		}
		assertFalse(output.getAll().contains("Secret reason"), "a log line quotes the reason");
		assertFalse(output.getAll().contains("Secret-password"), "a log line quotes the password");
		assertEquals(reason, versionRow(done.agreementId(), 1).get("owner_rejection_reason"));
	}
}
