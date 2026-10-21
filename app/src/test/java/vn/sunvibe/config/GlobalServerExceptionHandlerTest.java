package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.AuthenticationFailedException;
import vn.sunvibe.shared.constant.error.SystemErrorMessage;
import vn.sunvibe.shared.dto.response.error.BaseErrorModel;
import vn.sunvibe.shared.exception.ApplicationException;
import vn.sunvibe.shared.exception.BaseBusinessException;

/**
 * The application-wide handler answers with a fixed message code, never the text of the exception or of its cause
 * (.claude/rules/security.md: never return exception messages): those can name a user, a phone number or a SQL detail.
 * For the same reason the log names only the exception classes (no PII in logs).
 */
class GlobalServerExceptionHandlerTest {

	private static final String DETAIL = "user 0901234567 not found";
	private static final String CAUSE_DETAIL = "duplicate key value violates unique constraint \"idx_user_phone\"";

	private final GlobalServerExceptionHandler handler = new GlobalServerExceptionHandler();
	private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalServerExceptionHandler.class);
	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	@BeforeEach
	void captureLogs() {
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	void releaseLogs() {
		logger.detachAppender(logs);
	}

	@Test
	void handleBusinessException_message_answers500WithFixedCode() {
		ResponseEntity<BaseErrorModel> response = handler.handleBusinessException(
				new TestApplicationException(DETAIL, null));

		assertFixed(response, HttpStatus.INTERNAL_SERVER_ERROR, SystemErrorMessage.INTERNAL_ERROR);
	}

	@Test
	void handleBusinessException_messageAndCause_answers500WithFixedCode() {
		ResponseEntity<BaseErrorModel> response = handler.handleBusinessException(
				new TestApplicationException(DETAIL, new SQLException(CAUSE_DETAIL)));

		assertFixed(response, HttpStatus.INTERNAL_SERVER_ERROR, SystemErrorMessage.INTERNAL_ERROR);
	}

	@Test
	void handleInternalAuthException_messageAndCause_answers401WithFixedCode() {
		ResponseEntity<BaseErrorModel> response = handler.handleInternalAuthException(
				new AuthenticationFailedException(DETAIL, new SQLException(CAUSE_DETAIL)));

		assertFixed(response, HttpStatus.UNAUTHORIZED, SystemErrorMessage.UNAUTHORIZED);
	}

	@Test
	void handleUserNotFoundException_message_answers404WithFixedCode() {
		ResponseEntity<BaseErrorModel> response = handler.handleUserNotFoundException(
				new BaseBusinessException("USER_NOT_FOUND", DETAIL));

		assertFixed(response, HttpStatus.NOT_FOUND, SystemErrorMessage.RESOURCE_NOT_FOUND);
	}

	@Test
	void handleBusinessException_messageAndCause_logsOnlyTheClassNames() {
		handler.handleBusinessException(new TestApplicationException(DETAIL, new SQLException(CAUSE_DETAIL)));

		assertLoggedClassesOnly("TestApplicationException", "SQLException");
	}

	@Test
	void handleInternalAuthException_messageAndCause_logsOnlyTheClassNames() {
		handler.handleInternalAuthException(new AuthenticationFailedException(DETAIL, new SQLException(CAUSE_DETAIL)));

		assertLoggedClassesOnly("AuthenticationFailedException", "SQLException");
	}

	@Test
	void handleUserNotFoundException_message_logsOnlyTheClassName() {
		handler.handleUserNotFoundException(new BaseBusinessException("USER_NOT_FOUND", DETAIL));

		assertLoggedClassesOnly("BaseBusinessException");
	}

	private void assertLoggedClassesOnly(String... classNames) {
		assertThat(logs.list).hasSize(1);
		ILoggingEvent event = logs.list.getFirst();
		assertThat(event.getFormattedMessage()).contains(classNames)
				.doesNotContain(DETAIL).doesNotContain(CAUSE_DETAIL);
		assertThat(event.getThrowableProxy()).isNull();
	}

	private static void assertFixed(ResponseEntity<BaseErrorModel> response, HttpStatus status, String message) {
		assertThat(response.getStatusCode()).isEqualTo(status);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo(status.value());
		assertThat(response.getBody().message()).isEqualTo(message);
		assertThat(response.getBody().errorTime()).isNotNull();
	}

	private static final class TestApplicationException extends ApplicationException {
		TestApplicationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
