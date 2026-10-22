package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * {@code POST /api/v1/public/owner-intake-requests} end to end (acquisition-owner-intake spec 6.3, 6.5; W9-1..W9-9,
 * W9-11, W9-13, W9-16, W9-18, W9-24): anonymous HTTP through the real security chain and filters, the submit service
 * and its adapters on PostgreSQL migrated by Flyway. The rate limit is off here (the suite would exceed it from one
 * IP); RateLimitFilterTest and RateLimitRulesConfigTest cover it. Each test submits its own phone and address so
 * the 5-minute repeat rule never joins two tests. Needs Docker, like the other Postgres tests.
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
class OwnerIntakeApiPostgresTest {

	private static final String ENDPOINT = "/api/v1/public/owner-intake-requests";
	private static final String REQUEST_CODE = "^SV-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$";
	private static final String HEX_64 = "^[0-9a-f]{64}$";
	private static final String NOTICE_VERSION = "owner-intake-2026-10-21";
	/** The notice that still named "bán" (acquisition-owner-intake BR-9); stored leads keep it, new ones are refused. */
	private static final String PREVIOUS_NOTICE_VERSION = "owner-intake-2026-10";
	private static final String HAI_CHAU = "20242";
	private static final String HOANG_SA = "20333";
	/** app.cors.allowed-origins of application.yml. */
	private static final String ALLOWED_ORIGIN = "http://localhost:3000";
	private static final List<String> INTAKE_TABLES = List.of("consent_records", "owner_leads",
			"owner_lead_activities", "owner_lead_duplicate_candidates", "owner_intake_idempotency_keys");
	/** The six property types of acquisition-owner-intake BR-3, in form order. */
	private static final List<String> PROPERTY_TYPES = List.of("HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY",
			"CONDOTEL", "APARTMENT");
	/** The values before acquisition-owner-intake Q-16: unit layouts, townhouse, land and "other". */
	private static final List<String> OLD_PROPERTY_TYPES = List.of("STUDIO", "ONE_BEDROOM", "TWO_BEDROOM",
			"THREE_BEDROOM", "FOUR_BEDROOM", "DUPLEX", "PENTHOUSE", "TOWNHOUSE", "LAND", "OTHER");
	private static final String LEADS = "/api/v1/staff/owner-leads";

