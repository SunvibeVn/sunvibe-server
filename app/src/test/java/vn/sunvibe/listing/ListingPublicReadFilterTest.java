package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.listing.core.presentation.filter.ListingPublicReadFilter;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * The filter of the anonymous public path (listing-publish E06-S7 spec E06S7-D7, D8; criterion PB-13; Review Focus 1):
 * matched on {@code ListingPublicController.PATH} as MVC and Security match it, GET only. It sets {@code Cache-Control:
 * no-store} on every answer of the path; with the switch off, or a code that is not well formed, it writes the one 404
 * body itself and never calls the chain, so no controller, transaction or connection is reached. Built by hand, as
 * {@code OwnerIntakeEnabledFilterTest} builds its filter.
 */
class ListingPublicReadFilterTest {

	private static final String CORRELATION_ID = "01J0000000000000000000TEST";
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String PATH = "/api/v1/public/listings/";
	private static final String CODE = "TD-2345-6789";

	/** A chain that fails the test when the filter calls it. */
	private static final FilterChain NEVER = (request, response) -> {
		throw new AssertionError("the chain must not be called");
	};

	@BeforeEach
	void setUp() {
		MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
	}

	@AfterEach
	void tearDown() {
		MDC.remove(CorrelationId.MDC_KEY);
	}

	/** PB-13: the switch off, a well-formed code: the one 404 body with no-store, the chain never called. */
	@Test
	void doFilter_switchOffWellFormedCode_notFoundNoStoreNoChain() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ListingPublicReadFilter(JSON, false).doFilter(get(PATH + CODE), response, NEVER);

		assertNotFound(response);
	}

	/** PB-13, Review Focus 1: the switch on, a code not of the code's form: the same, the chain never called. */
	@ParameterizedTest
	@ValueSource(strings = { "td-abcd-efgh", "TD-ABCD-EFG", "TD-IOIO-2345", "TD-ABCD-EFGHJ", "x" })
	void doFilter_switchOnMalformedCode_notFoundNoStoreNoChain(String code) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ListingPublicReadFilter(JSON, true).doFilter(get(PATH + code), response, NEVER);

		assertNotFound(response);
	}

	/** Review Focus 1: a 10,000-character code segment answers the one 404 without the chain. */
	@Test
	void doFilter_switchOnVeryLongSegment_notFoundNoChain() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new ListingPublicReadFilter(JSON, true).doFilter(get(PATH + "T".repeat(10_000)), response, NEVER);

		assertNotFound(response);
	}

	/** PB-13: a well-formed code reaches the chain with no-store already set. */
	@Test
	void doFilter_switchOnWellFormedCode_chainWithNoStore() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new ListingPublicReadFilter(JSON, true).doFilter(get(PATH + CODE), response, chain);

		assertNotNull(chain.getRequest());
		assertEquals("no-store", response.getHeader("Cache-Control"));
		assertEquals(200, response.getStatus());
	}

	/** Review Focus 1: a percent-encoded well-formed code is read as MVC decodes it, so it passes. */
	@Test
	void doFilter_switchOnEncodedWellFormedCode_chainAsMvcDecodesIt() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new ListingPublicReadFilter(JSON, true).doFilter(get(PATH + "TD-ABCD-EFG%48"), response, chain);

		assertNotNull(chain.getRequest());
		assertEquals("no-store", response.getHeader("Cache-Control"));
	}

	/** PB-13: other paths, and another method on the path, pass untouched, the switch off. */
	@Test
	void doFilter_otherPathsAndMethods_untouched() throws Exception {
		List<String> touched = new ArrayList<>();
		for (MockHttpServletRequest request : new MockHttpServletRequest[] {
				get("/api/v1/public/provinces"), get(PATH + CODE + "/photos"), get("/api/v1/public/listings"),
				request("POST", PATH + CODE), request("PUT", PATH + CODE), request("HEAD", PATH + CODE) }) {
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockFilterChain chain = new MockFilterChain();

			new ListingPublicReadFilter(JSON, false).doFilter(request, response, chain);

			if (chain.getRequest() == null || response.getHeader("Cache-Control") != null
					|| response.getStatus() != 200) {
				touched.add(request.getMethod() + " " + request.getRequestURI());
			}
		}

		assertEquals(List.of(), touched);
	}

	private static void assertNotFound(MockHttpServletResponse response) throws Exception {
		assertEquals(404, response.getStatus());
		assertEquals("no-store", response.getHeader("Cache-Control"));
		JsonNode body = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
		assertEquals("NOT_FOUND", body.path("code").asString());
		assertEquals("The resource was not found.", body.path("message").asString());
		assertEquals(0, body.path("errors").size());
		assertEquals(CORRELATION_ID, body.path("correlationId").asString());
	}

	private static MockHttpServletRequest get(String uri) {
		return request("GET", uri);
	}

	private static MockHttpServletRequest request(String method, String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.setRequestURI(uri);
		return request;
	}
}
