package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.List;
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
 * The survey-media operations in the contract (survey-media spec 7.1..7.3; plan Global Constraints "OpenAPI"): each
 * with an explicit operation id, a strict request schema (every key required), its error codes and {@code Retry-After}
 * where spec 7.3 sets it. One test per operation, added by the task that builds it. Read from {@code /v3/api-docs} on
 * the H2 test profile like {@code SurveyOpenApiTest}, so no database is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurveyMediaOpenApiTest {

	private static final String CREATE = "/api/v1/staff/survey-checklists/{checklistId}/media";
	private static final String CONTENT = "/api/v1/staff/survey-media/{mediaId}/content";
	private static final String READ = "/api/v1/staff/survey-media/{mediaId}/content/{variant}";
	private static final String REMOVAL = "/api/v1/staff/survey-media/{mediaId}/removal";
	private static final String HOLD = "/api/v1/staff/survey-media/{mediaId}/hold";
	private static final String RELEASE = "/api/v1/staff/survey-media/{mediaId}/hold-release";

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
	void createOperation_hasOperationIdStrictBodyAndErrorsWithRetryAfter() {
		JsonNode create = contract.path("paths").path(CREATE).path("post");
		assertFalse(create.isMissingNode(), CREATE);
		assertEquals("createSurveyMedia", create.path("operationId").asString());

		assertEquals("#/components/schemas/MediaCreateRequest", create.path("requestBody").path("content")
				.path("application/json").path("schema").path("$ref").asString());
		JsonNode request = schema("MediaCreateRequest");
		Set<String> required = new LinkedHashSet<>();
		request.path("required").forEach(key -> required.add(key.asString()));
		assertEquals(Set.of("uploadKey", "spaceId", "category", "caption", "sizeBytes", "contentSha256"), required);
		assertEquals(required, Set.copyOf(fieldNames(request.path("properties"))));

		JsonNode responses = create.path("responses");
		assertEquals(Set.of("200", "201", "400", "401", "403", "404", "409", "413", "422", "429", "503"),
				Set.copyOf(fieldNames(responses)));
		for (String status : List.of("200", "201")) {
			JsonNode content = responses.path(status).path("content");
			assertEquals(1, content.size(), status);
			assertEquals("#/components/schemas/SurveyMediaCreateResponse", content.properties().iterator().next()
					.getValue().path("schema").path("$ref").asString(), status);
		}
		assertFalse(responses.path("429").path("headers").path("Retry-After").isMissingNode(),
				"spec 7.3: Retry-After always on 429");

		String descriptions = responses.toString();
		for (String code : List.of("VALIDATION_FAILED", "NOT_FOUND", "CHECKLIST_READ_ONLY",
				"CHECKLIST_TAKEOVER_REQUIRED", "IDEMPOTENCY_KEY_REUSED", "MEDIA_UPLOAD_DISABLED",
				"MEDIA_DUPLICATE_CONTENT", "MEDIA_FROM_OTHER_VISIT", "MEDIA_LIMIT_REACHED", "PAYLOAD_TOO_LARGE",
				"SURVEYOR_INELIGIBLE", "RATE_LIMITED", "MEDIA_INTAKE_PAUSED")) {
			assertTrue(descriptions.contains(code), code);
		}
	}

	@Test
	void contentPutOperation_octetStreamBody_lengthAndDigestHeaders_retryAfter() {
		JsonNode put = contract.path("paths").path(CONTENT).path("put");
		assertFalse(put.isMissingNode(), CONTENT);
		assertEquals("uploadSurveyMediaContent", put.path("operationId").asString());

		JsonNode body = put.path("requestBody");
		assertTrue(body.path("required").asBoolean());
		assertEquals(Set.of("application/octet-stream"), Set.copyOf(fieldNames(body.path("content"))));
		JsonNode schema = body.path("content").path("application/octet-stream").path("schema");
		assertEquals("string", schema.path("type").asString());
		assertEquals("binary", schema.path("format").asString());

		Set<String> requiredHeaders = new LinkedHashSet<>();
		put.path("parameters").forEach(parameter -> {
			if ("header".equals(parameter.path("in").asString()) && parameter.path("required").asBoolean()) {
				requiredHeaders.add(parameter.path("name").asString());
			}
		});
		assertTrue(requiredHeaders.containsAll(Set.of("Content-Length", "Content-Digest")), requiredHeaders.toString());

		JsonNode responses = put.path("responses");
		assertEquals(Set.of("202", "400", "401", "403", "404", "409", "413", "415", "422", "429", "503"),
				Set.copyOf(fieldNames(responses)));
		JsonNode accepted = responses.path("202").path("content");
		assertEquals("#/components/schemas/SurveyMediaContentResponse", accepted.properties().iterator().next()
				.getValue().path("schema").path("$ref").asString());
		assertFalse(responses.path("429").path("headers").path("Retry-After").isMissingNode());
		assertFalse(responses.path("503").path("headers").path("Retry-After").isMissingNode(),
				"spec 7.3: Retry-After on MEDIA_SERVER_BUSY");
		String descriptions = responses.toString();
		for (String code : List.of("MALFORMED_REQUEST", "PAYLOAD_TOO_LARGE", "MEDIA_NOT_AVAILABLE",
				"MEDIA_UPLOAD_EXPIRED", "MEDIA_UPLOAD_IN_PROGRESS", "MEDIA_CONTENT_ALREADY_RECEIVED",
				"MEDIA_UPLOAD_DISABLED", "MEDIA_DUPLICATE_CONTENT", "MEDIA_FROM_OTHER_VISIT", "RATE_LIMITED",
				"MEDIA_SERVER_BUSY", "MEDIA_INTAKE_PAUSED", "MEDIA_STORE_UNAVAILABLE", "SURVEYOR_INELIGIBLE")) {
			assertTrue(descriptions.contains(code), code);
		}
	}

	@Test
	void listOperation_knownVersionParameterAnd204() {
		JsonNode list = contract.path("paths").path(CREATE).path("get");
		assertEquals("listSurveyMedia", list.path("operationId").asString());
		JsonNode known = null;
		for (JsonNode parameter : list.path("parameters")) {
			if ("knownVersion".equals(parameter.path("name").asString())) {
				known = parameter;
			}
		}
		assertTrue(known != null && "query".equals(known.path("in").asString()), String.valueOf(known));
		assertFalse(known.path("required").asBoolean());
		JsonNode responses = list.path("responses");
		assertEquals(Set.of("200", "204", "400", "401", "403", "404", "429"), Set.copyOf(fieldNames(responses)));
		assertEquals("#/components/schemas/ChecklistMediaResponse", responses.path("200").path("content")
				.properties().iterator().next().getValue().path("schema").path("$ref").asString());
		assertTrue(responses.path("204").path("content").isMissingNode(), "204 has no body");
		assertFalse(responses.path("429").path("headers").path("Retry-After").isMissingNode());
	}

	@Test
	void contentGetOperation_imageJpegBinary_head405_retryAfterOn429And503() {
		JsonNode get = contract.path("paths").path(READ).path("get");
		assertEquals("getSurveyMediaContent", get.path("operationId").asString());
		JsonNode responses = get.path("responses");
		assertEquals(Set.of("200", "400", "401", "403", "404", "405", "409", "429", "503"),
				Set.copyOf(fieldNames(responses)));
		JsonNode image = responses.path("200").path("content").path("image/jpeg").path("schema");
		assertEquals("string", image.path("type").asString());
		assertEquals("binary", image.path("format").asString());
		assertFalse(responses.path("429").path("headers").path("Retry-After").isMissingNode());
		assertFalse(responses.path("503").path("headers").path("Retry-After").isMissingNode());
		JsonNode download = null;
		for (JsonNode parameter : get.path("parameters")) {
			if ("download".equals(parameter.path("name").asString())) {
				download = parameter;
			}
		}
		assertTrue(download != null && !download.path("required").asBoolean());
		String descriptions = responses.toString();
		for (String code : List.of("METHOD_NOT_ALLOWED", "MEDIA_NOT_AVAILABLE", "MEDIA_SERVING_DISABLED",
				"MEDIA_SERVER_BUSY", "MEDIA_STORE_UNAVAILABLE", "RATE_LIMITED")) {
			assertTrue(descriptions.contains(code), code);
		}
	}

	/** survey-media spec 7.1, 5.4 removal order (plan Task A19). */
	@Test
	void removalOperation_hasOperationIdAndErrors() {
		JsonNode removal = contract.path("paths").path(REMOVAL).path("post");
		assertEquals("removeSurveyMedia", removal.path("operationId").asString());
		assertEquals("#/components/schemas/MediaRemovalRequest", removal.path("requestBody").path("content")
				.path("application/json").path("schema").path("$ref").asString());
		JsonNode request = schema("MediaRemovalRequest");
		Set<String> required = new LinkedHashSet<>();
		request.path("required").forEach(key -> required.add(key.asString()));
		assertEquals(Set.of("correction"), required);
		JsonNode responses = removal.path("responses");
		assertEquals(Set.of("200", "400", "401", "403", "404", "409", "413", "422"), Set.copyOf(fieldNames(responses)));
		JsonNode ok = responses.path("200").path("content");
		assertEquals(1, ok.size());
		assertEquals("#/components/schemas/SurveyMediaContentResponse", ok.properties().iterator().next().getValue()
				.path("schema").path("$ref").asString());
		String descriptions = responses.toString();
		for (String code : List.of("MEDIA_REFERENCED", "CHECKLIST_READ_ONLY", "CHECKLIST_TAKEOVER_REQUIRED",
				"PAYLOAD_TOO_LARGE", "SURVEYOR_INELIGIBLE", "VALIDATION_FAILED")) {
			assertTrue(descriptions.contains(code), code);
		}
	}

	/** survey-media spec 7.1, A-23, 5.4 hold order (plan Task A20). */
	@Test
	void holdAndReleaseOperations_haveOperationIdsAndErrors() {
		JsonNode hold = contract.path("paths").path(HOLD).path("post");
		JsonNode release = contract.path("paths").path(RELEASE).path("post");
		assertEquals("holdSurveyMedia", hold.path("operationId").asString());
		assertEquals("releaseSurveyMediaHold", release.path("operationId").asString());
		assertEquals(Set.of("200", "400", "401", "403", "404", "409", "413"), Set.copyOf(fieldNames(hold.path(
				"responses"))));
		assertEquals(Set.of("200", "400", "401", "403", "404", "413"), Set.copyOf(fieldNames(release.path(
				"responses"))));
		JsonNode request = schema("MediaHoldRequest");
		List<String> reasons = new java.util.ArrayList<>();
		request.path("properties").path("reason").path("enum").forEach(value -> {
			if (!value.isNull()) {
				reasons.add(value.asString());
			}
		});
		assertEquals(List.of("SECURITY_INCIDENT", "CREDENTIAL_EXPOSURE"), reasons);
		assertFalse(schema("MediaHoldReleaseRequest").isMissingNode());
		assertTrue(hold.path("responses").toString().contains("MEDIA_NOT_AVAILABLE"));
		JsonNode ok = hold.path("responses").path("200").path("content");
		assertEquals("#/components/schemas/SurveyMediaStateResponse", ok.properties().iterator().next().getValue()
				.path("schema").path("$ref").asString());
	}

	@Test
	void mediaItem_hasNoStorageKeyHashUploadKeyOrFileName() {
		Set<String> fields = Set.copyOf(fieldNames(schema("SurveyMediaItem").path("properties")));

		assertTrue(fields.containsAll(Set.of("id", "mediaNo", "status", "statusReason", "spaceId",
				"spaceCodeAtUpload", "category", "caption", "captionHidden", "uploadedByUserId", "uploadedByName",
				"createdAt", "contentReceivedAt", "readyAt", "width", "height", "removed", "held",
				"contentAvailable", "unavailableReason")), fields.toString());
		for (String hidden : List.of("storageKey", "sourceStorageKey", "sourceSha256", "contentSha256", "uploadKey",
				"fileName")) {
			assertFalse(fields.contains(hidden), hidden);
		}
	}

	/** ADR-006 ids are opaque strings in the API (SV-244 spec AC-4, D-3): no number type, no format, no pattern. */
	@Test
	void removal_bySaveIdIsAnOpaqueNullableString() {
		JsonNode property = schema("SurveyMediaRemoval").path("properties").path("bySaveId");
		assertFalse(property.isMissingNode(), "SurveyMediaRemoval.bySaveId");

		Set<String> types = new LinkedHashSet<>();
		property.path("type").forEach(type -> types.add(type.asString()));
		assertEquals(Set.of("string", "null"), types, property.toString());
		assertTrue(property.path("format").isMissingNode(), property.toString());
		assertEquals("Opaque id of the G1 save of a CASCADE removal; equals saveId of that save's checklist correction"
				+ " when it recorded one", property.path("description").asString());
	}

	private JsonNode schema(String name) {
		JsonNode schema = contract.path("components").path("schemas").path(name);
		assertFalse(schema.isMissingNode(), name);
		return schema;
	}

	private static List<String> fieldNames(JsonNode node) {
		return node.properties().stream().map(java.util.Map.Entry::getKey).toList();
	}
}
