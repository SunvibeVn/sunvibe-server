package vn.sunvibe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * Shapes the contract had wrong while the wire was right (contract-wire spec 5.1, S-3, S-5, S-6): closed sets written as
 * plain strings, the amount exported as a string, and answers described as an empty object. Read from
 * {@code /v3/api-docs} on the H2 test profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiContractShapesTest {

	private static final String ERROR_RESPONSE = "#/components/schemas/ErrorResponse";

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
	void propertyAnswer_statusAndType_areEnums() {
		JsonNode properties = schema("PropertyCreateResponse").path("properties");

		assertEquals(List.of("AVAILABLE", "UNAVAILABLE", "RENTED", "SOLD", "UNDER_MAINTENANCE", "PENDING_APPROVAL", "DRAFT"),
				values(properties.path("status")));
		assertEquals(List.of("STUDIO", "ONE_BEDROOM", "TWO_BEDROOM", "THREE_BEDROOM", "FOUR_BEDROOM", "DUPLEX", "PENTHOUSE"),
				values(properties.path("propertyType")));
	}

	/** ADR-006: a whole number of VND. The Java field stays decimal so that 1500.5 reaches the validator (spec D5-5). */
	@Test
	void askingPriceAmount_isAnIntegerWithItsBounds() {
		JsonNode amount = schema("AskingPriceRequest").path("properties").path("amount");

		assertEquals("integer", amount.path("type").asString(), amount.toString());
		assertEquals("int64", amount.path("format").asString(), amount.toString());
		assertEquals(1L, amount.path("minimum").asLong(), amount.toString());
		assertEquals(999_999_999_999_999L, amount.path("maximum").asLong(), amount.toString());
	}

	/** The wire has no offset (2026-11-05T19:59:18.1712426), so {@code date-time} would be a false promise (spec D5-6). */
	@Test
	void propertyAnswer_createdAt_isNotADateTime() {
		JsonNode createdAt = schema("PropertyCreateResponse").path("properties").path("createdAt");

		assertEquals("string", createdAt.path("type").asString(), createdAt.toString());
		assertEquals("local-date-time", createdAt.path("format").asString(), createdAt.toString());
		assertFalse(createdAt.path("description").asString().isBlank(), createdAt.toString());
	}

	@Test
	void roleAdmin_401And403_areErrorResponse() {
		JsonNode roles = contract.path("paths").path("/api/v1/staff/users/{userId}/roles");
		JsonNode role = contract.path("paths").path("/api/v1/staff/users/{userId}/roles/{role}");

		for (JsonNode operation : List.of(roles.path("get"), role.path("put"), role.path("delete"))) {
			assertEquals(ERROR_RESPONSE, responseSchema(operation, "401").path("$ref").asString(), operation.toString());
			assertEquals(ERROR_RESPONSE, responseSchema(operation, "403").path("$ref").asString(), operation.toString());
		}
	}

	@Test
	void propertyCreate_errors_areTheShapesTheServerSends() {
		JsonNode create = contract.path("paths").path("/api/v1/properties").path("post");

		assertEquals(ERROR_RESPONSE, responseSchema(create, "401").path("$ref").asString(), create.toString());
		assertEquals(ERROR_RESPONSE, responseSchema(create, "403").path("$ref").asString(), create.toString());
		assertEquals(ERROR_RESPONSE, responseSchema(create, "404").path("$ref").asString(), create.toString());
		assertEquals(2, responseSchema(create, "400").path("anyOf").size(), create.toString());
		assertFalse(responseSchema(create, "400").has("oneOf"), create.toString());
	}

	/** The wire check found these two: the intake switch answers 404 on every intake path (spec D5-10, intake A-11). */
	@Test
	void intakeListReads_document404WhileTheSwitchIsOff() {
		for (String path : List.of("/api/v1/staff/inventory-cases", "/api/v1/me/property-confirmations")) {
			JsonNode list = contract.path("paths").path(path).path("get");

			assertFalse(list.path("responses").path("404").isMissingNode(), path);
			assertEquals(ERROR_RESPONSE, responseSchema(list, "404").path("$ref").asString(), path);
		}
	}

	/** The handler exists only to refuse; the 405 itself is pinned by MediaContentReadApiPostgresTest (spec D5-7). */
	@Test
	void mediaContent_hasNoHeadOperation() {
		JsonNode content = contract.path("paths").path("/api/v1/staff/survey-media/{mediaId}/content/{variant}");

		assertFalse(content.path("get").isMissingNode(), content.toString());
		assertEquals(List.of("get"), fieldNames(content));
	}

	/**
	 * Every JSON answer names its shape (SV-177): an object schema with no properties, reference, additional properties or
	 * composition takes any object, so the wire check passes whatever the server sends behind it.
	 */
	@Test
	void everyJsonAnswer_namesItsShape() {
		List<String> loose = new ArrayList<>();
		contract.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation ->
				operation.getValue().path("responses").properties().forEach(response ->
						response.getValue().path("content").properties().forEach(media -> {
							if (isJson(media.getKey()) && isLoose(media.getValue().path("schema"))) {
								loose.add(operation.getKey().toUpperCase(Locale.ROOT) + ' ' + path.getKey() + ' '
										+ response.getKey());
							}
						}))));

		assertEquals(List.of(), loose, loose.size() + " loose answers");
	}

	private static boolean isJson(String mediaType) {
		return mediaType.contains("json") || "*/*".equals(mediaType);
	}

	private static boolean isLoose(JsonNode schema) {
		return "object".equals(schema.path("type").asString())
				&& Stream.of("properties", "$ref", "additionalProperties", "allOf", "anyOf", "oneOf").noneMatch(schema::has);
	}

	private static JsonNode responseSchema(JsonNode operation, String status) {
		JsonNode content = operation.path("responses").path(status).path("content");
		return content.properties().iterator().next().getValue().path("schema");
	}

	private JsonNode schema(String name) {
		JsonNode schema = contract.path("components").path("schemas").path(name);
		assertFalse(schema.isMissingNode(), name);
		return schema;
	}

	private static List<String> values(JsonNode property) {
		List<String> values = new ArrayList<>();
		property.path("enum").forEach(value -> values.add(value.asString()));
		return values;
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(field -> names.add(field.getKey()));
		return names;
	}
}
