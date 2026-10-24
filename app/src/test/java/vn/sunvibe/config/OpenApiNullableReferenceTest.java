package vn.sunvibe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A nullable property that refers to another schema is written as "that schema or null" ({@code anyOf} of the
 * reference and {@code {"type": "null"}}), so generated clients accept both; a {@code type} next to a {@code $ref}
 * would combine with it and allow neither. The requirement change sends {@code askingPrice} as a price or as null,
 * and the key itself is required (survey-schedule spec A-17, 7.1); the lead detail returns the prices of a
 * requirement change as a price or null (spec 7.2). Read from {@code /v3/api-docs} on the H2 test profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiNullableReferenceTest {

	@Value("${local.server.port}")
	private int port;

	private JsonNode contract;
	private JsonNode schemas;

	@BeforeAll
	void readContract() throws Exception {
		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpResponse<String> response = http.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			contract = JsonMapper.builder().build().readTree(response.body());
			schemas = contract.path("components").path("schemas");
		}
	}

	@Test
	void changeLeadIntent_askingPrice_isARequiredKeyHoldingAPriceOrNull() {
		JsonNode request = schemas.path("ChangeLeadIntentRequest");

		assertTrue(texts(request.path("required")).contains("askingPrice"), request.toString());
		assertPriceOrNull(request.path("properties").path("askingPrice"), "AskingPriceRequest");
		assertFalse(request.path("properties").path("askingPrice").path("description").asString().isBlank());
	}

	@Test
	void leadIntentChange_prices_areAPriceOrNull() {
		JsonNode change = schemas.path("LeadIntentChange").path("properties");

		assertPriceOrNull(change.path("fromAskingPrice"), "AskingPriceResponse");
		assertPriceOrNull(change.path("toAskingPrice"), "AskingPriceResponse");
	}

	/** Anywhere in the document (paths, items, nested schemas), not only the top-level component properties. */
	@Test
	void noSchema_hasATypeOrNullableNextToAReference() {
		List<String> broken = new ArrayList<>();
		collectBroken(contract, "", broken);
		assertEquals(List.of(), broken);
	}

	private static void collectBroken(JsonNode node, String path, List<String> broken) {
		if (node.isObject()) {
			if (node.has("$ref") && (node.has("type") || node.has("nullable"))) {
				broken.add(path);
			}
			for (Map.Entry<String, JsonNode> child : node.properties()) {
				collectBroken(child.getValue(), path + "/" + child.getKey(), broken);
			}
		} else if (node.isArray()) {
			for (int i = 0; i < node.size(); i++) {
				collectBroken(node.get(i), path + "/" + i, broken);
			}
		}
	}

	private static void assertPriceOrNull(JsonNode property, String priceSchema) {
		assertFalse(property.has("$ref"), property.toString());
		assertFalse(property.has("type"), property.toString());
		JsonNode anyOf = property.path("anyOf");
		assertEquals(2, anyOf.size(), property.toString());
		assertEquals("#/components/schemas/" + priceSchema, anyOf.path(0).path("$ref").asString(), property.toString());
		assertEquals("null", anyOf.path(1).path("type").asString(), property.toString());
	}

	private static List<String> texts(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(value -> values.add(value.asString()));
		return values;
	}
}
