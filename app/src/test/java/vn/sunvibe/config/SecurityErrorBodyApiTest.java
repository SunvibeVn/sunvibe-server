package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The global 401 and 403 bodies on the ADR-010 envelope (survey-media spec A-13, 7.4; plan PD-2; SV-030): code
 * UNAUTHORIZED or FORBIDDEN, a fixed message, errors [] and the correlation id, written through the JSON mapper; never
 * the request path or an exception message; CORS headers kept; Connection: close on the media content paths. Real
 * HTTP through the whole filter chain.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityErrorBodyApiTest {

	private static final String UNAUTHORIZED_MESSAGE = "Authentication is required.";
	private static final String FORBIDDEN_MESSAGE = "You do not have permission to access this resource.";
	private static final String LEAD = "01JLEADSECBODYAAAAAAAAAAAA";
	private static final String ORIGIN = "http://localhost:3000";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	private AcquisitionApiTestSupport api;
	private TestUser customer;
	private TestUser sale;

	@BeforeAll
	void users() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		customer = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));
		sale = api.logIn(api.createUser(UserStatus.ACTIVE, "SALE"));
	}

	@Test
	void unauthenticated_answersEnvelopeWithoutPath() throws Exception {
		Result result = api.get(null, "/api/v1/staff/owner-leads");

		assertEnvelope(result, 401, "UNAUTHORIZED", UNAUTHORIZED_MESSAGE);
	}

	@Test
	void pathWithQuoteAndScriptTag_isNeverEchoed() throws Exception {
		Result result = api.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
				+ "/api/v1/staff/owner-leads/%22%3Cscript%3Ealert(1)%22%3E")).GET().build());

		assertEnvelope(result, 401, "UNAUTHORIZED", UNAUTHORIZED_MESSAGE);
		assertThat(result.rawBody()).doesNotContain("script").doesNotContain("\\\"");
	}

	@Test
	void roleForbidden_answersEnvelope() throws Exception {
		Result result = api.send(api.request(sale, "/features/admin/anything")
				.POST(HttpRequest.BodyPublishers.ofString("{}")).build());

		assertEnvelope(result, 403, "FORBIDDEN", FORBIDDEN_MESSAGE);
	}

	@Test
	void preAuthorizeDenied_rethrownBySurveyAcquisitionAndAuthHandlers_answersEnvelope() throws Exception {
		for (String path : List.of("/api/v1/staff/owner-leads/" + LEAD + "/survey",
				"/api/v1/staff/owner-leads/" + LEAD, "/api/v1/staff/users/" + sale.userId() + "/roles")) {
			TestUser caller = path.contains("/users/") ? sale : customer;

			assertEnvelope(api.get(caller, path), 403, "FORBIDDEN", FORBIDDEN_MESSAGE);
		}
	}

	@Test
	void correlationId_inBodyEqualsHeader() throws Exception {
		Result result = api.send(HttpRequest.newBuilder(api.uri("/api/v1/staff/owner-leads"))
				.header("X-Correlation-Id", "corr-12345678").GET().build());

		assertEquals("corr-12345678", result.body().path("correlationId").asString());
		assertEquals("corr-12345678", result.header("X-Correlation-Id"));
	}

	@Test
	void allowedOrigin_corsHeadersKeptOn401And403() throws Exception {
		Result unauthorized = api.send(HttpRequest.newBuilder(api.uri("/api/v1/staff/owner-leads"))
				.header("Origin", ORIGIN).GET().build());
		Result forbidden = api.send(api.request(customer, "/api/v1/staff/owner-leads/" + LEAD)
				.header("Origin", ORIGIN).GET().build());

		assertEquals(401, unauthorized.status());
		assertEquals(ORIGIN, unauthorized.header("Access-Control-Allow-Origin"));
		assertEquals(403, forbidden.status());
		assertEquals(ORIGIN, forbidden.header("Access-Control-Allow-Origin"));
	}

	@Test
	void mediaContentPath_401_carriesConnectionClose() throws Exception {
		List<String> head = rawGet("/api/v1/staff/survey-media/01JMEDIASECBODYAAAAAAAAAAA/content/thumbnail", null);

		assertTrue(head.get(0).contains(" 401"), head.get(0));
		assertThat(head).anyMatch(line -> line.equalsIgnoreCase("Connection: close"));
		List<String> other = rawGet("/api/v1/staff/owner-leads", null);
		assertThat(other).noneMatch(line -> line.equalsIgnoreCase("Connection: close"));
	}

	@Test
	void exceptionMessage_neverInBody() throws Exception {
		Result badToken = api.send(HttpRequest.newBuilder(api.uri("/api/v1/staff/owner-leads"))
				.header("Authorization", "Bearer not.a.jwt").GET().build());

		assertEnvelope(badToken, 401, "UNAUTHORIZED", UNAUTHORIZED_MESSAGE);
		assertThat(badToken.rawBody()).doesNotContainIgnoringCase("jwt").doesNotContainIgnoringCase("full authentication");
	}

	private static void assertEnvelope(Result result, int status, String code, String message) {
		assertEquals(status, result.status(), result.rawBody());
		JsonNode body = result.body();
		assertEquals(List.of("code", "message", "errors", "correlationId"), fieldNames(body), result.rawBody());
		assertEquals(code, body.path("code").asString());
		assertEquals(message, body.path("message").asString());
		assertTrue(body.path("errors").isArray() && body.path("errors").isEmpty(), result.rawBody());
		assertTrue(body.path("path").isMissingNode(), result.rawBody());
	}

	private static List<String> fieldNames(JsonNode body) {
		List<String> names = new ArrayList<>();
		body.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}

	/** The status line and headers of a raw HTTP/1.1 GET, so hop-by-hop headers are visible. */
	private List<String> rawGet(String path, String bearer) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(10_000);
			OutputStream out = socket.getOutputStream();
			out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost:" + port + "\r\n"
					+ (bearer == null ? "" : "Authorization: Bearer " + bearer + "\r\n") + "\r\n")
					.getBytes(StandardCharsets.US_ASCII));
			out.flush();
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
					StandardCharsets.US_ASCII));
			List<String> lines = new ArrayList<>();
			for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
				lines.add(line);
			}
			return lines;
		}
	}
}
