package vn.sunvibe.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import vn.sunvibe.contract.WireContract.Fault;

/**
 * What the filter leaves to which dispatch, and that a broken check never reaches the client (contract-wire spec 4.3,
 * S-7). The real dispatches are in {@link WireContractActiveApiTest}.
 */
class WireContractFilterTest {

	private static final String PATH = "/api/v1/filter-test/7";
	private static final String TEMPLATE = "/api/v1/filter-test/{id}";
	private static final String CONTRACT = """
			{ "openapi": "3.1.0", "paths": { "/api/v1/filter-test/{id}": { "get": { "responses": {
			  "200": { "description": "A name", "content": { "*/*": { "schema": { "type": "object", "properties": {
			    "name": { "type": "string" } } } } } },
			  "404": { "description": "None" }
			} } } } }
			""";

	private final WireLedger ledger = new WireLedger();
	private final WireContractFilter filter = new WireContractFilter(() -> WireContract.of(CONTRACT), ledger);

	@Test
	void answerWithAFault_isRecordedAndStillReachesTheClient() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(get(PATH), response, writes(200, "{\"name\":null}"));

		assertEquals(200, response.getStatus());
		assertEquals("{\"name\":null}", response.getContentAsString());
		assertEquals(List.of(new Fault("GET", TEMPLATE, 200, "/name", "type")), ledger.drainFaults());
		assertTrue(ledger.seen("GET", TEMPLATE, 200));
	}

	@Test
	void unreadableContract_isAFaultAndTheClientKeepsItsAnswer() throws Exception {
		WireContractFilter broken = new WireContractFilter(() -> {
			throw new IllegalStateException("no contract");
		}, ledger);
		MockHttpServletResponse response = new MockHttpServletResponse();

		broken.doFilter(get(PATH), response, writes(200, "{\"name\":\"a\"}"));

		assertEquals(200, response.getStatus());
		assertEquals("{\"name\":\"a\"}", response.getContentAsString());
		assertEquals(List.of(new Fault("GET", PATH, 200, "", "check-failed")), ledger.drainFaults());
	}

	/** Without the rule the empty pass would count as a body missing, and the same answer would count twice. */
	@Test
	void passEndingInSendError_isLeftToItsErrorDispatch() throws Exception {
		String path = "/api/v1/filter-test/send-error";

		filter.doFilter(get(path), new MockHttpServletResponse(),
				(request, response) -> ((jakarta.servlet.http.HttpServletResponse) response).sendError(404));

		assertFalse(ledger.seen("GET", TEMPLATE, 404));
		assertEquals(List.of(), ledger.drainFaults());
	}

	@Test
	void errorDispatch_usesTheOriginalPath() throws Exception {
		String original = "/api/v1/filter-test-error/7";
		MockHttpServletRequest dispatch = new MockHttpServletRequest("GET", "/error");
		dispatch.setDispatcherType(DispatcherType.ERROR);
		dispatch.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, original);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(dispatch, response, writes(404, "{\"code\":\"NOT_FOUND\"}"));

		assertTrue(ledger.seen("GET", original, 404));
		assertEquals("{\"code\":\"NOT_FOUND\"}", response.getContentAsString());
	}

	@Test
	void chainThatThrows_recordsNothing() {
		String path = "/api/v1/filter-test/thrown";
		IllegalStateException failure = new IllegalStateException("chain");

		IllegalStateException thrown = assertThrows(IllegalStateException.class,
				() -> filter.doFilter(get(path), new MockHttpServletResponse(), (request, response) -> {
					throw failure;
				}));

		assertSame(failure, thrown);
		assertFalse(ledger.seen("GET", TEMPLATE, 200));
	}

	@Test
	void binaryRead_isNotBuffered() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<ServletResponse> seen = new AtomicReference<>();

		filter.doFilter(get("/api/v1/staff/survey-media/m1/content/preview"), response,
				(request, passed) -> seen.set(passed));

		assertSame(response, seen.get());
	}

	@Test
	void pathOutsideTheApi_isNotBuffered() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<ServletResponse> seen = new AtomicReference<>();

		filter.doFilter(get("/v3/api-docs"), response, (request, passed) -> seen.set(passed));

		assertSame(response, seen.get());
	}

	private static MockHttpServletRequest get(String path) {
		return new MockHttpServletRequest("GET", path);
	}

	private static FilterChain writes(int status, String body) {
		return (request, response) -> {
			jakarta.servlet.http.HttpServletResponse http = (jakarta.servlet.http.HttpServletResponse) response;
			http.setStatus(status);
			http.setContentType("application/json");
			write(http, body);
		};
	}

	private static void write(jakarta.servlet.http.HttpServletResponse response, String body) throws IOException {
		response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
	}
}
