package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
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
 * The survey endpoints are additive (survey-schedule spec 7.4): the operation ids the generated UI client already uses
 * stay as they were, and the survey list carries its own explicit id. Read from {@code /v3/api-docs} on the H2 test
 * profile like {@code OpenApiExportTest}, so no database is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurveyOpenApiTest {

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
	void openApi_existingOperationIds_areUnchanged_andTheSurveyListHasItsOwn() {
		Map<String, String> ids = new LinkedHashMap<>();
		ids.put("get /api/v1/staff/sale-territories", "list");
		ids.put("get /api/v1/staff/owner-leads", "list_1");
		ids.put("get /api/v1/staff/owner-lead-assignees", "list_2");
		ids.put("get /api/v1/staff/survey-appointments", "listSurveyAppointments");

		ids.forEach((key, id) -> {
			String[] parts = key.split(" ");
			assertEquals(id, contract.path("paths").path(parts[1]).path(parts[0]).path("operationId").asString(), key);
		});
	}
}
