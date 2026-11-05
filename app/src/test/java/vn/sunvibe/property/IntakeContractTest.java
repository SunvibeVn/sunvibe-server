package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The committed contract of property-create intake spec 7 (S-31). springdoc keys a schema by its simple name, so two records with
 * one name would silently become one, and a client would get the wrong fields.
 */
class IntakeContractTest {

	private static JsonNode contract() throws IOException {
		Path file = Files.exists(Path.of("api/openapi.json")) ? Path.of("api/openapi.json") : Path.of("../api/openapi.json");
		return JsonMapper.builder().build().readTree(Files.readString(file));
	}

	@Test
	void schemas_haveNoGenericNameThatTwoRecordsCouldShare() throws IOException {
		JsonNode schemas = contract().path("components").path("schemas");

		for (String generic : List.of("Item", "Items", "Target", "Result", "Part", "Gate", "Check", "Term", "Source", "Space", "Owner",
				"Mapping", "Availability", "Completion", "Refusal", "Signals", "Suspension", "Release", "Code", "Confirm", "Phrase")) {
			assertTrue(schemas.path(generic).isMissingNode(), generic);
		}
	}

	@Test
	void block_namesItsValuesAndItsNulls() throws IOException {
		JsonNode block = contract().path("components").path("schemas").path("IntakeBlockView").path("properties");

		assertEquals("[\"string\",\"null\"]", block.path("status").path("type").toString());
		assertEquals("[\"NONE\",\"WAITING\",\"CONFIRMED\"]", block.path("ownerState").path("enum").toString());
		assertEquals("[\"NONE\",\"OPEN\",\"WAITING\",\"DECIDED\"]", block.path("caseState").path("enum").toString());
		assertFalse(block.path("target").path("anyOf").isMissingNode(), "a target can be null");
	}

	@Test
	void target_requestAndBlockTargetAreTwoSchemas() throws IOException {
		JsonNode paths = contract().path("paths");

		JsonNode put = paths.path("/api/v1/staff/owner-leads/{leadId}/inventory-intake/target").path("put");

		assertEquals("#/components/schemas/IntakeTargetRequest",
				put.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asString());
	}
}
