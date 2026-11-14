package vn.sunvibe.survey.core.presentation.controller.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.survey.core.application.query.media.MediaRead;
import vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler;

/**
 * survey-media spec A-25: the read watchdog ends a content read's store stream at its deadline wherever the request
 * thread is blocked, in the store read or in the write to the client. The no-progress timeout is an hour here, so the
 * deadline is the only bound that can end these reads.
 */
class SurveyMediaReadControllerStreamTest {

	private static final Duration NO_PROGRESS = Duration.ofHours(1);
	private static final String CORRELATION_ID = "01J0000000000000000000TEST";

	private final SurveyExceptionHandler handler = new SurveyExceptionHandler();
	private final ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(
			SurveyExceptionHandler.class);
	private final Logger logger = (Logger) LoggerFactory.getLogger(SurveyExceptionHandler.class);
	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private Level loggerLevel;

	@BeforeEach
	void captureHandlerLog() {
		MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
		loggerLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	void releaseHandlerLog() {
		logger.detachAppender(logs);
		logger.setLevel(loggerLevel);
		MDC.clear();
	}

	/** SV-268: the client closed the tab while the photo was written; the survey catch-all logs it at DEBUG only. */
	@Test
	void stream_clientAbortOnWrite_reachesTheCatchAllAndIsLoggedAtDebug() throws Exception {
		OutputStream closedClient = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new ClientAbortException(new IOException("Broken pipe"));
			}

			@Override
			public void write(byte[] buffer, int offset, int length) throws IOException {
				write(0);
			}
		};
		MediaRead read = read(new ByteArrayInputStream(new byte[1024]), Instant.now().plusSeconds(60));

