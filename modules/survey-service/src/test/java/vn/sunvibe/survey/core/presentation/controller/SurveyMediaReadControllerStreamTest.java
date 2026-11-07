package vn.sunvibe.survey.core.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.application.query.media.MediaRead;

/**
 * survey-media spec A-25: the read watchdog ends a content read's store stream at its deadline wherever the request
 * thread is blocked, in the store read or in the write to the client. The no-progress timeout is an hour here, so the
 * deadline is the only bound that can end these reads.
 */
class SurveyMediaReadControllerStreamTest {

	private static final Duration NO_PROGRESS = Duration.ofHours(1);

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
