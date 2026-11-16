package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Editing the draft of a brokerage agreement over HTTP on PostgreSQL (brokerage-agreement US-1 AC-3, AC-5; BR-5, BR-6,
 * BR-8, BR-9, BR-17, BR-18; E05-S3 spec 3.1, 4, ED-1, ED-3, ED-4, ED-6 to ED-8, ED-12, ED-17): the content written, the
 * refusals with their exact fields, and nothing written on a refusal. In the property test package to share the intake
 * fixtures and their Spring context (plan decision E05S3-P2). Needs Docker.
 */
class AgreementDraftApiPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String WARD_NAME = "Hải Châu";
	private static final String GROUP_MESSAGE = "This group stops the agreement.";
	private static final String FIELD_MESSAGE = "This value is not accepted.";
	private static final long MAX = 999_999_999_999_999L;

	private final List<String> created = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0948";
	}

	@AfterAll
	void deleteAgreements() {
		AgreementFixture.delete(jdbc, created);
	}

	// ==================== HTTP and bodies ====================

	private Result put(TestUser caller, String agreementId, Map<String, Object> body) throws Exception {
		return api.put(caller, AGREEMENTS + "/" + agreementId + "/draft", body);
	}

	/** The assigned Sale's draft of the unit; the answer must be 201. */
	private String drafted(String leadId, String unitId) throws Exception {
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

	/** The example body of spec section 4 with this data version. */
	static Map<String, Object> example(long dataVersion) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", dataVersion);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		body.put("photoConsent", true);
		body.put("exteriorPhotoConsent", false);
		Map<String, Object> terms = new LinkedHashMap<>();
		terms.put("monthlyRent", rent(8_000_000L));
		terms.put("deposit", deposit("MONTHS", 2, null, null));
		terms.put("minimumTermMonths", 12);
		terms.put("paymentPeriodMonths", 1);
		terms.put("mandatoryFees", new ArrayList<>(List.of(fee("Electricity", "AMOUNT", 3_500L, "VND", "PER_KWH"),
				fee("Water", "PROVIDER_TARIFF", null, null, null))));
		body.put("terms", terms);
		return body;
	}

	/** A body with every part of the terms null, the consent not answered. */
	static Map<String, Object> empty(long dataVersion) {
		Map<String, Object> body = example(dataVersion);
		body.put("photoConsent", null);
		Map<String, Object> terms = new LinkedHashMap<>();
		terms.put("monthlyRent", null);
		terms.put("deposit", null);
		terms.put("minimumTermMonths", null);
		terms.put("paymentPeriodMonths", null);
		terms.put("mandatoryFees", null);
		body.put("terms", terms);
		return body;
	}

	static Map<String, Object> rent(Object amount) {
		Map<String, Object> rent = new LinkedHashMap<>();
		rent.put("amount", amount);
		rent.put("currency", "VND");
		rent.put("unit", "MONTH");
		return rent;
	}

	static Map<String, Object> deposit(String basis, Object months, Object amount, String currency) {
		Map<String, Object> deposit = new LinkedHashMap<>();
		deposit.put("basis", basis);
		deposit.put("months", months);
		deposit.put("amount", amount);
		deposit.put("currency", currency);
		return deposit;
	}

	static Map<String, Object> fee(String name, String basis, Object amount, String currency, String chargeUnit) {
		Map<String, Object> fee = new LinkedHashMap<>();
		fee.put("name", name);
		fee.put("basis", basis);
		fee.put("amount", amount);
		fee.put("currency", currency);
		fee.put("chargeUnit", chargeUnit);
		return fee;
	}

	/** The example body with one change made to it. */
	private static Map<String, Object> changed(long dataVersion, Consumer<Map<String, Object>> change) {
		Map<String, Object> body = example(dataVersion);
		change.accept(body);
		return body;
	}

	@SuppressWarnings("unchecked") // the example body's terms are a map of this class
	private static Map<String, Object> terms(Map<String, Object> body) {
		return (Map<String, Object>) body.get("terms");
	}

	// ==================== fixtures ====================

	private record Done(String leadId, TestUser owner, String propertyId, String unitId, String agreementId) {
	}

	/** A house lead of {@code sale} with a completed intake, and the Sale's draft on its unit. */
	private Done done() throws Exception {
		Passed passed = house(unique("Draft Street"));
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String unit = unitOf(passed.leadId());
		return new Done(passed.leadId(), owner, propertyId, unit, drafted(passed.leadId(), unit));
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

	/** The draft's fee rows as "fee_no|name|basis|amount|charge_unit", by number. */
	private List<String> feeRows(String agreementId) {
		return jdbc.query("select f.fee_no, f.name, f.basis, f.amount, f.charge_unit from brokerage_agreement_version_fees f"
				+ " join brokerage_agreement_versions v on v.id = f.version_id where v.agreement_id = ? order by f.fee_no",
				(row, index) -> row.getInt("fee_no") + "|" + row.getString("name") + "|" + row.getString("basis") + "|"
						+ (row.getBigDecimal("amount") == null ? "null" : row.getBigDecimal("amount").toPlainString())
						+ "|" + row.getString("charge_unit"), agreementId);
	}

	/** The agreement's history as "event version actor", oldest first. */
	private List<String> events(String agreementId) {
		return jdbc.queryForList("select event_type || ' ' || version_no || ' ' || actor_user_id from"
				+ " brokerage_agreement_events where agreement_id = ? order by occurred_at, id", String.class, agreementId);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	/** Everything a refused edit must leave as it was. */
	private record Written(Map<String, Object> agreement, Map<String, Object> version, List<String> fees,
			List<String> events) {
	}

	private Written written(String agreementId) {
		return new Written(agreementRow(agreementId), versionRow(agreementId), feeRows(agreementId), events(agreementId));
	}

	private static void assertError(Result answer, int status, String code, String errors) {
		assertEquals(status, answer.status(), answer.rawBody());
		assertEquals(code, answer.body().path("code").asString(), answer.rawBody());
		assertEquals(errors, answer.body().path("errors").toString(), answer.rawBody());
	}

	private static String fieldError(String field) {
		return "[{\"field\":\"" + field + "\",\"message\":\"" + FIELD_MESSAGE + "\"}]";
	}

	/** A 400 on exactly this field, and nothing written. */
	private void assertRefusedField(Done done, Map<String, Object> body, String field) throws Exception {
		Written before = written(done.agreementId());

		assertError(put(sale, done.agreementId(), body), 400, "VALIDATION_FAILED", fieldError(field));

		assertEquals(before, written(done.agreementId()), field);
	}

	private void assertNotEligible(Done done, String... groups) throws Exception {
		Written before = written(done.agreementId());
		String errors = Arrays.stream(groups)
				.map(group -> "{\"field\":\"reasonGroups." + group + "\",\"message\":\"" + GROUP_MESSAGE + "\"}")
				.reduce((left, right) -> left + "," + right).orElse("");

		Result answer = put(sale, done.agreementId(), example(rowVersion(done.agreementId())));

		assertError(answer, 409, "AGREEMENT_NOT_ELIGIBLE", "[" + errors + "]");
		assertEquals("This unit cannot get an agreement now.", answer.body().path("message").asString());
		assertEquals(before, written(done.agreementId()));
	}

	// ==================== ED-1: the content written ====================

	@Test
	void draft_assignedSale_writesContentFeesAndOneEvent() throws Exception {
		Done done = done();
		Map<String, Object> agreementBefore = agreementRow(done.agreementId());
		Map<String, Object> versionBefore = versionRow(done.agreementId());

		Result answer = put(sale, done.agreementId(), example(0));

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals("", answer.response().body());
		Map<String, Object> version = versionRow(done.agreementId());
		assertEquals(LocalDate.parse("2027-01-01"), ((Date) version.get("start_date")).toLocalDate());
		assertEquals(LocalDate.parse("2027-12-31"), ((Date) version.get("end_date")).toLocalDate());
		assertEquals(true, version.get("photo_consent"));
		assertEquals(false, version.get("exterior_photo_consent"));
		assertEquals(0, new BigDecimal("8000000").compareTo((BigDecimal) version.get("monthly_rent_amount")));
		assertEquals("MONTHS", version.get("deposit_basis"));
		assertEquals(2, version.get("deposit_months"));
		assertNull(version.get("deposit_amount"));
		assertEquals(12, version.get("minimum_term_months"));
		assertEquals(1, version.get("payment_period_months"));
		assertEquals(true, version.get("mandatory_fees_stated"));
		assertEquals(List.of("1|Electricity|AMOUNT|3500|PER_KWH", "2|Water|PROVIDER_TARIFF|null|null"),
				feeRows(done.agreementId()));
		assertEquals(1L, rowVersion(done.agreementId()));
		assertEquals(List.of("CREATED 1 " + sale.userId(), "DRAFT_EDITED 1 " + sale.userId()), events(done.agreementId()));
		assertEquals(0, AgreementFixture.rows(jdbc, "brokerage_agreement_access_logs", done.agreementId()));
		Map<String, Object> agreement = agreementRow(done.agreementId());
		for (String column : List.of("unit_id", "unit_code", "property_id", "owner_user_id", "basis_lead_id",
				"transaction_type", "status", "created_by_user_id", "created_at")) {
			assertEquals(agreementBefore.get(column), agreement.get(column), column);
		}
		assertTrue(((Timestamp) agreement.get("updated_at")).after((Timestamp) agreementBefore.get("updated_at")));
		for (String column : List.of("id", "version_no", "status", "basis_lead_id", "basis_intake_id",
				"created_by_user_id", "created_at")) {
			assertEquals(versionBefore.get(column), version.get(column), column);
		}
	}

	@Test
	void draft_secondSaveAllNull_noFeesNotStated() throws Exception {
		Done done = done();
		assertEquals(204, put(sale, done.agreementId(), example(0)).status());

		Result answer = put(sale, done.agreementId(), empty(1));

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> version = versionRow(done.agreementId());
		for (String column : List.of("photo_consent", "monthly_rent_amount", "deposit_basis", "deposit_months",
				"deposit_amount", "minimum_term_months", "payment_period_months")) {
			assertNull(version.get(column), column);
		}
		assertEquals(false, version.get("exterior_photo_consent"));
		assertEquals(false, version.get("mandatory_fees_stated"));
		assertEquals(List.of(), feeRows(done.agreementId()));
		assertEquals(2L, rowVersion(done.agreementId()));
		assertEquals(List.of("CREATED 1 " + sale.userId(), "DRAFT_EDITED 1 " + sale.userId(),
				"DRAFT_EDITED 1 " + sale.userId()), events(done.agreementId()));
	}

	/**
	 * brokerage-agreement E05-S4 pin (U-8): the E05-S3 body, which holds no fee policy, still saves and reads back the
	 * same content. Named columns and keys only, so that columns added later do not change this test.
	 */
	@Test
	void draft_bodyWithoutFeePolicy_storesAsToday() throws Exception {
		Done done = done();

		Result answer = put(sale, done.agreementId(), example(0));

		assertEquals(204, answer.status(), answer.rawBody());
		Map<String, Object> version = versionRow(done.agreementId());
		assertEquals(true, version.get("photo_consent"));
		assertEquals(false, version.get("exterior_photo_consent"));
		assertEquals(0, new BigDecimal("8000000").compareTo((BigDecimal) version.get("monthly_rent_amount")));
		assertEquals("MONTHS", version.get("deposit_basis"));
		assertEquals(2, version.get("deposit_months"));
		assertNull(version.get("deposit_amount"));
		assertEquals(12, version.get("minimum_term_months"));
		assertEquals(1, version.get("payment_period_months"));
		assertEquals(true, version.get("mandatory_fees_stated"));
		assertEquals(List.of("1|Electricity|AMOUNT|3500|PER_KWH", "2|Water|PROVIDER_TARIFF|null|null"),
				feeRows(done.agreementId()));
		assertEquals(List.of("CREATED 1 " + sale.userId(), "DRAFT_EDITED 1 " + sale.userId()), events(done.agreementId()));

		Result detail = api.get(sale, AGREEMENTS + "/" + done.agreementId());

		assertEquals(200, detail.status(), detail.rawBody());
		JsonNode read = detail.body().path("versions").path(0);
		assertEquals(true, read.path("photoConsent").asBoolean(), detail.rawBody());
		assertEquals(false, read.path("exteriorPhotoConsent").asBoolean(), detail.rawBody());
		assertEquals(api.json().readTree("{\"monthlyRent\":{\"amount\":8000000,\"currency\":\"VND\",\"unit\":\"MONTH\"},"
				+ "\"deposit\":{\"basis\":\"MONTHS\",\"months\":2,\"amount\":null,\"currency\":null},"
				+ "\"minimumTermMonths\":12,\"paymentPeriodMonths\":1,\"mandatoryFees\":["
				+ "{\"name\":\"Electricity\",\"basis\":\"AMOUNT\",\"amount\":3500,\"currency\":\"VND\",\"chargeUnit\":\"PER_KWH\"},"
				+ "{\"name\":\"Water\",\"basis\":\"PROVIDER_TARIFF\",\"amount\":null,\"currency\":null,\"chargeUnit\":null}]}"),
				read.path("terms"), detail.rawBody());
	}

	// ==================== ED-3: only a draft is edited ====================

	@Test
	void draft_awaitingOwnerOrCancelled_notDraft() throws Exception {
		Passed passed = house(unique("Not Draft Street"));
		for (String[] state : List.of(new String[] {"DRAFTING", "AWAITING_OWNER"}, new String[] {"CANCELLED", "CANCELLED"})) {
			String id = AgreementFixture.insert(jdbc, ids, ids.generateId(), passed.leadId(), state[0], state[1]);
			created.add(id);
			Written before = written(id);

			Result answer = put(sale, id, example(0));

			assertError(answer, 409, "AGREEMENT_NOT_DRAFT", "[]");
			assertEquals("This agreement has no draft to edit.", answer.body().path("message").asString());
			assertEquals(before, written(id), state[0]);
		}
	}

	// ==================== ED-4: the E05-S1 check on the draft's own basis ====================

	@Test
	void draft_eligibilityGroups_notEligibleAndNothingWritten() throws Exception {
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

		String street = unique("Two Leads");
		Passed first = house(street);
		TestUser owner = owner();
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
				drafted(second.leadId(), unit));
		suspendLead(first.leadId(), null, "Data request");
		assertNotEligible(linked, "PROPERTY");

		String buildingStreet = unique("Two Leads Building");
		Passed building = fixture.parts(leader, sale, "WHOLE_BUILDING", buildingStreet, "P101", "P102");
		TestUser buildingOwner = owner();
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
		Done room = new Done(rooms.leadId(), buildingOwner, buildingProperty, p101, drafted(rooms.leadId(), p101));
		suspendLead(building.leadId(), p101, "Room only");
		assertNotEligible(room, "UNIT");
	}

	/** D9-18: an availability that differs from the survey stops nothing. */
	@Test
	void draft_availabilityDiffersOnly_saved() throws Exception {
		Passed passed = house(unique("Link Street"));
		TestUser owner = owner();
		checked(passed, owner);
		String address = jdbc.queryForObject("select address_norm from inventory_intakes where lead_id = ?", String.class,
				passed.leadId());
		String propertyId = property(owner.userId(), WARD, WARD_NAME, address, "HOUSE", address);
		ownership(propertyId, owner.userId());
		String unit = unit(propertyId, "U1", "OCCUPIED");
		SurveyApiTestSupport.ok(linkTarget(passed.leadId(), propertyId, passed.spaceIds(), List.of(unit)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String id = drafted(passed.leadId(), unitOf(passed.leadId()));

		Result answer = put(sale, id, example(0));

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(1L, rowVersion(id));
	}

	// ==================== ED-6: the terms' rules and bounds ====================

	@Test
	void draft_termBoundaries_acceptedAndRefusedWithTheField() throws Exception {
		Done done = done();
		String id = done.agreementId();
		List<Consumer<Map<String, Object>>> accepted = List.of(
				body -> body.putAll(empty(0)),
				body -> terms(body).put("deposit", deposit("MONTHS", 0, null, null)),
				body -> terms(body).put("deposit", deposit("AMOUNT", null, 0, "VND")),
				body -> terms(body).put("monthlyRent", rent(1L)),
				body -> terms(body).put("monthlyRent", rent(MAX)),
				body -> terms(body).put("minimumTermMonths", 1),
				body -> terms(body).put("minimumTermMonths", 120),
				body -> terms(body).put("paymentPeriodMonths", 12),
				body -> terms(body).put("mandatoryFees", List.of()),
				body -> terms(body).put("mandatoryFees", fees(20)),
				body -> terms(body).put("mandatoryFees", List.of(fee("a".repeat(100), "INCLUDED", null, null, null))),
				body -> terms(body).put("mandatoryFees", List.of(fee("  Water  ", "INCLUDED", null, null, null))));
		for (int i = 0; i < accepted.size(); i++) {
			long dataVersion = i;
			Result answer = put(sale, id, changed(dataVersion, body -> {
				accepted.get((int) dataVersion).accept(body);
				body.put("expectedDataVersion", dataVersion);
			}));
			assertEquals(204, answer.status(), i + ": " + answer.rawBody());
			if (i == 4) {
				assertEquals(BigDecimal.valueOf(MAX), versionRow(id).get("monthly_rent_amount"));
			}
		}
		assertEquals(List.of("1|Water|INCLUDED|null|null"), feeRows(id));
		long now = accepted.size();
		assertEquals(now, rowVersion(id));

		List<Map.Entry<String, Consumer<Map<String, Object>>>> refused = List.of(
				Map.entry("terms.monthlyRent.amount", body -> terms(body).put("monthlyRent", rent(0L))),
				Map.entry("terms.monthlyRent.amount", body -> terms(body).put("monthlyRent", rent(MAX + 1))),
				Map.entry("terms.monthlyRent.currency", body -> rentOf(body).put("currency", "USD")),
				Map.entry("terms.monthlyRent.unit", body -> rentOf(body).put("unit", "WEEK")),
				Map.entry("terms.deposit.months", body -> terms(body).put("deposit", deposit("MONTHS", 13, null, null))),
				Map.entry("terms.deposit.amount", body -> terms(body).put("deposit", deposit("MONTHS", 2, 1_000, "VND"))),
				Map.entry("terms.deposit.amount", body -> terms(body).put("deposit", deposit("AMOUNT", null, -1, "VND"))),
				Map.entry("terms.deposit.currency", body -> terms(body).put("deposit",
						deposit("AMOUNT", null, 1_000, null))),
				Map.entry("terms.minimumTermMonths", body -> terms(body).put("minimumTermMonths", 0)),
				Map.entry("terms.minimumTermMonths", body -> terms(body).put("minimumTermMonths", 121)),
				Map.entry("terms.paymentPeriodMonths", body -> terms(body).put("paymentPeriodMonths", 0)),
				Map.entry("terms.paymentPeriodMonths", body -> terms(body).put("paymentPeriodMonths", 13)),
				Map.entry("terms.mandatoryFees[0].amount", body -> oneFee(body,
						fee("Power", "AMOUNT", 0, "VND", "PER_KWH"))),
				Map.entry("terms.mandatoryFees[0].chargeUnit", body -> oneFee(body, fee("Power", "AMOUNT", 10, "VND", null))),
				Map.entry("terms.mandatoryFees[0].amount", body -> oneFee(body, fee("Water", "INCLUDED", 10, "VND", null))),
				Map.entry("terms.mandatoryFees[0].currency", body -> oneFee(body,
						fee("Power", "AMOUNT", 10, null, "PER_KWH"))),
				Map.entry("terms.mandatoryFees[0].name", body -> oneFee(body, fee("   ", "INCLUDED", null, null, null))),
				Map.entry("terms.mandatoryFees[0].name", body -> oneFee(body, fee("", "INCLUDED", null, null, null))),
				Map.entry("terms.mandatoryFees[0].name", body -> oneFee(body,
						fee("a".repeat(101), "INCLUDED", null, null, null))),
				Map.entry("terms.mandatoryFees[0].name", body -> oneFee(body,
						fee("Wa\u0000ter", "INCLUDED", null, null, null))),
				Map.entry("terms.mandatoryFees", body -> terms(body).put("mandatoryFees", fees(21))));
		for (Map.Entry<String, Consumer<Map<String, Object>>> refusal : refused) {
			assertRefusedField(done, changed(now, refusal.getValue()), refusal.getKey());
		}
	}

	@Test
	void draft_unreadableValues_malformed() throws Exception {
		Done done = done();
		Written before = written(done.agreementId());

		assertError(put(sale, done.agreementId(), changed(0, body -> terms(body).put("monthlyRent", rent("abc")))), 400,
				"MALFORMED_REQUEST", "[]");
		assertError(put(sale, done.agreementId(), changed(0, body -> body.put("startDate", "2027-02-30"))), 400,
				"MALFORMED_REQUEST", "[]");

		assertEquals(before, written(done.agreementId()));
	}

	/** E05S3-P4: a fraction is refused, never cut and stored. */
	@Test
	void draft_fractionalNumbers_malformedNothingWritten() throws Exception {
		Done done = done();
		List<Consumer<Map<String, Object>>> fractions = List.of(
				body -> terms(body).put("monthlyRent", rent(8_000_000.5)),
				body -> terms(body).put("deposit", deposit("MONTHS", 2.5, null, null)),
				body -> terms(body).put("minimumTermMonths", 12.5),
				body -> body.put("expectedDataVersion", 0.5));

		for (Consumer<Map<String, Object>> fraction : fractions) {
			Written before = written(done.agreementId());

			assertError(put(sale, done.agreementId(), changed(0, fraction)), 400, "MALFORMED_REQUEST", "[]");

			assertEquals(before, written(done.agreementId()));
		}
		assertEquals(0L, rowVersion(done.agreementId()));
	}

	// ==================== ED-7: the photo consent ====================

	@Test
	void draft_exteriorConsentRules_asBr9() throws Exception {
		Done done = done();
		for (Object interior : Arrays.asList(null, false)) {
			assertRefusedField(done, changed(0, body -> {
				body.put("photoConsent", interior);
				body.put("exteriorPhotoConsent", true);
			}), "exteriorPhotoConsent");
		}
		assertRefusedField(done, changed(0, body -> body.remove("exteriorPhotoConsent")), "exteriorPhotoConsent");

		assertEquals(204, put(sale, done.agreementId(), changed(0, body -> body.put("exteriorPhotoConsent", true)))
				.status());
		assertEquals(true, versionRow(done.agreementId()).get("exterior_photo_consent"));

		assertEquals(204, put(sale, done.agreementId(), changed(1, body -> body.put("photoConsent", null))).status());
		Map<String, Object> version = versionRow(done.agreementId());
		assertNull(version.get("photo_consent"));
		assertEquals(false, version.get("exterior_photo_consent"));
	}

	// ==================== ED-8: the period and type as the create ====================

	@Test
	void draft_periodAndTypeRules_asTheCreate() throws Exception {
		Done done = done();
		for (String[] period : List.of(new String[] {"2027-01-02", "2027-01-01"}, new String[] {"2027-01-01", "2028-01-02"},
				new String[] {"2028-02-29", "2029-03-01"})) {
			assertRefusedField(done, changed(0, body -> {
				body.put("startDate", period[0]);
				body.put("endDate", period[1]);
			}), "endDate");
		}
		for (Object version : Arrays.asList(null, -1)) {
			assertRefusedField(done, changed(0, body -> body.put("expectedDataVersion", version)), "expectedDataVersion");
		}
		Written before = written(done.agreementId());
		Result type = put(sale, done.agreementId(), changed(0, body -> body.put("transactionType", "SALE")));
		assertEquals(400, type.status(), type.rawBody());
		assertEquals("VALIDATION_FAILED", type.body().path("code").asString());
		assertEquals(1, type.body().path("errors").size(), type.rawBody());
		assertEquals("transactionType", type.body().path("errors").path(0).path("field").asString());
		assertEquals("[\"LONG_TERM_RENT\"]", type.body().path("errors").path(0).path("allowedValues").toString());
		assertEquals(before, written(done.agreementId()));

		assertEquals(204, put(sale, done.agreementId(), changed(0, body -> {
			body.put("startDate", "2028-02-29");
			body.put("endDate", "2029-02-28");
		})).status());
	}

	// ==================== ED-12, ED-17 ====================

	private record Snapshot(String leadStatus, long leadVersion, int activities, int leadAccessLogs, int intakes,
			int suspensions, int inventoryAccessLogs, int intakeEvents, int ownerships) {
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
				count("select count(*) from property_ownerships where property_id = ?", propertyId));
	}

	@Test
	void draft_writesNothingOutsideBrokerage() throws Exception {
		Done done = done();
		String other = AgreementFixture.insert(jdbc, ids, ids.generateId(), done.leadId(), "CANCELLED", "CANCELLED");
		created.add(other);
		JsonNode readinessBefore = blockOk(sale, done.leadId()).path("readiness");
		Snapshot before = snapshot(done.leadId(), done.propertyId());
		Written otherBefore = written(other);

		Result answer = put(sale, done.agreementId(), example(0));

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(before, snapshot(done.leadId(), done.propertyId()));
		assertEquals(otherBefore, written(other));
		assertEquals(readinessBefore, blockOk(sale, done.leadId()).path("readiness"));
	}

	@Test
	void draft_refusals_quoteNoValue() throws Exception {
		Done done = done();
		List<Map<String, Object>> bodies = List.of(
				changed(0, body -> oneFee(body, fee("Zebra Fee", "INCLUDED", 123_456_789, "VND", null))),
				changed(0, body -> oneFee(body, fee("Zebra\u0000Fee", "INCLUDED", null, null, null))),
				changed(0, body -> oneFee(body, fee("Zebra Fee", "AMOUNT", 123_456_789, "VND", null))),
				changed(0, body -> terms(body).put("monthlyRent", rent(MAX + 1))),
				changed(0, body -> terms(body).put("deposit", deposit("MONTHS", 2, 123_456_789, "VND"))),
				changed(0, body -> {
					body.put("startDate", "2027-03-15");
					body.put("endDate", "2028-04-20");
				}),
				changed(5, body -> { }));

		for (Map<String, Object> body : bodies) {
			Result answer = put(sale, done.agreementId(), body);

			assertTrue(answer.status() >= 400, answer.rawBody());
			for (String value : List.of("123456789", "Zebra", String.valueOf(MAX + 1), "2027-03-15", "2028-04-20",
					"8000000", "Electricity")) {
				assertFalse(answer.rawBody().contains(value), value + " in " + answer.rawBody());
			}
		}
	}

	private static List<Map<String, Object>> fees(int count) {
		List<Map<String, Object>> fees = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			fees.add(fee("Fee " + i, "AMOUNT", 1_000L + i, "VND", "ONE_OFF"));
		}
		return fees;
	}

	private static void oneFee(Map<String, Object> body, Map<String, Object> fee) {
		terms(body).put("mandatoryFees", List.of(fee));
	}

	@SuppressWarnings("unchecked") // the example body's rent is a map of this class
	private static Map<String, Object> rentOf(Map<String, Object> body) {
		return (Map<String, Object>) terms(body).get("monthlyRent");
	}
}
