package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The three listing operations in the contract (listing-publish E06-S1 spec section 4, E06S1-D1, E06S1-P7, LC-14): their
 * explicit and unique operationIds, the tag, their answers, the seven schemas with every property required and the
 * closed values listed, and, of the public paths, only the anonymous listing read of E06-S7. Read from
 * {@code /v3/api-docs} with the annotations of
 * {@code BrokerageOpenApiTest}, so no database is needed and its context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingOpenApiTest {

	private static final String COLLECTION = "/api/v1/staff/listings";
	private static final String ITEM = "/api/v1/staff/listings/{listingId}";
	private static final String BY_AGREEMENT = "/api/v1/staff/listings/by-agreement/{agreementId}";
	private static final String RECONFIRMATIONS = "/api/v1/staff/listings/reconfirmations";
	private static final String REVIEW_QUEUE = "/api/v1/staff/listings/review-queue";
	private static final String STATES = "[\"UNPUBLISHED\",\"PUBLISHED\",\"PAUSED\",\"URGENTLY_UNPUBLISHED\",\"ARCHIVED\"]";

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
	void openApi_listingOperations_idsTagsAndAnswers() {
		Map<String, String> operations = Map.of(
				"post " + COLLECTION, "createListing",
				"get " + ITEM, "getListing",
				"get " + BY_AGREEMENT, "getAgreementListings");
		Map<String, List<String>> answers = Map.of(
				"createListing", List.of("201", "400", "401", "403", "404", "409", "500"),
				"getListing", List.of("200", "401", "403", "404"),
				"getAgreementListings", List.of("200", "401", "403", "404", "500"));
		Map<String, String> successes = Map.of(
				"createListing", "ListingCreated",
				"getListing", "ListingDetail",
				"getAgreementListings", "AgreementListings");
		for (Map.Entry<String, String> expected : operations.entrySet()) {
			String[] key = expected.getKey().split(" ");
			JsonNode path = contract.path("paths").path(key[1]);
			assertEquals(List.of(key[0]), names(path), key[1]);
			JsonNode operation = path.path(key[0]);
			String operationId = expected.getValue();
			assertEquals(operationId, operation.path("operationId").asString());
			assertEquals("[\"Listing\"]", operation.path("tags").toString(), operationId);
			assertEquals(List.of(expected.getKey()), operationsWithId(operationId));

			JsonNode responses = operation.path("responses");
			assertEquals(answers.get(operationId), names(responses).stream().sorted().toList(), operationId);
			for (String status : answers.get(operationId)) {
				String schema = status.startsWith("2") ? successes.get(operationId) : "ErrorResponse";
				assertEquals("#/components/schemas/" + schema, schemaRef(responses.path(status)), operationId + status);
			}
			assertTrue(responses.path("403").path("description").asString().contains("FORBIDDEN"), operationId);
			assertTrue(responses.path("404").path("description").asString().contains("NOT_FOUND"), operationId);
		}
		JsonNode create = contract.path("paths").path(COLLECTION).path("post");
		assertEquals("#/components/schemas/ListingCreateRequest",
				create.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asString());
		for (String code : List.of("LISTING_NOT_ALLOWED", "LISTING_EXISTS", "CONCURRENT_MODIFICATION")) {
			assertTrue(create.path("responses").path("409").path("description").asString().contains(code), code);
		}
		for (String code : List.of("VALIDATION_FAILED", "MALFORMED_REQUEST")) {
			assertTrue(create.path("responses").path("400").path("description").asString().contains(code), code);
		}

		// the export orders the paths by key: the five paths follow this one, before /api/v1/staff/owner-leads (spec 4;
		// listing-publish E06-S3 spec section 4 adds /api/v1/staff/listings/reconfirmations, E06-S4 spec section 4
		// adds /api/v1/staff/listings/review-queue)
		List<String> paths = names(contract.path("paths")).stream().sorted().toList();
		int before = paths.indexOf("/api/v1/staff/inventory-cases/{caseId}/decision");
		assertEquals(List.of(COLLECTION, ITEM, BY_AGREEMENT, RECONFIRMATIONS, REVIEW_QUEUE).stream().sorted().toList(),
				paths.subList(before + 1, before + 6));
		assertTrue(before + 6 <= paths.indexOf("/api/v1/staff/owner-leads"), paths.toString());
	}

	@Test
	void openApi_listingSchemas_requiredFieldsAndEnums() {
		JsonNode schemas = contract.path("components").path("schemas");
		Map<String, List<String>> properties = Map.of(
				"ListingCreateRequest", List.of("agreementId"),
				"ListingCreated", List.of("code", "listingId"),
				"ListingDetail", List.of("agreementId", "code", "createdAt", "dataVersion", "hasOlderRevisions",
						"listingId", "openRevisionNo", "revisions", "state", "transactionType", "unitId",
						"viewerIsAssignedSale"),
				"ListingRevisionItem", List.of("createdAt", "revisionNo", "status"),
				"AgreementListings", List.of("agreementId", "creation", "hasOlder", "listings"),
				"ListingItem", List.of("code", "createdAt", "listingId", "state"),
				"ListingCreation", List.of("advertisingNotStarted", "reasonGroups", "viewerMayCreate"));
		for (Map.Entry<String, List<String>> expected : properties.entrySet()) {
			JsonNode schema = schemas.path(expected.getKey());
			assertEquals(expected.getValue(), names(schema.path("properties")).stream().sorted().toList(),
					expected.getKey());
			assertEquals(expected.getValue(), requiredOf(schema), expected.getKey() + " " + schema);
			// listing-publish E06-S2 spec E06S2-D20: ListingDetail.openRevisionNo is the one nullable property
			schema.path("properties").properties().stream()
					.filter(property -> !("ListingDetail".equals(expected.getKey())
							&& "openRevisionNo".equals(property.getKey())))
					.forEach(property -> assertFalse(
					property.getValue().toString().contains("null"), expected.getKey() + "." + property.getKey()));
		}
		JsonNode openRevisionNo = schemas.path("ListingDetail").path("properties").path("openRevisionNo");
		assertEquals("int32", openRevisionNo.path("format").asString(), openRevisionNo.toString());
		assertTrue(openRevisionNo.toString().contains("null"), openRevisionNo.toString());

		JsonNode request = schemas.path("ListingCreateRequest").path("properties").path("agreementId");
		assertEquals("^[0-9A-Z]{26}$", request.path("pattern").asString());

		JsonNode detail = schemas.path("ListingDetail").path("properties");
		assertEquals(STATES, detail.path("state").path("enum").toString());
		assertEquals("[\"LONG_TERM_RENT\"]", detail.path("transactionType").path("enum").toString());
		assertEquals("date-time", detail.path("createdAt").path("format").asString());
		assertEquals("int64", detail.path("dataVersion").path("format").asString());
		assertEquals("boolean", detail.path("viewerIsAssignedSale").path("type").asString());
		assertEquals("boolean", detail.path("hasOlderRevisions").path("type").asString());
		assertEquals("#/components/schemas/ListingRevisionItem", detail.path("revisions").path("items").path("$ref").asString());

		JsonNode revision = schemas.path("ListingRevisionItem").path("properties");
		assertEquals("[\"DRAFT\",\"PENDING_REVIEW\",\"NEEDS_CHANGES\",\"REJECTED\",\"APPROVED\",\"LIVE\",\"REPLACED\","
				+ "\"CANCELLED\"]", revision.path("status").path("enum").toString());
		assertEquals("int32", revision.path("revisionNo").path("format").asString());
		assertEquals("date-time", revision.path("createdAt").path("format").asString());

		JsonNode listings = schemas.path("AgreementListings").path("properties");
		assertEquals("#/components/schemas/ListingItem", listings.path("listings").path("items").path("$ref").asString());
		assertEquals("#/components/schemas/ListingCreation", listings.path("creation").path("$ref").asString());
		assertEquals("boolean", listings.path("hasOlder").path("type").asString());
		assertEquals(STATES, schemas.path("ListingItem").path("properties").path("state").path("enum").toString());

		JsonNode creation = schemas.path("ListingCreation").path("properties");
		assertEquals("[\"NO_AGREEMENT\",\"NOT_IN_EFFECT\",\"NOT_STARTED\",\"SUSPENDED\",\"ENDED\",\"BASIS\",\"PROPERTY\","
				+ "\"UNIT\",\"OWNER\"]", creation.path("reasonGroups").path("items").path("enum").toString());
		assertEquals("boolean", creation.path("viewerMayCreate").path("type").asString());
		assertEquals("boolean", creation.path("advertisingNotStarted").path("type").asString());
	}

	/** listing-publish E06-S7 spec S-17, PB-27: of the public paths, only the anonymous listing read names a listing. */
	@Test
	void openApi_publicPaths_onlyThePublicListingRead() {
		List<String> publicPaths = names(contract.path("paths")).stream()
				.filter(path -> path.startsWith("/api/v1/public"))
				.toList();

		assertFalse(publicPaths.isEmpty(), "the contract has public paths to check");
		List<String> listingPaths = publicPaths.stream()
				.filter(path -> path.toLowerCase(Locale.ROOT).contains("listing"))
				.toList();
		assertEquals(List.of("/api/v1/public/listings/{code}"), listingPaths);
		JsonNode publicRead = contract.path("paths").path("/api/v1/public/listings/{code}");
		assertEquals(List.of("get"), names(publicRead));
		assertEquals("getPublicListing", publicRead.path("get").path("operationId").asString());
		assertEquals("[\"Public listing\"]", publicRead.path("get").path("tags").toString());
		assertFalse(publicRead.path("get").has("security"), publicRead.toString());
		for (String path : publicPaths) {
			if (!listingPaths.contains(path)) {
				assertFalse(path.toLowerCase(Locale.ROOT).contains("listing"), path);
			}
		}
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

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}

	private static List<String> requiredOf(JsonNode schema) {
		List<String> required = new ArrayList<>();
		schema.path("required").forEach(field -> required.add(field.asString()));
		return required.stream().sorted().toList();
	}

	/** The schema of the one media type of an answer. */
	private static String schemaRef(JsonNode response) {
		List<JsonNode> contents = new ArrayList<>();
		response.path("content").forEach(contents::add);
		assertEquals(1, contents.size(), response.toString());
		return contents.get(0).path("schema").path("$ref").asString();
	}
}
