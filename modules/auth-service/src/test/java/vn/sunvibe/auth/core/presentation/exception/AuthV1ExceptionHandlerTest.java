package vn.sunvibe.auth.core.presentation.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import vn.sunvibe.auth.core.application.exception.auth.AccountDisabledException;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.exception.auth.OriginNotAllowedException;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.auth.core.shared.service.RefreshCookieProperties;
import vn.sunvibe.auth.core.shared.service.SessionCookieService;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Error bodies of the v1 auth endpoints (auth-browser-session spec §3.4, ADR-010; W10-3, W10-17). Each exception goes
 * through the handler method Spring MVC would pick ({@link ExceptionHandlerMethodResolver}), so the tests check the
 * dispatch (a subclass row wins over its parent) as well as the body. Every body carries the code, the fields (or
 * {@code []}) and the correlation id, and never a submitted value or the exception's own message.
 */
class AuthV1ExceptionHandlerTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";
    /** Stands for a submitted value (a phone number) that must reach neither a body nor a log line. */
    private static final String SUBMITTED = "0909123456";

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

    private final AuthV1ExceptionHandler handler = new AuthV1ExceptionHandler(
            new SessionCookieService(new RefreshCookieProperties("sv_refresh", true), 604_800_000L));
    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(AuthV1ExceptionHandler.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuthV1ExceptionHandler.class);
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
    static void submit(@RequestHeader("X-Device") String device) {
    }

    // ==================== Handler wiring (S-28) ====================

    @Test
    void handler_isScopedToTheV1ControllersWithHighestPrecedence() {
        RestControllerAdvice advice = AuthV1ExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertArrayEquals(new String[]{"vn.sunvibe.auth.core.presentation.controller.v1"}, advice.basePackages());
        assertEquals(Ordered.HIGHEST_PRECEDENCE, AuthV1ExceptionHandler.class.getAnnotation(Order.class).value());
    }

    @Test
    void legacyHandler_isOrderedRightAfterTheScopedHandlers() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, AuthExceptionHandler.class.getAnnotation(Order.class).value());
    }

    // ==================== One test per row ====================

    @Test
    void handle_methodArgumentNotValid_answers400ValidationFailed() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "loginRequest");
        binding.addError(new FieldError("loginRequest", "username", SUBMITTED, false, null, null,
                "Phone number is required."));
        binding.addError(new FieldError("loginRequest", "password", "Password is required."));
        binding.addError(new FieldError("loginRequest", "password", "Second message, same field."));

        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new MethodArgumentNotValidException(parameter(), binding));

        assertBody(response, 400, ErrorCode.VALIDATION_FAILED, List.of("password", "username"));
        assertEquals("Password is required.", response.getBody().errors().get(0).message());
    }

    @Test
    void handle_handlerMethodValidation_answers400ValidationFailed() throws Exception {
        MethodValidationResult result = MethodValidationResult.create(new Object(),
                AuthV1ExceptionHandlerTest.class.getDeclaredMethod("submit", String.class),
                List.of(new ParameterValidationResult(parameter(), SUBMITTED,
                        List.of(new DefaultMessageSourceResolvable(new String[]{"Pattern"}, "must match")),
                        null, null, null, (error, type) -> null)));

        assertBody(handle(new HandlerMethodValidationException(result)), 400, ErrorCode.VALIDATION_FAILED,
                List.of("X-Device"));
    }

    @Test
    void handle_authValidation_answers400ValidationFailed() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new AuthValidationException(AuthFieldName.USER_NAME, "Phone number is already in use."));

        assertBody(response, 400, ErrorCode.VALIDATION_FAILED, List.of("username"));
        assertEquals("Phone number is already in use.", response.getBody().errors().get(0).message());
    }

    @Test
    void handle_httpMessageNotReadable_answers400MalformedRequest() throws Exception {
        assertBody(handle(new HttpMessageNotReadableException("JSON parse error: " + SUBMITTED, INPUT)),
                400, ErrorCode.MALFORMED_REQUEST, List.of());
    }

    @Test
    void handle_httpMediaTypeNotSupported_answers415MalformedRequest() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertBody(response, 415, ErrorCode.MALFORMED_REQUEST, List.of());
        assertEquals(List.of(MediaType.APPLICATION_JSON), response.getHeaders().getAccept());
    }

    @Test
    void handle_accountTemporarilyLocked_answers423AccountLocked() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new AccountTemporarilyLockedException(Duration.ofSeconds(90)));

        assertBody(response, 423, ErrorCode.ACCOUNT_LOCKED, List.of());
        assertEquals("90", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void handle_lockedAccount_retryAfterAtLeastOneSecond() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new AccountTemporarilyLockedException(Duration.ofMillis(300)));

        assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void handle_accountDisabled_answers401AccountDisabled() throws Exception {
        assertBody(handle(new AccountDisabledException()), 401, ErrorCode.ACCOUNT_DISABLED, List.of());
    }

    @Test
    void handle_authenticationFailed_answers401InvalidCredentials() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new AuthenticationFailedException("Account is locked."));

        assertBody(response, 401, ErrorCode.INVALID_CREDENTIALS, List.of());
        assertFalse(response.getBody().message().contains("locked"), "a locked account looks like a wrong password");
    }

    @Test
    void handle_invalidRefreshToken_answers401SessionExpired() throws Exception {
        assertBody(handle(new InvalidRefreshTokenException("Refresh token was reused.")),
                401, ErrorCode.SESSION_EXPIRED, List.of());
    }

    @Test
    void handle_invalidRefreshToken_clearsTheNarrowCookie() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new InvalidRefreshTokenException("Refresh token expired."));

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(1, cookies.size(), "only the v1 refresh cookie; the legacy cookies are not touched");
        String cookie = cookies.get(0);
        assertTrue(cookie.startsWith("sv_refresh=;"), cookie);
        assertTrue(cookie.contains("Max-Age=0"), cookie);
        assertTrue(cookie.contains("Path=/api/v1/auth/session"), cookie);
    }

    @Test
    void handle_originNotAllowed_answers403OriginNotAllowed() throws Exception {
        OriginNotAllowedException exception = new OriginNotAllowedException();

        ResponseEntity<ErrorResponse<ValidationError>> response = handle(exception);

        assertBody(response, 403, ErrorCode.ORIGIN_NOT_ALLOWED, List.of());
        assertNotEquals(exception.getMessage(), response.getBody().message());
    }

    static Stream<Arguments> verificationReasons() {
        return Stream.of(
                Arguments.of(VerificationException.Reason.CODE_INCORRECT, ErrorCode.CODE_INCORRECT),
                Arguments.of(VerificationException.Reason.CODE_EXPIRED, ErrorCode.CODE_EXPIRED),
                Arguments.of(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, ErrorCode.CODE_ATTEMPTS_EXCEEDED),
                Arguments.of(VerificationException.Reason.ALREADY_VERIFIED, ErrorCode.ALREADY_VERIFIED),
                Arguments.of(VerificationException.Reason.NO_EMAIL, ErrorCode.NO_EMAIL),
                Arguments.of(VerificationException.Reason.REGISTRATION_TOKEN_INVALID,
                        ErrorCode.REGISTRATION_TOKEN_INVALID));
    }

    @ParameterizedTest
    @MethodSource("verificationReasons")
    void handle_verification_answers400CodeByReason(VerificationException.Reason reason, String code)
            throws Exception {
        assertBody(handle(new VerificationException(reason, "Code sent to " + SUBMITTED + " is wrong.")),
                400, code, List.of());
    }

    @Test
    void handle_verification_everyReasonHasACode() {
        assertEquals(VerificationException.Reason.values().length, verificationReasons().count());
    }

    @Test
    void handle_invalidOtp_answers400CodeIncorrect() throws Exception {
        assertBody(handle(new InvalidOtpException("Wrong code.")), 400, ErrorCode.CODE_INCORRECT, List.of());
    }

    @Test
    void handle_passwordResetTokenInvalid_answers400ResetTokenInvalid() throws Exception {
        assertBody(handle(new PasswordResetTokenException(PasswordResetTokenException.Reason.TOKEN_INVALID,
                "Token expired.")), 400, ErrorCode.RESET_TOKEN_INVALID, List.of());
    }

    @Test
    void handle_passwordResetTokenAttemptsExceeded_answers400CodeAttemptsExceeded() throws Exception {
        assertBody(handle(new PasswordResetTokenException(PasswordResetTokenException.Reason.ATTEMPTS_EXCEEDED,
                "Too many wrong codes.")), 400, ErrorCode.CODE_ATTEMPTS_EXCEEDED, List.of());
    }

    @Test
    void handle_verificationThrottledResendTooSoon_answers429CodeResendTooSoon() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new VerificationThrottledException(
                VerificationThrottledException.Reason.RESEND_TOO_SOON, "Wait.", Duration.ofSeconds(42)));

        assertBody(response, 429, ErrorCode.CODE_RESEND_TOO_SOON, List.of());
        assertEquals("42", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void handle_verificationThrottledDailyLimit_answers429CodeDailyLimit() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new VerificationThrottledException(
                VerificationThrottledException.Reason.DAILY_LIMIT, "Limit.", Duration.ZERO));

        assertBody(response, 429, ErrorCode.CODE_DAILY_LIMIT, List.of());
        assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void handle_userNotFound_answers404NotFound() throws Exception {
        assertBody(handle(new UserNotFoundException("User " + SUBMITTED + " not found.")),
                404, ErrorCode.NOT_FOUND, List.of());
    }

    @Test
    void handle_internalAuthenticationService_answers500() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new InternalAuthenticationServiceException(
                "Unable to acquire JDBC Connection", new DataAccessResourceFailureException("Connection refused",
                        new SQLException("Connection to localhost:5432 refused.", "08001"))));

        assertBody(response, 500, ErrorCode.INTERNAL_ERROR, List.of());
        assertOneLogLineWithStackTrace("InternalAuthenticationServiceException");
    }

    /** spec §3.4: details of an infrastructure failure stay in the log, so the database outage can be traced. */
    @Test
    void handle_infrastructureFailure_logsTheStackTrace() throws Exception {
        handle(new IllegalStateException("pool exhausted"));

        assertOneLogLineWithStackTrace("IllegalStateException");
    }

    @Test
    void handle_dataIntegrityViolation_logsOnlyClassesAndCorrelationId() throws Exception {
        handle(new DataIntegrityViolationException("duplicate key (username)=(" + SUBMITTED + ")",
                new SQLException("Key (username)=(" + SUBMITTED + ") already exists.", "23505")));

        assertOneLogLineWithClassOnly("DataIntegrityViolationException");
        assertTrue(logs.list.get(0).getFormattedMessage().contains("SQLException"), "cause classes are named");
    }

    @Test
    void handle_integritySqlErrorInsideACause_logsNoStackTrace() throws Exception {
        handle(new IllegalStateException("wrapper",
                new SQLException("Key (email)=(user@example.com) already exists.", "23505")));

        assertOneLogLineWithClassOnly("IllegalStateException");
    }

    @Test
    void handle_mailFailureInsideACause_logsOnlyClassesAndCorrelationId() throws Exception {
        handle(new IllegalStateException("wrapper", new MailSendException("Invalid address user@example.com")));

        assertOneLogLineWithClassOnly("IllegalStateException");
        assertTrue(logs.list.get(0).getFormattedMessage().contains("MailSendException"), "cause classes are named");
    }

    /** A failed rollback or close is attached as suppressed; its SQL message carries values just as well. */
    @Test
    void handle_suppressedIntegrityError_logsNoStackTrace() throws Exception {
        IllegalStateException failure = new IllegalStateException("wrapper");
        failure.addSuppressed(new DataIntegrityViolationException("Key (username)=(" + SUBMITTED + ")"));

        handle(failure);

        assertOneLogLineWithClassOnly("IllegalStateException");
    }

    @Test
    void handle_accessDenied_isRethrown() {
        AccessDeniedException denied = new AccessDeniedException("denied");

        assertSame(denied, assertThrows(AccessDeniedException.class, () -> handle(denied)));
    }

    @Test
    void handle_otherAuthenticationException_isRethrown() {
        BadCredentialsException failure = new BadCredentialsException("bad");

        assertSame(failure, assertThrows(BadCredentialsException.class, () -> handle(failure)));
    }

    @Test
    void handle_springErrorResponse_keepsItsStatusWithMalformedRequest() throws Exception {
        assertBody(handle(new HttpRequestMethodNotSupportedException("GET", List.of("POST"))),
                405, ErrorCode.MALFORMED_REQUEST, List.of());
    }

    @Test
    void handle_unexpected_answers500InternalError() throws Exception {
        assertBody(handle(new SendingMailFailedException("Could not send to user@example.com")),
                500, ErrorCode.INTERNAL_ERROR, List.of());
        assertOneLogLineWithClassOnly("SendingMailFailedException");
    }

    @Test
    void handle_unexpected_bodyNeverContainsTheExceptionMessage() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handle(new IllegalStateException("broken " + SUBMITTED));

        assertBody(response, 500, ErrorCode.INTERNAL_ERROR, List.of());
        assertFalse(response.getBody().message().contains("broken"));
        assertEquals(1, logs.list.size());
        assertFalse(logs.list.get(0).getFormattedMessage().contains(SUBMITTED));
    }

    // ==================== Helpers ====================

    private static void assertBody(ResponseEntity<ErrorResponse<ValidationError>> response, int status, String code,
                                   List<String> fields) {
        assertEquals(status, response.getStatusCode().value());
        ErrorResponse<ValidationError> body = response.getBody();
        assertNotNull(body);
        assertEquals(code, body.code());
        assertEquals(fields, body.errors().stream().map(ValidationError::field).toList());
        assertEquals(CORRELATION_ID, body.correlationId());
        assertFalse(body.message().isBlank());
        assertFalse(body.toString().contains(SUBMITTED), body.toString());
    }

    private void assertOneLogLineWithClassOnly(String className) {
        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        String line = event.getFormattedMessage();
        assertTrue(line.contains(className), line);
        assertTrue(line.contains(CORRELATION_ID), line);
        assertFalse(line.contains(SUBMITTED), line);
        assertFalse(line.contains("@"), line);
        assertNull(event.getThrowableProxy(), "no stack trace: messages and causes can carry personal data");
    }

    private void assertOneLogLineWithStackTrace(String className) {
        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        String line = event.getFormattedMessage();
        assertTrue(line.contains(className), line);
        assertTrue(line.contains(CORRELATION_ID), line);
        assertNotNull(event.getThrowableProxy(), "no request data anywhere, so the stack trace stays");
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

    private static MethodParameter parameter() throws NoSuchMethodException {
        return new MethodParameter(AuthV1ExceptionHandlerTest.class.getDeclaredMethod("submit", String.class), 0);
    }
}
