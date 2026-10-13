package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Writes the API contract to api/openapi.json. Runs only through {@code ./gradlew exportOpenApi}
 * (the tag is excluded from the normal test task), on the H2 test profile, so no database is needed.
 */
@Tag("openapi-export")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"springdoc.writer-with-default-pretty-printer=true",
				"springdoc.writer-with-order-by-keys=true"
		})
@ActiveProfiles("test")
class OpenApiExportTest {

	/** The random test port would otherwise change the file on every run. */
	private static final String STABLE_SERVER_URL = "http://localhost:8082";

	@Value("${local.server.port}")
	private int port;

	@Test
	void exportOpenApi_writesTheContractFile() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode(), response.body());

		String contract = response.body().replace("http://localhost:" + port, STABLE_SERVER_URL);
		assertTrue(contract.contains("\"openapi\""), "Response is not an OpenAPI document");

		Path output = Path.of(System.getProperty("openapi.output", "../api/openapi.json"));
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, contract.endsWith("\n") ? contract : contract + "\n");
	}
}