	/** Phones 091xxxxxxx; 0909 123 456 is kept for the first test, which checks its stored form. */
	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);
	private static final AtomicInteger ADDRESS_SEQUENCE = new AtomicInteger(1);

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

	@BeforeAll
	void setUp() {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
	}

	@AfterAll
	void closeHttpClient() {
		api.http().close();
	}

	// ==================== W9-1, W9-2: a valid submission ====================

	/** W9-1: one lead (WEB, NEW, normalized phone), its consent, one SUBMITTED activity and the snapshot. */
	@Test
	void post_validBody_returns202AndStoresOneLeadWithConsentAndActivity() throws Exception {
		Result result = post(validBody("0909 123 456", nextAddress()));

		assertEquals(202, result.status(), result.rawBody());
		String code = result.body().path("requestCode").asString();
		assertTrue(code.matches(REQUEST_CODE), code);
		assertEquals(List.of("requestCode"), List.copyOf(result.body().propertyNames()));
		assertNotNull(result.header("X-Correlation-Id"));
		assertNull(result.header("Idempotent-Replayed"));

		Map<String, Object> lead = lead(code);
		assertEquals("WEB", lead.get("source"));
		assertEquals("NEW", lead.get("status"));
		assertEquals("+84909123456", lead.get("phone_normalized"));
		assertFalse(String.valueOf(lead.get("submission_snapshot")).isBlank());
		assertEquals(1, count("select count(*) from owner_leads where phone_normalized = ?", "+84909123456"));
		assertEquals(1, count("select count(*) from consent_records where id = ?", lead.get("consent_record_id")));
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type = 'SUBMITTED'",
				lead.get("id")));
		assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ?", lead.get("id")));
	}

	/** W9-2: the form never creates a property, a unit or an ownership. */
	@Test
	void post_validBody_changesNoPropertyUnitOrOwnershipRow() throws Exception {
		int properties = count("select count(*) from properties");
		int units = count("select count(*) from units");
		int ownerships = count("select count(*) from property_ownerships");

		Result result = post(validBody(nextPhone(), nextAddress()));

		assertEquals(202, result.status(), result.rawBody());
		assertEquals(properties, count("select count(*) from properties"));
		assertEquals(units, count("select count(*) from units"));
		assertEquals(ownerships, count("select count(*) from property_ownerships"));
	}

	// ==================== W9-3, W9-4, W9-5, W9-6: refused values ====================

	Stream<Arguments> invalidFields() {
		return Stream.concat(Stream.of(
				invalid("contactName missing", b -> b.remove("contactName"), "contactName"),
				invalid("phone missing", b -> b.remove("phone"), "phone"),
				invalid("intent missing", b -> b.remove("intent"), "intent"),
				invalid("provinceCode missing", b -> b.remove("provinceCode"), "provinceCode"),
				invalid("wardCode missing", b -> b.remove("wardCode"), "wardCode"),
				invalid("addressDetail missing", b -> b.remove("addressDetail"), "addressDetail"),
				invalid("consent missing", b -> b.remove("consent"), "consent"),
				invalid("phone too short", b -> b.put("phone", "0123"), "phone"),
				invalid("phone with 00", b -> b.put("phone", "00909123456"), "phone"),
				invalid("phone 84 without plus", b -> b.put("phone", "84909123456"), "phone"),
				invalid("contactName 101 chars", b -> b.put("contactName", "a".repeat(101)), "contactName"),
				invalid("addressDetail 256 chars", b -> b.put("addressDetail", "a".repeat(256)), "addressDetail"),
				invalid("notes 1001 chars", b -> b.put("notes", "a".repeat(1001)), "notes"),
				invalid("preferredCallbackTime 101 chars", b -> b.put("preferredCallbackTime", "a".repeat(101)),
						"preferredCallbackTime"),
				invalid("email 255 chars", b -> b.put("email", emailOfLength(255)), "email"),
				invalid("email without dot in domain", b -> b.put("email", "a@b"), "email"),
				invalid("price 0", b -> price(b).put("amount", 0), "askingPrice.amount"),
				invalid("price 16 digits", b -> price(b).put("amount", 1_000_000_000_000_000L), "askingPrice.amount"),
				invalid("price with decimals", b -> price(b).put("amount", 1500.5), "askingPrice.amount"),
				invalid("price with .0", b -> price(b).put("amount", new BigDecimal("1500.0")), "askingPrice.amount"),
				invalid("price without amount", b -> price(b).remove("amount"), "askingPrice.amount"),
				invalid("currency USD", b -> price(b).put("currency", "USD"), "askingPrice.currency"),
				invalid("unit NIGHT for long-term rent", b -> price(b).put("unit", "NIGHT"), "askingPrice.unit"),
				invalid("unknown unit", b -> price(b).put("unit", "WEEK"), "askingPrice.unit"),
				invalid("unknown intent", b -> b.put("intent", "RENT"), "intent"),
				invalid("unknown property type", b -> b.put("propertyType", "CASTLE"), "propertyType"),
				invalid("bad date", b -> b.put("availableFrom", "2026-13-45"), "availableFrom"),
				invalid("latitude not a number", b -> b.put("latitude", "abc"), "latitude")),
				OLD_PROPERTY_TYPES.stream().map(old ->
						invalid("old property type " + old, b -> b.put("propertyType", old), "propertyType")));
	}

	/** W9-3, W9-16: 400 VALIDATION_FAILED on exactly that field, the call's correlation id, nothing stored. */
	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidFields")
	void post_eachInvalidField_returns400OnThatFieldAndStoresNothing(String name, Consumer<Map<String, Object>> change,
			String field) throws Exception {
		String phone = nextPhone();
		Map<String, Object> body = validBody(phone, nextAddress());
		change.accept(body);
		Map<String, Integer> before = intakeRowCounts();

		Result result = post(body);

		assertValidationError(result, field);
		assertEquals(before, intakeRowCounts());
		assertFalse(result.rawBody().contains(phone.replace(" ", "")), "the phone is never echoed");
	}

	// ==================== acquisition-owner-intake BR-3, Q-16: the six property types ====================

	/** acquisition-owner-intake BR-3: each of the six types is accepted and stored as given. */
	@ParameterizedTest(name = "{0}")
	@MethodSource("propertyTypes")
	void post_eachPropertyType_returns202AndStoresIt(String type) throws Exception {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("propertyType", type);

		Result result = post(body);

		assertEquals(202, result.status(), result.rawBody());
		assertEquals(type, lead(result.body().path("requestCode").asString()).get("property_type"));
	}

	/** acquisition-owner-intake BR-3: the type stays optional; without it the lead has no type. */
	@Test
	void post_withoutPropertyType_returns202AndStoresNull() throws Exception {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.remove("propertyType");

		Result result = post(body);

		assertEquals(202, result.status(), result.rawBody());
		assertNull(lead(result.body().path("requestCode").asString()).get("property_type"));
	}

	/** acquisition-owner-intake BR-3: staff see the new code, or null for a lead without a type. */
	@Test
	void staffListAndDetail_showTheSubmittedTypeOrNull() throws Exception {
		Map<String, Object> villa = validBody(nextPhone(), nextAddress());
		villa.put("propertyType", "VILLA");
		Map<String, Object> untyped = validBody(nextPhone(), nextAddress());
		untyped.remove("propertyType");
		String villaCode = post(villa).body().path("requestCode").asString();
		String untypedCode = post(untyped).body().path("requestCode").asString();
		String villaId = AcquisitionApiTestSupport.leadId(jdbc, villaCode);
		String untypedId = AcquisitionApiTestSupport.leadId(jdbc, untypedCode);
		AcquisitionApiTestSupport.TestUser leader = api.createUser(UserStatus.ACTIVE, "SALE_LEADER");
		try {
			AcquisitionApiTestSupport.TestUser caller = api.logIn(leader);

			assertEquals("VILLA", listItem(caller, villaCode, villaId).path("propertyType").asString());
			JsonNode untypedItem = listItem(caller, untypedCode, untypedId);
			assertTrue(untypedItem.has("propertyType") && untypedItem.path("propertyType").isNull(),
					untypedItem.toString());
			assertEquals("VILLA", detail(caller, villaId).path("propertyType").asString());
			assertTrue(detail(caller, untypedId).path("propertyType").isNull());
		} finally {
			AcquisitionApiTestSupport.deleteUsers(jdbc, List.of(leader.userId()));
		}
	}

	static Stream<String> propertyTypes() {
		return PROPERTY_TYPES.stream();
	}

	/** Spec 6.3: 255 characters is refused with the length message only; 254 is accepted and stored whole. */
	@Test
	void post_emailOf255And254Chars_refusedOnLengthAndAccepted() throws Exception {
		Map<String, Object> tooLong = validBody(nextPhone(), nextAddress());
		tooLong.put("email", emailOfLength(255));
		Result refused = post(tooLong);
		assertValidationError(refused, "email");
		assertEquals("Email must be at most 254 characters.", refused.body().path("errors").get(0).path("message")
				.asString());

		Map<String, Object> longest = validBody(nextPhone(), nextAddress());
		longest.put("email", emailOfLength(254));
		Result accepted = post(longest);
		assertEquals(202, accepted.status(), accepted.rawBody());
		assertEquals(emailOfLength(254), lead(accepted.body().path("requestCode").asString()).get("email"));
	}

	/** A blank name is reported once, as missing, not with the length message. */
	@Test
	void post_blankContactName_returns400WithTheRequiredMessage() throws Exception {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("contactName", "   ");

		Result result = post(body);

		assertValidationError(result, "contactName");
		assertEquals("Contact name is required.", result.body().path("errors").get(0).path("message").asString());
	}

	/** W9-4: consent must be given, to the current notice. */
	@Test
	void post_consentMissingOrFalseOrWrongVersion_returns400OnConsentField() throws Exception {
		Map<String, Integer> before = intakeRowCounts();

		Map<String, Object> missing = validBody(nextPhone(), nextAddress());
		missing.remove("consent");
		assertValidationError(post(missing), "consent");

		Map<String, Object> refused = validBody(nextPhone(), nextAddress());
		refused.put("consent", Map.of("accepted", false, "noticeVersion", NOTICE_VERSION));
		assertValidationError(post(refused), "consent.accepted");

		Map<String, Object> acceptedMissing = validBody(nextPhone(), nextAddress());
		acceptedMissing.put("consent", Map.of("noticeVersion", NOTICE_VERSION));
		assertValidationError(post(acceptedMissing), "consent.accepted");

		Map<String, Object> oldNotice = validBody(nextPhone(), nextAddress());
		oldNotice.put("consent", Map.of("accepted", true, "noticeVersion", "owner-intake-2020-01"));
		assertValidationError(post(oldNotice), "consent.noticeVersion");

		assertEquals(before, intakeRowCounts());
	}

	/** acquisition-owner-intake BR-9: the previous notice (it still named "bán") is refused like any unknown version. */
	@Test
	void post_previousNoticeVersion_returns400OnNoticeVersionAndStoresNothing() throws Exception {
		Map<String, Integer> before = intakeRowCounts();
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("consent", Map.of("accepted", true, "noticeVersion", PREVIOUS_NOTICE_VERSION));

		assertValidationError(post(body), "consent.noticeVersion");
		assertEquals(before, intakeRowCounts());
	}

	/**
	 * acquisition-owner-intake BR-1, BR-4 (Q-14, G2 D-45): the form no longer offers a sale, but the API still accepts
	 * the reserved intent SALE with a total price.
	 */
	@Test
	void post_saleIntentWithTotalPrice_isStillAccepted() throws Exception {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("intent", "SALE");
		price(body).put("amount", 5_000_000_000L);
		price(body).put("unit", "TOTAL");

		Result result = post(body);

		assertEquals(202, result.status(), result.rawBody());
		Map<String, Object> lead = lead(result.body().path("requestCode").asString());
		assertEquals("SALE", lead.get("intent"));
		assertEquals("TOTAL", lead.get("asking_price_unit"));
	}

	/** W9-5: the ward must be in the catalog and in the chosen province; the province must be known. */
	@Test
	void post_unknownWard_unknownProvince_wardOfOtherProvince_return400() throws Exception {
		jdbc.update("insert into provinces (code, name) values ('99', 'Tỉnh Thử')");
		jdbc.update("insert into wards (code, province_code, name, type) values ('99001', '99', 'Phường Thử', 'WARD')");
		try {
			Map<String, Integer> before = intakeRowCounts();

			assertValidationError(post(withPlace("48", "00000")), "wardCode");
			assertValidationError(post(withPlace("77", HAI_CHAU)), "provinceCode");
			assertValidationError(post(withPlace("48", "99001")), "wardCode");
			assertValidationError(post(withPlace("99", HAI_CHAU)), "wardCode");

			assertEquals(before, intakeRowCounts());
		} finally {
			jdbc.update("delete from wards where code = '99001'");
			jdbc.update("delete from provinces where code = '99'");
		}
	}

	/** W9-6: a pin inside the box (edges included) is kept; outside, half a pair or any pin on Hoàng Sa is not. */
	@Test
	void post_coordinates_insideAccepted_outsideOnlyOneAndHoangSaRejected() throws Exception {
		Result inside = post(withCoordinates(HAI_CHAU, "16.0678", "108.2208"));
		assertEquals(202, inside.status(), inside.rawBody());
		Map<String, Object> lead = lead(inside.body().path("requestCode").asString());
		assertEquals(0, new BigDecimal("16.067800").compareTo((BigDecimal) lead.get("latitude")));
		assertEquals(0, new BigDecimal("108.220800").compareTo((BigDecimal) lead.get("longitude")));

		Result corner = post(withCoordinates(HAI_CHAU, "15.85", "107.75"));
		assertEquals(202, corner.status(), corner.rawBody());

		Map<String, Integer> before = intakeRowCounts();
		assertValidationError(post(withCoordinates(HAI_CHAU, "21.0285", "105.8542")), "latitude");
		assertValidationError(post(withCoordinates(HAI_CHAU, "16.0678", null)), "latitude");
		assertValidationError(post(withCoordinates(HAI_CHAU, null, "108.2208")), "latitude");
		assertValidationError(post(withCoordinates(HOANG_SA, "16.0678", "108.2208")), "latitude");
		assertEquals(before, intakeRowCounts());

		Result hoangSaWithoutPin = post(withCoordinates(HOANG_SA, null, null));
		assertEquals(202, hoangSaWithoutPin.status(), hoangSaWithoutPin.rawBody());
	}

	/**
	 * Security gate F1: a ~200-byte number such as {@code 1e999999999} must not be written out digit by digit or
	 * rounded (that takes all the memory or minutes of CPU). It is refused by bean validation on its own field,
	 * quickly, and nothing is stored.
	 */
	@ParameterizedTest(name = "{0} = {1}")
	@MethodSource("absurdNumbers")
	void post_numberWithAbsurdExponent_returns400QuicklyAndStoresNothing(String field, String literal)
			throws Exception {
		String phone = nextPhone();
		String json = api.json().writeValueAsString(validBody(phone, nextAddress()));
		String valid = switch (field) {
			case "latitude" -> "\"latitude\":16.0678";
			case "longitude" -> "\"longitude\":108.2208";
			default -> "\"amount\":15000000";
		};
		String fieldName = field.substring(field.lastIndexOf('.') + 1);
		assertTrue(json.contains(valid), json);
		String body = json.replace(valid, "\"" + fieldName + "\":" + literal);
		Map<String, Integer> before = intakeRowCounts();

		long start = System.nanoTime();
		Result result = api.send(intakeRequest()
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build());
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

		assertValidationError(result, field);
		assertTrue(elapsedMillis < 5_000, "answered in " + elapsedMillis + " ms");
		assertFalse(result.rawBody().contains("999999999"), "the value is never echoed");
		assertEquals(before, intakeRowCounts());
	}

	Stream<Arguments> absurdNumbers() {
		return Stream.of(
				Arguments.of("latitude", "1e999999999"),
				Arguments.of("latitude", "-1e999999999"),
				Arguments.of("latitude", "1e-999999999"),
				Arguments.of("longitude", "1e999999999"),
				Arguments.of("longitude", "-1e999999999"),
				Arguments.of("longitude", "1e-999999999"),
				Arguments.of("askingPrice.amount", "1e999999999"),
				Arguments.of("askingPrice.amount", "1e-999999999"));
	}

	// ==================== W9-7, W9-8: Idempotency-Key ====================

	/** W9-7: the same key and body replay the first code; still one lead, one consent, one activity. */
	@Test
	void post_sameKeySameBody_returns202SameCodeWithReplayedHeader() throws Exception {
		String phone = nextPhone();
		Map<String, Object> body = validBody(phone, nextAddress());
		String key = newKey();

		Result first = post(body, "Idempotency-Key", key);
		Map<String, Integer> afterFirst = intakeRowCounts();
		Result second = post(body, "Idempotency-Key", key);

		assertEquals(202, first.status(), first.rawBody());
		assertNull(first.header("Idempotent-Replayed"));
		assertEquals(202, second.status(), second.rawBody());
		assertEquals("true", second.header("Idempotent-Replayed"));
		assertEquals(first.body().path("requestCode").asString(), second.body().path("requestCode").asString());
		assertEquals(afterFirst, intakeRowCounts());
		assertEquals(1, count("select count(*) from owner_leads where phone_normalized = ?", normalized(phone)));
	}

	/** W9-8: the same key with another body within 24 hours is refused; no new lead. */
	@Test
	void post_sameKeyDifferentBody_returns409() throws Exception {
		String phone = nextPhone();
		String key = newKey();
		Map<String, Object> body = validBody(phone, nextAddress());
		assertEquals(202, post(body, "Idempotency-Key", key).status());
		Map<String, Integer> before = intakeRowCounts();

		body.put("notes", "Một ghi chú khác");
		Result result = post(body, "Idempotency-Key", key);

		assertEquals(409, result.status(), result.rawBody());
		assertEquals("IDEMPOTENCY_KEY_REUSED", result.body().path("code").asString());
		assertEquals(0, result.body().path("errors").size());
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
		assertFalse(result.rawBody().contains(key), "the key is never echoed");
		assertEquals(before, intakeRowCounts());
	}

	/**
	 * W9-8: a key stored more than 24 hours ago is forgotten. The old row is deleted and a row with the same key is
	 * inserted in the same transaction, for the new lead.
	 */
	@Test
	void post_keyOlderThan24h_isAcceptedAsNew() throws Exception {
		String phone = nextPhone();
		String key = newKey();
		Result first = post(validBody(phone, nextAddress()), "Idempotency-Key", key);
		assertEquals(202, first.status(), first.rawBody());
		jdbc.update("update owner_intake_idempotency_keys set created_at = created_at - interval '25 hours' "
				+ "where idempotency_key = ?", key);

		Result second = post(validBody(phone, nextAddress()), "Idempotency-Key", key);

		assertEquals(202, second.status(), second.rawBody());
		assertNull(second.header("Idempotent-Replayed"));
		String newCode = second.body().path("requestCode").asString();
		assertNotEquals(first.body().path("requestCode").asString(), newCode);
		assertEquals(2, count("select count(*) from owner_leads where phone_normalized = ?", normalized(phone)));
		Map<String, Object> keyRow = jdbc.queryForMap(
				"select lead_id, created_at from owner_intake_idempotency_keys where idempotency_key = ?", key);
		assertEquals(lead(newCode).get("id"), keyRow.get("lead_id"));
		Instant storedAt = ((java.sql.Timestamp) keyRow.get("created_at")).toInstant();
		assertTrue(storedAt.isAfter(Instant.now().minus(1, ChronoUnit.HOURS)), "the key row is the new one");
	}

	/** Spec 6.3: a malformed key is a 400 on the header; a valid incoming correlation id is echoed (W9-16). */
	@Test
	void post_badIdempotencyKeyFormat_returns400OnHeaderField() throws Exception {
		Map<String, Integer> before = intakeRowCounts();

		Result result = post(validBody(nextPhone(), nextAddress()), "Idempotency-Key", "short",
				"X-Correlation-Id", "intake-test-0001");

		assertValidationError(result, "Idempotency-Key");
		assertEquals("intake-test-0001", result.body().path("correlationId").asString());
		assertEquals(before, intakeRowCounts());
	}

	// ==================== W9-9: the same submission within 5 minutes ====================

	/** W9-9: same phone, intent, ward and address (other case and spacing) within 5 minutes is the same lead. */
	@Test
	void post_sameSubmissionWithin5Minutes_returnsSameCode() throws Exception {
		String phone = nextPhone();
		int number = ADDRESS_SEQUENCE.getAndIncrement();
		Result first = post(validBody(phone, number + " Bạch Đằng, căn A-1203"));
		Map<String, Integer> afterFirst = intakeRowCounts();

		Result second = post(validBody(phone.replace(" ", ""), "  " + number + "  BẠCH ĐẰNG,   CĂN a-1203 "));

		assertEquals(202, first.status(), first.rawBody());
		assertEquals(202, second.status(), second.rawBody());
		assertEquals(first.body().path("requestCode").asString(), second.body().path("requestCode").asString());
		assertEquals("true", second.header("Idempotent-Replayed"));
		assertEquals(afterFirst, intakeRowCounts());
	}

	/** W9-9: after 5 minutes the same submission is a new lead. */
	@Test
	void post_sameSubmissionAfter5Minutes_createsNewLead() throws Exception {
		String phone = nextPhone();
		Map<String, Object> body = validBody(phone, nextAddress());
		Result first = post(body);
		String firstCode = first.body().path("requestCode").asString();
		jdbc.update("update owner_leads set created_at = created_at - interval '6 minutes' where request_code = ?",
				firstCode);

		Result second = post(body);

		assertEquals(202, second.status(), second.rawBody());
		assertNotEquals(firstCode, second.body().path("requestCode").asString());
		assertEquals(2, count("select count(*) from owner_leads where phone_normalized = ?", normalized(phone)));
	}

	// ==================== W9-11: possible duplicates ====================

	/** W9-11: a new lead is linked to every open lead with its phone; a closed lead is not a candidate. */
	@Test
	void post_phoneOfAnOpenLead_createsLeadAndCandidateRows_closedLeadIsNot() throws Exception {
		String phone = nextPhone();
		String open = post(validBody(phone, nextAddress())).body().path("requestCode").asString();
		String closed = post(validBody(phone, nextAddress())).body().path("requestCode").asString();
		jdbc.update("update owner_leads set status = 'REJECTED' where request_code = ?", closed);

		Result result = post(validBody(phone, nextAddress()));

		assertEquals(202, result.status(), result.rawBody());
		String newCode = result.body().path("requestCode").asString();
		assertEquals(3, count("select count(*) from owner_leads where phone_normalized = ?", normalized(phone)));
		List<String> candidates = jdbc.queryForList(
				"select candidate_lead_id from owner_lead_duplicate_candidates where lead_id = ?", String.class,
				lead(newCode).get("id"));
		assertEquals(List.of(lead(open).get("id")), candidates);
	}

	// ==================== W9-13: consent and personal data ====================

	/** W9-13: notice version, purpose, channel, acceptance time, and IP and user agent only as 64-hex hashes. */
	@Test
	void post_consentRecord_storesVersionPurposeChannelAndSixtyFourHexHashes() throws Exception {
		Result result = post(validBody(nextPhone(), nextAddress()), "User-Agent", "SunvibeIntakeTest/1.0");

		assertEquals(202, result.status(), result.rawBody());
		Map<String, Object> consent = jdbc.queryForMap("select * from consent_records where id = ?",
				lead(result.body().path("requestCode").asString()).get("consent_record_id"));
		assertEquals(NOTICE_VERSION, consent.get("notice_version"));
		assertEquals("OWNER_INTAKE_CONTACT", consent.get("purpose"));
		assertEquals("WEB", consent.get("channel"));
		assertNotNull(consent.get("accepted_at"));
		assertTrue(String.valueOf(consent.get("ip_hash")).matches(HEX_64), "ip_hash is 64 hex");
		assertTrue(String.valueOf(consent.get("user_agent_hash")).matches(HEX_64), "user_agent_hash is 64 hex");
		assertNotEquals(consent.get("ip_hash"), consent.get("user_agent_hash"));
	}

	/** W9-13: neither the raw client IP nor the user agent is stored in any text column of the intake tables. */
	@Test
	void post_rawIpAndUserAgent_appearNowhereInTheDatabase() throws Exception {
		String userAgent = "SunvibeIntakeTest/2.0 (marker-7Q2K)";
		HttpRequest request = HttpRequest.newBuilder(api.ipv4Uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.header("User-Agent", userAgent)
				.POST(HttpRequest.BodyPublishers.ofString(api.json().writeValueAsString(
						validBody(nextPhone(), nextAddress()))))
				.build();

		Result result = api.send(request);

		assertEquals(202, result.status(), result.rawBody());
		List<String> textColumns = new ArrayList<>();
		jdbc.query("select table_name, column_name from information_schema.columns where table_schema = "
				+ "current_schema() and table_name in ('consent_records', 'owner_leads', 'owner_lead_activities', "
				+ "'owner_lead_duplicate_candidates', 'owner_intake_idempotency_keys') and data_type in "
				+ "('character varying', 'text', 'character')", row -> {
					textColumns.add(row.getString("table_name") + "." + row.getString("column_name"));
				});
		assertTrue(textColumns.contains("owner_leads.submission_snapshot"), textColumns.toString());
		for (String column : textColumns) {
			String table = column.substring(0, column.indexOf('.'));
			String name = column.substring(column.indexOf('.') + 1);
			int hits = count("select count(*) from " + table + " where " + name + " like ? or " + name + " like ?",
					"%127.0.0.1%", "%marker-7Q2K%");
			assertEquals(0, hits, column + " holds the raw IP or user agent");
		}
	}

	// ==================== W9-24: body size and snapshot ====================

	/** W9-24: over 16 KB is 413 PAYLOAD_TOO_LARGE, whether the size is announced or the body is chunked. */
	@Test
	void post_bodyOver16Kb_returns413() throws Exception {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("notes", "x".repeat(17_000));
		byte[] bytes = api.json().writeValueAsBytes(body);
		Map<String, Integer> before = intakeRowCounts();

		Result announced = api.send(intakeRequest().POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
		Result chunked = api.send(intakeRequest()
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)))
				.build());

		for (Result result : List.of(announced, chunked)) {
			assertEquals(413, result.status(), result.rawBody());
			assertEquals("PAYLOAD_TOO_LARGE", result.body().path("code").asString());
			assertEquals(0, result.body().path("errors").size());
			assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
		}
		assertEquals(before, intakeRowCounts());
	}

	/** W9-24 boundary: exactly 16384 bytes reaches validation, 16385 is 413, announced or chunked. */
	@Test
	void post_bodyOfExactly16KbAccepted_oneByteMoreReturns413() throws Exception {
		byte[] atLimit = bodyOfSize(16_384);
		byte[] overLimit = bodyOfSize(16_385);
		assertEquals(16_384, atLimit.length);
		assertEquals(16_385, overLimit.length);

		for (byte[] bytes : List.of(atLimit, overLimit)) {
			Result announced = api.send(intakeRequest().POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
			Result chunked = api.send(intakeRequest()
					.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)))
					.build());
			for (Result result : List.of(announced, chunked)) {
				if (bytes == atLimit) {
					// The long notes fail validation; what matters is that the body was read
					assertValidationError(result, "notes");
				} else {
					assertEquals(413, result.status(), result.rawBody());
					assertEquals("PAYLOAD_TOO_LARGE", result.body().path("code").asString());
				}
			}
		}
	}

	/** The 413 of an announced oversize body still carries the CORS headers a browser needs to read it. */
	@Test
	void post_bodyOver16KbFromAllowedOrigin_returns413WithCorsHeader() throws Exception {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("notes", "x".repeat(17_000));

		Result result = api.send(intakeRequest()
				.header("Origin", ALLOWED_ORIGIN)
				.POST(HttpRequest.BodyPublishers.ofByteArray(api.json().writeValueAsBytes(body)))
				.build());

		assertEquals(413, result.status(), result.rawBody());
		assertEquals("PAYLOAD_TOO_LARGE", result.body().path("code").asString());
		assertEquals(ALLOWED_ORIGIN, result.header("Access-Control-Allow-Origin"));
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
	}

	/**
	 * A chunked multipart body is parsed by the container from the raw request, past the counting stream, so it is
	 * refused before any parsing: 415 even though its closing boundary never comes (parsing it would fail).
	 */
	@Test
	void post_chunkedMultipartBody_returns415BeforeParsingAndStoresNothing() throws Exception {
		String part = "--sv-boundary\r\nContent-Disposition: form-data; name=\"notes\"\r\n\r\n" + "x".repeat(20_000);
		byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
		Map<String, Integer> before = intakeRowCounts();

		Result result = api.send(HttpRequest.newBuilder(api.uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", "multipart/form-data; boundary=sv-boundary")
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)))
				.build());

		assertEquals(415, result.status(), result.rawBody());
		assertEquals("MALFORMED_REQUEST", result.body().path("code").asString());
		assertEquals(0, result.body().path("errors").size());
		assertNotNull(result.header("X-Correlation-Id"));
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
		assertEquals(before, intakeRowCounts());
	}

	/** The size check reads a chunked body under the limit to the end, unchanged. */
	@Test
	void post_chunkedBodyUnder16Kb_isAccepted() throws Exception {
		byte[] bytes = api.json().writeValueAsBytes(validBody(nextPhone(), nextAddress()));

		Result result = api.send(intakeRequest()
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)))
				.build());

		assertEquals(202, result.status(), result.rawBody());
	}

	/** W9-24: the snapshot is the body as typed (phone spacing, address case), without unknown fields, IP or agent. */
	@Test
	void post_unknownFields_areNotStoredAndPhoneAndAddressStayAsTyped() throws Exception {
		String phone = nextPhone();
		String address = "  " + ADDRESS_SEQUENCE.getAndIncrement() + " BẠCH Đằng,  Căn A-1203";
		Map<String, Object> body = validBody(phone, address);
		body.put("clientIp", "10.9.8.7");
		body.put("unexpectedField", "dropped-7Q2K");

		Result result = post(body, "User-Agent", "SunvibeIntakeTest/3.0");

		assertEquals(202, result.status(), result.rawBody());
		Map<String, Object> lead = lead(result.body().path("requestCode").asString());
		JsonNode snapshot = api.json().readTree(String.valueOf(lead.get("submission_snapshot")));
		assertEquals(phone, snapshot.path("phone").asString());
		assertEquals(address, snapshot.path("addressDetail").asString());
		assertTrue(snapshot.path("clientIp").isMissingNode());
		assertTrue(snapshot.path("unexpectedField").isMissingNode());
		String raw = String.valueOf(lead.get("submission_snapshot"));
		assertFalse(raw.contains("dropped-7Q2K") || raw.contains("10.9.8.7") || raw.contains("SunvibeIntakeTest"), raw);
		assertEquals(normalized(phone), lead.get("phone_normalized"));
	}

	// ==================== Spec 6.5: unreadable bodies ====================

	@Test
	void post_malformedJson_returns400MalformedRequest() throws Exception {
		Result result = api.send(intakeRequest().POST(HttpRequest.BodyPublishers.ofString("{\"phone\": 09")).build());

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("MALFORMED_REQUEST", result.body().path("code").asString());
		assertEquals(0, result.body().path("errors").size());
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
	}

	/** An unknown or invalid body charset is refused like an unsupported type, never a 500; nothing is stored. */
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "application/json;charset=x-unknown-charset", "application/json;charset=\"a b\"" })
	void post_unknownCharset_returns415NotA500(String contentType) throws Exception {
		Map<String, Integer> before = intakeRowCounts();

		Result result = api.send(HttpRequest.newBuilder(api.uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", contentType)
				.POST(HttpRequest.BodyPublishers.ofString(api.json().writeValueAsString(
						validBody(nextPhone(), nextAddress())), StandardCharsets.UTF_8))
				.build());

		assertEquals(415, result.status(), result.rawBody());
		assertEquals("MALFORMED_REQUEST", result.body().path("code").asString());
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
		assertEquals(before, intakeRowCounts());
	}

	@Test
	void post_unsupportedContentType_returns415() throws Exception {
		Result result = api.send(HttpRequest.newBuilder(api.uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", "text/plain")
				.POST(HttpRequest.BodyPublishers.ofString("hello"))
				.build());

		assertEquals(415, result.status(), result.rawBody());
		assertEquals("MALFORMED_REQUEST", result.body().path("code").asString());
	}

	// ==================== W9-18: nothing else became public ====================

	/**
	 * Spec 6.5, ADR-010: a method error raised before a handler is chosen stays on the global handler and its legacy
	 * body (numeric code); the body-size filter must not answer a signed-in GET without a body with its own 415.
	 */
	@Test
	void authenticatedGet_intakePath_reachesTheGlobalHandlerNotThe415OfTheFilter() throws Exception {
		AcquisitionApiTestSupport.TestUser sale = api.logIn(api.createUser(UserStatus.ACTIVE, "SALE"));

		Result result = api.send(api.request(sale, ENDPOINT).GET().build());

		assertNotEquals(415, result.status(), result.rawBody());
		assertTrue(result.body().path("code").isNumber(), "legacy global body: " + result.rawBody());
	}

	@Test
	void anonymous_otherApiV1Paths_return401() throws Exception {
		assertEquals(401, api.send(api.request(null, "/api/v1/staff/owner-leads").GET().build()).status());
		assertEquals(401, api.send(api.request(null, "/api/v1/public/anything-else")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}")).build()).status());
		assertEquals(401, api.send(api.request(null, ENDPOINT).GET().build()).status());
	}

	// ==================== Helpers ====================

	private static Arguments invalid(String name, Consumer<Map<String, Object>> change, String field) {
		return Arguments.of(name, change, field);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> price(Map<String, Object> body) {
		return (Map<String, Object>) body.get("askingPrice");
	}

	/** The example body of spec 6.3, with the given phone and address. */
	private static Map<String, Object> validBody(String phone, String address) {
		Map<String, Object> price = new LinkedHashMap<>();
		price.put("amount", 15_000_000);
		price.put("currency", "VND");
		price.put("unit", "MONTH");
		Map<String, Object> consent = new LinkedHashMap<>();
		consent.put("accepted", true);
		consent.put("noticeVersion", NOTICE_VERSION);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("contactName", "Nguyễn Thị Lan");
		body.put("phone", phone);
		body.put("email", "lan@example.com");
		body.put("intent", "LONG_TERM_RENT");
		body.put("provinceCode", "48");
		body.put("wardCode", HAI_CHAU);
		body.put("addressDetail", address);
		body.put("latitude", 16.0678);
		body.put("longitude", 108.2208);
		body.put("propertyType", "APARTMENT");
		body.put("askingPrice", price);
		body.put("availableFrom", "2026-11-01");
		body.put("notes", "Có nội thất");
		body.put("preferredCallbackTime", "Buổi tối");
		body.put("consent", consent);
		return body;
	}

	private static Map<String, Object> withPlace(String provinceCode, String wardCode) {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("provinceCode", provinceCode);
		body.put("wardCode", wardCode);
		return body;
	}

	private static Map<String, Object> withCoordinates(String wardCode, String latitude, String longitude) {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("wardCode", wardCode);
		body.put("latitude", latitude == null ? null : new BigDecimal(latitude));
		body.put("longitude", longitude == null ? null : new BigDecimal(longitude));
		return body;
	}

	/** A new phone as an owner types it, with spaces: {@code 0910 000 001}. */
	private static String nextPhone() {
		String digits = "091" + String.format("%07d", PHONE_SEQUENCE.getAndIncrement());
		return digits.substring(0, 4) + " " + digits.substring(4, 7) + " " + digits.substring(7);
	}

	private static String normalized(String typedPhone) {
		return "+84" + typedPhone.replace(" ", "").substring(1);
	}

	private static String nextAddress() {
		return ADDRESS_SEQUENCE.getAndIncrement() + " Bạch Đằng, căn A-1203";
	}

	private static String newKey() {
		return "intake-" + UUID.randomUUID();
	}

	/**
	 * An email that passes every format rule (64-character local part, labels of at most 63 characters, a dot in the
	 * domain) and has exactly {@code length} characters, from 197 up.
	 */
	private static String emailOfLength(int length) {
		String local = "a".repeat(64);
		String fixedLabels = "b".repeat(63) + "." + "c".repeat(63) + ".";
		int lastLabel = length - local.length() - 1 - fixedLabels.length() - ".vn".length();
		return local + "@" + fixedLabels + "d".repeat(lastLabel) + ".vn";
	}

	/** A valid body whose ASCII notes make the JSON exactly {@code size} bytes long. */
	private byte[] bodyOfSize(int size) {
		Map<String, Object> body = validBody(nextPhone(), nextAddress());
		body.put("notes", "");
		int empty = api.json().writeValueAsBytes(body).length;
		body.put("notes", "x".repeat(size - empty));
		return api.json().writeValueAsBytes(body);
	}

	private HttpRequest.Builder intakeRequest() {
		return HttpRequest.newBuilder(api.uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", "application/json");
	}

	/** POSTs the body as JSON; {@code headers} are name, value pairs. */
	private Result post(Map<String, Object> body, String... headers) throws Exception {
		HttpRequest.Builder request = intakeRequest()
				.POST(HttpRequest.BodyPublishers.ofString(api.json().writeValueAsString(body), StandardCharsets.UTF_8));
		for (int i = 0; i < headers.length; i += 2) {
			request.header(headers[i], headers[i + 1]);
		}
		return api.send(request.build());
	}

	private static void assertValidationError(Result result, String field) {
		assertEquals(400, result.status(), result.rawBody());
		assertEquals("VALIDATION_FAILED", result.body().path("code").asString(), result.rawBody());
		List<String> fields = new ArrayList<>();
		result.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		assertEquals(List.of(field), fields, result.rawBody());
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());
	}

	private JsonNode detail(AcquisitionApiTestSupport.TestUser caller, String leadId) throws Exception {
		Result result = api.get(caller, LEADS + "/" + leadId);
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}

	/** The staff list item of one lead, found by its request code in the whole queue. */
	private JsonNode listItem(AcquisitionApiTestSupport.TestUser caller, String requestCode, String leadId)
			throws Exception {
		Result page = api.get(caller, LEADS + "?scope=all&requestCode=" + requestCode);
		assertEquals(200, page.status(), page.rawBody());
		assertEquals(1, page.body().path("items").size(), page.rawBody());
		JsonNode item = page.body().path("items").get(0);
		assertEquals(leadId, item.path("id").asString());
		return item;
	}

	private Map<String, Object> lead(String requestCode) {
		return jdbc.queryForMap("select * from owner_leads where request_code = ?", requestCode);
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}

	private Map<String, Integer> intakeRowCounts() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		INTAKE_TABLES.forEach(table -> counts.put(table, count("select count(*) from " + table)));
		return counts;
	}
}
