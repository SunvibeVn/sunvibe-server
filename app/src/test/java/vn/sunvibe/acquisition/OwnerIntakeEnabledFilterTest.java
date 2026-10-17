package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.acquisition.core.presentation.controller.OwnerIntakeController;
import vn.sunvibe.acquisition.core.presentation.filter.OwnerIntakeEnabledFilter;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * The server switch of the public intake form (acquisition-owner-intake BR-15, spec C-7): while it is off the form
 * path answers 404 before anything reads the body, so no personal data is accepted; other paths are not touched.
 */
class OwnerIntakeEnabledFilterTest {

	private static final String CORRELATION_ID = "01J0000000000000000000TEST";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@BeforeEach
	void setUp() {
		MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
	}

	@AfterEach
	void tearDown() {
		MDC.remove(CorrelationId.MDC_KEY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "POST", "GET", "PUT" })
	void doFilter_switchOff_answers404BeforeTheChain(String method) throws Exception {
		MockHttpServletRequest request = intakeRequest(method, "{\"contactName\":\"Nguyen Van A\"}");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		new OwnerIntakeEnabledFilter(JSON, false).doFilter(request, response, chain);

		assertNull(chain.getRequest(), "the request must not reach the application");
		assertEquals(404, response.getStatus());
		JsonNode body = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
		assertEquals("NOT_FOUND", body.path("code").asString());
		assertEquals("Resource not found.", body.path("message").asString());
		assertEquals(0, body.path("errors").size());
		assertEquals(CORRELATION_ID, body.path("correlationId").asString());
	}

	@Test
	void doFilter_switchOn_passesTheRequestOn() throws Exception {
		MockHttpServletRequest request = intakeRequest("POST", "{}");
		MockFilterChain chain = new MockFilterChain();

		new OwnerIntakeEnabledFilter(JSON, true).doFilter(request, new MockHttpServletResponse(), chain);

		assertNotNull(chain.getRequest());
	}

	@Test
	void doFilter_switchOffOtherPath_passesTheRequestOn() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/provinces");
		MockFilterChain chain = new MockFilterChain();

		new OwnerIntakeEnabledFilter(JSON, false).doFilter(request, new MockHttpServletResponse(), chain);

		assertNotNull(chain.getRequest());
	}

	private static MockHttpServletRequest intakeRequest(String method, String body) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, OwnerIntakeController.PATH);
		request.setContentType("application/json");
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		return request;
	}
}
