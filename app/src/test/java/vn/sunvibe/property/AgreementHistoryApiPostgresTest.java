package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.AgreementListFixture;
import vn.sunvibe.brokerage.AgreementListFixture.Version;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The history of a brokerage agreement over HTTP on PostgreSQL (brokerage-agreement US-4 AC-4; BR-16, BR-17; E05-S7 spec
 * S7-D6 to S7-D8, S7-D13; criteria HS-1 to HS-6). Agreements are made through the API chain as
 * {@link AgreementActivationApiPostgresTest} makes them, or by {@link AgreementListFixture} on a real lead where stored
 * rows are all a case needs. The annotations of its base, so it shares the context of the other agreement API tests.
 * Needs Docker.
 */
class AgreementHistoryApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String OWNER_STATEMENT = "brokerage-agreement-2026-11";
	private static final String UNKNOWN = "01JUNKNOWN0000000000000000";
	private static final String REASON = "The rent is far too low for us.";
	private static final String NOTE = "Rent on the signed paper differs.";

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0927";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== HTTP ====================

	private Result history(TestUser caller, String agreementId, String query) throws Exception {
		return api.get(caller, AGREEMENTS + "/" + agreementId + "/history" + query);
	}

	/** The history, which must answer 200. */
	private JsonNode historyOk(TestUser caller, String agreementId, String query) throws Exception {
		Result answer = history(caller, agreementId, query);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body();
	}

	private static void assertNotFound(Result answer) {
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("The resource was not found.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
	}

	private static void assertValidationFailed(Result answer, String field) {
		assertEquals(400, answer.status(), answer.rawBody());
		assertEquals("VALIDATION_FAILED", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("Validation failed.", answer.body().path("message").asString(), answer.rawBody());
		JsonNode errors = answer.body().path("errors");
		assertEquals(1, errors.size(), answer.rawBody());
		assertEquals(field, errors.path(0).path("field").asString(), answer.rawBody());
		assertEquals("This value is not accepted.", errors.path(0).path("message").asString(), answer.rawBody());
		assertTrue(errors.path(0).path("allowedValues").isMissingNode(), answer.rawBody());
	}

	private static List<String> sortedKeys(JsonNode node) {
		List<String> keys = new ArrayList<>();
		node.properties().forEach(entry -> keys.add(entry.getKey()));
		return keys.stream().sorted().toList();
	}

	/** The entries as "type version actorType actorName", in the answer's order. */
	private static List<String> entries(JsonNode history) {
		List<String> entries = new ArrayList<>();
		history.path("entries").forEach(entry -> entries.add(entry.path("type").asString() + " "
				+ (entry.path("versionNo").isNull() ? "-" : entry.path("versionNo").asInt()) + " "
				+ entry.path("actorType").asString() + " "
				+ (entry.path("actorName").isNull() ? "-" : entry.path("actorName").asString())));
		return entries;
	}

	// ==================== fixtures ====================

	/** A fixture agreement drafting on the lead, created now. */
	private String fixture(String leadId) {
		String id = AgreementListFixture.insert(jdbc, ids, leadId, "P-HISTORY", "DRAFTING",
				Instant.now().truncatedTo(ChronoUnit.MICROS),
				new Version(1, "DRAFT", LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31")));
		created.add(id);
		return id;
	}

	private String fullName(TestUser user) {
		return jdbc.queryForObject("select full_name from users where id = ?", String.class, user.userId());
	}

	private record Chain(String leadId, TestUser owner, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake for a new owner, and the Sale's complete draft on its unit. */
	private Chain drafted() throws Exception {
		Passed passed = house(unique("History Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", passed.leadId());
		body.put("unitId", unit);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		Map<String, Object> draft = AgreementFeePolicyApiPostgresTest.complete(rowVersion(id));
		draft.put("startDate", today().toString());
		draft.put("endDate", today().plusMonths(6).toString());
		Result saved = api.put(sale, AGREEMENTS + "/" + id + "/draft", draft);
		assertEquals(204, saved.status(), saved.rawBody());
		return new Chain(passed.leadId(), owner, id);
	}

	private void send(String agreementId) throws Exception {
		Result answer = api.post(sale, AGREEMENTS + "/" + agreementId + "/draft/submission",
				Map.of("expectedDataVersion", rowVersion(agreementId)));
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

	private void activate(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		body.put("versionNo", 1);
		body.put("statementVersion", ActivationStatement.VERSION);
		body.put("documentNumber", "HDMG-2026/014");
		body.put("signedOn", today().toString());
		body.put("sunvibeSignatoryName", "Nguyen Van A");
		body.put("sunvibeSignatoryTitle", "Director");
		Result answer = api.post(manager, AGREEMENTS + "/" + agreementId + "/activation", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private int accessRows(String agreementId) {
		return count("select count(*) from brokerage_agreement_access_logs where agreement_id = ?", agreementId);
	}

	private int events(String agreementId) {
		return count("select count(*) from brokerage_agreement_events where agreement_id = ?", agreementId);
	}

	// ==================== HS-1: the entries ====================

	/**
	 * HS-1 (US-4 AC-4; BR-17): created, edited, sent, confirmed by the owner, activated, and a stored start with no
	 * actor: one entry per stored row, newest first, staff by their full names, the owner as OWNER with no name, the
	 * start as SYSTEM.
	 */
	@Test
	void history_lifecycle_oneEntryPerRowNewestFirstWithActors() throws Exception {
		Chain chain = drafted();
		send(chain.agreementId());
		confirm(chain.owner(), chain.agreementId(), 1);
		activate(chain.agreementId());
		Instant last = jdbc.queryForObject("select max(occurred_at) from brokerage_agreement_events where agreement_id = ?",
				Timestamp.class, chain.agreementId()).toInstant();
		AgreementListFixture.event(jdbc, chain.agreementId(), 1, "VERSION_STARTED", null, last.plusSeconds(1));

		JsonNode history = historyOk(manager, chain.agreementId(), "");

		String bySale = fullName(sale);
		assertEquals(List.of("VERSION_STARTED 1 SYSTEM -", "ACTIVATED 1 STAFF " + fullName(manager),
				"OWNER_CONFIRMED 1 OWNER -", "VERSION_SENT 1 STAFF " + bySale, "DRAFT_EDITED 1 STAFF " + bySale,
				"CREATED 1 STAFF " + bySale), entries(history));
		assertEquals(events(chain.agreementId()), history.path("totalElements").asInt());
		assertEquals(0, history.path("page").asInt());
		assertEquals(50, history.path("size").asInt());
		List<Instant> times = new ArrayList<>();
		history.path("entries").forEach(entry -> times.add(Instant.parse(entry.path("occurredAt").asString())));
		assertEquals(last.plusSeconds(1), times.getFirst());
		assertEquals(times.stream().sorted(java.util.Comparator.reverseOrder()).toList(), times);
	}

	/** HS-1 (S7-D7): a row by a staff account that was deleted since keeps its name. */
	@Test
	void history_deletedStaffAccount_nameKept() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));
		TestUser gone = survey.user("History Gone Staff", "SALE");
		AgreementListFixture.event(jdbc, agreementId, 1, "DRAFT_EDITED", gone.userId(),
				Instant.now().truncatedTo(ChronoUnit.MICROS));
		jdbc.update("update users set user_status = ?, deleted_at = ? where id = ?", UserStatus.DELETED.name(),
				Timestamp.from(Instant.now()), gone.userId());

		assertEquals(List.of("DRAFT_EDITED 1 STAFF History Gone Staff"), entries(historyOk(sale, agreementId, "")));
	}

	// ==================== HS-2: no reason, note, term or user id ====================

	/**
	 * HS-2 (US-4 AC-4; BR-17): a version declined with a reason and an activation refused with a note: each entry has
	 * exactly the contract's keys, the body holds neither the reason, the note nor a user id, and the detail still
	 * answers both to its readers.
	 */
	@Test
	void history_entryKeys_noReasonNoteTermOrUserId() throws Exception {
		Chain chain = drafted();
		send(chain.agreementId());
		Result rejected = api.post(chain.owner(), ME + "/" + chain.agreementId() + "/rejection",
				Map.of("versionNo", 1, "reason", REASON));
		assertEquals(204, rejected.status(), rejected.rawBody());
		Result opened = api.post(sale, AGREEMENTS + "/" + chain.agreementId() + "/draft",
				Map.of("expectedDataVersion", rowVersion(chain.agreementId())));
		assertEquals(204, opened.status(), opened.rawBody());
		send(chain.agreementId());
		confirm(chain.owner(), chain.agreementId(), 2);
		Map<String, Object> refusal = new LinkedHashMap<>();
		refusal.put("expectedDataVersion", rowVersion(chain.agreementId()));
		refusal.put("versionNo", 2);
		refusal.put("reasonType", "DOCUMENT_DIFFERS");
		refusal.put("note", NOTE);
		Result refused = api.post(manager, AGREEMENTS + "/" + chain.agreementId() + "/activation-refusal", refusal);
		assertEquals(204, refused.status(), refused.rawBody());

		Result answer = history(manager, chain.agreementId(), "");

		assertEquals(200, answer.status(), answer.rawBody());
		assertTrue(answer.body().path("entries").size() >= 7, answer.rawBody());
		for (JsonNode entry : answer.body().path("entries")) {
			assertEquals(List.of("actorName", "actorType", "occurredAt", "type", "versionNo"), sortedKeys(entry));
		}
		assertEquals(List.of("entries", "page", "size", "totalElements"), sortedKeys(answer.body()));
		List<String> types = new ArrayList<>();
		answer.body().path("entries").forEach(entry -> types.add(entry.path("type").asString()));
		assertTrue(types.containsAll(List.of("OWNER_REJECTED", "DRAFT_OPENED", "ACTIVATION_REFUSED")), types.toString());
		for (String secret : List.of(REASON, NOTE, "DOCUMENT_DIFFERS", sale.userId(), manager.userId(),
				chain.owner().userId(), "8000000")) {
			assertFalse(answer.rawBody().contains(secret), secret + " in " + answer.rawBody());
		}
		String detail = api.get(manager, AGREEMENTS + "/" + chain.agreementId()).rawBody();
		assertTrue(detail.contains(REASON), detail);
		assertTrue(detail.contains(NOTE), detail);
	}

	// ==================== HS-3: the readers ====================

	/** HS-3 (BR-16; S2-D10): the assigned Sale and the managers read; another Sale, an unknown and a malformed id 404. */
	@Test
	void history_visibility_assignedSaleAndManagersReadOthersNotFound() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));
		AgreementListFixture.event(jdbc, agreementId, 1, "CREATED", sale.userId(),
				Instant.now().truncatedTo(ChronoUnit.MICROS));

		for (TestUser reader : List.of(sale, leader, manager, admin)) {
			assertEquals(List.of("CREATED 1 STAFF " + fullName(sale)), entries(historyOk(reader, agreementId, "")),
					reader.toString());
		}
		Result hidden = history(otherSale, agreementId, "");
		assertNotFound(hidden);
		for (String other : List.of(UNKNOWN, "not-an-id", agreementId.toLowerCase(Locale.ROOT))) {
			Result answer = history(sale, other, "");
			assertNotFound(answer);
			assertEquals(hidden.body().path("message"), answer.body().path("message"));
		}
	}

	@Test
	void history_customerRole_forbidden() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));
		TestUser customer = survey.user("History Customer", "CUSTOMER");

		Result answer = history(customer, agreementId, "");

		assertEquals(403, answer.status(), answer.rawBody());
		assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
	}

	@Test
	void history_noToken_unauthorized() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));

		Result answer = history(null, agreementId, "");

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
	}

	/** HS-3, Review Focus 5 (BR-16, E-10): after a reassignment the former Sale gets 404 and the new Sale reads. */
	@Test
	void history_afterReassignment_formerSaleNotFoundNewSaleReads() throws Exception {
		String leadId = survey.lead(leader, sale, "ASSIGNED");
		String agreementId = fixture(leadId);
		assertEquals(200, history(sale, agreementId, "").status());

		fixture.checklists().reassign(leader, leadId, otherSale);

		assertNotFound(history(sale, agreementId, ""));
		assertEquals(200, history(otherSale, agreementId, "").status());
	}

	// ==================== HS-4: nothing written ====================

	/** HS-4 (BR-17, S7-D6): a history read writes no access-log, history or outbox row. */
	@Test
	void history_read_writesNoAccessHistoryOrOutboxRow() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));
		Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
		AgreementListFixture.event(jdbc, agreementId, 1, "CREATED", sale.userId(), at);
		AgreementListFixture.event(jdbc, agreementId, 1, "DRAFT_EDITED", sale.userId(), at.plusSeconds(1));
		int bySale = count("select count(*) from brokerage_agreement_access_logs where user_id = ?", sale.userId());
		int byManager = count("select count(*) from brokerage_agreement_access_logs where user_id = ?", manager.userId());
		int outbox = count("select count(*) from outbox_events");

		historyOk(sale, agreementId, "");
		historyOk(manager, agreementId, "?page=0&size=1");

		assertEquals(0, accessRows(agreementId));
		assertEquals(2, events(agreementId));
		assertEquals(bySale, count("select count(*) from brokerage_agreement_access_logs where user_id = ?", sale.userId()));
		assertEquals(byManager, count("select count(*) from brokerage_agreement_access_logs where user_id = ?",
				manager.userId()));
		assertEquals(outbox, count("select count(*) from outbox_events"));
	}

	// ==================== HS-5: the parameters and paging ====================

	/** HS-5 (S7-D13): bounds refused on their parameter, the edges read, wrong types named with no value. */
	@Test
	void history_pageAndSize_boundsAndWrongTypes() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));

		assertValidationFailed(history(sale, agreementId, "?size=101"), "size");
		assertValidationFailed(history(sale, agreementId, "?size=0"), "size");
		assertValidationFailed(history(sale, agreementId, "?page=-1"), "page");
		assertValidationFailed(history(sale, agreementId, "?page=10000"), "page");
		assertEquals(100, historyOk(sale, agreementId, "?size=100").path("size").asInt());
		assertEquals(9999, historyOk(sale, agreementId, "?page=9999&size=1").path("page").asInt());
		for (String query : List.of("?page=x", "?size=x")) {
			Result answer = history(sale, agreementId, query);
			assertValidationFailed(answer, query.substring(1, query.indexOf('=')));
			assertFalse(answer.rawBody().contains("x"), query + " " + answer.rawBody());
		}
	}

	/** HS-5 (S7-D13): a parameter refusal is the same for an agreement the caller cannot see and for an unknown id. */
	@Test
	void history_parameterRefusal_sameForHiddenAndUnknownAgreement() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));

		Result hidden = history(otherSale, agreementId, "?size=0");
		Result unknown = history(otherSale, UNKNOWN, "?size=0");

		assertValidationFailed(hidden, "size");
		assertValidationFailed(unknown, "size");
		assertEquals(hidden.body().path("errors"), unknown.body().path("errors"));
		assertEquals(hidden.body().path("message"), unknown.body().path("message"));
	}

	/** HS-5: pages of two go through five rows without repeat or gap; the count is every row. */
	@Test
	void history_pageOfTwo_noRepeatNoGap() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));
		Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
		List<String> newestFirst = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			AgreementListFixture.event(jdbc, agreementId, index + 1, "DRAFT_EDITED", sale.userId(), at.plusSeconds(index));
			newestFirst.addFirst("DRAFT_EDITED " + (index + 1) + " STAFF " + fullName(sale));
		}

		List<String> paged = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			JsonNode history = historyOk(sale, agreementId, "?page=" + page + "&size=2");
			assertEquals(5, history.path("totalElements").asInt());
			paged.addAll(entries(history));
		}

		assertEquals(newestFirst, paged);
	}

	// ==================== HS-6: an unknown event ====================

	/** HS-6 (S7-D8): a stored event code the server does not know answers OTHER. */
	@Test
	void history_unknownEventCode_answersOther() throws Exception {
		String agreementId = fixture(survey.lead(leader, sale, "ASSIGNED"));
		AgreementListFixture.event(jdbc, agreementId, 1, "SOMETHING_NEW", null,
				Instant.now().truncatedTo(ChronoUnit.MICROS));

		Result answer = history(sale, agreementId, "");

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(List.of("OTHER 1 SYSTEM -"), entries(answer.body()));
		assertFalse(answer.rawBody().contains("SOMETHING_NEW"), answer.rawBody());
	}
}
