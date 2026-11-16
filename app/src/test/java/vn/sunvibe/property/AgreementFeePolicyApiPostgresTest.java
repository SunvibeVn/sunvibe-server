package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
 * The fee policy of an agreement's draft over HTTP on PostgreSQL (brokerage-agreement US-1 AC-3, BR-7, BR-16, BR-17;
 * E05-S4 spec 3.1, 3.5, 4, FP-1 to FP-3, SD-11, SD-14): what the PUT stores, its tier rows as the edits move their starts,
 * the bounds of every part with their exact fields, and the detail's answer. The annotations of
 * {@link AgreementDraftApiPostgresTest} (those of its base), so both share one Spring context (E05-S4 plan E05S4-P6).
 * Needs Docker.
 */
class AgreementFeePolicyApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String FIELD_MESSAGE = "This value is not accepted.";
	private static final long MAX = 999_999_999_999_999L;
	private static final List<String> FEE_COLUMNS = List.of("fee_formula_type", "fee_fixed_amount", "fee_free_reason",
			"fee_due_days", "renewal_fee_type", "renewal_fee_fixed_amount", "early_lease_end_type",
			"early_lease_end_refund_percent", "early_lease_end_within_months", "protection_days");

	private final List<String> created = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0943";
	}

	@AfterAll
	void deleteAgreements() {
		AgreementFixture.delete(jdbc, created);
	}

	// ==================== bodies ====================

	static Map<String, Object> formula(String type, List<Map<String, Object>> tiers, Object amount, String currency,
			String freeReason) {
		Map<String, Object> formula = new LinkedHashMap<>();
		formula.put("type", type);
		formula.put("tiers", tiers);
		formula.put("amount", amount);
		formula.put("currency", currency);
		formula.put("freeReason", freeReason);
		return formula;
	}

	static Map<String, Object> tier(Object fromMonths, Object percent) {
		Map<String, Object> tier = new LinkedHashMap<>();
		tier.put("fromMonths", fromMonths);
		tier.put("percent", percent);
		return tier;
	}

	/** Tiers from pairs of (fromMonths, percent). */
	static List<Map<String, Object>> tiers(int... pairs) {
		List<Map<String, Object>> tiers = new ArrayList<>();
		for (int index = 0; index < pairs.length; index += 2) {
			tiers.add(tier(pairs[index], pairs[index + 1]));
		}
		return tiers;
	}

	static Map<String, Object> early(String type, Object refundPercent, Object withinMonths) {
		Map<String, Object> early = new LinkedHashMap<>();
		early.put("type", type);
		early.put("refundPercent", refundPercent);
		early.put("withinMonths", withinMonths);
		return early;
	}

	static Map<String, Object> policy(Map<String, Object> formula, Object dueDays, Map<String, Object> renewal,
			Map<String, Object> earlyLeaseEnd, Object protectionDays) {
		Map<String, Object> policy = new LinkedHashMap<>();
		policy.put("formula", formula);
		policy.put("dueDays", dueDays);
		policy.put("renewal", renewal);
		policy.put("earlyLeaseEnd", earlyLeaseEnd);
		policy.put("protectionDays", protectionDays);
		return policy;
	}

	/** The fee policy of spec section 4's example. */
	static Map<String, Object> examplePolicy() {
		return policy(formula("PERCENT_OF_RENT", tiers(1, 50, 12, 100), null, null, null), 7,
				formula("NOT_CHARGED", null, null, null, null), early("PARTIAL_REFUND", 50, 3), 90);
	}

	/** The complete draft of the criteria: the E05-S3 example terms, photo consent yes, the example fee policy. */
	static Map<String, Object> complete(long dataVersion) {
		Map<String, Object> body = AgreementDraftApiPostgresTest.example(dataVersion);
		body.put("feePolicy", examplePolicy());
		return body;
	}

	private static Map<String, Object> withPolicy(long dataVersion, Map<String, Object> policy) {
		Map<String, Object> body = AgreementDraftApiPostgresTest.example(dataVersion);
		body.put("feePolicy", policy);
		return body;
	}

	private Result put(TestUser caller, String agreementId, Map<String, Object> body) throws Exception {
		return api.put(caller, AGREEMENTS + "/" + agreementId + "/draft", body);
	}

	// ==================== fixtures ====================

	private record Done(String leadId, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's draft on its unit. */
	private Done done() throws Exception {
		Passed passed = house(unique("Fee Street"));
		checked(passed, owner());
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
		return new Done(passed.leadId(), id);
	}

	// ==================== reading rows ====================

	private Map<String, Object> versionRow(String agreementId) {
		return jdbc.queryForMap("select * from brokerage_agreement_versions where agreement_id = ?", agreementId);
	}

	/** The fee columns of the draft's row, by name. */
	private Map<String, Object> feeColumns(String agreementId) {
		Map<String, Object> row = versionRow(agreementId);
		Map<String, Object> columns = new LinkedHashMap<>();
		FEE_COLUMNS.forEach(column -> columns.put(column, row.get(column)));
		return columns;
	}

	/** The draft's tier rows as "renewal|tier_no|from_months|percent", the fee's first, by number. */
	private List<String> tierRows(String agreementId) {
		return jdbc.query("select t.renewal, t.tier_no, t.from_months, t.percent from brokerage_agreement_version_fee_tiers t"
				+ " join brokerage_agreement_versions v on v.id = t.version_id where v.agreement_id = ?"
				+ " order by t.renewal, t.tier_no", (row, index) -> row.getBoolean("renewal") + "|" + row.getInt("tier_no")
						+ "|" + row.getInt("from_months") + "|" + row.getInt("percent"), agreementId);
	}

	private List<String> feeRows(String agreementId) {
		return jdbc.queryForList("select f.fee_no || '|' || f.name from brokerage_agreement_version_fees f"
				+ " join brokerage_agreement_versions v on v.id = f.version_id where v.agreement_id = ? order by f.fee_no",
				String.class, agreementId);
	}

	private List<String> events(String agreementId) {
		return jdbc.queryForList("select event_type from brokerage_agreement_events where agreement_id = ?"
				+ " order by occurred_at, id", String.class, agreementId);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	/** Everything a refused edit must leave as it was. */
	private record Written(Map<String, Object> agreement, Map<String, Object> version, List<String> fees,
			List<String> tiers, List<String> events) {
	}

	private Written written(String agreementId) {
		return new Written(jdbc.queryForMap("select * from brokerage_agreements where id = ?", agreementId),
				versionRow(agreementId), feeRows(agreementId), tierRows(agreementId), events(agreementId));
	}

	private JsonNode readFeePolicy(String agreementId) throws Exception {
		Result answer = api.get(sale, AGREEMENTS + "/" + agreementId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body().path("versions").path(0);
	}

	private static void assertError(Result answer, int status, String code, String errors) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	private static String fieldError(String field) {
		return "[{\"field\":\"" + field + "\",\"message\":\"" + FIELD_MESSAGE + "\"}]";
	}

	// ==================== FP-1: what the PUT stores and the detail answers ====================

	@Test
	void feePolicy_completeDraft_storedAndAnswered() throws Exception {
		Done done = done();

		Result answer = put(sale, done.agreementId(), complete(0));

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> columns = feeColumns(done.agreementId());
		assertEquals("PERCENT_OF_RENT", columns.get("fee_formula_type"));
		assertNull(columns.get("fee_fixed_amount"));
		assertNull(columns.get("fee_free_reason"));
		assertEquals(7, columns.get("fee_due_days"));
		assertEquals("NOT_CHARGED", columns.get("renewal_fee_type"));
		assertNull(columns.get("renewal_fee_fixed_amount"));
		assertEquals("PARTIAL_REFUND", columns.get("early_lease_end_type"));
		assertEquals(50, columns.get("early_lease_end_refund_percent"));
		assertEquals(3, columns.get("early_lease_end_within_months"));
		assertEquals(90, columns.get("protection_days"));
		assertEquals(List.of("false|1|1|50", "false|2|12|100"), tierRows(done.agreementId()));
		assertEquals(List.of("CREATED", "DRAFT_EDITED"), events(done.agreementId()));

		JsonNode version = readFeePolicy(done.agreementId());
		assertEquals(api.json().readTree("{\"payer\":\"OWNER\",\"chargedWhen\":\"LEASE_SIGNED\",\"formula\":{\"type\":"
				+ "\"PERCENT_OF_RENT\",\"tiers\":[{\"fromMonths\":1,\"percent\":50},{\"fromMonths\":12,\"percent\":100}],"
				+ "\"amount\":null,\"currency\":null,\"freeReason\":null},\"dueDays\":7,\"renewal\":{\"type\":"
				+ "\"NOT_CHARGED\",\"tiers\":[],\"amount\":null,\"currency\":null,\"freeReason\":null},\"earlyLeaseEnd\":"
				+ "{\"type\":\"PARTIAL_REFUND\",\"refundPercent\":50,\"withinMonths\":3},\"protectionDays\":90}"),
				version.path("feePolicy"), version.toString());
		assertTrue(version.has("sentAt") && version.path("sentAt").isNull(), version.toString());
	}

	@Test
	void feePolicy_withoutFeePolicy_nothingStored() throws Exception {
		Done done = done();
		JsonNode before = readFeePolicy(done.agreementId());
		assertEquals(204, put(sale, done.agreementId(), complete(0)).status());

		assertEquals(204, put(sale, done.agreementId(), AgreementDraftApiPostgresTest.example(1)).status());
		assertTrue(feeColumns(done.agreementId()).values().stream().allMatch(value -> value == null),
				feeColumns(done.agreementId()).toString());
		assertEquals(List.of(), tierRows(done.agreementId()));

		assertEquals(204, put(sale, done.agreementId(), complete(2)).status());
		assertEquals(204, put(sale, done.agreementId(), withPolicy(3, null)).status());
		assertTrue(feeColumns(done.agreementId()).values().stream().allMatch(value -> value == null));
		assertEquals(List.of(), tierRows(done.agreementId()));

		String none = "{\"payer\":\"OWNER\",\"chargedWhen\":\"LEASE_SIGNED\",\"formula\":null,\"dueDays\":null,"
				+ "\"renewal\":null,\"earlyLeaseEnd\":null,\"protectionDays\":null}";
		assertEquals(api.json().readTree(none), before.path("feePolicy"), before.toString());
		assertEquals(api.json().readTree(none), readFeePolicy(done.agreementId()).path("feePolicy"));
	}

	@Test
	void feePolicy_fixedAndFreeKinds_stored() throws Exception {
		Done done = done();

		assertEquals(204, put(sale, done.agreementId(), withPolicy(0, policy(
				formula("FIXED_AMOUNT", null, 2_000_000, "VND", null), 7,
				formula("PERCENT_OF_RENT", tiers(1, 30), null, null, null), early("NO_REFUND", null, null), 90)))
				.status());
		Map<String, Object> fixed = feeColumns(done.agreementId());
		assertEquals("FIXED_AMOUNT", fixed.get("fee_formula_type"));
		assertEquals(0, new BigDecimal("2000000").compareTo((BigDecimal) fixed.get("fee_fixed_amount")));
		assertEquals("PERCENT_OF_RENT", fixed.get("renewal_fee_type"));
		assertEquals(List.of("true|1|1|30"), tierRows(done.agreementId()));
		JsonNode fixedPolicy = readFeePolicy(done.agreementId()).path("feePolicy");
		assertEquals(api.json().readTree("{\"type\":\"FIXED_AMOUNT\",\"tiers\":[],\"amount\":2000000,\"currency\":\"VND\","
				+ "\"freeReason\":null}"), fixedPolicy.path("formula"));

		assertEquals(204, put(sale, done.agreementId(), withPolicy(1, policy(
				formula("FREE", null, null, null, "  First unit with Sunvibe  "), 7,
				formula("FIXED_AMOUNT", null, 1_000_000, "VND", null), early("NO_REFUND", null, null), 90))).status());
		Map<String, Object> free = feeColumns(done.agreementId());
		assertEquals("FREE", free.get("fee_formula_type"));
		assertEquals("First unit with Sunvibe", free.get("fee_free_reason"));
		assertNull(free.get("fee_fixed_amount"));
		assertEquals("FIXED_AMOUNT", free.get("renewal_fee_type"));
		assertEquals(0, new BigDecimal("1000000").compareTo((BigDecimal) free.get("renewal_fee_fixed_amount")));
		assertEquals(List.of(), tierRows(done.agreementId()));
		JsonNode freePolicy = readFeePolicy(done.agreementId()).path("feePolicy");
		assertEquals(api.json().readTree("{\"type\":\"FREE\",\"tiers\":[],\"amount\":null,\"currency\":null,"
				+ "\"freeReason\":\"First unit with Sunvibe\"}"), freePolicy.path("formula"));
		assertEquals(api.json().readTree("{\"type\":\"FIXED_AMOUNT\",\"tiers\":[],\"amount\":1000000,\"currency\":\"VND\","
				+ "\"freeReason\":null}"), freePolicy.path("renewal"));
	}

	/** Spec S4-D4: edits that move a start from one row to another each store exactly the rows sent. */
	@Test
	void feePolicy_tierStartsMoved_exactlyTheRowsSent() throws Exception {
		Done done = done();
		List<List<Map<String, Object>>> lists = List.of(tiers(1, 50, 12, 100), tiers(1, 50, 6, 75, 12, 100),
				tiers(1, 50, 12, 100, 24, 150), tiers(1, 50, 12, 100));
		List<List<String>> fee = List.of(List.of("false|1|1|50", "false|2|12|100"),
				List.of("false|1|1|50", "false|2|6|75", "false|3|12|100"),
				List.of("false|1|1|50", "false|2|12|100", "false|3|24|150"),
				List.of("false|1|1|50", "false|2|12|100"));
		long dataVersion = 0;
		for (int index = 0; index < lists.size(); index++) {
			Result answer = put(sale, done.agreementId(), withPolicy(dataVersion++, policy(
					formula("PERCENT_OF_RENT", lists.get(index), null, null, null), 7, null, null, null)));
			assertEquals(204, answer.status(), index + ": " + answer.rawBody());
			assertEquals(fee.get(index), tierRows(done.agreementId()), "fee " + index);
		}
		for (int index = 0; index < lists.size(); index++) {
			Result answer = put(sale, done.agreementId(), withPolicy(dataVersion++, policy(
					formula("PERCENT_OF_RENT", tiers(1, 50, 12, 100), null, null, null), 7,
					formula("PERCENT_OF_RENT", lists.get(index), null, null, null), null, null)));
			assertEquals(204, answer.status(), index + ": " + answer.rawBody());
			List<String> expected = new ArrayList<>(List.of("false|1|1|50", "false|2|12|100"));
			fee.get(index).forEach(row -> expected.add(row.replace("false|", "true|")));
			assertEquals(expected, tierRows(done.agreementId()), "renewal " + index);
		}
	}

	// ==================== FP-2: bounds, with the exact field and nothing written ====================

	@Test
	void feePolicy_boundaries_acceptedAndRefusedWithTheField() throws Exception {
		Done done = done();
		String id = done.agreementId();
		List<Map<String, Object>> accepted = List.of(
				policy(formula("PERCENT_OF_RENT", tiers(1, 1), null, null, null), null, null, null, null),
				policy(formula("PERCENT_OF_RENT", tiers(1, 300), null, null, null), null, null, null, null),
				policy(formula("PERCENT_OF_RENT", tiers(1, 10, 12, 20, 24, 30, 36, 40, 48, 50), null, null, null), null,
						null, null, null),
				policy(formula("PERCENT_OF_RENT", tiers(1, 10, 120, 20), null, null, null), null, null, null, null),
				policy(formula("FIXED_AMOUNT", null, 1, "VND", null), null, null, null, null),
				policy(formula("FIXED_AMOUNT", null, MAX, "VND", null), null, null, null, null),
				policy(null, 0, null, null, null),
				policy(null, 30, null, null, null),
				policy(null, null, null, null, 0),
				policy(null, null, null, null, 180),
				policy(null, null, null, early("PARTIAL_REFUND", 1, 1), null),
				policy(null, null, null, early("PARTIAL_REFUND", 100, 12), null),
				policy(formula("FREE", null, null, null, "a".repeat(500)), null, null, null, null));
		for (int index = 0; index < accepted.size(); index++) {
			Result answer = put(sale, id, withPolicy(index, accepted.get(index)));
			assertEquals(204, answer.status(), index + ": " + answer.rawBody());
			if (index == 5) {
				assertEquals(BigDecimal.valueOf(MAX), feeColumns(id).get("fee_fixed_amount"));
			}
		}
		assertEquals("a".repeat(500), feeColumns(id).get("fee_free_reason"));
		long now = accepted.size();
		assertEquals(now, rowVersion(id));

		List<Map.Entry<String, Map<String, Object>>> refused = List.of(
				Map.entry("feePolicy.formula.tiers[0].percent", policy(formula("PERCENT_OF_RENT", tiers(1, 0), null, null,
						null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers[0].percent", policy(formula("PERCENT_OF_RENT", tiers(1, 301), null,
						null, null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers", policy(formula("PERCENT_OF_RENT",
						tiers(1, 10, 12, 20, 24, 30, 36, 40, 48, 50, 60, 60), null, null, null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers[0].fromMonths", policy(formula("PERCENT_OF_RENT", tiers(2, 50), null,
						null, null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers[2].fromMonths", policy(formula("PERCENT_OF_RENT",
						tiers(1, 50, 12, 60, 12, 70), null, null, null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers[2].fromMonths", policy(formula("PERCENT_OF_RENT",
						tiers(1, 50, 12, 60, 6, 70), null, null, null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers[0].fromMonths", policy(formula("PERCENT_OF_RENT", tiers(0, 50), null,
						null, null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers[1].fromMonths", policy(formula("PERCENT_OF_RENT", tiers(1, 50, 121, 60),
						null, null, null), null, null, null, null)),
				Map.entry("feePolicy.formula.tiers", policy(formula("PERCENT_OF_RENT", null, null, null, null), null, null,
						null, null)),
				Map.entry("feePolicy.formula.tiers", policy(formula("PERCENT_OF_RENT", List.of(), null, null, null), null,
						null, null, null)),
				Map.entry("feePolicy.formula.amount", policy(formula("PERCENT_OF_RENT", tiers(1, 50), 1_000, "VND", null),
						null, null, null, null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("PERCENT_OF_RENT", tiers(1, 50), null, null,
						"Reason"), null, null, null, null)),
				Map.entry("feePolicy.formula.amount", policy(formula("FIXED_AMOUNT", null, 0, "VND", null), null, null,
						null, null)),
				Map.entry("feePolicy.formula.amount", policy(formula("FIXED_AMOUNT", null, MAX + 1, "VND", null), null,
						null, null, null)),
				Map.entry("feePolicy.formula.currency", policy(formula("FIXED_AMOUNT", null, 1_000, null, null), null,
						null, null, null)),
				Map.entry("feePolicy.formula.tiers", policy(formula("FIXED_AMOUNT", tiers(1, 50), 1_000, "VND", null),
						null, null, null, null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("FREE", null, null, null, null), null, null, null,
						null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("FREE", null, null, null, "   "), null, null, null,
						null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("FREE", null, null, null, "a".repeat(501)), null,
						null, null, null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("FREE", null, null, null, "Fir\u0000st"), null,
						null, null, null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("FREE", null, null, null, "Fir\u007Fst"), null,
						null, null, null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("FREE", null, null, null, "Fir\u0085st"), null,
						null, null, null)),
				Map.entry("feePolicy.formula.freeReason", policy(formula("FREE", null, null, null, "Fir‮st"), null,
						null, null, null)),
				Map.entry("feePolicy.formula.amount", policy(formula("FREE", null, 1_000, "VND", "Reason"), null, null, null,
						null)),
				Map.entry("feePolicy.renewal.amount", policy(null, null, formula("NOT_CHARGED", null, 1_000, "VND", null),
						null, null)),
				Map.entry("feePolicy.dueDays", policy(null, -1, null, null, null)),
				Map.entry("feePolicy.dueDays", policy(null, 31, null, null, null)),
				Map.entry("feePolicy.protectionDays", policy(null, null, null, null, -1)),
				Map.entry("feePolicy.protectionDays", policy(null, null, null, null, 181)),
				Map.entry("feePolicy.earlyLeaseEnd.refundPercent", policy(null, null, null,
						early("PARTIAL_REFUND", null, 3), null)),
				Map.entry("feePolicy.earlyLeaseEnd.refundPercent", policy(null, null, null,
						early("PARTIAL_REFUND", 0, 3), null)),
				Map.entry("feePolicy.earlyLeaseEnd.refundPercent", policy(null, null, null,
						early("PARTIAL_REFUND", 101, 3), null)),
				Map.entry("feePolicy.earlyLeaseEnd.withinMonths", policy(null, null, null,
						early("PARTIAL_REFUND", 50, 0), null)),
				Map.entry("feePolicy.earlyLeaseEnd.withinMonths", policy(null, null, null,
						early("PARTIAL_REFUND", 50, 13), null)),
				Map.entry("feePolicy.earlyLeaseEnd.refundPercent", policy(null, null, null, early("NO_REFUND", 50, null),
						null)));
		for (Map.Entry<String, Map<String, Object>> refusal : refused) {
			Written before = written(id);

			assertError(put(sale, id, withPolicy(now, refusal.getValue())), 400, "VALIDATION_FAILED",
					fieldError(refusal.getKey()));

			assertEquals(before, written(id), refusal.getKey() + " " + refusal.getValue());
		}
	}

	@Test
	void feePolicy_typeOutsideItsPlace_allowedValuesOfThePlace() throws Exception {
		Done done = done();
		String id = done.agreementId();
		String renewalTypes = "[\"NOT_CHARGED\",\"PERCENT_OF_RENT\",\"FIXED_AMOUNT\"]";
		String feeTypes = "[\"PERCENT_OF_RENT\",\"FIXED_AMOUNT\",\"FREE\"]";
		Map<String, Object> percent = formula("PERCENT_OF_RENT", tiers(1, 50), null, null, null);
		List<Object[]> cases = List.of(
				new Object[] {"feePolicy.renewal.type", renewalTypes, policy(percent, 7,
						formula("FREE", null, null, null, "Reason"), null, null)},
				new Object[] {"feePolicy.renewal.type", renewalTypes, policy(percent, 7,
						formula("MONTHLY", null, null, null, null), null, null)},
				new Object[] {"feePolicy.formula.type", feeTypes, policy(formula("NOT_CHARGED", null, null, null, null), 7,
						null, null, null)},
				new Object[] {"feePolicy.formula.type", feeTypes, policy(formula("MONTHLY", null, null, null, null), 7,
						null, null, null)},
				new Object[] {"feePolicy.earlyLeaseEnd.type", "[\"NO_REFUND\",\"PARTIAL_REFUND\"]", policy(percent, 7,
						null, early("FULL_REFUND", null, null), null)});
		for (Object[] refusal : cases) {
			Written before = written(id);
			@SuppressWarnings("unchecked") // the cases hold a policy body
			Map<String, Object> policy = (Map<String, Object>) refusal[2];

			Result answer = put(sale, id, withPolicy(0, policy));

			assertEquals(400, answer.status(), answer.rawBody());
			assertEquals("VALIDATION_FAILED", answer.body().path("code").asString());
			JsonNode errors = answer.body().path("errors");
			assertEquals(1, errors.size(), answer.rawBody());
			assertEquals(refusal[0], errors.path(0).path("field").asString(), answer.rawBody());
			assertEquals(FIELD_MESSAGE, errors.path(0).path("message").asString());
			assertEquals(refusal[1], errors.path(0).path("allowedValues").toString(), answer.rawBody());
			assertEquals(before, written(id));
		}

		Written before = written(id);
		assertError(put(sale, id, withPolicy(0, policy(formula(null, tiers(1, 50), null, null, null), 7, null, null,
				null))), 400, "VALIDATION_FAILED", fieldError("feePolicy.formula.type"));
		assertEquals(before, written(id));
	}

	@Test
	void feePolicy_unreadableNumbers_malformed() throws Exception {
		Done done = done();
		String id = done.agreementId();
		List<Map<String, Object>> unreadable = List.of(
				policy(formula("PERCENT_OF_RENT", List.of(tier(1, 50.5)), null, null, null), null, null, null, null),
				policy(formula("PERCENT_OF_RENT", List.of(tier(1, "fifty")), null, null, null), null, null, null, null),
				policy(formula("FIXED_AMOUNT", null, 2_000_000.5, "VND", null), null, null, null, null),
				policy(formula("FIXED_AMOUNT", null, "abc", "VND", null), null, null, null, null),
				policy(null, 7.5, null, null, null),
				policy(null, null, null, null, "ninety"));

		for (Map<String, Object> policy : unreadable) {
			Written before = written(id);

			assertError(put(sale, id, withPolicy(0, policy)), 400, "MALFORMED_REQUEST", "[]");

			assertEquals(before, written(id), policy.toString());
		}
		assertEquals(0L, rowVersion(id));
	}

	// ==================== FP-3: the E05-S3 refusals with a fee policy in the body ====================

	@Test
	void feePolicy_draftRefusals_unchanged() throws Exception {
		Done done = done();
		String id = done.agreementId();
		Written before = written(id);

		assertError(put(sale, id, complete(3)), 409, "CONCURRENT_MODIFICATION",
				"[{\"field\":\"expectedDataVersion\",\"message\":\"This value is out of date.\"}]");
		assertError(put(otherSale, id, complete(0)), 404, "NOT_FOUND", "[]");
		assertError(put(manager, id, complete(0)), 403, "AGREEMENT_NOT_ASSIGNEE", "[]");
		assertEquals(before, written(id));

		Passed passed = house(unique("Sent Fee Street"));
		String awaiting = AgreementFixture.insert(jdbc, ids, ids.generateId(), passed.leadId(), "DRAFTING",
				"AWAITING_OWNER");
		created.add(awaiting);
		Written awaitingBefore = written(awaiting);
		assertError(put(sale, awaiting, complete(0)), 409, "AGREEMENT_NOT_DRAFT", "[]");
		assertEquals(awaitingBefore, written(awaiting));
	}

	/** brokerage-agreement BR-17 (SD-14): no error body quotes an amount, a percentage or a reason sent. */
	@Test
	void feePolicy_refusals_quoteNoValue() throws Exception {
		Done done = done();
		List<Map<String, Object>> policies = List.of(
				policy(formula("FIXED_AMOUNT", null, 123_456_789, null, null), null, null, null, null),
				policy(formula("FIXED_AMOUNT", null, 123_456_789, "VND", "Zebra reason"), null, null, null, null),
				policy(formula("FREE", null, null, null, "Zebra\u0000reason"), null, null, null, null),
				policy(formula("PERCENT_OF_RENT", tiers(2, 287), null, null, null), null, null, null, null),
				policy(formula("PERCENT_OF_RENT", tiers(1, 287, 1, 288), null, null, null), null, null, null, null),
				policy(formula("PERCENT_OF_RENT", tiers(1, 287), null, null, null), null,
						formula("FREE", null, null, null, "Zebra reason"), null, null),
				policy(null, null, null, early("PARTIAL_REFUND", 99, 77), null));

		for (Map<String, Object> policy : policies) {
			Result answer = put(sale, done.agreementId(), withPolicy(0, policy));

			assertTrue(answer.status() >= 400, answer.rawBody());
			for (String value : List.of("123456789", "Zebra", "287", "288", "99", "77")) {
				assertFalse(answer.rawBody().contains(value), value + " in " + answer.rawBody());
			}
		}
	}
}
