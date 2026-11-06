package vn.sunvibe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every response field the server can write as null allows null in the contract, a field that refers to another schema
 * as {@code anyOf} of it and null (contract-wire spec 5.2, S-1, SV-020). Read from {@code /v3/api-docs} on the H2 test
 * profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiNullableFieldsTest {

	private static final Map<String, List<String>> NULLABLE = Map.ofEntries(
			Map.entry("OwnerLeadSummaryResponse", List.of("assignedUserId", "nextActionAt")),
			Map.entry("OwnerLeadDetailResponse", List.of("assignedUserId", "nextActionAt", "email", "latitude", "longitude",
					"askingPrice", "availableFrom", "notes", "preferredCallbackTime", "duplicateOfLeadId", "closeReason")),
			Map.entry("LeadActivityResponse", List.of("actorUserId", "fromStatus", "toStatus", "fromAssigneeId",
					"toAssigneeId", "note", "nextActionAt")),
			Map.entry("SaleTerritoryResponse", List.of("userId", "assignedAt")),
			Map.entry("PropertyCreateResponse", List.of("postalCode", "description")),
			Map.entry("SurveyAppointmentResponse", List.of("previousVisitId", "resurveyReason", "locationReason",
					"accessNote", "onSiteContactName", "onSiteContactRole", "visit", "cancellation", "surveyorName",
					"createdByName")),
			Map.entry("SurveyBlockResponse", List.of("currentVisitId")),
			Map.entry("SurveyBookingResponse", List.of("kind", "previousVisitId", "blockedBy")),
			Map.entry("SurveyCancellationResponse", List.of("description", "cancelledByName")),
			Map.entry("SurveyConfirmationResponse", List.of("note", "cause", "changedByName")),
			Map.entry("SurveyEventResponse", List.of("fromSurveyorUserId", "fromSurveyorName", "toSurveyorUserId",
					"toSurveyorName", "confirmationCause", "note", "actorUserId", "actorName", "revisionNo")),
			Map.entry("SurveyHistoryEntryResponse", List.of("fromStatus", "toStatus", "fromAssigneeId", "fromAssigneeName",
					"toAssigneeId", "toAssigneeName", "note", "nextActionAt", "actorName")),
			Map.entry("SurveyHolderResponse", List.of("to", "name")),
			Map.entry("SurveyRevisionResponse", List.of("accessNote", "onSiteContactName", "onSiteContactRole", "reason",
					"actorName")),
			Map.entry("SurveyVisitResponse", List.of("proxy", "performedByName", "recordedByName")),
			Map.entry("SurveyLocationResponse", List.of("latitude", "longitude")),
			Map.entry("SurveyListItemResponse", List.of("surveyorName")),
			Map.entry("SurveyListConfirmation", List.of("cause")),
			Map.entry("SurveyCommandResponse", List.of("historyEntry")),
			Map.entry("SurveyResumptionResponse", List.of("actorName")),
			Map.entry("ChecklistReadiness", List.of("submissionUnavailableReason")));

	@Value("${local.server.port}")
	private int port;

	private JsonNode schemas;

	@BeforeAll
	void readContract() throws Exception {
		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpResponse<String> response = http.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			schemas = JsonMapper.builder().build().readTree(response.body()).path("components").path("schemas");
		}
	}

	@ParameterizedTest(name = "{0}.{1}")
	@MethodSource("listedFields")
	void listedField_allowsNull(String schema, String field) {
		JsonNode property = schemas.path(schema).path("properties").path(field);

		assertFalse(property.isMissingNode(), schema + "." + field + " is not in the contract");
		assertTrue(allowsNull(property), schema + "." + field + ": " + property);
	}

	static Stream<Arguments> listedFields() {
		return NULLABLE.entrySet().stream()
				.flatMap(entry -> entry.getValue().stream().map(field -> Arguments.of(entry.getKey(), field)));
	}

	private static boolean allowsNull(JsonNode property) {
		if (property.has("$ref")) {
			return false;
		}
		for (JsonNode type : property.path("type")) {
			if ("null".equals(type.asString())) {
				return true;
			}
		}
		for (JsonNode option : property.path("anyOf")) {
			if ("null".equals(option.path("type").asString())) {
				return true;
			}
		}
		return false;
	}
}
