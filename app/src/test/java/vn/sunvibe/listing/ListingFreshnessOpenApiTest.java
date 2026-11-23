package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * The three freshness operations and their six schemas in the contract (listing-publish E06-S3 spec section 4,
 * E06S3-D1, E06S3-D15; FR-2, FR-11): explicit operationIds and answers, the tag, every JSON answer named, every property
 * required, nullable exactly as section 4 says, {@code latest} as {@code anyOf} of its reference and null, the closed
 * lists listed. Read from {@code /v3/api-docs} with the annotations of {@link ListingOpenApiTest}, so its context is
 * reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingFreshnessOpenApiTest {

	private static final String RECORDS = "/api/v1/staff/listings/{listingId}/freshness-records";
	private static final String FRESHNESS = "/api/v1/staff/listings/{listingId}/freshness";
	private static final String RECONFIRMATIONS = "/api/v1/staff/listings/reconfirmations";
	private static final List<String> OPERATION_IDS = List.of("recordListingFreshness", "getListingFreshness",
			"getListingReconfirmations");
	private static final List<String> SCHEMAS = List.of("ListingFreshnessRecordRequest", "ListingFreshnessRecorded",
			"ListingFreshness", "ListingFreshnessRecord", "ListingReconfirmations", "ListingReconfirmationItem");

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
	void openApi_freshnessOperations_idsTagsAndAnswers() {
		assertEquals(List.of(List.of("post " + RECORDS), List.of("get " + FRESHNESS), List.of("get " + RECONFIRMATIONS)),
				OPERATION_IDS.stream().map(this::operationsWithId).toList());

		Map<String, List<String>> statuses = new LinkedHashMap<>();
		Map<String, String> tags = new LinkedHashMap<>();
		List<String> unnamedAnswers = new ArrayList<>();
		for (String operationId : OPERATION_IDS) {
			JsonNode operation = operation(operationId);
			statuses.put(operationId, names(operation.path("responses")).stream().sorted().toList());
			tags.put(operationId, operation.path("tags").toString());
			// every media type of every answer: springdoc writes the JSON bodies under */*
			operation.path("responses").properties().forEach(answer -> answer.getValue().path("content").properties()
					.forEach(media -> {
						if (!media.getValue().path("schema").has("$ref")) {
							unnamedAnswers.add(operationId + " " + answer.getKey() + " " + media.getKey());
						}
					}));
		}
		assertEquals(Map.of(
				"recordListingFreshness", List.of("201", "400", "401", "403", "404", "409", "500"),
				"getListingFreshness", List.of("200", "401", "403", "404", "500"),
				"getListingReconfirmations", List.of("200", "401", "403", "500")), statuses);
		assertEquals(Map.of(
				"recordListingFreshness", "[\"Listing\"]",
				"getListingFreshness", "[\"Listing\"]",
				"getListingReconfirmations", "[\"Listing\"]"), tags);
		assertEquals(List.of(), unnamedAnswers);
	}

	@Test
	void openApi_freshnessSchemas_exactPropertiesRequiredNullable() {
		JsonNode schemas = contract.path("components").path("schemas");
		JsonNode request = schemas.path("ListingFreshnessRecordRequest").path("properties");

		assertEquals(2000, request.path("note").path("maxLength").asInt());
		assertEquals("int64", request.path("dataVersion").path("format").asString());
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
		assertEquals(properties, required);
		assertEquals(Map.of(
				"ListingFreshnessRecordRequest", List.of("note", "termsStillApply", "unitUnchanged"),
				"ListingFreshnessRecorded", List.of(),
				"ListingFreshness", List.of("confirmedOn", "latest", "reconfirmFrom", "validThrough"),
				"ListingFreshnessRecord", List.of("note", "termsStillApply", "unitUnchanged"),
				"ListingReconfirmations", List.of(),
				"ListingReconfirmationItem", List.of()), nullable);
		assertEquals(List.of("channel", "dataVersion", "note", "outcome", "termsStillApply", "unitUnchanged"),
				properties.get("ListingFreshnessRecordRequest"));
		assertEquals(Map.of(
				"ListingFreshnessRecorded", List.of("dataVersion", "listingId", "valid"),
				"ListingFreshness", List.of("confirmedOn", "deadlinePassed", "latest", "listingId", "reconfirmFrom",
						"reconfirmationDue", "validThrough"),
				"ListingFreshnessRecord", List.of("channel", "contactDate", "note", "outcome", "recordedAt",
						"termsStillApply", "unitUnchanged", "valid"),
				"ListingReconfirmations", List.of("hasMore", "items"),
				"ListingReconfirmationItem", List.of("code", "confirmedOn", "deadlinePassed", "latestRecordValid",
						"listingId", "reconfirmFrom", "state", "validThrough")),
				Map.of(
						"ListingFreshnessRecorded", properties.get("ListingFreshnessRecorded"),
						"ListingFreshness", properties.get("ListingFreshness"),
						"ListingFreshnessRecord", properties.get("ListingFreshnessRecord"),
						"ListingReconfirmations", properties.get("ListingReconfirmations"),
						"ListingReconfirmationItem", properties.get("ListingReconfirmationItem")));
	}

	@Test
	void openApi_freshnessLatest_anyOfReferenceAndNull() {
		JsonNode latest = contract.path("components").path("schemas").path("ListingFreshness").path("properties")
				.path("latest");

		assertEquals(List.of(), Stream.of("type", "nullable").filter(latest::has).toList(), latest.toString());
		List<String> options = new ArrayList<>();
		latest.path("anyOf").forEach(option -> options.add(option.has("$ref") ? option.path("$ref").asString()
				: option.path("type").asString()));
		assertEquals(List.of("#/components/schemas/ListingFreshnessRecord", "null"), options, latest.toString());
	}

	@Test
	void openApi_freshnessEnums_allowableValuesNotNullable() {
		JsonNode schemas = contract.path("components").path("schemas");
		Map<String, List<String>> closed = new LinkedHashMap<>();
		closed.put("ListingFreshnessRecordRequest.channel", List.of("CALL", "MESSAGE", "IN_PERSON"));
		closed.put("ListingFreshnessRecordRequest.outcome", List.of("AVAILABLE", "NOT_AVAILABLE", "UNREACHABLE"));
		closed.put("ListingFreshnessRecord.channel", List.of("CALL", "MESSAGE", "IN_PERSON"));
		closed.put("ListingFreshnessRecord.outcome", List.of("AVAILABLE", "NOT_AVAILABLE", "UNREACHABLE"));
		closed.put("ListingReconfirmationItem.state", List.of("UNPUBLISHED", "PUBLISHED", "PAUSED",
				"URGENTLY_UNPUBLISHED"));

		for (String key : closed.keySet()) {
			JsonNode property = property(schemas, key);
			assertFalse(isNullable(property) || enumHasNull(property), key + " " + property);
		}
		Map<String, List<String>> listed = new LinkedHashMap<>();
		for (String key : closed.keySet()) {
			List<String> values = new ArrayList<>();
			property(schemas, key).path("enum").forEach(value -> values.add(value.asString()));
			listed.put(key, values);
		}
		assertEquals(closed, listed);
	}

	private static JsonNode property(JsonNode schemas, String key) {
		String[] parts = key.split("\\.");
		return schemas.path(parts[0]).path("properties").path(parts[1]);
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

	private static boolean enumHasNull(JsonNode property) {
		boolean[] hasNull = {false};
		property.path("enum").forEach(value -> hasNull[0] |= value.isNull());
		return hasNull[0];
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}
}