		IOException thrown = assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read,
				closedClient));
		handle(thrown);

		assertEquals(1, logs.list.size(), () -> "log events: " + logs.list);
		assertEquals(Level.DEBUG, logs.list.get(0).getLevel());
	}

	@Test
	void stream_clientAbortOnFlush_reachesTheCatchAllAndIsLoggedAtDebug() throws Exception {
		OutputStream closedClient = new OutputStream() {
			@Override
			public void write(int b) {
			}

			@Override
			public void flush() throws IOException {
				throw new ClientAbortException(new IOException("Broken pipe"));
			}
		};
		MediaRead read = read(new ByteArrayInputStream(new byte[1024]), Instant.now().plusSeconds(60));

		IOException thrown = assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read,
				closedClient));
		handle(thrown);

		assertEquals(1, logs.list.size(), () -> "log events: " + logs.list);
		assertEquals(Level.DEBUG, logs.list.get(0).getLevel());
	}

	/** Pin: a store read that fails with the text of a closed client is a server fault and stays an ERROR. */
	@Test
	void stream_storeReadConnectionReset_staysAnError() throws Exception {
		InputStream brokenStore = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("Connection reset by peer");
			}
		};
		MediaRead read = read(brokenStore, Instant.now().plusSeconds(60));

		IOException thrown = assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read,
				OutputStream.nullOutputStream()));
		handle(thrown);

		assertEquals(1, logs.list.size(), () -> "log events: " + logs.list);
		assertEquals(Level.ERROR, logs.list.get(0).getLevel());
	}

	/** Pin: the deadline ended the answer, not the client: an ERROR. */
	@Test
	void stream_deadlinePassed_staysAnError() throws Exception {
		MediaRead read = read(new ByteArrayInputStream(new byte[1024]), Instant.now().minusSeconds(1));

		IOException thrown = assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read,
				OutputStream.nullOutputStream()));
		handle(thrown);

		assertEquals(1, logs.list.size(), () -> "log events: " + logs.list);
		assertEquals(Level.ERROR, logs.list.get(0).getLevel());
	}

	/**
	 * Pin (SV-268 U-11, D-8): a stalled client whose blocked write fails only after the watchdog ended the read is the
	 * slow-read case of spec A-25, a server stop; it stays an ERROR as before SV-268.
	 */
	@Test
	void stream_clientWriteBlockedUntilWatchdogThenAborted_staysAnError() throws Exception {
		CountDownLatch storeClosed = new CountDownLatch(1);
		InputStream content = new ByteArrayInputStream(new byte[1024 * 1024]) {
			@Override
			public void close() {
				storeClosed.countDown();
			}
		};
		OutputStream stalledClient = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				write(new byte[] {(byte) b}, 0, 1);
			}

			@Override
			public void write(byte[] buffer, int offset, int length) throws IOException {
				try {
					if (!storeClosed.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("the watchdog did not end the store stream");
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("interrupted", e);
				}
				// Tomcat's write timeout, or the reset of the client, after the watchdog fired
				throw new ClientAbortException(new SocketTimeoutException());
			}
		};
		MediaRead read = read(content, Instant.now().plusMillis(300));

		IOException thrown = assertTimeoutPreemptively(Duration.ofSeconds(10),
				() -> assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read, stalledClient)));
		handle(thrown);

		assertEquals(1, logs.list.size(), () -> "log events: " + logs.list);
		assertEquals(Level.ERROR, logs.list.get(0).getLevel());
		assertTrue(logs.list.get(0).getFormattedMessage().startsWith("Unexpected error in survey: java.io.IOException"),
				logs.list.get(0).getFormattedMessage());
	}

	/**
	 * SV-268 follow-up (security review Minor 1, D-8): a write that fails because Tomcat's own write timeout ran out is
	 * a stalled client held by the server, not a client that left, even when the watchdog has not fired yet (a
	 * connection timeout set below the no-progress timeout). It stays an ERROR.
	 */
	@Test
	void stream_clientWriteTimesOutBeforeTheWatchdog_staysAnError() throws Exception {
		OutputStream timedOutClient = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new ClientAbortException(new SocketTimeoutException());
			}

			@Override
			public void write(byte[] buffer, int offset, int length) throws IOException {
				write(0);
			}
		};
		MediaRead read = read(new ByteArrayInputStream(new byte[1024]), Instant.now().plusSeconds(60));

		IOException thrown = assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read,
				timedOutClient));
		handle(thrown);

		assertEquals(1, logs.list.size(), () -> "log events: " + logs.list);
		assertEquals(Level.ERROR, logs.list.get(0).getLevel());
		assertTrue(logs.list.get(0).getFormattedMessage().startsWith("Unexpected error in survey: java.io.IOException"),
				logs.list.get(0).getFormattedMessage());
	}

	private void handle(Exception exception) throws Exception {
		Method method = resolver.resolveMethod(exception);
		try {
			method.invoke(handler, exception);
		} catch (InvocationTargetException e) {
			throw (Exception) e.getCause();
		}
	}

	@Test
	void stream_storeReadBlockedPastDeadline_watchdogEndsStoreStream() {
		CountDownLatch storeClosed = new CountDownLatch(1);
		InputStream blocked = new InputStream() {
			@Override
			public int read() throws IOException {
				return read(new byte[1], 0, 1);
			}

			@Override
			public int read(byte[] buffer, int offset, int length) throws IOException {
				try {
					storeClosed.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				throw new IOException("The store stream is closed.");
			}

			@Override
			public void close() {
				storeClosed.countDown();
			}
		};
		MediaRead read = read(blocked, Instant.now().plusMillis(300));

		assertTimeoutPreemptively(Duration.ofSeconds(5),
				() -> assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read,
						OutputStream.nullOutputStream())));
		assertEquals(0, storeClosed.getCount(), "the store stream was closed");
	}

	@Test
	void stream_clientWriteBlockedPastDeadline_storeStreamEndedWhileWriteBlocked() {
		CountDownLatch storeClosed = new CountDownLatch(1);
		InputStream content = new ByteArrayInputStream(new byte[1024 * 1024]) {
			@Override
			public void close() {
				storeClosed.countDown();
			}
		};
		// a client that reads nothing until the store stream has been ended (or gives up after 5 s)
		OutputStream slowClient = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				write(new byte[] {(byte) b}, 0, 1);
			}

			@Override
			public void write(byte[] buffer, int offset, int length) throws IOException {
				try {
					if (!storeClosed.await(5, TimeUnit.SECONDS)) {
						throw new IOException("The client went away.");
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted.", e);
				}
			}
		};
		MediaRead read = read(content, Instant.now().plusMillis(300));

		assertThrows(IOException.class, () -> SurveyMediaReadController.stream(read, slowClient));
		assertEquals(0, storeClosed.getCount(), "the watchdog ended the store stream while the write was blocked");
	}

	private static MediaRead read(InputStream content, Instant deadline) {
		return new MediaRead("01JDEADL1NE000000000000000", content, 1024 * 1024, false, NO_PROGRESS, deadline,
				() -> { }, () -> {
					try {
						content.close();
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				});
	}
}
