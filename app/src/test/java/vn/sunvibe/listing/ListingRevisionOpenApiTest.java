package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The seven revision operations and their schemas in the contract (listing-publish E06-S2 spec section 4, E06S2-D1,
 * E06S2-D17, E06S2-D20; RD-3, RD-12): explicit operationIds and answers, every property required, nullable exactly where
 * section 4 says "or null", every closed list listed, and no request that could type a term. Read from
 * {@code /v3/api-docs} with the annotations of {@link ListingOpenApiTest}, which stays untouched (plan E06S2-P1), so its
 * context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingRevisionOpenApiTest {

	private static final String LISTING = "/api/v1/staff/listings/{listingId}";
	private static final String REVISIONS = LISTING + "/revisions";
	private static final String REVISION = REVISIONS + "/{revisionNo}";
	private static final List<String> STATUSES = List.of("DRAFT", "PENDING_REVIEW", "NEEDS_CHANGES", "REJECTED",
			"APPROVED", "LIVE", "REPLACED", "CANCELLED");
	private static final List<String> FEATURE_NAMES = List.of("airConditioner", "balcony", "carParking", "elevator",
			"fridge", "furnishing", "internet", "kitchen", "motorbikeParking", "noSharedLandlord", "pets", "washingMachine",
			"waterHeater");
	private static final Set<String> NULLABLE_FEATURES = Set.of("furnishing", "kitchen", "pets", "washingMachine");

	@Value("${local.server.port}")
	private int port;

	private JsonNode contract;

	@BeforeAll
	void readContract() throws Exception {
		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpResponse<String> response = http.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			contract = JsonMapper.builder().build().readTree(response.body());
		}
	}

	@Test
	void revisionSchemas_everyPropertyRequired_nullableOnlyAsSaid() {
		Map<String, List<String>> properties = new LinkedHashMap<>();
		Map<String, Set<String>> nullable = new LinkedHashMap<>();
		schema(properties, nullable, "ListingRevisionCommand", List.of("dataVersion"));
		schema(properties, nullable, "ListingRevisionContent", List.of("dataVersion", "description", "features", "title"),
				"description", "title");
		schema(properties, nullable, "ListingFeaturesInput", FEATURE_NAMES, NULLABLE_FEATURES.toArray(String[]::new));
		schema(properties, nullable, "ListingTermsCapture", List.of("dataVersion", "source"));
		schema(properties, nullable, "ListingRevisionChange", List.of("dataVersion", "listingId", "revisionNo", "status"));
		schema(properties, nullable, "ListingRevisionDetail", List.of("createdAt", "dataVersion", "description",
				"features", "listingId", "problems", "revisionNo", "status", "submittedAt", "termsSnapshot", "title",
				"unitSnapshot", "updatedAt"), "description", "submittedAt", "termsSnapshot", "title", "unitSnapshot");
		schema(properties, nullable, "ListingFeatures", FEATURE_NAMES, NULLABLE_FEATURES.toArray(String[]::new));
		schema(properties, nullable, "ListingTermsSnapshot", List.of("capturedAt", "pendingStartDate", "source", "terms"),
				"pendingStartDate");
		schema(properties, nullable, "ListingTerms", List.of("deposit", "mandatoryFees", "minimumTermMonths",
				"monthlyRent", "paymentPeriodMonths"));
		schema(properties, nullable, "ListingRent", List.of("amount", "currency", "unit"));
		schema(properties, nullable, "ListingDeposit", List.of("amount", "basis", "currency", "months"), "amount",
				"currency", "months");
		schema(properties, nullable, "ListingFee", List.of("amount", "basis", "chargeUnit", "currency", "name"), "amount",
				"chargeUnit", "currency");
		schema(properties, nullable, "ListingUnitSnapshot", List.of("areaSqm", "assetType", "bathrooms", "bedrooms",
				"provinceCode", "provinceName", "wardCode", "wardName"), "assetType", "provinceCode", "provinceName",
				"wardCode", "wardName");
		// listing-publish E06-S7 spec 4.3 (S-10): the fee's position, required and nullable
		schema(properties, nullable, "ListingRevisionProblem", List.of("part", "position", "rule"), "position");
		schema(properties, nullable, "ListingTermsSources", List.of("ended", "inEffect", "pending"), "inEffect",
				"pending");
		schema(properties, nullable, "ListingPendingTerms", List.of("startDate", "terms"));

		JsonNode schemas = contract.path("components").path("schemas");
		for (Map.Entry<String, List<String>> expected : properties.entrySet()) {
			JsonNode schema = schemas.path(expected.getKey());
			assertEquals(expected.getValue(), names(schema.path("properties")).stream().sorted().toList(),
					expected.getKey());
			assertEquals(expected.getValue(), requiredOf(schema), expected.getKey() + " " + schema);
			Set<String> nullableNow = new TreeSet<>();
			schema.path("properties").properties().forEach(property -> {
				if (isNullable(property.getValue())) {
					nullableNow.add(property.getKey());
				}
			});
			assertEquals(new TreeSet<>(nullable.get(expected.getKey())), nullableNow, expected.getKey());
		}
		JsonNode detail = schemas.path("ListingRevisionDetail").path("properties");
		assertEquals("int32", detail.path("revisionNo").path("format").asString());
		assertEquals("int64", detail.path("dataVersion").path("format").asString());
		assertEquals("int64", schemas.path("ListingRent").path("properties").path("amount").path("format").asString());
		assertEquals("int32",
				schemas.path("ListingTerms").path("properties").path("minimumTermMonths").path("format").asString());
		assertEquals("double",
				schemas.path("ListingUnitSnapshot").path("properties").path("areaSqm").path("format").asString());
		assertEquals("date",
				schemas.path("ListingPendingTerms").path("properties").path("startDate").path("format").asString());
		assertEquals("date-time", detail.path("createdAt").path("format").asString());
	}

	@Test
	void revisionSchemas_closedLists_allowableValues() {
		JsonNode schemas = contract.path("components").path("schemas");
		Map<String, List<String>> lists = new LinkedHashMap<>();
		lists.put("ListingRevisionChange.status", STATUSES);
		lists.put("ListingRevisionDetail.status", STATUSES);
		for (String features : List.of("ListingFeaturesInput", "ListingFeatures")) {
			lists.put(features + ".furnishing", List.of("FULL", "BASIC", "NONE"));
			lists.put(features + ".washingMachine", List.of("PRIVATE", "SHARED"));
			lists.put(features + ".kitchen", List.of("PRIVATE", "SHARED"));
			lists.put(features + ".pets", List.of("ALLOWED", "NOT_ALLOWED"));
		}
		lists.put("ListingTermsCapture.source", List.of("IN_EFFECT", "PENDING_START"));
		lists.put("ListingTermsSnapshot.source", List.of("IN_EFFECT", "PENDING_START"));
		lists.put("ListingRent.currency", List.of("VND"));
		lists.put("ListingRent.unit", List.of("MONTH"));
		lists.put("ListingDeposit.basis", List.of("MONTHS", "AMOUNT"));
		lists.put("ListingDeposit.currency", List.of("VND"));
		lists.put("ListingFee.basis", List.of("AMOUNT", "INCLUDED", "PROVIDER_TARIFF"));
		lists.put("ListingFee.currency", List.of("VND"));
		lists.put("ListingFee.chargeUnit", List.of("PER_MONTH", "PER_KWH", "PER_M3", "PER_PERSON_PER_MONTH",
				"PER_VEHICLE_PER_MONTH", "ONE_OFF"));
		lists.put("ListingUnitSnapshot.assetType", List.of("HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL",
				"APARTMENT"));
		lists.put("ListingRevisionProblem.part", List.of("TITLE", "DESCRIPTION", "TERMS", "FEE_NAME"));
		lists.put("ListingRevisionProblem.rule", List.of("REQUIRED", "TOO_SHORT", "TOO_LONG", "PHONE_NUMBER", "EMAIL",
				"LINK", "SOCIAL_HANDLE", "FORBIDDEN_WORD"));
		for (Map.Entry<String, List<String>> expected : lists.entrySet()) {
			String[] key = expected.getKey().split("\\.");
			JsonNode property = schemas.path(key[0]).path("properties").path(key[1]);
			List<String> values = new ArrayList<>();
			property.path("enum").forEach(value -> {
				if (!value.isNull()) {
					values.add(value.asString());
				}
			});
			assertEquals(expected.getValue(), values, expected.getKey() + " " + property);
		}
	}

	@Test
	void revisionRequests_noAmountTermOrFee_termsCannotBeTyped() {
		JsonNode schemas = contract.path("components").path("schemas");
		for (String request : List.of("ListingRevisionCommand", "ListingRevisionContent", "ListingFeaturesInput",
				"ListingTermsCapture")) {
			for (String name : names(schemas.path(request).path("properties"))) {
				String lower = name.toLowerCase(Locale.ROOT);
				for (String word : List.of("rent", "deposit", "term", "period", "fee", "amount", "price")) {
					assertFalse(lower.contains(word), request + "." + name);
				}
			}
		}
		Map<String, String> bodies = Map.of(
				"post " + REVISIONS, "ListingRevisionCommand",
				"put " + REVISION + "/content", "ListingRevisionContent",
				"put " + REVISION + "/terms", "ListingTermsCapture",
				"post " + REVISION + "/submission", "ListingRevisionCommand",
				"post " + REVISION + "/withdrawal", "ListingRevisionCommand");
		for (Map.Entry<String, String> expected : bodies.entrySet()) {
			String[] key = expected.getKey().split(" ");
			JsonNode body = contract.path("paths").path(key[1]).path(key[0]).path("requestBody");
			assertEquals("#/components/schemas/" + expected.getValue(),
					body.path("content").path("application/json").path("schema").path("$ref").asString(), expected.getKey());
		}
	}

	@Test
	void revisionOperations_idsAndCodes_asSection4() {
		Map<String, String> operations = new LinkedHashMap<>();
		operations.put("get " + REVISION, "getListingRevision");
		operations.put("post " + REVISIONS, "startListingRevision");
		operations.put("put " + REVISION + "/content", "saveListingRevisionContent");
		operations.put("put " + REVISION + "/terms", "captureListingRevisionTerms");
		operations.put("post " + REVISION + "/submission", "submitListingRevision");
		operations.put("post " + REVISION + "/withdrawal", "withdrawListingRevision");
		operations.put("get " + LISTING + "/terms-sources", "getListingTermsSources");
		Map<String, List<String>> answers = Map.of(
				"getListingRevision", List.of("200", "401", "403", "404"),
				"startListingRevision", List.of("201", "400", "401", "403", "404", "409"),
				"saveListingRevisionContent", List.of("200", "400", "401", "403", "404", "409"),
				"captureListingRevisionTerms", List.of("200", "400", "401", "403", "404", "409", "500"),
				"submitListingRevision", List.of("200", "400", "401", "403", "404", "409", "422", "500"),
				"withdrawListingRevision", List.of("200", "400", "401", "403", "404", "409"),
				"getListingTermsSources", List.of("200", "401", "403", "404", "500"));
		Map<String, String> successes = Map.of(
				"getListingRevision", "ListingRevisionDetail",
				"getListingTermsSources", "ListingTermsSources");
		Map<String, List<String>> conflicts = Map.of(
				"startListingRevision", List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION", "OPEN_REVISION_EXISTS"),
				"saveListingRevisionContent", List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION", "REVISION_NOT_DRAFT"),
				"captureListingRevisionTerms", List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION",
						"REVISION_NOT_DRAFT", "TERMS_SOURCE_ABSENT"),
				"submitListingRevision", List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION", "REVISION_NOT_DRAFT"),
				"withdrawListingRevision", List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION", "REVISION_NOT_PENDING"));
		for (Map.Entry<String, String> expected : operations.entrySet()) {
			String[] key = expected.getKey().split(" ");
			JsonNode operation = contract.path("paths").path(key[1]).path(key[0]);
			String operationId = expected.getValue();
			assertEquals(operationId, operation.path("operationId").asString(), expected.getKey());
			assertEquals("[\"Listing\"]", operation.path("tags").toString(), operationId);
			assertEquals(List.of(expected.getKey()), operationsWithId(operationId));
			JsonNode responses = operation.path("responses");
			assertEquals(answers.get(operationId), names(responses).stream().sorted().toList(), operationId);
			for (String status : answers.get(operationId)) {
				String schema = status.startsWith("2")
						? successes.getOrDefault(operationId, "ListingRevisionChange")
						: "ErrorResponse";
				assertEquals("#/components/schemas/" + schema, schemaRef(responses.path(status)), operationId + status);
			}
			assertTrue(responses.path("403").path("description").asString().contains("FORBIDDEN"), operationId);
			assertTrue(responses.path("404").path("description").asString().contains("NOT_FOUND"), operationId);
			for (String code : conflicts.getOrDefault(operationId, List.of())) {
				assertTrue(responses.path("409").path("description").asString().contains(code), operationId + code);
			}
			if (responses.has("400")) {
				assertTrue(responses.path("400").path("description").asString().contains("VALIDATION_FAILED"), operationId);
				assertTrue(responses.path("400").path("description").asString().contains("MALFORMED_REQUEST"), operationId);
			}
		}
		assertTrue(contract.path("paths").path(REVISION + "/submission").path("post").path("responses").path("422")
				.path("description").asString().contains("REVISION_NOT_SUBMITTABLE"));
		assertEquals(List.of("get"), names(contract.path("paths").path(REVISION)));
		assertEquals(List.of("post"), names(contract.path("paths").path(REVISIONS)));
	}

	@Test
	void listingDetail_openRevisionNo_int32Nullable() {
		JsonNode detail = contract.path("components").path("schemas").path("ListingDetail");
		JsonNode openRevisionNo = detail.path("properties").path("openRevisionNo");

		assertEquals("int32", openRevisionNo.path("format").asString(), openRevisionNo.toString());
		assertTrue(isNullable(openRevisionNo), openRevisionNo.toString());
		assertTrue(requiredOf(detail).contains("openRevisionNo"));
	}

	/** Nullable in OpenAPI 3.1: a type list with null, an anyOf with a null type, or an enum with null. */
	private static boolean isNullable(JsonNode property) {
		boolean[] nullable = {false};
		property.path("type").forEach(type -> nullable[0] |= "null".equals(type.asString()));
		property.path("anyOf").forEach(option -> nullable[0] |= "null".equals(option.path("type").asString()));
		property.path("oneOf").forEach(option -> nullable[0] |= "null".equals(option.path("type").asString()));
		return nullable[0];
	}

	private static void schema(Map<String, List<String>> properties, Map<String, Set<String>> nullable, String name,
			List<String> sortedProperties, String... nullableProperties) {
		properties.put(name, sortedProperties.stream().sorted().toList());
		nullable.put(name, Set.of(nullableProperties));
	}

	private List<String> operationsWithId(String operationId) {
		List<String> withTheId = new ArrayList<>();
		contract.path("paths").properties().forEach(entry -> entry.getValue().properties().forEach(operation -> {
			if (operationId.equals(operation.getValue().path("operationId").asString())) {
				withTheId.add(operation.getKey() + " " + entry.getKey());
			}
		}));
		return withTheId;
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}

	private static List<String> requiredOf(JsonNode schema) {
		List<String> required = new ArrayList<>();
		schema.path("required").forEach(field -> required.add(field.asString()));
		return required.stream().sorted().toList();
	}

	/** The schema of the one media type of an answer. */
	private static String schemaRef(JsonNode response) {
		List<JsonNode> contents = new ArrayList<>();
		response.path("content").forEach(contents::add);
		assertEquals(1, contents.size(), response.toString());
		return contents.get(0).path("schema").path("$ref").asString();
	}
}
