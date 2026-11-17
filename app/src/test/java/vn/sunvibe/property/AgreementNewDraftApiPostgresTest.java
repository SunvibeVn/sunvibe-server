package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The new draft version over HTTP on PostgreSQL (brokerage-agreement US-2 AC-3, E-6, E-10; BR-5, BR-16; E05-S5 spec 3.5,
 * S5-D10, ND-1 to ND-3): after the owner's rejection the assigned Sale opens version 2, a copy of version 1, which the
 * E05-S3 edit and the E05-S4 send then act on. The annotations of {@link AgreementSendApiPostgresTest} (those of its
 * base), so both share one context, where the lapse job runs: no version here is ever due (E05-S5 plan E05S5-P2). Needs
 * Docker.
 */
class AgreementNewDraftApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String GROUP_MESSAGE = "This group stops the agreement.";
	/** The columns a new draft copies from the latest version. */
	private static final List<String> CONTENT = List.of("start_date", "end_date", "basis_lead_id", "basis_intake_id",
			"photo_consent", "exterior_photo_consent", "monthly_rent_amount", "deposit_basis", "deposit_months",
			"deposit_amount", "minimum_term_months", "payment_period_months", "mandatory_fees_stated",
			"fee_formula_type", "fee_fixed_amount", "fee_free_reason", "fee_due_days", "renewal_fee_type",
			"renewal_fee_fixed_amount", "early_lease_end_type", "early_lease_end_refund_percent",
			"early_lease_end_within_months", "protection_days");

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0938";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== HTTP ====================

	private Result openDraft(TestUser caller, String agreementId, Object expectedDataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", expectedDataVersion);
		return api.post(caller, AGREEMENTS + "/" + agreementId + "/draft", body);
	}

	// ==================== fixtures ====================

	private record Done(String leadId, TestUser owner, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's complete draft on its unit, sent. */
	private Done sent() throws Exception {
		Passed passed = house(unique("New Draft Street"));
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
		Result createdAnswer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, createdAnswer.status(), createdAnswer.rawBody());
		String id = createdAnswer.body().path("agreementId").asString();
		created.add(id);
		Done done = new Done(passed.leadId(), owner, id);
		saveComplete(sale, id);
		send(sale, id);
		return done;
	}

	/** {@link #sent()} declined by its owner: DRAFTING with version 1 OWNER_REJECTED. */
	private Done rejected() throws Exception {
		Done done = sent();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("versionNo", 1);
		body.put("reason", "The rent is too low.");
		Result answer = api.post(done.owner(), ME + "/" + done.agreementId() + "/rejection", body);
		assertEquals(204, answer.status(), answer.rawBody());
		return done;
	}

	private void saveComplete(TestUser caller, String agreementId) throws Exception {
		Result answer = api.put(caller, AGREEMENTS + "/" + agreementId + "/draft",
				AgreementFeePolicyApiPostgresTest.complete(rowVersion(agreementId)));
		assertEquals(204, answer.status(), answer.rawBody());
	}

	private void send(TestUser caller, String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		Result answer = api.post(caller, AGREEMENTS + "/" + agreementId + "/draft/submission", body);
		assertEquals(204, answer.status(), answer.rawBody());
	}

	// ==================== reading rows ====================

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private Map<String, Object> versionRow(String agreementId, int versionNo) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ? and version_no = ?",
				agreementId, versionNo);
	}

	private List<String> rowsOf(String table, String agreementId, int versionNo) {
		String columns = "brokerage_agreement_version_fees".equals(table) ? "f.fee_no || '|' || f.name || '|' || f.basis"
				: "f.renewal || '|' || f.tier_no || '|' || f.from_months || '|' || f.percent";
		return jdbc.queryForList("select " + columns + " from " + table + " f join brokerage_agreement_versions v on"
				+ " v.id = f.version_id where v.agreement_id = ? and v.version_no = ? order by 1", String.class,
				agreementId, versionNo);
	}

	/** The agreement's history as "event version actor", oldest first. */
	private List<String> events(String agreementId) {
		return jdbc.queryForList("select event_type || ' ' || version_no || ' ' || coalesce(actor_user_id, '-') from"
				+ " brokerage_agreement_events where agreement_id = ? order by occurred_at, id", String.class, agreementId);
	}

	private int versions(String agreementId) {
		return AgreementFixture.rows(jdbc, "brokerage_agreement_versions", agreementId);
	}

	private int notices(TestUser owner) {
		return jdbc.queryForObject("select count(*) from auth_email_notifications where user_id = ?"
				+ " and kind = 'AGREEMENT_PENDING'", Integer.class, owner.userId());
	}

	private static void assertError(Result answer, int status, String code, String errors) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	// ==================== ND-1 ====================

	/** ND-1, Review Focus 5: version 2 is a copy of version 1; the edit and the send then act on it as on a first draft. */
	@Test
	void newDraft_afterRejection_version2CopyThenEditAndSend() throws Exception {
		Done done = rejected();
		Map<String, Object> first = versionRow(done.agreementId(), 1);
		long dataVersion = api.get(sale, AGREEMENTS + "/" + done.agreementId()).body().path("dataVersion").asLong();

		Result answer = openDraft(sale, done.agreementId(), dataVersion);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("", answer.response().body());
		Map<String, Object> second = versionRow(done.agreementId(), 2);
		assertEquals("DRAFT", second.get("status"));
		assertEquals(sale.userId(), second.get("created_by_user_id"));
		assertNull(second.get("sent_at"));
		assertNull(second.get("owner_answer"));
		for (String column : CONTENT) {
			assertEquals(first.get(column), second.get(column), column);
		}
		assertEquals(rowsOf("brokerage_agreement_version_fees", done.agreementId(), 1),
				rowsOf("brokerage_agreement_version_fees", done.agreementId(), 2));
		assertEquals(rowsOf("brokerage_agreement_version_fee_tiers", done.agreementId(), 1),
				rowsOf("brokerage_agreement_version_fee_tiers", done.agreementId(), 2));
		assertEquals(first, versionRow(done.agreementId(), 1));
		assertEquals("DRAFT_OPENED 2 " + sale.userId(), events(done.agreementId()).getLast());
		assertEquals(dataVersion + 1, rowVersion(done.agreementId()));

		JsonNode detail = api.get(sale, AGREEMENTS + "/" + done.agreementId()).body();
		assertEquals("DRAFTING", detail.path("status").asString());
		assertEquals(2, detail.path("versions").size());
		saveComplete(sale, done.agreementId());
		send(sale, done.agreementId());

		assertEquals("AWAITING_OWNER", versionRow(done.agreementId(), 2).get("status"));
		assertEquals(2, notices(done.owner()));
		Result list = api.get(done.owner(), ME);
		JsonNode item = list.body().path("items").path(0);
		assertEquals(done.agreementId(), item.path("agreementId").asString(), list.rawBody());
		assertEquals(List.of("OWNER_REJECTED", "AWAITING_OWNER"), List.of(
				item.path("versions").path(0).path("status").asString(),
				item.path("versions").path(1).path("status").asString()), list.rawBody());
		assertEquals(true, item.path("statement").path("text").asString().startsWith("Version 2 "), list.rawBody());
	}

	// ==================== ND-2 ====================

	@Test
	void newDraft_pendingConfirmedOrDraft_notAllowedNothingWritten() throws Exception {
		Done waiting = sent();
		Done confirmed = sent();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", passwordOf(confirmed.owner()));
		body.put("statementVersion", "brokerage-agreement-2026-11");
		body.put("versions", List.of(Map.of("agreementId", confirmed.agreementId(), "versionNo", 1)));
		assertEquals(204, api.post(confirmed.owner(), ME + "/confirmations", body).status());
		Done draft = rejected();
		assertEquals(204, openDraft(sale, draft.agreementId(), rowVersion(draft.agreementId())).status());

		for (Done done : List.of(waiting, confirmed, draft)) {
			int versionsBefore = versions(done.agreementId());
			List<String> eventsBefore = events(done.agreementId());
			long rowVersion = rowVersion(done.agreementId());

			Result answer = openDraft(sale, done.agreementId(), rowVersion);

			assertError(answer, 409, "AGREEMENT_NEW_DRAFT_NOT_ALLOWED", "[]");
			assertEquals("A new draft can be opened only when no version is pending.",
					answer.body().path("message").asString());
			assertEquals(versionsBefore, versions(done.agreementId()));
			assertEquals(eventsBefore, events(done.agreementId()));
			assertEquals(rowVersion, rowVersion(done.agreementId()));
		}
	}

	@Test
	void newDraft_staleDataVersion_409WithEntry() throws Exception {
		Done done = rejected();
		long rowVersion = rowVersion(done.agreementId());

		Result answer = openDraft(sale, done.agreementId(), rowVersion - 1);

		assertError(answer, 409, "CONCURRENT_MODIFICATION",
				"[{\"field\":\"expectedDataVersion\",\"message\":\"This value is out of date.\"}]");
		assertEquals(1, versions(done.agreementId()));
		assertError(openDraft(sale, done.agreementId(), null), 400, "VALIDATION_FAILED",
				"[{\"field\":\"expectedDataVersion\",\"message\":\"This value is not accepted.\"}]");
	}

	// ==================== ND-3 ====================

	/** ND-3, brokerage-agreement BR-16: only the lead's assigned Sale or Sale leader opens the draft. */
	@Test
	void newDraft_callers_byRoleAndAssignment() throws Exception {
		Done done = rejected();
		TestUser customer = survey.user("New Draft Customer", "CUSTOMER");
		long rowVersion = rowVersion(done.agreementId());

		for (TestUser caller : List.of(leader, manager, admin)) {
			assertError(openDraft(caller, done.agreementId(), rowVersion), 403, "AGREEMENT_NOT_ASSIGNEE", "[]");
		}
		Result hidden = openDraft(otherSale, done.agreementId(), rowVersion);
		assertError(hidden, 404, "NOT_FOUND", "[]");
		for (TestUser caller : List.of(customer, done.owner())) {
			Result answer = openDraft(caller, done.agreementId(), rowVersion);
			assertEquals(403, answer.status(), answer.rawBody());
			assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
		}
		assertEquals(401, openDraft(null, done.agreementId(), rowVersion).status());
		assertError(openDraft(sale, "01JUNKNOWN0000000000000000", rowVersion), 404, "NOT_FOUND", "[]");
		assertEquals(1, versions(done.agreementId()));

		fixture.checklists().reassign(leader, done.leadId(), leader);
		Result byLeader = openDraft(leader, done.agreementId(), rowVersion);

		assertEquals(204, byLeader.status(), byLeader.rawBody());
		assertEquals("DRAFT_OPENED 2 " + leader.userId(), events(done.agreementId()).getLast());
	}

	/** ND-3, brokerage-agreement E-10: a reassignment moves the right at once. */
	@Test
	void newDraft_reassigned_newAssigneeOnly() throws Exception {
		Done done = rejected();
		fixture.checklists().reassign(leader, done.leadId(), otherSale);
		long rowVersion = rowVersion(done.agreementId());

		assertError(openDraft(sale, done.agreementId(), rowVersion), 404, "NOT_FOUND", "[]");
		Result answer = openDraft(otherSale, done.agreementId(), rowVersion);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("DRAFT_OPENED 2 " + otherSale.userId(), events(done.agreementId()).getLast());
	}

	/** ND-3: the E05-S1 check reruns on the latest version's basis. */
	@Test
	void newDraft_stop_reasonGroups() throws Exception {
		Done done = rejected();
		hold(done.leadId());

		Result answer = openDraft(sale, done.agreementId(), rowVersion(done.agreementId()));

		assertError(answer, 409, "AGREEMENT_NOT_ELIGIBLE",
				"[{\"field\":\"reasonGroups.BASIS\",\"message\":\"" + GROUP_MESSAGE + "\"}]");
		assertEquals(1, versions(done.agreementId()));
	}
}
