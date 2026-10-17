package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.PostgresContainerConfig;

/**
 * The anonymous catalog endpoints end to end (acquisition-owner-intake spec 6.1, 6.2; W9-14, W9-16, W9-18): no login,
 * real security chain, PostgreSQL seeded by Flyway. Needs Docker, like the other Postgres tests.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AdministrativeAreaApiPostgresTest {

	private static final String PROVINCES = "/api/v1/public/provinces";

	@Value("${local.server.port}")
	private int port;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final JsonMapper json = JsonMapper.builder().build();

	/** W9-14: the one seeded province, with only code and name. */
	@Test
	void getProvinces_noLogin_returnsDaNang() throws Exception {
		HttpResponse<String> response = send("GET", PROVINCES);

		assertEquals(200, response.statusCode());
		assertEquals(json.readTree("[{\"code\":\"48\",\"name\":\"Đà Nẵng\"}]"), json.readTree(response.body()));
	}

	/** W9-14: sixteen wards with code, name and type, sorted by name; no province or audit fields leak. */
	@Test
	void getWards_daNang_returnsSixteenSortedWardsWithTypes() throws Exception {
		HttpResponse<String> response = send("GET", PROVINCES + "/48/wards");

		assertEquals(200, response.statusCode());
		JsonNode wards = json.readTree(response.body());
		assertEquals(16, wards.size());
		List<String> names = new ArrayList<>();
		wards.forEach(ward -> {
			names.add(ward.get("name").asString());
			assertEquals(List.of("code", "name", "type"), ward.propertyNames().stream().sorted().toList());
		});
		assertTrue(names.indexOf("An Hải") < names.indexOf("Bà Nà"), "sorted by Vietnamese name: " + names);
		assertTrue(names.indexOf("Liên Chiểu") < names.indexOf("Ngũ Hành Sơn"), "sorted by name: " + names);
		assertEquals("20242", codeOf(wards, "Hải Châu"));
		assertEquals("WARD", typeOf(wards, "Hải Châu"));
		assertEquals("COMMUNE", typeOf(wards, "Hòa Vang"));
		assertEquals("SPECIAL_ZONE", typeOf(wards, "Hoàng Sa"));
	}

	/** W9-14, W9-16: an unknown province is 404 NOT_FOUND in the ADR-010 shape, with the correlation id of the call. */
	@Test
	void getWards_unknownProvince_returns404WithCodeAndCorrelationId() throws Exception {
		HttpResponse<String> response = send("GET", PROVINCES + "/99/wards");

		assertEquals(404, response.statusCode());
		JsonNode body = json.readTree(response.body());
		assertEquals("NOT_FOUND", body.get("code").asString());
		assertTrue(body.get("errors").isArray());
		assertEquals(0, body.get("errors").size());
		String headerId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
		assertEquals(headerId, body.get("correlationId").asString());
		assertFalse(body.get("message").asString().contains("99"), "the submitted value is never echoed");
	}

	/** W9-18: only the two GET catalog paths are public; any other method on the path still needs a token. */
	@Test
	void postProvinces_noToken_returns401() throws Exception {
		HttpResponse<String> response = send("POST", PROVINCES);

		assertEquals(401, response.statusCode());
	}

	/** W9-18: a sibling path under the same prefix is not public either. */
	@Test
	void getProvinceByCode_noToken_returns401() throws Exception {
		HttpResponse<String> response = send("GET", PROVINCES + "/48");

		assertEquals(401, response.statusCode());
	}

	private HttpResponse<String> send(String method, String path) throws Exception {
		HttpRequest.BodyPublisher body = method.equals("POST")
				? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody();
		return http.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
						.timeout(Duration.ofSeconds(30))
						.header("Content-Type", "application/json")
						.method(method, body)
						.build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private static String codeOf(JsonNode wards, String name) {
		return field(wards, name, "code");
	}

	private static String typeOf(JsonNode wards, String name) {
		return field(wards, name, "type");
	}

	private static String field(JsonNode wards, String name, String field) {
		for (JsonNode ward : wards) {
			if (ward.get("name").asString().equals(name)) {
				return ward.get(field).asString();
			}
		}
		throw new AssertionError("no ward named " + name);
	}
}
