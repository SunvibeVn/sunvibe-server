package vn.sunvibe.config;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * The container's error page (ADR-010; survey-media security re-review, SV-069; keeps SV-030 resolved). Tomcat ends a
 * request itself when its body cannot be read (400, or 408 on a read timeout), which drops the application's own answer
 * and dispatches here: the answer is the shared envelope with the request's correlation id, never Spring Boot's default
 * error body with the request path, and never the security chain's 401 for a valid token. A failed body read answers
 * 400 MALFORMED_REQUEST on {@code body}, as survey-media spec 4.4 step 2 asks of a short or timed-out upload. Hidden
 * from the API contract: no client calls it.
 */
@Hidden
@RestController
public class ContainerErrorController implements ErrorController {

    private static final String UNREADABLE = "The request body could not be read.";

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ErrorResponse<ValidationError>> error(HttpServletRequest request,
                                                                HttpServletResponse response) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        // the first dispatch set the header; the logging context of that dispatch is gone here
        String correlationId = response.getHeader(CorrelationId.HEADER);
        int status = statusAttribute instanceof Integer value ? value : HttpStatus.NOT_FOUND.value();
        boolean unreadBody = exception instanceof IOException
                && (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.REQUEST_TIMEOUT.value());
        if (unreadBody) {
            return answer(HttpStatus.BAD_REQUEST.value(), ErrorCode.MALFORMED_REQUEST, UNREADABLE,
                    List.of(ValidationError.of("body", UNREADABLE)), correlationId);
        }
        if (status == HttpStatus.REQUEST_TIMEOUT.value() || status == HttpStatus.BAD_REQUEST.value()) {
            return answer(HttpStatus.BAD_REQUEST.value(), ErrorCode.MALFORMED_REQUEST, "The request could not be read.",
                    List.of(), correlationId);
        }
        if (status == HttpStatus.CONTENT_TOO_LARGE.value()) {
            return answer(status, ErrorCode.PAYLOAD_TOO_LARGE, "The request is too large.", List.of(), correlationId);
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return answer(status, ErrorCode.NOT_FOUND, "Not found.", List.of(), correlationId);
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return answer(status, ErrorCode.UNAUTHORIZED, "Authentication is required.", List.of(), correlationId);
        }
        if (status == HttpStatus.FORBIDDEN.value()) {
            return answer(status, ErrorCode.FORBIDDEN, "You do not have permission to access this resource.", List.of(),
                    correlationId);
        }
        if (status == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return answer(status, ErrorCode.METHOD_NOT_ALLOWED, "The method is not allowed.", List.of(), correlationId);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return answer(status, ErrorCode.RATE_LIMITED, "Too many requests.", List.of(), correlationId);
        }
        if (status >= 400 && status < 500) {
            return answer(status, ErrorCode.MALFORMED_REQUEST, "The request was refused.", List.of(), correlationId);
        }
        // a server-side status keeps its value (a 502 or 503 of the container is not turned into 500)
        int serverStatus = status >= 500 && status < 600 ? status : HttpStatus.INTERNAL_SERVER_ERROR.value();
        return answer(serverStatus, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", List.of(), correlationId);
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> answer(int status, String code, String message,
                                                                         List<ValidationError> errors,
                                                                         String correlationId) {
        return ResponseEntity.status(status).body(new ErrorResponse<>(code, message, errors, correlationId));
    }
}
