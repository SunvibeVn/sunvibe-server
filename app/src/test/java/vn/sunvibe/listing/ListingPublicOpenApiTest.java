package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * The anonymous public read in the contract (listing-publish E06-S7 spec section 4.1; criterion PB-27): one operation,
 * {@code getPublicListing}, GET, tag {@code Public listing}, no {@code security} entry; 200 {@code ListingPublic}, 404,
 * 429 and 500 as {@code ErrorResponse}, each with the {@code Cache-Control} header; every property of
 * {@code ListingPublic} required, nullable exactly {@code propertyType}, {@code provinceCode} and {@code wardCode}, the
 * two dates of format {@code date}, the terms and the features by reference to the staff schemas. Read from
 * {@code /v3/api-docs} with the annotations of {@link ListingPublicationOpenApiTest}, so its context is reused. The staff
 * preview of spec 4.2 too: {@code getListingRevisionPublicPreview}, tag {@code Listing}, no {@code security} entry.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingPublicOpenApiTest {

	private static final String PUBLIC_PATH = "/api/v1/public/listings/{code}";
	private static final List<String> PUBLIC_PROPERTIES = List.of("areaSqm", "bathrooms", "bedrooms", "code",
			"description", "features", "lastConfirmedOn", "liveOn", "propertyType", "provinceCode", "terms", "title",
			"transactionType", "wardCode");
	private static final String PREVIEW_PATH =
			"/api/v1/staff/listings/{listingId}/revisions/{revisionNo}/public-preview";
	private static final List<String> PREVIEW_PROPERTIES = List.of("areaSqm", "bathrooms", "bedrooms", "code",
			"description", "features", "lastConfirmedOn", "liveOn", "propertyType", "provinceCode", "provinceName",
			"revisionNo", "revisionStatus", "terms", "title", "transactionType", "wardCode", "wardName");

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

	/** PB-27: one GET, its id, its tag, no security entry, its four answers with their bodies and no-store. */
	@Test
	void publicRead_oneAnonymousGetWithItsAnswers() {
		JsonNode path = contract.path("paths").path(PUBLIC_PATH);
		assertEquals(List.of("get"), names(path));
		JsonNode operation = path.path("get");

		assertEquals("getPublicListing", operation.path("operationId").asString());
		assertEquals("[\"Public listing\"]", operation.path("tags").toString());
		assertFalse(operation.has("security"), operation.toString());
		JsonNode responses = operation.path("responses");
		assertEquals(List.of("200", "404", "429", "500"), names(responses).stream().sorted().toList());
		assertEquals("#/components/schemas/ListingPublic", schemaRef(responses.path("200")));
		List<String> wrong = new ArrayList<>();
		for (String status : List.of("404", "429", "500")) {
			if (!"#/components/schemas/ErrorResponse".equals(schemaRef(responses.path(status)))) {
				wrong.add(status + " " + schemaRef(responses.path(status)));
			}
		}
		for (String status : List.of("200", "404", "500")) {
			if (!responses.path(status).path("headers").has("Cache-Control")) {
				wrong.add(status + " has no Cache-Control header");
			}
		}
		assertEquals(List.of(), wrong);
		assertEquals(List.of("NOT_FOUND", "RATE_LIMITED", "INTERNAL_ERROR"), List.of(
				firstWord(responses.path("404")), firstWord(responses.path("429")), firstWord(responses.path("500"))));
		JsonNode parameter = operation.path("parameters").get(0);
		assertEquals(List.of("code", "path", "true"), List.of(parameter.path("name").asString(),
				parameter.path("in").asString(), parameter.path("required").asString()));
	}

	/** PB-27, E06S7-D6: every property required, nullable exactly the place codes and the type, the dates as dates. */
	@Test
	void listingPublic_everyPropertyRequiredNullableAsSaid() {
		JsonNode schema = contract.path("components").path("schemas").path("ListingPublic");
		JsonNode properties = schema.path("properties");

		assertEquals(PUBLIC_PROPERTIES, names(properties).stream().sorted().toList());
		assertEquals(PUBLIC_PROPERTIES, requiredOf(schema));
		Set<String> nullable = new TreeSet<>();
		properties.properties().forEach(property -> {
			if (isNullable(property.getValue())) {
				nullable.add(property.getKey());
			}
		});
		assertEquals(Set.of("propertyType", "provinceCode", "wardCode"), nullable);
		assertEquals(List.of("date", "date"), List.of(properties.path("liveOn").path("format").asString(),
				properties.path("lastConfirmedOn").path("format").asString()));
		assertEquals(List.of("#/components/schemas/ListingTerms", "#/components/schemas/ListingFeatures"),
				List.of(properties.path("terms").path("$ref").asString(),
						properties.path("features").path("$ref").asString()));
		assertEquals(List.of("int32", "int32", "double"), List.of(properties.path("bedrooms").path("format").asString(),
				properties.path("bathrooms").path("format").asString(),
				properties.path("areaSqm").path("format").asString()));
		List<String> types = new ArrayList<>();
		properties.path("propertyType").path("enum").forEach(value -> {
			if (!value.isNull()) {
				types.add(value.asString());
			}
		});
		assertEquals(List.of("HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL", "APARTMENT"), types);
		assertEquals("[\"LONG_TERM_RENT\"]", properties.path("transactionType").path("enum").toString());
	}

	/** PB-27, spec 4.2: the staff preview, one GET, its id, the tag Listing, no security entry, its five answers. */
	@Test
	void publicPreview_oneStaffGetWithItsAnswers() {
		JsonNode path = contract.path("paths").path(PREVIEW_PATH);
		assertEquals(List.of("get"), names(path));
		JsonNode operation = path.path("get");

		assertEquals("getListingRevisionPublicPreview", operation.path("operationId").asString());
		assertEquals("[\"Listing\"]", operation.path("tags").toString());
		assertFalse(operation.has("security"), operation.toString());
		JsonNode responses = operation.path("responses");
		assertEquals(List.of("200", "401", "403", "404", "500"), names(responses).stream().sorted().toList());
		List<String> refs = new ArrayList<>();
		for (String status : List.of("200", "401", "403", "404", "500")) {
			refs.add(status + " " + schemaRef(responses.path(status)));
		}
		assertEquals(List.of("200 #/components/schemas/ListingPublicPreview", "401 #/components/schemas/ErrorResponse",
				"403 #/components/schemas/ErrorResponse", "404 #/components/schemas/ErrorResponse",
				"500 #/components/schemas/ErrorResponse"), refs);
	}

	/** PB-27, spec 4.2: every property required, nullable exactly as 4.2 says, the statuses and the dates. */
	@Test
	void listingPublicPreview_everyPropertyRequiredNullableAsSaid() {
		JsonNode schema = contract.path("components").path("schemas").path("ListingPublicPreview");
		JsonNode properties = schema.path("properties");

		assertEquals(PREVIEW_PROPERTIES, names(properties).stream().sorted().toList());
		assertEquals(PREVIEW_PROPERTIES, requiredOf(schema));
		Set<String> nullable = new TreeSet<>();
		properties.properties().forEach(property -> {
			if (isNullable(property.getValue())) {
				nullable.add(property.getKey());
			}
		});
		assertEquals(new TreeSet<>(Set.of("areaSqm", "bathrooms", "bedrooms", "description", "lastConfirmedOn", "liveOn",
				"propertyType", "provinceCode", "provinceName", "terms", "title", "wardCode", "wardName")), nullable);
		assertEquals("[\"DRAFT\",\"PENDING_REVIEW\",\"NEEDS_CHANGES\",\"REJECTED\",\"APPROVED\",\"LIVE\",\"REPLACED\","
				+ "\"CANCELLED\"]", properties.path("revisionStatus").path("enum").toString());
		assertEquals(List.of("int32", "date", "date"), List.of(properties.path("revisionNo").path("format").asString(),
				properties.path("liveOn").path("format").asString(),
				properties.path("lastConfirmedOn").path("format").asString()));
		assertEquals("[\"LONG_TERM_RENT\"]", properties.path("transactionType").path("enum").toString());
	}

	private static String firstWord(JsonNode answer) {
		return answer.path("description").asString().split("[ :]")[0];
	}

	private static String schemaRef(JsonNode answer) {
		String[] reference = {""};
		answer.path("content").properties().forEach(media -> {
			if (reference[0].isEmpty()) {
				reference[0] = media.getValue().path("schema").path("$ref").asString("");
			}
		});
		return reference[0];
	}

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

	private static List<String> requiredOf(JsonNode schema) {
		List<String> required = new ArrayList<>();
		schema.path("required").forEach(field -> required.add(field.asString()));
		return required.stream().sorted().toList();
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}
}
