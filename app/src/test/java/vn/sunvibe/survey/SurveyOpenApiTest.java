package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * stay as they were, and the survey list carries its own explicit id. The checklist endpoints (survey-checklist spec
 * 7.1..7.4, W16-22) bring only {@code Checklist*} components, no DELETE, a nullable {@code meta} reference and request
 * schemas whose every key is required. Read from {@code /v3/api-docs} on the H2 test profile like
 * {@code OpenApiExportTest}, so no database is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurveyOpenApiTest {

	@Value("${local.server.port}")
	private int port;

	private JsonNode contract;

	private static final String CHECKLISTS = "/api/v1/staff/survey-checklists";
	private static final String START = "/api/v1/staff/survey-appointments/{appointmentId}/checklists";
	/** Components the checklist operations may share with the earlier survey endpoints. */
	private static final Set<String> SHARED_COMPONENTS = Set.of("ErrorResponse", "ValidationError",
			"SurveyLocationResponse", "SurveyLeadStateResponse");

	/** Survey-checklist submission spec 7.1: the decision body names only what its outcome uses. */
	private static final Set<String> OPTIONAL_DECISION_KEYS = Set.of("ChecklistDecisionRequest.needsChangesKind",
			"ChecklistDecisionRequest.failedKind", "ChecklistDecisionRequest.reason", "ChecklistDecisionRequest.notes",
			"ChecklistDecisionRequest.limitations");

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

	@Test
	void openApi_checklistPaths_haveNoDeleteOperation() {
		contract.path("paths").properties().forEach(path -> {
			if (path.getKey().startsWith(CHECKLISTS)) {
				assertTrue(path.getValue().path("delete").isMissingNode(), path.getKey());
			}
		});
		assertFalse(contract.path("paths").path(CHECKLISTS + "/{checklistId}").isMissingNode());
		assertFalse(contract.path("paths").path(START).path("post").isMissingNode());
	}

	@Test
	void openApi_checklistOperations_useOnlyChecklistComponents() {
		Set<String> reached = new LinkedHashSet<>();
		Deque<JsonNode> pending = new ArrayDeque<>();
		contract.path("paths").properties().forEach(path -> {
			if ((path.getKey().startsWith(CHECKLISTS) || path.getKey().equals(START)) && !isMediaPath(path.getKey())) {
				pending.add(path.getValue());
			}
		});
		while (!pending.isEmpty()) {
			JsonNode node = pending.poll();
			if (node.isObject() && node.has("$ref")) {
				String name = node.path("$ref").asString().substring("#/components/schemas/".length());
				if (reached.add(name)) {
					pending.add(contract.path("components").path("schemas").path(name));
				}
			}
			node.forEach(pending::add);
		}

		assertTrue(reached.contains("ChecklistResponse"), reached.toString());
		for (String name : reached) {
			assertTrue(name.startsWith("Checklist") || SHARED_COMPONENTS.contains(name), name);
		}
	}

	/**
	 * The survey-media operations (survey-media spec 7.1; plan Task A13) are listed apart: their components are
	 * {@code SurveyMedia*}, {@code Media*} and {@code ChecklistMedia*} ones, the checklist History entry they answer
	 * (spec 7.2 {@code historyEntry}) and the shared error components.
	 */
	@Test
	void openApi_mediaOperations_useOnlyMediaComponents() {
		Set<String> reached = new LinkedHashSet<>();
		Deque<JsonNode> pending = new ArrayDeque<>();
		contract.path("paths").properties().forEach(path -> {
			if (isMediaPath(path.getKey())) {
				pending.add(path.getValue());
			}
		});
		assertFalse(pending.isEmpty());
		while (!pending.isEmpty()) {
			JsonNode node = pending.poll();
			if (node.isObject() && node.has("$ref")) {
				String name = node.path("$ref").asString().substring("#/components/schemas/".length());
				if (reached.add(name)) {
					pending.add(contract.path("components").path("schemas").path(name));
				}
			}
			node.forEach(pending::add);
		}

		assertTrue(reached.contains("SurveyMediaItem"), reached.toString());
		for (String name : reached) {
			assertTrue(name.startsWith("SurveyMedia") || name.startsWith("Media") || name.startsWith("ChecklistMedia")
					|| name.startsWith("ChecklistHistoryEntry") || SHARED_COMPONENTS.contains(name), name);
		}
	}

	private static List<String> fieldNames(JsonNode object) {
		List<String> names = new java.util.ArrayList<>();
		object.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}

	private static boolean isMediaPath(String path) {
		return path.startsWith("/api/v1/staff/survey-media/") || (path.startsWith(CHECKLISTS)
				&& (path.endsWith("/media") || path.endsWith("/deselection")));
	}

	@Test
	void openApi_meta_isANullableReference_andNotReadOnly() {
		for (String wrapper : List.of("ChecklistTextField", "ChecklistLocationField", "ChecklistRentTerm",
				"ChecklistObservedSpace", "ChecklistHazard", "ChecklistAmenityItem")) {
			JsonNode meta = schema(wrapper).path("properties").path("meta");
			assertEquals("#/components/schemas/ChecklistUnitMeta", meta.path("anyOf").path(0).path("$ref").asString(),
					wrapper);
			assertEquals("null", meta.path("anyOf").path(1).path("type").asString(), wrapper);
			assertTrue(meta.path("readOnly").isMissingNode(), wrapper);
		}
	}

	@Test
	void openApi_everyKeyOfAChecklistRequestSchema_isRequired() {
		List<String> checked = new ArrayList<>();
		contract.path("components").path("schemas").properties().forEach(entry -> {
			String name = entry.getKey();
			boolean checklistBody = name.startsWith("Checklist") && (name.endsWith("Request") || name.endsWith("Input"));
			boolean mediaBody = name.startsWith("Media") && name.endsWith("Request");
			if (!checklistBody && !mediaBody) {
				return;
			}
			Set<String> required = new LinkedHashSet<>();
			entry.getValue().path("required").forEach(key -> required.add(key.asString()));
			entry.getValue().path("properties").properties().forEach(property -> {
				if (!property.getKey().equals("otherGroupVersions") && !OPTIONAL_DECISION_KEYS.contains(name + "."
						+ property.getKey())) {
					assertTrue(required.contains(property.getKey()), name + "." + property.getKey());
				}
			});
			checked.add(name);
		});
		assertTrue(checked.containsAll(List.of("ChecklistStartRequest", "ChecklistIntakeRequest",
				"ChecklistBasisRequest", "ChecklistTakeoverRequest", "MediaCreateRequest")), checked.toString());
	}

	/**
	 * survey-media spec 7.1, 7.2 (plan Task A18): the checklist answer carries {@code media {available, uploadEnabled,
	 * setVersion}}, a missing item's problem may be EVIDENCE_MISSING or EVIDENCE_PROCESSING and the reason submission
	 * is unavailable may be SUBMISSION_NOT_AVAILABLE.
	 */
	@Test
	void openApi_checklistResponse_hasTheMediaBlockAndTheEvidenceProblems() {
		JsonNode schemas = contract.path("components").path("schemas");
		assertEquals("#/components/schemas/ChecklistMedia", schemas.path("ChecklistResponse").path("properties")
				.path("media").path("$ref").asString());
		JsonNode media = schemas.path("ChecklistMedia").path("properties");
		assertEquals(List.of("available", "setVersion", "uploadEnabled"), fieldNames(media).stream().sorted().toList());
		assertEquals("boolean", media.path("available").path("type").asString());
		assertEquals("boolean", media.path("uploadEnabled").path("type").asString());
		assertEquals("integer", media.path("setVersion").path("type").asString());
		List<String> problems = new java.util.ArrayList<>();
		schemas.path("ChecklistMissingItem").path("properties").path("problem").path("enum")
				.forEach(value -> problems.add(value.asString()));
		assertTrue(problems.containsAll(List.of("EVIDENCE_PENDING", "EVIDENCE_MISSING", "EVIDENCE_PROCESSING")),
				problems.toString());
		List<String> reasons = new java.util.ArrayList<>();
		schemas.path("ChecklistReadiness").path("properties").path("submissionUnavailableReason").path("enum")
				.forEach(value -> reasons.add(value.asString()));
		assertEquals(List.of("EVIDENCE_NOT_AVAILABLE", "SUBMISSION_NOT_AVAILABLE"), reasons);
	}

	@Test
	void openApi_checklistErrorResponses_nameTheNewCodes() {
		StringBuilder descriptions = new StringBuilder();
		contract.path("paths").properties().forEach(path -> {
			if (path.getKey().startsWith(CHECKLISTS) || path.getKey().equals(START)) {
				path.getValue().forEach(operation -> operation.path("responses").forEach(response ->
						descriptions.append(response.path("description").asString()).append(' ')));
			}
		});
		for (String code : List.of("CHECKLIST_NOT_ALLOWED", "CHECKLIST_ALREADY_STARTED", "CHECKLIST_READ_ONLY",
				"CHECKLIST_TAKEOVER_REQUIRED")) {
			assertTrue(descriptions.toString().contains(code), code);
		}
	}

	/**
	 * survey-checklist spec 7.2, survey-media spec 5.3: the History entry of a checklist answer is a
	 * LeadActivityResponse of five types.
	 */
	@Test
	void openApi_checklistHistoryEntry_hasTheActivityFields_andTheFiveChecklistTypes() {
		List<String> activity = new ArrayList<>();
		schema("LeadActivityResponse").path("properties").properties().forEach(p -> activity.add(p.getKey()));
		List<String> entry = new ArrayList<>();
		JsonNode checklistEntry = schema("ChecklistHistoryEntryResponse");
		checklistEntry.path("properties").properties().forEach(p -> entry.add(p.getKey()));
		List<String> types = new ArrayList<>();
		checklistEntry.path("properties").path("type").path("enum").forEach(t -> types.add(t.asString()));

		assertEquals(activity, entry);
		assertEquals(List.of("CHECKLIST_STARTED", "CHECKLIST_TAKEN_OVER", "CHECKLIST_SAVED", "CHECKLIST_MEDIA_ADDED",
				"CHECKLIST_MEDIA_REMOVED", "CHECKLIST_SUBMITTED", "CHECKLIST_REVISION_STARTED"), types);
	}

	// ==================== survey-checklist submission spec 7 ====================

	@Test
	void openApi_reviewOperations_exist() {
		Map<String, String> ids = new LinkedHashMap<>();
		ids.put(CHECKLISTS + "/{checklistId}/submission", "post");
		ids.put(CHECKLISTS + "/{checklistId}/decision", "post");
		ids.put(CHECKLISTS + "/{checklistId}/revisions", "post");
		ids.put(CHECKLISTS + "/{checklistId}/media/{mediaId}/deselection", "post");
		ids.put(CHECKLISTS + "/review-queue", "get");

		ids.forEach((path, method) -> assertFalse(contract.path("paths").path(path).path(method).isMissingNode(), path));
	}

	/** A repeated deselection is a list read and counts in its budget. */
	@Test
	void openApi_deselection_documentsTheBudget() {
		JsonNode responses = contract.path("paths").path(CHECKLISTS + "/{checklistId}/media/{mediaId}/deselection")
				.path("post").path("responses");

		assertFalse(responses.path("429").isMissingNode(), responses.toString());
	}

	@Test
	void openApi_checklistResponse_hasTheReviewParts() {
		List<String> properties = fieldNames(schema("ChecklistResponse").path("properties"));

		assertTrue(properties.containsAll(List.of("previousChecklistId", "submission", "decision", "review", "passed")),
				properties.toString());
		assertEquals(List.of("canReview", "decisionBlockedBy", "reason"),
				fieldNames(schema("ChecklistReviewResponse").path("properties")).stream().sorted().toList());
		assertEquals(List.of("NOT_REVIEWER_ROLE", "NOT_SUBMITTED", "LEAD_STATUS", "INTENT_CHANGED", "SERVING_DISABLED",
				"NOT_INDEPENDENT"), values(schema("ChecklistReviewResponse").path("properties").path("reason")));
		assertEquals(List.of("INTENT_CHANGED", "LEAD_CLOSED", "EVIDENCE_HELD", "EVIDENCE_GONE", "REQUALIFIED"),
				values(schema("ChecklistPassedResponse").path("properties").path("reason")));
	}

	@Test
	void openApi_surveyBlock_hasTheRecordAndTheStarts() {
		List<String> properties = fieldNames(schema("SurveyBlockResponse").path("properties"));

		assertTrue(properties.containsAll(List.of("revisionStart", "resume", "survey", "resumeAllowed")),
				properties.toString());
		assertEquals(List.of("SURVEY_SCHEDULED", "QUALIFIED"),
				values(schema("SurveyResumeResponse").path("properties").path("target")));
		assertTrue(values(schema("SurveyBookingResponse").path("properties").path("blockedBy"))
				.containsAll(List.of("CHECKLIST_SUBMITTED", "CHECKLIST_PASSED")));
		assertTrue(values(schema("ChecklistStartStateResponse").path("properties").path("blockedBy"))
				.contains("CHECKLIST_EXISTS"));
		assertTrue(fieldNames(schema("ChecklistSummaryResponse").path("properties")).contains("outcome"));
	}

	@Test
	void openApi_reviewErrorResponses_nameTheCodes() {
		StringBuilder descriptions = new StringBuilder();
		contract.path("paths").properties().forEach(path -> {
			if (path.getKey().startsWith(CHECKLISTS)) {
				path.getValue().forEach(operation -> operation.path("responses").forEach(response ->
						descriptions.append(response.path("description").asString()).append(' ')));
			}
		});
		for (String code : List.of("CHECKLIST_SUBMISSION_NOT_AVAILABLE", "CHECKLIST_NOT_READY",
				"CHECKLIST_REVIEW_NOT_ALLOWED", "CHECKLIST_DECISION_NOT_ALLOWED")) {
			assertTrue(descriptions.toString().contains(code), code);
		}
	}

	// ==================== property-create requalification spec 7, S-10 ====================

	@Test
	void openApi_requalification_isTheContractOfTheSpec() {
		JsonNode post = contract.path("paths").path(CHECKLISTS + "/{checklistId}/requalification").path("post");

		assertEquals("requalifySurveyChecklist", post.path("operationId").asString());
		assertEquals(List.of("200", "400", "401", "403", "404", "409"),
				fieldNames(post.path("responses")).stream().sorted().toList());
		String conflicts = post.path("responses").path("409").path("description").asString();
		for (String code : List.of("CONCURRENT_MODIFICATION", "SURVEY_NOT_ALLOWED", "INVALID_STATE_TRANSITION")) {
			assertTrue(conflicts.contains(code), code);
		}
		assertEquals(List.of("basisDate", "eventSource", "expectedVersion", "reason", "reasonKind", "scope"),
				fieldNames(schema("ChecklistRequalificationRequest").path("properties")).stream().sorted().toList());
		assertEquals(List.of("WRONG_ASSET", "WRONG_OWNER", "WRONG_PERSON", "WRONG_SCOPE", "NEED_CHANGED", "PHOTO_PROBLEM",
				"HAZARD", "OTHER"), values(schema("ChecklistRequalificationRequest").path("properties").path("reasonKind")));
		assertEquals(List.of("checklist", "historyEntry", "lead"),
				fieldNames(schema("ChecklistRequalifiedResponse").path("properties")).stream().sorted().toList());
		assertEquals(List.of("REQUALIFICATION_MARKED"),
				values(schema("ChecklistRequalificationHistoryEntryResponse").path("properties").path("type")));
	}

	@Test
	void openApi_requalificationReads_areTheChangedResponsesOfTheSpec() {
		JsonNode checklist = schema("ChecklistResponse").path("properties").path("requalification");
		assertTrue(checklist.toString().contains("ChecklistRequalificationResponse"), checklist.toString());
		assertTrue(acceptsNull(checklist), checklist.toString());
		assertEquals(List.of("basisDate", "eventSource", "markedAt", "markedBy", "reason", "reasonKind", "scope"),
				fieldNames(schema("ChecklistRequalificationResponse").path("properties")).stream().sorted().toList());
		assertTrue(fieldNames(schema("ChecklistSummaryResponse").path("properties")).contains("requalified"));
		assertTrue(fieldNames(schema("SurveyRecordResponse").path("properties")).contains("requalification"));
		JsonNode state = schema("ChecklistRequalificationStateResponse").path("properties");
		assertEquals(List.of("allowed", "checklistId", "expectedVersion"), fieldNames(state).stream().sorted().toList());
		assertTrue(acceptsNull(state.path("checklistId")), state.toString());
		assertTrue(acceptsNull(state.path("expectedVersion")), state.toString());
		assertTrue(state.path("expectedVersion").toString().contains("\"integer\""), state.toString());
		assertTrue(values(schema("LeadActivityResponse").path("properties").path("type"))
				.contains("REQUALIFICATION_MARKED"));
	}

	/** OpenAPI 3.1: a nullable property lists the null type (SV-020). */
	private static boolean acceptsNull(JsonNode property) {
		return property.toString().contains("\"null\"");
	}

	private static List<String> values(JsonNode property) {
		List<String> values = new ArrayList<>();
		property.path("enum").forEach(value -> values.add(value.asString()));
		return values;
	}

	private JsonNode schema(String name) {
		JsonNode schema = contract.path("components").path("schemas").path(name);
		assertFalse(schema.isMissingNode(), name);
		return schema;
	}
}
