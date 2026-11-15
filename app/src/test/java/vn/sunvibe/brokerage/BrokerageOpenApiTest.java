package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The brokerage agreement eligibility operation in the contract (E05-S1 spec 4, S1-D11, EL-13): its own explicit and
 * unique operationId, its tag, its answers and its two schemas with their enums and the nullable unit code. Read from
 * {@code /v3/api-docs} on the H2 test profile as {@code SurveyOpenApiTest} does, so no database is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrokerageOpenApiTest {

	private static final String PATH = "/api/v1/staff/owner-leads/{leadId}/brokerage-eligibility";
	private static final String OPERATION_ID = "getBrokerageEligibility";

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
	void openApi_eligibilityOperation_idTagAndAnswers() {
		JsonNode path = contract.path("paths").path(PATH);
		assertEquals(List.of("get"), names(path));
		JsonNode get = path.path("get");
		assertEquals(OPERATION_ID, get.path("operationId").asString());
		assertEquals("[\"Brokerage agreement\"]", get.path("tags").toString());

		List<String> withTheId = new ArrayList<>();
		contract.path("paths").properties().forEach(entry -> entry.getValue().properties().forEach(operation -> {
			if (OPERATION_ID.equals(operation.getValue().path("operationId").asString())) {
				withTheId.add(operation.getKey() + " " + entry.getKey());
			}
		}));
		assertEquals(List.of("get " + PATH), withTheId);

		JsonNode responses = get.path("responses");
		assertEquals(List.of("200", "401", "403", "404"), names(responses).stream().sorted().toList());
		assertEquals("#/components/schemas/BrokerageEligibility", schemaRef(responses.path("200")));
		for (String status : List.of("401", "403", "404")) {
			assertEquals("#/components/schemas/ErrorResponse", schemaRef(responses.path(status)), status);
		}
	}

	@Test
	void openApi_eligibilitySchemas_enumsAndNullableCode() {
		JsonNode schemas = contract.path("components").path("schemas");
		JsonNode eligibility = schemas.path("BrokerageEligibility").path("properties");
		assertEquals("[\"NONE\",\"USABLE\",\"SUSPENDED\",\"ENDED\"]", eligibility.path("basisState").path("enum").toString());
		assertEquals("boolean", eligibility.path("viewerIsAssignedSale").path("type").asString());
		assertEquals("#/components/schemas/BrokerageEligibilityUnit",
				eligibility.path("units").path("items").path("$ref").asString());

		JsonNode unit = schemas.path("BrokerageEligibilityUnit").path("properties");
		assertEquals("[\"string\",\"null\"]", unit.path("unitCode").path("type").toString());
		assertEquals("string", unit.path("unitId").path("type").asString());
		assertEquals("boolean", unit.path("eligible").path("type").asString());
		assertEquals("[\"BASIS\",\"PROPERTY\",\"UNIT\",\"OWNER\"]",
				unit.path("reasonGroups").path("items").path("enum").toString());
		assertTrue(unit.path("reasonGroups").path("description").asString().contains("No group names a lead."),
				unit.toString());
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}

	/** The schema reference of an answer's one content type. */
	private static String schemaRef(JsonNode response) {
		List<JsonNode> contents = new ArrayList<>();
		response.path("content").forEach(contents::add);
		assertEquals(1, contents.size(), response.toString());
		return contents.get(0).path("schema").path("$ref").asString();
	}
}
