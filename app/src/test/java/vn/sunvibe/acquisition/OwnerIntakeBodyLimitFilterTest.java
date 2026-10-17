package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.acquisition.core.presentation.controller.OwnerIntakeController;
import vn.sunvibe.acquisition.core.presentation.filter.OwnerIntakeBodyLimitFilter;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * The 16 KB body cap of the public intake form (acquisition-owner-intake spec 5.1; W9-24), without a server: a body
 * whose size is not announced is only let through when it is JSON, because only the JSON reader goes through the
 * counting stream (a multipart or form body is parsed by the container from the raw request).
 */
class OwnerIntakeBodyLimitFilterTest {

	private static final String CORRELATION_ID = "01J0000000000000000000TEST";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final OwnerIntakeBodyLimitFilter filter = new OwnerIntakeBodyLimitFilter(JSON);

	@BeforeEach
	void setUp() {
		MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
	}

	@AfterEach
	void tearDown() {
		MDC.remove(CorrelationId.MDC_KEY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "multipart/form-data; boundary=x", "application/x-www-form-urlencoded", "text/plain",
			"not a media type" })
	void doFilter_bodyOfUnknownLengthThatIsNotJson_answers415BeforeTheChain(String contentType) throws Exception {
		MockHttpServletRequest request = intakeRequest(contentType, "a=b");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(unknownLength(request), response, chain);

		assertNull(chain.getRequest(), "the request must not reach the application");
		assertEquals(415, response.getStatus());
		JsonNode body = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
		assertEquals("MALFORMED_REQUEST", body.path("code").asString());
		assertEquals("The request cannot be processed.", body.path("message").asString());
		assertEquals(0, body.path("errors").size());
		assertEquals(CORRELATION_ID, body.path("correlationId").asString());
	}

	@Test
	void doFilter_bodyOfUnknownLengthWithoutContentType_answers415BeforeTheChain() throws Exception {
		MockHttpServletRequest request = intakeRequest(null, "{}");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(unknownLength(request), response, chain);

		assertNull(chain.getRequest());
		assertEquals(415, response.getStatus());
	}

	@ParameterizedTest
	@ValueSource(strings = { "application/json", "application/json;charset=UTF-8", "application/merge-patch+json",
			"APPLICATION/JSON" })
	void doFilter_jsonBodyOfUnknownLength_reachesTheChain(String contentType) throws Exception {
		MockHttpServletRequest request = intakeRequest(contentType, "{}");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(unknownLength(request), new MockHttpServletResponse(), chain);

		assertNotNull(chain.getRequest());
	}

	/** A request without a body (no length, no Transfer-Encoding) of another method keeps Spring MVC's 405. */
	@ParameterizedTest
	@ValueSource(strings = { "GET", "HEAD", "PUT", "DELETE", "OPTIONS" })
	void doFilter_otherMethodWithoutBody_reachesTheChain(String method) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, OwnerIntakeController.PATH);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertNotNull(chain.getRequest(), "the request must reach the application");
		assertEquals(200, response.getStatus(), "the filter wrote no answer");
	}

	/**
	 * A POST without Content-Length and without Transfer-Encoding has no body; if it is not JSON it is answered 415
	 * here, the same answer Spring MVC gives a POST that is not JSON.
	 */
	@Test
	void doFilter_postWithoutBodyThatIsNotJson_answers415() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", OwnerIntakeController.PATH);
		request.addHeader("Content-Type", "text/plain");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertNull(chain.getRequest());
		assertEquals(415, response.getStatus());
	}

	/** Another method with a chunked body that is not JSON is refused too: the container would parse it. */
	@Test
	void doFilter_chunkedMultipartWithOtherMethod_answers415BeforeTheChain() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("PUT", OwnerIntakeController.PATH);
		request.addHeader("Content-Type", "multipart/form-data; boundary=x");
		request.addHeader("Transfer-Encoding", "chunked");
		request.setContent("--x".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(unknownLength(request), response, chain);

		assertNull(chain.getRequest());
		assertEquals(415, response.getStatus());
	}

	/** A form body of announced, small size is left to Spring MVC, which answers 415 itself. */
	@Test
	void doFilter_nonJsonBodyOfKnownSmallLength_reachesTheChain() throws Exception {
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(intakeRequest("text/plain", "hello"), new MockHttpServletResponse(), chain);

		assertNotNull(chain.getRequest());
	}

	/** Servlet contract: an unknown charset is UnsupportedEncodingException (a 4xx), never a runtime 500. */
	@Test
	void getReader_unknownCharset_throwsUnsupportedEncodingException() throws Exception {
		MockHttpServletRequest request = intakeRequest("application/json", "{}");
		request.setCharacterEncoding("x-unknown-charset");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);
		ServletRequest passed = chain.getRequest();

		assertNotNull(passed);
		assertThrows(UnsupportedEncodingException.class, passed::getReader);
	}

	/**
	 * A name that is not even a valid charset name. Set after the filter: the mock would hand it to the path matching
	 * too, which Tomcat does not (OwnerIntakeApiPostgresTest covers it end to end).
	 */
	@Test
	void getReader_invalidCharsetName_throwsUnsupportedEncodingException() throws Exception {
		MockHttpServletRequest request = intakeRequest("application/json", "{}");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);
		request.setCharacterEncoding("not a charset!");
		ServletRequest passed = chain.getRequest();

		assertNotNull(passed);
		assertThrows(UnsupportedEncodingException.class, passed::getReader);
	}

	@Test
	void getReader_knownCharset_readsTheBody() throws Exception {
		MockHttpServletRequest request = intakeRequest("application/json", "{\"a\":1}");
		request.setCharacterEncoding("UTF-8");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertEquals("{\"a\":1}", chain.getRequest().getReader().readLine());
	}

	private static MockHttpServletRequest intakeRequest(String contentType, String body) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", OwnerIntakeController.PATH);
		if (contentType != null) {
			request.addHeader("Content-Type", contentType);
		}
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		return request;
	}

	/** The same request as a chunked one: no Content-Length. */
	private static HttpServletRequest unknownLength(HttpServletRequest request) {
		return new HttpServletRequestWrapper(request) {
			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
	}
}
