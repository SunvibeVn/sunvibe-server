package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The three publication operations and their six schemas in the contract (listing-publish E06-S6 spec sections 4.1 to
 * 4.4; PL-12, PL-15): explicit operationIds, the tag, each answer's status and the codes its description names, the
 * bodies by reference; every property required, only {@code live} and {@code approved} nullable, each nullable reference
 * as {@code anyOf} of its reference and null, the closed lists listed; and the changed texts of the copy, the decision
 * and the freshness record, word for word. Read from {@code /v3/api-docs} with the annotations of
 * {@link ListingReviewOpenApiTest}, so its context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingPublicationOpenApiTest {

	private static final String BASE = "/api/v1/staff/listings";
	private static final String REVISION = BASE + "/{listingId}/revisions/{revisionNo}";
	private static final List<String> SCHEMAS = List.of("ListingPublication", "ListingPublicationLive",
			"ListingPublicationApproved", "ListingDisplayAnswer", "ListingLiveDifference", "ListingLiveRevision");
	private static final List<String> REASONS = List.of("LISTING_STATE", "ADVERTISING_RIGHT", "TERMS_NOT_IN_EFFECT",
			"UNIT_NOT_AVAILABLE", "FRESHNESS");
	private static final List<String> GROUPS = List.of("NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED", "SUSPENDED",
			"ENDED", "BASIS", "PROPERTY", "UNIT", "OWNER");

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
	void operations_pathsMethodsAndCodes() {
		Map<String, String> operations = new LinkedHashMap<>();
		operations.put("putListingRevisionLive", "post " + REVISION + "/publication");
		operations.put("getListingPublication", "get " + BASE + "/{listingId}/publication");
		operations.put("getListingRevisionLiveDifference", "get " + REVISION + "/live-difference");
		Map<String, List<String>> found = new LinkedHashMap<>();
		Map<String, List<String>> expected = new LinkedHashMap<>();
		operations.forEach((operationId, operation) -> {
			found.put(operationId, operationsWithId(operationId));
			expected.put(operationId, List.of(operation));
		});
		assertEquals(expected, found);

		Map<String, List<String>> statuses = new LinkedHashMap<>();
		Map<String, String> successes = new LinkedHashMap<>();
		List<String> wrong = new ArrayList<>();
		for (String operationId : operations.keySet()) {
			JsonNode operation = operation(operationId);
			if (!"[\"Listing\"]".equals(operation.path("tags").toString())) {
				wrong.add(operationId + " tags " + operation.path("tags"));
			}
			JsonNode responses = operation.path("responses");
			statuses.put(operationId, names(responses).stream().sorted().toList());
			for (String status : names(responses)) {
				String schema = schemaRef(responses.path(status));
				if (status.startsWith("2")) {
					successes.put(operationId, schema);
				} else if (!"#/components/schemas/ErrorResponse".equals(schema)) {
					wrong.add(operationId + " " + status + " " + schema);
				}
			}
		}
		assertEquals(List.of(), wrong);
		assertEquals(Map.of(
				"putListingRevisionLive", List.of("200", "400", "401", "403", "404", "409", "500"),
				"getListingPublication", List.of("200", "401", "403", "404", "500"),
				"getListingRevisionLiveDifference", List.of("200", "401", "403", "404", "500")), statuses);
		assertEquals(Map.of(
				"putListingRevisionLive", "#/components/schemas/ListingRevisionChange",
				"getListingPublication", "#/components/schemas/ListingPublication",
				"getListingRevisionLiveDifference", "#/components/schemas/ListingLiveDifference"), successes);
		assertEquals("#/components/schemas/ListingRevisionCommand", operation("putListingRevisionLive")
				.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asString());

		// each description names its codes and, for the 403, the roles the gate admits
		Map<String, List<String>> codes = new LinkedHashMap<>();
		codes.put("putListingRevisionLive 400", List.of("VALIDATION_FAILED", "MALFORMED_REQUEST"));
		codes.put("putListingRevisionLive 403", List.of("FORBIDDEN", "SALE", "SALE_LEADER"));
		codes.put("putListingRevisionLive 404", List.of("NOT_FOUND"));
		List<String> refusal = new ArrayList<>(List.of("LISTING_ARCHIVED", "CONCURRENT_MODIFICATION",
				"REVISION_NOT_APPROVED", "PUT_LIVE_REFUSED", "reasonGroups."));
		REASONS.forEach(reason -> refusal.add("reasons." + reason));
		codes.put("putListingRevisionLive 409", refusal);
		codes.put("putListingRevisionLive 500", List.of("INTERNAL_ERROR"));
		codes.put("getListingPublication 403", List.of("FORBIDDEN", "SALE", "SALE_LEADER", "SALES_MANAGER", "ADMIN",
				"LISTING_REVIEWER"));
		codes.put("getListingPublication 404", List.of("NOT_FOUND"));
		codes.put("getListingPublication 500", List.of("INTERNAL_ERROR"));
		codes.put("getListingRevisionLiveDifference 403", List.of("FORBIDDEN", "SALE_LEADER", "SALES_MANAGER", "ADMIN",
				"LISTING_REVIEWER"));
		codes.put("getListingRevisionLiveDifference 404", List.of("NOT_FOUND"));
		codes.put("getListingRevisionLiveDifference 500", List.of("INTERNAL_ERROR"));
		List<String> missing = new ArrayList<>();
		codes.forEach((answer, names) -> {
			String[] parts = answer.split(" ");
			String description = operation(parts[0]).path("responses").path(parts[1]).path("description").asString();
			names.stream().filter(name -> !description.contains(name)).forEach(name -> missing.add(answer + " " + name));
		});
		assertEquals(List.of(), missing);
	}

	@Test
	void schemas_requiredAndNullable() {
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
		expectedProperties.put("ListingPublication", List.of("approved", "dataVersion", "listingId", "live", "state",
				"viewerMayCopy", "viewerMayPutLive"));
		expectedProperties.put("ListingPublicationLive", List.of("display", "revisionNo", "wentLiveAt"));
		expectedProperties.put("ListingPublicationApproved", List.of("display", "revisionNo", "termsSource"));
		expectedProperties.put("ListingDisplayAnswer", List.of("holds", "reasonGroups", "reasons"));
		expectedProperties.put("ListingLiveDifference", List.of("changedParts", "comparison", "live", "revisionNo"));
		expectedProperties.put("ListingLiveRevision", List.of("description", "features", "revisionNo", "terms", "title",
				"wentLiveAt"));
		assertEquals(expectedProperties, properties);
		assertEquals(expectedProperties, required);
		Map<String, List<String>> expectedNullable = new LinkedHashMap<>();
		SCHEMAS.forEach(name -> expectedNullable.put(name, List.of()));
		expectedNullable.put("ListingPublication", List.of("approved", "live"));
		expectedNullable.put("ListingLiveDifference", List.of("live"));
		assertEquals(expectedNullable, nullable);

		// the nullable references are anyOf of the reference and null; the others plain references
		Map<String, String> nullableReferences = new LinkedHashMap<>();
		nullableReferences.put("ListingPublication.live", "#/components/schemas/ListingPublicationLive");
		nullableReferences.put("ListingPublication.approved", "#/components/schemas/ListingPublicationApproved");
		nullableReferences.put("ListingLiveDifference.live", "#/components/schemas/ListingLiveRevision");
		Map<String, List<String>> options = new LinkedHashMap<>();
		Map<String, List<String>> expectedOptions = new LinkedHashMap<>();
		nullableReferences.forEach((key, reference) -> {
			List<String> found = new ArrayList<>();
			property(schemas, key).path("anyOf").forEach(option -> found.add(option.has("$ref")
					? option.path("$ref").asString() : option.path("type").asString()));
			options.put(key, found);
			expectedOptions.put(key, List.of(reference, "null"));
		});
		assertEquals(expectedOptions, options);
		Map<String, String> references = new LinkedHashMap<>();
		references.put("ListingPublicationLive.display", "#/components/schemas/ListingDisplayAnswer");
		references.put("ListingPublicationApproved.display", "#/components/schemas/ListingDisplayAnswer");
		references.put("ListingLiveRevision.features", "#/components/schemas/ListingFeatures");
		references.put("ListingLiveRevision.terms", "#/components/schemas/ListingTermsSnapshot");
		Map<String, String> foundReferences = new LinkedHashMap<>();
		references.keySet().forEach(key -> foundReferences.put(key, property(schemas, key).path("$ref").asString()));
		assertEquals(references, foundReferences);

		// the closed lists
		Map<String, List<String>> closed = new LinkedHashMap<>();
		closed.put("ListingPublication.state", List.of("UNPUBLISHED", "PUBLISHED", "PAUSED", "URGENTLY_UNPUBLISHED",
				"ARCHIVED"));
		closed.put("ListingPublicationApproved.termsSource", List.of("IN_EFFECT", "PENDING_START"));
		closed.put("ListingDisplayAnswer.reasons[]", REASONS);
		closed.put("ListingDisplayAnswer.reasonGroups[]", GROUPS);
		closed.put("ListingLiveDifference.comparison", List.of("COMPARED", "NO_LIVE_REVISION", "IS_LIVE_REVISION"));
		closed.put("ListingLiveDifference.changedParts[]", List.of("TITLE", "DESCRIPTION", "FEATURES", "TERMS"));
		Map<String, List<String>> listed = new LinkedHashMap<>();
		for (String key : closed.keySet()) {
			JsonNode property = key.endsWith("[]") ? property(schemas, key.substring(0, key.length() - 2)).path("items")
					: property(schemas, key);
			List<String> values = new ArrayList<>();
			property.path("enum").forEach(value -> values.add(value.isNull() ? "null" : value.asString()));
			listed.put(key, values);
		}
		assertEquals(closed, listed);

		// the formats of the numbers
		Map<String, String> formats = new LinkedHashMap<>();
		formats.put("ListingPublication.dataVersion", "int64");
		formats.put("ListingPublicationLive.revisionNo", "int32");
		formats.put("ListingPublicationApproved.revisionNo", "int32");
		formats.put("ListingLiveDifference.revisionNo", "int32");
		formats.put("ListingLiveRevision.revisionNo", "int32");
		Map<String, String> foundFormats = new LinkedHashMap<>();
		formats.keySet().forEach(key -> foundFormats.put(key, property(schemas, key).path("format").asString()));
		assertEquals(formats, foundFormats);
	}

	@Test
	void texts_existingOperations() {
		Map<String, String> texts = new LinkedHashMap<>();
		texts.put("copyListingRevision summary", "Copy a Live, Approved or Needs changes revision into a new Draft with"
				+ " its content and terms snapshot");
		texts.put("copyListingRevision 409", "LISTING_ARCHIVED, CONCURRENT_MODIFICATION (the data version is stale),"
				+ " REVISION_NOT_COPYABLE (the revision is not Live, Approved or Needs changes) or OPEN_REVISION_EXISTS");
		texts.put("decideListingRevision summary", "Approve, return or reject a Pending review revision; an approval"
				+ " tries to put it live");
		texts.put("decideListingRevision 200", "Decided: status is the revision's status after the command (APPROVED,"
				+ " or LIVE when the approval put it live; NEEDS_CHANGES or REJECTED) and dataVersion the listing's new"
				+ " data version");
		texts.put("decideListingRevision 500", "INTERNAL_ERROR: the agreement's terms break the contract, or the put"
				+ " live meets an E05 group or availability code listing does not know; nothing is written");
		texts.put("recordListingFreshness 409", "LISTING_ARCHIVED or CONCURRENT_MODIFICATION (the data version is"
				+ " stale, or another command moved it meanwhile)");
		texts.put("recordListingFreshness 500", "INTERNAL_ERROR (also when the put live a valid confirmation tries"
				+ " meets an E05 group or availability code listing does not know; nothing is written)");
		Map<String, String> found = new LinkedHashMap<>();
		texts.keySet().forEach(key -> {
			String[] parts = key.split(" ");
			JsonNode operation = operation(parts[0]);
			found.put(key, "summary".equals(parts[1]) ? operation.path("summary").asString()
					: operation.path("responses").path(parts[1]).path("description").asString());
		});
		assertEquals(texts, found);
	}

	private static JsonNode property(JsonNode schemas, String key) {
		String[] parts = key.split("\\.");
		return schemas.path(parts[0]).path("properties").path(parts[1]);
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
