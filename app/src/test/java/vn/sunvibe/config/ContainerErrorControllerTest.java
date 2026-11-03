package vn.sunvibe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.RequestDispatcher;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * The container's error page (ADR-010; survey-media security re-review, SV-069; keeps SV-030 resolved): the envelope,
 * a failed body read answered as survey-media spec 4.4 step 2 asks (400 MALFORMED_REQUEST on body, a read timeout
 * included), the status of any other container error kept, and the correlation id of the request.
 */
class ContainerErrorControllerTest {

	private final ContainerErrorController controller = new ContainerErrorController();

	@Test
	void failedBodyRead_400MalformedOnBody_withTheRequestsCorrelationId() {
		ResponseEntity<ErrorResponse<ValidationError>> answer = answer(400, new EOFException("unexpected EOF"));

		assertEquals(400, answer.getStatusCode().value());
		assertEquals("MALFORMED_REQUEST", answer.getBody().code());
		assertEquals(List.of("body"), answer.getBody().errors().stream().map(ValidationError::field).toList());
		assertEquals("01JCORRELATION000000000001", answer.getBody().correlationId());
	}

	@Test
	void bodyReadTimeout408_answers400MalformedOnBody() {
		ResponseEntity<ErrorResponse<ValidationError>> answer = answer(408, new SocketTimeoutException("Read timed out"));

		assertEquals(400, answer.getStatusCode().value());
		assertEquals("MALFORMED_REQUEST", answer.getBody().code());
		assertEquals(List.of("body"), answer.getBody().errors().stream().map(ValidationError::field).toList());
	}

	@Test
	void otherContainerErrors_keepTheirStatus_messagesNeverEchoed() {
		assertEquals("PAYLOAD_TOO_LARGE", answer(413, null).getBody().code());
		assertEquals(413, answer(413, null).getStatusCode().value());
		assertEquals("NOT_FOUND", answer(404, null).getBody().code());
		assertEquals("MALFORMED_REQUEST", answer(400, null).getBody().code());
		assertEquals(List.of(), answer(400, null).getBody().errors());
		ResponseEntity<ErrorResponse<ValidationError>> failure = answer(500, new IllegalStateException("db password"));
		assertEquals(500, failure.getStatusCode().value());
		assertEquals("INTERNAL_ERROR", failure.getBody().code());
		assertEquals(false, failure.getBody().message().contains("db password"));
		assertEquals(503, answer(503, null).getStatusCode().value(), "a server status keeps its value");
		assertEquals("METHOD_NOT_ALLOWED", answer(405, null).getBody().code());
		assertEquals("UNAUTHORIZED", answer(401, null).getBody().code());
		assertEquals("FORBIDDEN", answer(403, null).getBody().code());
		assertEquals("RATE_LIMITED", answer(429, null).getBody().code());
	}

	@Test
	void directRequestWithoutContainerError_404() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
		ResponseEntity<ErrorResponse<ValidationError>> answer = controller.error(request,
				new MockHttpServletResponse());

		assertEquals(404, answer.getStatusCode().value());
		assertNull(answer.getBody().correlationId());
	}

	private ResponseEntity<ErrorResponse<ValidationError>> answer(int status, Throwable exceptionOrNull) {
		MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/error");
		request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
		if (exceptionOrNull != null) {
			request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exceptionOrNull);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setHeader(CorrelationId.HEADER, "01JCORRELATION000000000001");
		return controller.error(request, response);
	}
}
