package vn.sunvibe.acquisition.core.presentation.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.acquisition.core.application.exception.IdempotencyKeyReusedException;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Status mapping of the owner intake endpoints (acquisition-owner-intake spec 6.5, ADR-010; W9-3, W9-16). Each
 * exception goes through the handler method Spring MVC would pick ({@link ExceptionHandlerMethodResolver}), so the
 * table checks the dispatch as well as the body. Every body carries the code, the field (or {@code []}) and the
 * correlation id of the request, and never the submitted value.
 */
class AcquisitionExceptionHandlerTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";
    /** Stands for a submitted value (a phone number) that must reach neither a body nor a log line. */
    private static final String SUBMITTED = "0909123456";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpInputMessage INPUT = new HttpInputMessage() {
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    };

    private final AcquisitionExceptionHandler handler = new AcquisitionExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(AcquisitionExceptionHandler.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(AcquisitionExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        MDC.remove(CorrelationId.MDC_KEY);
    }

    /** Stand-in for a controller method, so the Spring exceptions get a real parameter. */
    @SuppressWarnings("unused")
    static void submit(@RequestHeader("Idempotency-Key") String idempotencyKey) {
    }

    static Stream<Arguments> mapping() throws Exception {
        MethodParameter header = headerParameter();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "ownerIntakeRequest");
        binding.addError(new FieldError("ownerIntakeRequest", "phone", SUBMITTED, false, null, null,
                "Phone is required."));
        binding.addError(new FieldError("ownerIntakeRequest", "askingPrice.amount", "Asking price amount must be a "
                + "whole number from 1 to 15 digits."));
        binding.addError(new FieldError("ownerIntakeRequest", "askingPrice.amount", "Second message, same field."));

        MethodValidationResult headerResult = MethodValidationResult.create(new Object(),
                AcquisitionExceptionHandlerTest.class.getDeclaredMethod("submit", String.class),
                List.of(new ParameterValidationResult(header, SUBMITTED,
                        List.of(new DefaultMessageSourceResolvable(new String[]{"Pattern"}, "must match")),
                        null, null, null, (error, type) -> null)));

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        ConstraintViolationException violation = new ConstraintViolationException(
                factory.getValidator().validate(new OwnerIntakeRequest(null, SUBMITTED, null, null, null, null, null,
                        null, null, null, null, null, null, null, null)).stream()
                        .filter(v -> v.getPropertyPath().toString().equals("contactName"))
                        .collect(java.util.stream.Collectors.toSet()));
        factory.close();

        return Stream.of(
                Arguments.of(new MethodArgumentNotValidException(header, binding),
                        400, ErrorCode.VALIDATION_FAILED, List.of("askingPrice.amount", "phone")),
                Arguments.of(new IntakeValidationException("wardCode", "Ward is not in the catalog."),
                        400, ErrorCode.VALIDATION_FAILED, List.of("wardCode")),
                Arguments.of(new InvalidIntakeValueException("phone", "Phone number is not valid."),
                        400, ErrorCode.VALIDATION_FAILED, List.of("phone")),
                Arguments.of(new MethodArgumentTypeMismatchException(SUBMITTED, Integer.class, "page", header, null),
                        400, ErrorCode.VALIDATION_FAILED, List.of("page")),
                Arguments.of(new MissingServletRequestParameterException("page", "int"),
                        400, ErrorCode.VALIDATION_FAILED, List.of("page")),
                Arguments.of(new MissingRequestHeaderException("Idempotency-Key", header),
                        400, ErrorCode.VALIDATION_FAILED, List.of("Idempotency-Key")),
                Arguments.of(new HandlerMethodValidationException(headerResult),
                        400, ErrorCode.VALIDATION_FAILED, List.of("Idempotency-Key")),
                Arguments.of(violation,
                        400, ErrorCode.VALIDATION_FAILED, List.of("contactName")),
                Arguments.of(new HttpMessageNotReadableException("JSON parse error", parseError("{\"intent\":"
                                + "\"RENT\"}"), INPUT),
                        400, ErrorCode.VALIDATION_FAILED, List.of("intent")),
                Arguments.of(new HttpMessageNotReadableException("JSON parse error", parseError("{not json"), INPUT),
                        400, ErrorCode.MALFORMED_REQUEST, List.of()),
                Arguments.of(new HttpMessageNotReadableException("Required request body is missing", INPUT),
                        400, ErrorCode.MALFORMED_REQUEST, List.of()),
                Arguments.of(new PayloadTooLargeException(),
                        413, ErrorCode.PAYLOAD_TOO_LARGE, List.of()),
                Arguments.of(new HttpMessageNotReadableException("I/O error", new PayloadTooLargeException(), INPUT),
                        413, ErrorCode.PAYLOAD_TOO_LARGE, List.of()),
                Arguments.of(new HttpMessageNotReadableException("JSON parse error",
                                new RuntimeException(new PayloadTooLargeException()), INPUT),
                        413, ErrorCode.PAYLOAD_TOO_LARGE, List.of()),
                Arguments.of(new IdempotencyKeyReusedException(),
                        409, ErrorCode.IDEMPOTENCY_KEY_REUSED, List.of()),
                Arguments.of(new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN,
                                List.of(MediaType.APPLICATION_JSON)),
                        415, ErrorCode.MALFORMED_REQUEST, List.of()),
                Arguments.of(new HttpRequestMethodNotSupportedException("GET", List.of("POST")),
                        405, ErrorCode.MALFORMED_REQUEST, List.of()),
                Arguments.of(new IllegalStateException("broken " + SUBMITTED),
                        500, ErrorCode.INTERNAL_ERROR, List.of()));
    }

    @ParameterizedTest
    @MethodSource("mapping")
    void handle_eachExceptionType_returnsStatusCodeAndFieldsOfSpec65(Exception exception, int status, String code,
                                                                     List<String> fields) throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(exception);

        assertEquals(status, response.getStatusCode().value());
        ErrorResponse<ValidationError> body = response.getBody();
        assertNotNull(body);
        assertEquals(code, body.code());
        assertEquals(fields, body.errors().stream().map(ValidationError::field).toList());
        assertEquals(CORRELATION_ID, body.correlationId());
        assertFalse(body.message().isBlank());
        assertFalse(body.toString().contains(SUBMITTED), body.toString());
    }

    @Test
    void handle_accessDenied_isRethrown() {
        AccessDeniedException denied = new AccessDeniedException("denied");

        assertSame(denied, assertThrows(AccessDeniedException.class, () -> handle(denied)));
    }

    @Test
    void handle_authentication_isRethrown() {
        BadCredentialsException failure = new BadCredentialsException("bad");

        assertSame(failure, assertThrows(BadCredentialsException.class, () -> handle(failure)));
    }

    @Test
    void handle_dataAccessException_logsOnlyClassAndCorrelationId() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint (phone)=(" + SUBMITTED + ")"));

        assertEquals(500, response.getStatusCode().value());
        assertOneLogLineWithClassOnly("DataIntegrityViolationException");
    }

    @Test
    void handle_jacksonException_logsOnlyClassAndCorrelationId() throws Exception {
        handle(parseError("{\"phone\":[\"" + SUBMITTED + "\"]}"));

        assertOneLogLineWithClassOnly("MismatchedInputException");
    }

    @Test
    void handle_beanValidationException_logsOnlyClassAndCorrelationId() throws Exception {
        handle(new jakarta.validation.ValidationException("value " + SUBMITTED));

        assertOneLogLineWithClassOnly("ValidationException");
    }

    @Test
    void handle_exceptionCausedBySqlError_logsNoStackTraceBecauseTheSqlMessageCarriesValues() throws Exception {
        handle(new IllegalStateException("wrapper", new SQLException("Key (phone)=(" + SUBMITTED + ")")));

        assertOneLogLineWithClassOnly("IllegalStateException");
    }

    /** A failed rollback or close is attached as suppressed; its SQL message carries values just as well. */
    @Test
    void handle_exceptionWithSuppressedDatabaseError_logsNoStackTrace() throws Exception {
        IllegalStateException failure = new IllegalStateException("wrapper");
        failure.addSuppressed(new DataIntegrityViolationException("Key (phone)=(" + SUBMITTED + ")"));

        handle(failure);

        assertOneLogLineWithClassOnly("IllegalStateException");
    }

    @Test
    void handle_suppressedErrorInsideACause_logsNoStackTrace() throws Exception {
        RuntimeException cause = new RuntimeException("cause");
        cause.addSuppressed(new SQLException("Key (phone)=(" + SUBMITTED + ")"));

        handle(new IllegalStateException("wrapper", cause));

        assertOneLogLineWithClassOnly("IllegalStateException");
    }

    @Test
    void handle_exceptionThatSuppressesItsOwnCause_stillAnswers500() throws Exception {
        RuntimeException cause = new RuntimeException("cause");
        IllegalStateException failure = new IllegalStateException("wrapper", cause);
        cause.addSuppressed(failure);

        ResponseEntity<ErrorResponse<ValidationError>> response = handle(failure);

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(logs.list.get(0).getThrowableProxy(), "no request data anywhere, so the stack trace stays");
    }

    @Test
    void handle_unexpected_returns500WithCorrelationId() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new IllegalStateException("no code left"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_ERROR, response.getBody().code());
        assertEquals(List.of(), response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
        assertFalse(response.getBody().message().contains("no code left"));
        assertEquals(1, logs.list.size());
        String line = logs.list.get(0).getFormattedMessage();
        assertTrue(line.contains("IllegalStateException") && line.contains(CORRELATION_ID), line);
        assertNotNull(logs.list.get(0).getThrowableProxy(), "other exceptions keep their stack trace");
    }

    @ParameterizedTest
    @CsvSource({
            "'{\"intent\":\"RENT\"}', intent",
            "'{\"askingPrice\":{\"unit\":\"WEEK\"}}', askingPrice.unit",
            "'{\"availableFrom\":\"2026-13-45\"}', availableFrom",
            "'{\"latitude\":\"abc\"}', latitude"})
    void handle_badEnum_usesJsonPathAsField(String json, String field) throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new HttpMessageNotReadableException("JSON parse error", parseError(json), INPUT));

        assertEquals(400, response.getStatusCode().value());
        assertEquals(ErrorCode.VALIDATION_FAILED, response.getBody().code());
        assertEquals(List.of(field), response.getBody().errors().stream().map(ValidationError::field).toList());
    }

    @Test
    void handle_unparsableJson_isMalformedRequest() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new HttpMessageNotReadableException("JSON parse error", parseError("{\"phone\": 09"), INPUT));

        assertEquals(400, response.getStatusCode().value());
        assertEquals(ErrorCode.MALFORMED_REQUEST, response.getBody().code());
        assertEquals(List.of(), response.getBody().errors());
    }

    @Test
    void handle_unsupportedMediaType_keepsStatus415WithMalformedRequestCode() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertEquals(415, response.getStatusCode().value());
        assertEquals(ErrorCode.MALFORMED_REQUEST, response.getBody().code());
        assertEquals(List.of(MediaType.APPLICATION_JSON), response.getHeaders().getAccept());
    }

    private void assertOneLogLineWithClassOnly(String className) {
        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        String line = event.getFormattedMessage();
        assertTrue(line.contains(className), line);
        assertTrue(line.contains(CORRELATION_ID), line);
        assertFalse(line.contains(SUBMITTED), line);
        assertNull(event.getThrowableProxy(), "no stack trace: the message can carry submitted values");
    }

    /** Calls the handler method Spring MVC would choose for the exception. */
    @SuppressWarnings("unchecked")
    private ResponseEntity<ErrorResponse<ValidationError>> handle(Exception exception) throws Exception {
        Method method = resolver.resolveMethod(exception);
        assertNotNull(method, "no handler method for " + exception.getClass().getName());
        try {
            return (ResponseEntity<ErrorResponse<ValidationError>>) method.invoke(handler, exception);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static MethodParameter headerParameter() throws NoSuchMethodException {
        return new MethodParameter(AcquisitionExceptionHandlerTest.class.getDeclaredMethod("submit", String.class), 0);
    }

    /** The exception Jackson throws for this body, as Spring MVC would wrap it. */
    private static JacksonException parseError(String json) {
        return assertThrows(JacksonException.class, () -> JSON.readValue(json, OwnerIntakeRequest.class));
    }
}
