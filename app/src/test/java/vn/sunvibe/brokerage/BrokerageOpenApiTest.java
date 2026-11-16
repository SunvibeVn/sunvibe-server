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

	/** E05-S2 spec 4, S2-D1, AD-12: two operations, each with its explicit and unique operationId, tag and answers. */
	@Test
	void openApi_agreementOperations_idsTagsAndAnswers() {
		JsonNode collection = contract.path("paths").path("/api/v1/staff/brokerage-agreements");
		JsonNode item = contract.path("paths").path("/api/v1/staff/brokerage-agreements/{agreementId}");
		assertEquals(List.of("post"), names(collection));
		assertEquals(List.of("get"), names(item));
		JsonNode post = collection.path("post");
		JsonNode get = item.path("get");
		assertEquals("createBrokerageAgreement", post.path("operationId").asString());
		assertEquals("getBrokerageAgreement", get.path("operationId").asString());
		assertEquals("[\"Brokerage agreement\"]", post.path("tags").toString());
		assertEquals("[\"Brokerage agreement\"]", get.path("tags").toString());
		for (String operationId : List.of("createBrokerageAgreement", "getBrokerageAgreement")) {
			List<String> withTheId = new ArrayList<>();
			contract.path("paths").properties().forEach(entry -> entry.getValue().properties().forEach(operation -> {
				if (operationId.equals(operation.getValue().path("operationId").asString())) {
					withTheId.add(operation.getKey() + " " + entry.getKey());
				}
			}));
			assertEquals(1, withTheId.size(), operationId + " " + withTheId);
		}

		JsonNode created = post.path("responses");
		assertEquals(List.of("201", "400", "401", "403", "404", "409"), names(created).stream().sorted().toList());
		assertEquals("#/components/schemas/BrokerageAgreementCreated", schemaRef(created.path("201")));
		for (String status : List.of("400", "401", "403", "404", "409")) {
			assertEquals("#/components/schemas/ErrorResponse", schemaRef(created.path(status)), status);
		}
		JsonNode read = get.path("responses");
		assertEquals(List.of("200", "401", "403", "404"), names(read).stream().sorted().toList());
		assertEquals("#/components/schemas/BrokerageAgreementDetail", schemaRef(read.path("200")));
		for (String status : List.of("401", "403", "404")) {
			assertEquals("#/components/schemas/ErrorResponse", schemaRef(read.path(status)), status);
		}
	}

	/** E05-S2 spec 4, S2-D4, S2-D5, AD-12: the whole BR-5 vocabulary, the one type, calendar dates, the nullable code. */
	@Test
	void openApi_agreementSchemas_enumsDatesAndNullableCode() {
		JsonNode schemas = contract.path("components").path("schemas");
		JsonNode detail = schemas.path("BrokerageAgreementDetail").path("properties");
		assertEquals("[\"DRAFTING\",\"AWAITING_CONFIRMATION\",\"ACTIVE\",\"SUSPENDED\",\"EXPIRED\",\"TERMINATED\",\"CANCELLED\"]",
				detail.path("status").path("enum").toString());
		assertEquals("[\"LONG_TERM_RENT\"]", detail.path("transactionType").path("enum").toString());
		assertEquals("[\"string\",\"null\"]", detail.path("unitCode").path("type").toString());
		assertEquals("#/components/schemas/BrokerageAgreementVersion",
				detail.path("versions").path("items").path("$ref").asString());

		JsonNode version = schemas.path("BrokerageAgreementVersion").path("properties");
		assertEquals("[\"DRAFT\",\"AWAITING_OWNER\",\"OWNER_CONFIRMED\",\"ACTIVATED\",\"EFFECTIVE\",\"SUPERSEDED\","
				+ "\"OWNER_REJECTED\",\"LAPSED\",\"ACTIVATION_REFUSED\",\"CANCELLED\"]", version.path("status").path("enum").toString());
		assertEquals("date", version.path("startDate").path("format").asString());
		assertEquals("date", version.path("endDate").path("format").asString());

		JsonNode request = schemas.path("BrokerageAgreementCreateRequest");
		assertEquals("[\"LONG_TERM_RENT\"]", request.path("properties").path("transactionType").path("enum").toString());
		assertEquals("date", request.path("properties").path("startDate").path("format").asString());
		assertEquals("date", request.path("properties").path("endDate").path("format").asString());
		List<String> required = new ArrayList<>();
		request.path("required").forEach(field -> required.add(field.asString()));
		assertEquals(List.of("endDate", "leadId", "startDate", "transactionType", "unitId"),
				required.stream().sorted().toList());
		assertEquals(List.of("agreementId"), names(schemas.path("BrokerageAgreementCreated").path("properties")));
	}

	/** E05-S2 spec S2-D11, AD-12: the unit of the eligibility answer says when it has an agreement in progress. */
	@Test
	void openApi_eligibilityUnit_agreementFields() {
		JsonNode unit = contract.path("components").path("schemas").path("BrokerageEligibilityUnit").path("properties");
		assertEquals("boolean", unit.path("unfinishedAgreement").path("type").asString(), unit.toString());
		assertEquals("[\"string\",\"null\"]", unit.path("agreementId").path("type").toString(), unit.toString());
	}

	// ==================== E05-S3 (ED-14) ====================

	/** E05-S3 spec 4, S3-D1: the draft write and the survey reference, each with its explicit unique operationId. */
	@Test
	void openApi_draftAndSurveyTermsOperations_idsTagAndAnswers() {
		JsonNode draft = contract.path("paths").path("/api/v1/staff/brokerage-agreements/{agreementId}/draft");
		JsonNode surveyTerms = contract.path("paths").path("/api/v1/staff/brokerage-agreements/{agreementId}/survey-terms");
		assertEquals(List.of("put"), names(draft));
		assertEquals(List.of("get"), names(surveyTerms));
		JsonNode put = draft.path("put");
		JsonNode get = surveyTerms.path("get");
		assertEquals("updateBrokerageAgreementDraft", put.path("operationId").asString());
		assertEquals("getBrokerageAgreementSurveyTerms", get.path("operationId").asString());
		assertEquals("[\"Brokerage agreement\"]", put.path("tags").toString());
		assertEquals("[\"Brokerage agreement\"]", get.path("tags").toString());
		for (String operationId : List.of("updateBrokerageAgreementDraft", "getBrokerageAgreementSurveyTerms")) {
			List<String> withTheId = new ArrayList<>();
			contract.path("paths").properties().forEach(entry -> entry.getValue().properties().forEach(operation -> {
				if (operationId.equals(operation.getValue().path("operationId").asString())) {
					withTheId.add(operation.getKey() + " " + entry.getKey());
				}
			}));
			assertEquals(1, withTheId.size(), operationId + " " + withTheId);
		}
		assertEquals("#/components/schemas/BrokerageAgreementDraftRequest",
				put.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asString());

		JsonNode written = put.path("responses");
		assertEquals(List.of("204", "400", "401", "403", "404", "409", "500"), names(written).stream().sorted().toList());
		assertTrue(written.path("204").path("content").isMissingNode(), written.path("204").toString());
		for (String status : List.of("400", "401", "403", "404", "409", "500")) {
			assertEquals("#/components/schemas/ErrorResponse", schemaRef(written.path(status)), status);
		}
		for (String code : List.of("AGREEMENT_NOT_DRAFT", "AGREEMENT_NOT_ELIGIBLE", "CONCURRENT_MODIFICATION")) {
			assertTrue(written.path("409").path("description").asString().contains(code), code);
		}
		JsonNode read = get.path("responses");
		assertEquals(List.of("200", "401", "403", "404", "409", "500"), names(read).stream().sorted().toList());
		assertEquals("#/components/schemas/BrokerageAgreementSurveyTerms", schemaRef(read.path("200")));
		for (String status : List.of("401", "403", "404", "409", "500")) {
			assertEquals("#/components/schemas/ErrorResponse", schemaRef(read.path(status)), status);
		}
	}

	/** E05-S3 spec 4, S3-D8: every new schema's closed values, nullable parts and bounds. */
	@Test
	void openApi_termsSchemas_enumsBoundsAndNullables() {
		JsonNode schemas = contract.path("components").path("schemas");
		JsonNode request = schemas.path("BrokerageAgreementDraftRequest").path("properties");
		assertEquals("[\"LONG_TERM_RENT\"]", request.path("transactionType").path("enum").toString());
		assertEquals(0, request.path("expectedDataVersion").path("minimum").asInt());
		assertEquals("int64", request.path("expectedDataVersion").path("format").asString());
		assertEquals("[\"boolean\",\"null\"]", request.path("photoConsent").path("type").toString());
		assertEquals("boolean", request.path("exteriorPhotoConsent").path("type").asString());
		List<String> required = new ArrayList<>();
		schemas.path("BrokerageAgreementDraftRequest").path("required").forEach(field -> required.add(field.asString()));
		assertEquals(List.of("endDate", "expectedDataVersion", "exteriorPhotoConsent", "startDate", "terms",
				"transactionType"), required.stream().sorted().toList());

		JsonNode termsInput = schemas.path("BrokerageAgreementTermsInput").path("properties");
		assertEquals(20, termsInput.path("mandatoryFees").path("maxItems").asInt());
		assertEquals("[\"array\",\"null\"]", termsInput.path("mandatoryFees").path("type").toString());
		assertEquals(120, termsInput.path("minimumTermMonths").path("maximum").asInt());
		assertEquals(12, termsInput.path("paymentPeriodMonths").path("maximum").asInt());
		JsonNode rentInput = schemas.path("BrokerageAgreementRentInput").path("properties");
		assertEquals(999_999_999_999_999L, rentInput.path("amount").path("maximum").asLong());
		assertEquals(1, rentInput.path("amount").path("minimum").asInt());
		assertEquals("[\"VND\"]", rentInput.path("currency").path("enum").toString());
		assertEquals("[\"MONTH\"]", rentInput.path("unit").path("enum").toString());
		assertEquals("[\"MONTHS\",\"AMOUNT\"]",
				schemas.path("BrokerageAgreementDepositInput").path("properties").path("basis").path("enum").toString());
		JsonNode feeInput = schemas.path("BrokerageAgreementFeeInput").path("properties");
		assertEquals(100, feeInput.path("name").path("maxLength").asInt());
		assertEquals("[\"AMOUNT\",\"INCLUDED\",\"PROVIDER_TARIFF\"]", feeInput.path("basis").path("enum").toString());

		JsonNode terms = schemas.path("BrokerageAgreementTerms").path("properties");
		for (String part : List.of("minimumTermMonths", "paymentPeriodMonths")) {
			assertEquals("[\"integer\",\"null\"]", terms.path(part).path("type").toString(), part);
		}
		assertEquals("[\"array\",\"null\"]", terms.path("mandatoryFees").path("type").toString());
		assertEquals("#/components/schemas/BrokerageAgreementFee",
				terms.path("mandatoryFees").path("items").path("$ref").asString());
		JsonNode rent = schemas.path("BrokerageAgreementRent").path("properties");
		assertEquals("int64", rent.path("amount").path("format").asString());
		assertEquals("[\"VND\"]", rent.path("currency").path("enum").toString());
		assertEquals("[\"MONTH\"]", rent.path("unit").path("enum").toString());
		JsonNode deposit = schemas.path("BrokerageAgreementDeposit").path("properties");
		assertEquals("[\"MONTHS\",\"AMOUNT\"]", deposit.path("basis").path("enum").toString());
		for (String part : List.of("months", "amount", "currency")) {
			assertTrue(deposit.path(part).path("type").toString().contains("null"), part);
		}
		JsonNode fee = schemas.path("BrokerageAgreementFee").path("properties");
		assertEquals("[\"AMOUNT\",\"INCLUDED\",\"PROVIDER_TARIFF\"]", fee.path("basis").path("enum").toString());
		assertEquals("[\"PER_MONTH\",\"PER_KWH\",\"PER_M3\",\"PER_PERSON_PER_MONTH\",\"PER_VEHICLE_PER_MONTH\",\"ONE_OFF\",null]",
				fee.path("chargeUnit").path("enum").toString());
		for (String part : List.of("amount", "currency", "chargeUnit")) {
			assertTrue(fee.path(part).path("type").toString().contains("null"), part);
		}
		JsonNode survey = schemas.path("BrokerageAgreementSurveyTerms").path("properties");
		assertEquals("#/components/schemas/BrokerageAgreementTerms", survey.path("terms").path("$ref").asString());
		assertEquals("#/components/schemas/BrokerageAgreementTermSource",
				survey.path("sources").path("items").path("$ref").asString());
		JsonNode source = schemas.path("BrokerageAgreementTermSource").path("properties");
		assertEquals("[\"OBSERVED_ON_SITE\",\"OWNER_STATED\",\"OWNER_FORM\",null]", source.path("source").path("enum").toString());
		assertEquals("date", source.path("date").path("format").asString());
	}

	/** E05-S3 spec S3-D14: the detail's data version and viewer flag, and the content of each version. */
	@Test
	void openApi_detailAndVersion_contentProperties() {
		JsonNode schemas = contract.path("components").path("schemas");
		JsonNode detail = schemas.path("BrokerageAgreementDetail").path("properties");
		assertEquals("integer", detail.path("dataVersion").path("type").asString());
		assertEquals("int64", detail.path("dataVersion").path("format").asString());
		assertEquals("boolean", detail.path("viewerIsAssignedSale").path("type").asString());
		JsonNode version = schemas.path("BrokerageAgreementVersion").path("properties");
		assertEquals("[\"SUNVIBE_CHANNELS\"]", version.path("advertisingScope").path("enum").toString());
		assertEquals("[\"boolean\",\"null\"]", version.path("photoConsent").path("type").toString());
		assertEquals("boolean", version.path("exteriorPhotoConsent").path("type").asString());
		assertEquals("#/components/schemas/BrokerageAgreementTerms", version.path("terms").path("$ref").asString());
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
