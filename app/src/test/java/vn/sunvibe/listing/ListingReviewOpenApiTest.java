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
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The six review operations and their nine schemas in the contract (listing-publish E06-S4 spec section 4, E06S4-D1,
 * E06S4-D2, E06S4-D6, E06S4-D15): explicit operationIds, the tag, each answer's status and the codes its description
 * names, the bodies by reference; every response property required, nullable exactly as section 4 says, each nullable
 * reference as {@code anyOf} of its reference and null, the closed lists and the list bounds listed, and no user id
 * anywhere. Read from {@code /v3/api-docs} with the annotations of {@link ListingOpenApiTest}, so its context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingReviewOpenApiTest {

	private static final String BASE = "/api/v1/staff/listings";
	private static final String REVISION = BASE + "/{listingId}/revisions/{revisionNo}";
	private static final Map<String, String> OPERATIONS = new LinkedHashMap<>();
	private static final List<String> SCHEMAS = List.of("ListingReviewQueue", "ListingReviewQueueItem",
			"ListingRevisionReview", "ListingReviewAdvertising", "ListingReviewUnit", "ListingReviewFreshness",
			"ListingRevisionDecisionRequest", "ListingRevisionDecision", "ListingUnitLocation");
	private static final List<String> REVIEW_REASONS = List.of("NOT_A_REVIEWER", "AUTHOR", "ASSIGNED_SALE",
			"NOT_PENDING", "LISTING_ARCHIVED");
	private static final List<String> CHECKS = List.of("CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES",
			"TERMS_MATCH_AGREEMENT", "NO_FRAUD_SIGNS", "AGREEMENT_END_AND_RIGHT");
	private static final List<String> CHANGE_REASONS = List.of("CONTENT_MISMATCH", "TEXT_RULE_VIOLATION",
			"MISSING_INFORMATION", "TERMS_MISMATCH", "OTHER");
	private static final List<String> REJECTION_GROUPS = List.of("SUSPECTED_FRAUD", "WRONG_UNIT", "SERIOUS_VIOLATION",
			"OTHER");

	static {
		OPERATIONS.put("getListingReviewQueue", "get " + BASE + "/review-queue");
		OPERATIONS.put("getListingRevisionReview", "get " + REVISION + "/review");
		OPERATIONS.put("decideListingRevision", "post " + REVISION + "/decision");
		OPERATIONS.put("getListingRevisionDecision", "get " + REVISION + "/decision");
		OPERATIONS.put("openListingUnitLocation", "post " + REVISION + "/unit-location");
		OPERATIONS.put("copyListingRevision", "post " + REVISION + "/copies");
	}

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
	void openApi_reviewOperations_idsRolesAndCodes() {
		Map<String, List<String>> found = new LinkedHashMap<>();
		OPERATIONS.keySet().forEach(operationId -> found.put(operationId, operationsWithId(operationId)));
		Map<String, List<String>> expected = new LinkedHashMap<>();
		OPERATIONS.forEach((operationId, operation) -> expected.put(operationId, List.of(operation)));
		assertEquals(expected, found);

		Map<String, List<String>> statuses = new LinkedHashMap<>();
		Map<String, String> successes = new LinkedHashMap<>();
		for (String operationId : OPERATIONS.keySet()) {
			JsonNode operation = operation(operationId);
			assertEquals("[\"Listing\"]", operation.path("tags").toString(), operationId);
			JsonNode responses = operation.path("responses");
			statuses.put(operationId, names(responses).stream().sorted().toList());
			for (String status : names(responses)) {
				String schema = schemaRef(responses.path(status));
				if (status.startsWith("2")) {
					successes.put(operationId, schema);
				} else {
					assertEquals("#/components/schemas/ErrorResponse", schema, operationId + " " + status);
				}
			}
			assertTrue(responses.path("403").path("description").asString().contains("FORBIDDEN"), operationId);
		}
		assertEquals(Map.of(
				"getListingReviewQueue", List.of("200", "401", "403", "500"),
				"getListingRevisionReview", List.of("200", "401", "403", "404", "500"),
				"decideListingRevision", List.of("200", "400", "401", "403", "404", "409", "500"),
				"getListingRevisionDecision", List.of("200", "401", "403", "404"),
				"openListingUnitLocation", List.of("200", "401", "403", "404", "409", "500"),
				"copyListingRevision", List.of("201", "400", "401", "403", "404", "409", "500")), statuses);
		assertEquals(Map.of(
				"getListingReviewQueue", "#/components/schemas/ListingReviewQueue",
				"getListingRevisionReview", "#/components/schemas/ListingRevisionReview",
				"decideListingRevision", "#/components/schemas/ListingRevisionChange",
				"getListingRevisionDecision", "#/components/schemas/ListingRevisionDecision",
				"openListingUnitLocation", "#/components/schemas/ListingUnitLocation",
				"copyListingRevision", "#/components/schemas/ListingRevisionChange"), successes);
		assertEquals("#/components/schemas/ListingRevisionDecisionRequest", requestBodyRef("decideListingRevision"));
		assertEquals("#/components/schemas/ListingRevisionCommand", requestBodyRef("copyListingRevision"));
		assertFalse(operation("openListingUnitLocation").has("requestBody"));

		// each description names its codes and, for the 403, the roles the gate admits
		Map<String, List<String>> codes = new LinkedHashMap<>();
		codes.put("getListingReviewQueue 403", List.of("SALE_LEADER", "SALES_MANAGER", "ADMIN", "LISTING_REVIEWER"));
		codes.put("getListingRevisionReview 403", List.of("SALE_LEADER", "SALES_MANAGER", "ADMIN", "LISTING_REVIEWER"));
		codes.put("getListingRevisionReview 404", List.of("NOT_FOUND"));
		codes.put("decideListingRevision 400", List.of("VALIDATION_FAILED", "MALFORMED_REQUEST",
				"decisionMatchesOutcome"));
		codes.put("decideListingRevision 403", List.of("LISTING_REVIEWER", "ADMIN", "REVIEWER_NOT_INDEPENDENT",
				"reasons.AUTHOR", "reasons.ASSIGNED_SALE"));
		codes.put("decideListingRevision 404", List.of("NOT_FOUND"));
		codes.put("decideListingRevision 409", List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION",
				"REVISION_NOT_PENDING", "REVISION_TERMS_MISMATCH"));
		codes.put("getListingRevisionDecision 403", List.of("SALE", "SALE_LEADER", "SALES_MANAGER", "ADMIN",
				"LISTING_REVIEWER"));
		codes.put("getListingRevisionDecision 404", List.of("NOT_FOUND"));
		codes.put("openListingUnitLocation 403", List.of("LISTING_REVIEWER", "ADMIN", "REVIEWER_NOT_INDEPENDENT"));
		codes.put("openListingUnitLocation 404", List.of("NOT_FOUND"));
		codes.put("openListingUnitLocation 409", List.of("LISTING_ARCHIVED", "REVISION_NOT_PENDING"));
		codes.put("copyListingRevision 400", List.of("VALIDATION_FAILED", "MALFORMED_REQUEST"));
		codes.put("copyListingRevision 403", List.of("SALE", "SALE_LEADER"));
		codes.put("copyListingRevision 404", List.of("NOT_FOUND"));
		codes.put("copyListingRevision 409", List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION",
				"REVISION_NOT_COPYABLE", "OPEN_REVISION_EXISTS"));
		List<String> missing = new ArrayList<>();
		codes.forEach((answer, names) -> {
			String[] parts = answer.split(" ");
			String description = operation(parts[0]).path("responses").path(parts[1]).path("description").asString();
			names.stream().filter(name -> !description.contains(name)).forEach(name -> missing.add(answer + " " + name));
		});
		assertEquals(List.of(), missing);
	}

	@Test
	void openApi_reviewSchemas_requiredEnumsAndNullables() {
		JsonNode schemas = contract.path("components").path("schemas");
		Map<String, List<String>> properties = new LinkedHashMap<>();
		Map<String, List<String>> required = new LinkedHashMap<>();
		Map<String, List<String>> nullable = new LinkedHashMap<>();
		for (String name : SCHEMAS) {
			JsonNode schema = schemas.path(name);
			properties.put(name, names(schema.path("properties")).stream().sorted().toList());
			List<String> requiredNow = new ArrayList<>();
			schema.path("required").forEach(field -> requiredNow.add(field.asString()));
			required.put(name, requiredNow.stream().sorted().toList());
			TreeSet<String> nullableNow = new TreeSet<>();
			schema.path("properties").properties().forEach(property -> {
				if (isNullable(property.getValue())) {
					nullableNow.add(property.getKey());
				}
			});
			nullable.put(name, List.copyOf(nullableNow));
		}
		Map<String, List<String>> expectedProperties = new LinkedHashMap<>();
		expectedProperties.put("ListingReviewQueue", List.of("hasMore", "items"));
		expectedProperties.put("ListingReviewQueueItem", List.of("code", "listingId", "notEligibleReasons",
				"revisionNo", "submittedAt", "title", "unit", "viewerMayReview"));
		expectedProperties.put("ListingRevisionReview", List.of("advertising", "checklist", "code", "dataVersion",
				"freshness", "listingId", "notEligibleReasons", "revisionNo", "status", "termsMatch", "unit",
				"viewerMayReview"));
		expectedProperties.put("ListingReviewAdvertising", List.of("allowed", "endDate", "ended", "exteriorPhotos",
				"interiorPhotos", "pending", "reasonGroups", "termsInEffect"));
		expectedProperties.put("ListingReviewUnit", List.of("availability", "availabilityDiffers", "unitCode"));
		expectedProperties.put("ListingReviewFreshness", List.of("confirmedOn", "deadlinePassed", "latestValid",
				"validThrough"));
		expectedProperties.put("ListingRevisionDecisionRequest", List.of("changeReasons", "checks", "dataVersion",
				"note", "outcome", "rejectionGroup"));
		expectedProperties.put("ListingRevisionDecision", List.of("changeReasons", "checklistVersion", "decidedAt",
				"note", "outcome", "rejectionGroup"));
		expectedProperties.put("ListingUnitLocation", List.of("locationLabel", "street", "unitCode", "wardName"));
		assertEquals(expectedProperties, properties);
		Map<String, List<String>> expectedRequired = new LinkedHashMap<>(expectedProperties);
		expectedRequired.put("ListingRevisionDecisionRequest", List.of("dataVersion", "outcome"));
		assertEquals(expectedRequired, required);
		Map<String, List<String>> expectedNullable = new LinkedHashMap<>();
		expectedNullable.put("ListingReviewQueue", List.of());
		expectedNullable.put("ListingReviewQueueItem", List.of());
		expectedNullable.put("ListingRevisionReview", List.of("advertising"));
		expectedNullable.put("ListingReviewAdvertising", List.of("endDate", "pending", "termsInEffect"));
		expectedNullable.put("ListingReviewUnit", List.of("availability", "unitCode"));
		expectedNullable.put("ListingReviewFreshness", List.of("confirmedOn", "latestValid", "validThrough"));
		expectedNullable.put("ListingRevisionDecisionRequest", List.of("changeReasons", "checks", "note",
				"rejectionGroup"));
		expectedNullable.put("ListingRevisionDecision", List.of("checklistVersion", "note", "rejectionGroup"));
		expectedNullable.put("ListingUnitLocation", List.of("locationLabel", "street", "unitCode", "wardName"));
		assertEquals(expectedNullable, nullable);

		// the nullable references are anyOf of the reference and null
		Map<String, String> references = new LinkedHashMap<>();
		references.put("ListingRevisionReview.advertising", "#/components/schemas/ListingReviewAdvertising");
		references.put("ListingReviewAdvertising.termsInEffect", "#/components/schemas/ListingTerms");
		references.put("ListingReviewAdvertising.pending", "#/components/schemas/ListingPendingTerms");
		references.forEach((key, reference) -> {
			JsonNode property = property(schemas, key);
			List<String> options = new ArrayList<>();
			property.path("anyOf").forEach(option -> options.add(option.has("$ref") ? option.path("$ref").asString()
					: option.path("type").asString()));
			assertEquals(List.of(reference, "null"), options, key + " " + property);
		});
		assertEquals("#/components/schemas/ListingUnitSnapshot",
				property(schemas, "ListingReviewQueueItem.unit").path("$ref").asString());
		assertEquals("#/components/schemas/ListingReviewUnit",
				property(schemas, "ListingRevisionReview.unit").path("$ref").asString());
		assertEquals("#/components/schemas/ListingReviewFreshness",
				property(schemas, "ListingRevisionReview.freshness").path("$ref").asString());

		// the closed lists, null listed where the value may be null
		Map<String, List<String>> closed = new LinkedHashMap<>();
		closed.put("ListingReviewQueueItem.notEligibleReasons[]", REVIEW_REASONS.subList(0, 3));
		closed.put("ListingRevisionReview.status", List.of("DRAFT", "PENDING_REVIEW", "NEEDS_CHANGES", "REJECTED",
				"APPROVED", "LIVE", "REPLACED", "CANCELLED"));
		closed.put("ListingRevisionReview.notEligibleReasons[]", REVIEW_REASONS);
		closed.put("ListingRevisionReview.termsMatch", List.of("IN_EFFECT", "PENDING_START", "NONE", "NO_SNAPSHOT"));
		closed.put("ListingRevisionReview.checklist[]", CHECKS);
		closed.put("ListingReviewAdvertising.reasonGroups[]", List.of("NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED",
				"SUSPENDED", "ENDED", "BASIS", "PROPERTY", "UNIT", "OWNER"));
		closed.put("ListingReviewUnit.availability", List.of("AVAILABLE", "OCCUPIED", "MAINTENANCE", "BLOCKED", "null"));
		closed.put("ListingRevisionDecisionRequest.outcome", List.of("APPROVED", "NEEDS_CHANGES", "REJECTED"));
		closed.put("ListingRevisionDecisionRequest.checks[]", CHECKS);
		closed.put("ListingRevisionDecisionRequest.changeReasons[]", CHANGE_REASONS);
		closed.put("ListingRevisionDecisionRequest.rejectionGroup", Stream.concat(REJECTION_GROUPS.stream(),
				Stream.of("null")).toList());
		closed.put("ListingRevisionDecision.outcome", List.of("APPROVED", "NEEDS_CHANGES", "REJECTED"));
		closed.put("ListingRevisionDecision.changeReasons[]", CHANGE_REASONS);
		closed.put("ListingRevisionDecision.rejectionGroup", Stream.concat(REJECTION_GROUPS.stream(),
				Stream.of("null")).toList());
		Map<String, List<String>> listed = new LinkedHashMap<>();
		for (String key : closed.keySet()) {
			JsonNode property = key.endsWith("[]") ? property(schemas, key.substring(0, key.length() - 2)).path("items")
					: property(schemas, key);
			List<String> values = new ArrayList<>();
			property.path("enum").forEach(value -> values.add(value.isNull() ? "null" : value.asString()));
			listed.put(key, values);
		}
		assertEquals(closed, listed);

		// the list bounds and the note's edge bound
		JsonNode request = schemas.path("ListingRevisionDecisionRequest").path("properties");
		assertEquals(5, request.path("checks").path("maxItems").asInt(-1), request.path("checks").toString());
		assertEquals(5, request.path("changeReasons").path("maxItems").asInt(-1),
				request.path("changeReasons").toString());
		assertEquals(4000, request.path("note").path("maxLength").asInt(-1));
		assertEquals("int64", request.path("dataVersion").path("format").asString());
		assertEquals("int64", schemas.path("ListingRevisionReview").path("properties").path("dataVersion")
				.path("format").asString());

		// no user id in any of them: the decision never names who decided (E06S4-D15)
		List<String> userIds = new ArrayList<>();
		for (String name : SCHEMAS) {
			names(schemas.path(name).path("properties")).stream()
					.filter(property -> {
						String lower = property.toLowerCase(Locale.ROOT);
						return lower.contains("reviewer") || lower.endsWith("by") || lower.contains("userid")
								|| lower.contains("actor");
					})
					.forEach(property -> userIds.add(name + "." + property));
		}
		assertEquals(List.of(), userIds);
	}

	private static JsonNode property(JsonNode schemas, String key) {
		String[] parts = key.split("\\.");
		return schemas.path(parts[0]).path("properties").path(parts[1]);
	}

	private String requestBodyRef(String operationId) {
		return operation(operationId).path("requestBody").path("content").path("application/json").path("schema")
				.path("$ref").asString();
	}

	/** The reference of an answer's body, whatever media type springdoc writes it under. */
	private static String schemaRef(JsonNode answer) {
		String[] reference = {""};
		answer.path("content").properties().forEach(media -> {
			if (reference[0].isEmpty()) {
				reference[0] = media.getValue().path("schema").path("$ref").asString("");
			}
		});
		return reference[0];
	}

	private JsonNode operation(String operationId) {
		JsonNode[] found = {null};
		contract.path("paths").properties().forEach(entry -> entry.getValue().properties().forEach(operation -> {
			if (operationId.equals(operation.getValue().path("operationId").asString())) {
				found[0] = operation.getValue();
			}
		}));
		return found[0] == null ? JsonMapper.builder().build().missingNode() : found[0];
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

	/** Nullable in OpenAPI 3.1: a type list with null, an anyOf with a null type, or a nullable flag. */
	private static boolean isNullable(JsonNode property) {
		boolean[] nullable = {property.path("nullable").asBoolean(false)};
		JsonNode type = property.path("type");
		if (type.isArray()) {
			type.forEach(option -> nullable[0] |= "null".equals(option.asString()));
		} else {
			nullable[0] |= "null".equals(type.asString(""));
		}
		property.path("anyOf").forEach(option -> nullable[0] |= "null".equals(option.path("type").asString()));
		return nullable[0];
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}
}
