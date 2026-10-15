package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.Username;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

/**
 * Characterization tests (wave W2) for {@code POST /api/v1/properties}: they record what the endpoint does
 * today, end to end (HTTP, security, mapper, use case, JPA on PostgreSQL), including behavior that later waves change.
 * A test whose name ends in {@code _changesInW5} / {@code _changesInW6} pins insecure behavior that is
 * planned to change (W5: owner taken from the caller, W6: roles). Do not "fix" these assertions here; the
 * wave that changes the behavior updates them.
 * <p>
 * Users are created through the auth domain repository and log in through {@code POST /api/auth/login},
 * so every request carries a real access token. Tokens and passwords are never printed.
 * <p>
 * Every 500 answers the same {@code INTERNAL_ERROR} body, so those tests also check the server log for the
 * exception that caused it. The database name below must stay unique to this class: the phone and code
 * sequences restart with each class and would collide in a shared database.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyCreateCharacterizationTest {

	private static final String ENDPOINT = "/api/v1/properties";
	private static final String DEFAULT_CITY = "Đà Nẵng";
	/** Seeded by R__property_master_data.sql: rule categories 1-6, amenity types 1-4 (groups) and 10+. */
	private static final List<Integer> RULE_IDS = List.of(1, 2);
	private static final List<Integer> AMENITY_IDS = List.of(10, 11);
	private static final int UNKNOWN_MASTER_ID = 9999;

	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);
	private static final AtomicInteger CODE_SEQUENCE = new AtomicInteger(100001);

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

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final JsonMapper json = JsonMapper.builder().build();

	private TestUser admin;
	private TestUser landlordA;
	private TestUser landlordB;
	private TestUser tenant;
	private TestUser unverifiedLandlord;

	private record TestUser(String userId, String username, String accessToken) {
		@Override
		public String toString() {
			// Keeps the token out of any assertion message or log line
			return "TestUser[" + username + "]";
		}
	}

	private record Result(int status, JsonNode body, String rawBody) {
	}

	@BeforeAll
	void checkMasterDataAndLogIn() throws Exception {
		assertEquals(RULE_IDS.size(), countIds("rule_categories", RULE_IDS),
				"rule categories not seeded (was PropertyMasterDataInitializer removed?)");
		assertEquals(AMENITY_IDS.size(), countIds("amenity_types", AMENITY_IDS),
				"amenity types not seeded (was PropertyMasterDataInitializer removed?)");
		assertEquals(0, countIds("rule_categories", List.of(UNKNOWN_MASTER_ID)));
		assertEquals(0, countIds("amenity_types", List.of(UNKNOWN_MASTER_ID)));

		// 5 logins, under the 10 per minute per IP limit on POST /api/auth/login
		admin = createUserAndLogIn("ADMIN", true);
		landlordA = createUserAndLogIn("LANDLORD", true);
		landlordB = createUserAndLogIn("LANDLORD", true);
		tenant = createUserAndLogIn("TENANT", true);
		unverifiedLandlord = createUserAndLogIn("LANDLORD", false);
	}

	@AfterAll
	void closeHttpClient() {
		http.close();
	}

	// ==================== C1 ====================

	@Test
	void create_adminWithImagesAmenitiesAndRules_returns201AndStoresAllRows() throws Exception {
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		body.put("city", "Hồ Chí Minh");
		body.put("ruleCategories", RULE_IDS);
		body.put("amenityTypes", AMENITY_IDS);
		body.put("images", List.of(
				image("https://cdn.example.test/a.jpg", true, 0),
				image("https://cdn.example.test/b.jpg", false, 1)));

		Result result = post(admin, body);

		assertEquals(201, result.status(), result.rawBody());
		JsonNode response = result.body();
		String id = response.path("id").asString();
		assertFalse(id.isBlank(), "response id");
		assertEquals(code, response.path("propertyCode").asString());
		assertEquals("Căn hộ test W2", response.path("title").asString());
		assertEquals("Hồ Chí Minh", response.path("city").asString());
		assertEquals("STUDIO", response.path("propertyType").asString());
		assertEquals(0, new BigDecimal("5000000").compareTo(response.path("monthlyPrice").decimalValue()));
		assertEquals("AVAILABLE", response.path("status").asString());
		assertEquals(landlordA.userId(), response.path("ownerId").asString());
		assertEquals(admin.username(), response.path("createdBy").asString(),
				"createdBy is authentication.getName(), i.e. the caller's username (phone number), not the user id");
		assertEquals(2, response.path("imageCount").asInt());
		assertEquals(2, response.path("ruleCount").asInt());
		assertEquals(2, response.path("amenityCount").asInt());
		assertEquals("Property created successfully", response.path("message").asString());
		assertFalse(response.path("createdAt").isMissingNode() || response.path("createdAt").isNull(), "createdAt");

		Map<String, Object> row = propertyRow(code);
		assertEquals(id, row.get("ID"));
		assertEquals(landlordA.userId(), row.get("OWNER_ID"), "owner_id is the value from the request body");
		assertEquals(admin.username(), row.get("CREATED_BY"), "created_by stores the username (phone number)");
		assertEquals(admin.username(), row.get("UPDATED_BY"));
		assertEquals("AVAILABLE", row.get("STATUS"));
		assertEquals("Hồ Chí Minh", row.get("CITY"));
		assertEquals("STUDIO", row.get("PROPERTY_TYPE"));
		assertEquals(2, countWherePropertyId("property_images", id));
		assertEquals(2, countWherePropertyId("property_amenity", id));
		assertEquals(2, countWherePropertyId("property_rules", id));
		assertEquals(1, jdbc.queryForObject(
				"select count(*) from property_images where property_id = ? and is_primary = true", Integer.class, id));
		assertEquals(Set.of(10, 11), Set.copyOf(jdbc.queryForList(
				"select amenity_type_id from property_amenity where property_id = ?", Integer.class, id)));
		assertEquals(Set.of(1, 2), Set.copyOf(jdbc.queryForList(
				"select rule_category_id from property_rules where property_id = ?", Integer.class, id)));
		assertEquals(Set.of(admin.username()), Set.copyOf(jdbc.queryForList(
				"select uploaded_by from property_images where property_id = ?", String.class, id)),
				"image uploaded_by is the username too");
	}

	@Test
	void create_validBody_storesNoAvailabilityAndNoPriceRows() throws Exception {
		// The domain Property creates an AVAILABLE PropertyAvailability, but PropertyEntityMapper.toEntity
		// does not map it (nor any price history), so nothing reaches those tables today.
		String code = uniqueCode();

		Result result = post(admin, validBody(code, landlordA.userId()));

		assertEquals(201, result.status(), result.rawBody());
		String id = result.body().path("id").asString();
		assertEquals(0, countWherePropertyId("property_availability", id));
		assertEquals(0, countWherePropertyId("property_prices", id));
	}

	// ==================== C2 / C3: owner and role (insecure today) ====================

	@Test
	void create_landlordForOwnProperty_returns201_changesInW6() throws Exception {
		String code = uniqueCode();

		Result result = post(landlordA, validBody(code, landlordA.userId()));

		assertEquals(201, result.status(), result.rawBody());
		assertEquals(landlordA.userId(), result.body().path("ownerId").asString());
		assertEquals(landlordA.username(), result.body().path("createdBy").asString());
		Map<String, Object> row = propertyRow(code);
		assertEquals(landlordA.userId(), row.get("OWNER_ID"));
		assertEquals(landlordA.username(), row.get("CREATED_BY"));
	}

	@Test
	void create_landlordAWithOwnerIdOfLandlordB_storesLandlordBAsOwner_changesInW5() throws Exception {
		// Audit R3 (IDOR): the owner comes from the request body, never from the caller.
		// W6 changes it too: a LANDLORD caller is expected to lose create access altogether.
		String code = uniqueCode();

		Result result = post(landlordA, validBody(code, landlordB.userId()));

		assertEquals(201, result.status(), result.rawBody());
		assertEquals(landlordB.userId(), result.body().path("ownerId").asString());
		Map<String, Object> row = propertyRow(code);
		assertEquals(landlordB.userId(), row.get("OWNER_ID"), "owner_id is another landlord's id");
		assertEquals(landlordA.username(), row.get("CREATED_BY"), "created_by still shows the real caller");
	}

	@Test
	void create_ownerIdThatIsNoUser_returns201AndStoresIt_changesInW5() throws Exception {
		// No foreign key and no lookup: any non-blank string is accepted as owner
		String code = uniqueCode();

		Result result = post(admin, validBody(code, "not-a-user-id"));

		assertEquals(201, result.status(), result.rawBody());
		assertEquals("not-a-user-id", propertyRow(code).get("OWNER_ID"));
	}

	@Test
	void create_landlordWithUnverifiedPhone_returns201_changesInW6() throws Exception {
		// The rule checks only the role, not the PHONE_VERIFIED authority; a PENDING account can log in
		String code = uniqueCode();

		Result result = post(unverifiedLandlord, validBody(code, unverifiedLandlord.userId()));

		assertEquals(201, result.status(), result.rawBody());
		assertEquals(unverifiedLandlord.username(), propertyRow(code).get("CREATED_BY"));
	}

	// ==================== C4 / C5: access ====================

	@Test
	void create_tenant_returns403WithSecurityBodyAndStoresNothing() throws Exception {
		String code = uniqueCode();

		Result result = post(tenant, validBody(code, tenant.userId()));

		assertEquals(403, result.status(), result.rawBody());
		assertEquals("Forbidden", result.body().path("error").asString());
		assertEquals("You do not have permission to access this resource.", result.body().path("message").asString());
		assertEquals(ENDPOINT, result.body().path("path").asString());
		assertEquals(0, countPropertiesWithCode(code));
	}

	@Test
	void create_withoutToken_returns401JsonBodyAndStoresNothing() throws Exception {
		String code = uniqueCode();

		Result result = post(null, validBody(code, landlordA.userId()));

		assertEquals(401, result.status(), result.rawBody());
		assertEquals("Unauthorized", result.body().path("error").asString());
		assertFalse(result.body().path("message").asString().isBlank(), "message");
		assertEquals(ENDPOINT, result.body().path("path").asString());
		assertEquals(0, countPropertiesWithCode(code));
	}

	// ==================== C6 - C11: validation and errors ====================

	@Test
	void create_missingTitle_returns400ValidationEnvelope() throws Exception {
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		body.remove("title");

		Result result = post(admin, body);

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of("title", "Title cannot be null"), validationErrors(result.body()));
		assertEquals(0, countPropertiesWithCode(code));
	}

	@Test
	void create_duplicatePropertyCode_returns409AndKeepsTheFirstRow() throws Exception {
		String code = uniqueCode();
		assertEquals(201, post(admin, validBody(code, landlordA.userId())).status());

		Result result = post(admin, validBody(code, landlordB.userId()));

		assertEquals(409, result.status(), result.rawBody());
		assertEquals(1, countPropertiesWithCode(code));
		assertEquals(landlordA.userId(), propertyRow(code).get("OWNER_ID"));
	}

	@Test
	void create_unknownAmenityId_returns422AndStoresNothing() throws Exception {
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		body.put("amenityTypes", List.of(10, UNKNOWN_MASTER_ID));

		Result result = post(admin, body);

		assertEquals(422, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of("amenityTypes", "Referenced master data was not found."), validationErrors(result.body()));
		assertEquals(0, countPropertiesWithCode(code));
	}

	@Test
	void create_unknownRuleCategoryId_returns422AndStoresNothing() throws Exception {
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		body.put("ruleCategories", List.of(UNKNOWN_MASTER_ID));

		Result result = post(admin, body);

		assertEquals(422, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of("ruleCategories", "Referenced master data was not found."), validationErrors(result.body()));
		assertEquals(0, countPropertiesWithCode(code));
	}

	@Test
	void create_propertyCodeWithTooFewDigits_returns400WithFieldErrorAndStoresNothing() throws Exception {
		assertPropertyCodeRejectedWith400("WT-CRT-1", "Property code must follow the format XX-XXX-001.");
	}

	@Test
	void create_lowerCasePropertyCode_returns400WithFieldErrorAndStoresNothing() throws Exception {
		assertPropertyCodeRejectedWith400("wt-crt-001", "Property code must follow the format XX-XXX-001.");
	}

	@Test
	void create_cityOmitted_storesDaNang() throws Exception {
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		assertFalse(body.containsKey("city"));

		Result result = post(admin, body);

		assertEquals(201, result.status(), result.rawBody());
		assertEquals(DEFAULT_CITY, result.body().path("city").asString());
		assertEquals(DEFAULT_CITY, propertyRow(code).get("CITY"));
	}

	@Test
	void create_invalidPropertyType_returns400WithFieldErrorAndStoresNothing() throws Exception {
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		body.put("propertyType", "CASTLE");

		Result result = post(admin, body);

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of("type", "Invalid property type."), validationErrors(result.body()));
		assertEquals(0, countPropertiesWithCode(code));
	}

	@Test
	void create_imageWithBlankUrl_returns400WithFieldErrorAndStoresNothing() throws Exception {
		// images has no @Valid, so the nested @NotBlank imageUrl never runs; the domain PropertyImage rejects it
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		body.put("images", List.of(image("", true, 0)));

		Result result = post(admin, body);

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of("images", "Image URL cannot be blank."), validationErrors(result.body()));
		assertEquals(0, countPropertiesWithCode(code));
	}

	@Test
	void create_zeroMonthlyPriceAndZeroBedrooms_returns400WithBothFieldErrors() throws Exception {
		String code = uniqueCode();
		Map<String, Object> body = validBody(code, landlordA.userId());
		body.put("monthlyPrice", 0);
		body.put("bedrooms", 0);

		Result result = post(admin, body);

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of(
				"monthlyPrice", "MonthlyPrice must be > 0",
				"bedrooms", "Bedrooms must be > 0"), validationErrors(result.body()));
		assertEquals(0, countPropertiesWithCode(code));
	}

	@Test
	void login_missingCredentials_returns400ValidationEnvelope() throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(uri("/api/auth/login"))
						.timeout(REQUEST_TIMEOUT)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString("{}"))
						.build(),
				HttpResponse.BodyHandlers.ofString());
		Result result = new Result(response.statusCode(), json.readTree(response.body()), response.body());

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of(
				"username", "Phone numbers is mandatory.",
				"password", "Password is mandatory."), validationErrors(result.body()));
	}

	@Test
	void login_malformedJson_returns400() throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(uri("/api/auth/login"))
						.timeout(REQUEST_TIMEOUT)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString("{"))
						.build(),
				HttpResponse.BodyHandlers.ofString());

		assertEquals(400, response.statusCode(), response.body());
	}

	// ==================== Helpers ====================

	private void assertPropertyCodeRejectedWith400(String code, String expectedMessage) throws Exception {
		Result result = post(admin, validBody(code, landlordA.userId()));

		assertEquals(400, result.status(), result.rawBody());
		assertEquals("Validation failed.", result.body().path("message").asString());
		assertEquals(Map.of("propertyCode", expectedMessage), validationErrors(result.body()));
		assertEquals(0, countPropertiesWithCode(code));
	}

	private TestUser createUserAndLogIn(String role, boolean phoneVerified) throws Exception {
		String username = "09" + String.format("%08d", PHONE_SEQUENCE.getAndIncrement());
		String password = UUID.randomUUID().toString();
		Instant now = AppClock.currentUTC();

		AppUser user = AppUser.create(
				UserId.create(ulidGenerator.generateId()),
				"W2 " + role,
				Username.create(username),
				null,
				Password.create(passwordEncoder.encode(password)),
				ProfileImage.create(null),
				null,
				new HashSet<>(Set.of(role)),
				now);
		if (phoneVerified) {
			user.verifyPhone(now);
		}
		AppUser saved = userRepository.save(user);

		HttpResponse<String> login = http.send(
				HttpRequest.newBuilder(uri("/api/auth/login"))
						.timeout(REQUEST_TIMEOUT)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(
								json.writeValueAsString(Map.of("username", username, "password", password))))
						.build(),
				HttpResponse.BodyHandlers.ofString());
		// The login body holds the token, so it is never put in the failure message
		assertEquals(200, login.statusCode(), "login failed for the " + role + " test user, status " + login.statusCode());
		String accessToken = json.readTree(login.body()).path("result").path("accessToken").asString();
		assertFalse(accessToken.isBlank(), "login returned no access token for the " + role + " test user");

		return new TestUser(saved.getId().getValue(), username, accessToken);
	}

	private Result post(TestUser caller, Map<String, Object> body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(ENDPOINT))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
		if (caller != null) {
			request.header("Authorization", "Bearer " + caller.accessToken());
		}
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		String raw = response.body();
		JsonNode parsed = raw == null || raw.isBlank() ? json.missingNode() : json.readTree(raw);
		return new Result(response.statusCode(), parsed, "status " + response.statusCode() + ", body " + raw);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	/** PropertyCode only accepts {@code ^[A-Z]{2}-[A-Z]{3}-\d{3,}$} (e.g. DA-APT-001). */
	private static String uniqueCode() {
		return "WT-CRT-" + CODE_SEQUENCE.getAndIncrement();
	}

	/** Every required field, valid; no city, rules, amenities or images. */
	private static Map<String, Object> validBody(String propertyCode, String ownerId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("propertyCode", propertyCode);
		body.put("title", "Căn hộ test W2");
		body.put("description", "Characterization test property");
		body.put("street", "1 Bạch Đằng");
		body.put("ward", "Thạch Thang");
		body.put("district", "Hải Châu");
		body.put("propertyType", "STUDIO");
		body.put("bedrooms", 1);
		body.put("bathrooms", 1);
		body.put("squareMeters", 35.5);
		body.put("monthlyPrice", 5000000);
		body.put("depositPrice", 5000000);
		body.put("minRentMonths", 6);
		body.put("ownerId", ownerId);
		return body;
	}

	private static Map<String, Object> image(String url, boolean primary, int displayOrder) {
		Map<String, Object> image = new LinkedHashMap<>();
		image.put("imageUrl", url);
		image.put("imageKey", "key/" + displayOrder);
		image.put("caption", "caption " + displayOrder);
		image.put("isPrimary", primary);
		image.put("displayOrder", displayOrder);
		return image;
	}

	private static Map<String, String> validationErrors(JsonNode body) {
		Map<String, String> errors = new LinkedHashMap<>();
		List<String> fields = new ArrayList<>();
		for (JsonNode error : body.path("errors")) {
			fields.add(error.path("field").asString());
			errors.put(error.path("field").asString(), error.path("message").asString());
		}
		assertEquals(fields.size(), errors.size(), "one error per field: " + fields);
		return errors;
	}

	private Map<String, Object> propertyRow(String propertyCode) {
		return jdbc.queryForMap("select * from properties where property_code = ?", propertyCode);
	}

	private int countPropertiesWithCode(String propertyCode) {
		return jdbc.queryForObject("select count(*) from properties where property_code = ?", Integer.class, propertyCode);
	}

	private int countIds(String table, List<Integer> ids) {
		String placeholders = String.join(", ", ids.stream().map(id -> "?").toList());
		return jdbc.queryForObject("select count(*) from " + table + " where id in (" + placeholders + ")",
				Integer.class, ids.toArray());
	}

	private int countWherePropertyId(String table, String propertyId) {
		return jdbc.queryForObject("select count(*) from " + table + " where property_id = ?", Integer.class, propertyId);
	}
}
