package vn.sunvibe.auth;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.shared.external.email.EmailServicePort;

/**
 * The reset mail leaves after the commit, on another thread, so a test that reads the token must wait for that mail
 * and not for any earlier one. The hold latch forces the order (testing.md: a race regression forces the
 * interleaving).
 */
class CapturingMessagesConfigTest {

	private static final String ADDRESS = "reset@example.test";

	private final CapturingMessagesConfig messages = new CapturingMessagesConfig();
	private final EmailServicePort mail = messages.capturingEmailService();
	private final ExecutorService threads = Executors.newFixedThreadPool(2);

	@AfterEach
	void stopThreads() {
		messages.holdMail(null);
		threads.shutdownNow();
	}

	@Test
	void awaitResetToken_addressHasAnEarlierMessage_waitsForTheResetMail() throws Exception {
		mail.sendRegistrationCodeEmail(ADDRESS, "111111");

		assertWaitsForHeldReset("222222", "reset-token-2");
	}

	@Test
	void awaitResetToken_secondReset_waitsForTheNewToken() throws Exception {
		mail.sendPasswordResetEmail(ADDRESS, "111111", "reset-token-1");
		assertEquals("reset-token-1", messages.awaitResetToken(ADDRESS));

		assertWaitsForHeldReset("222222", "reset-token-2");
	}

	/** Holds a reset mail, checks the reader is still waiting, then lets the mail go and reads its token and code. */
	private void assertWaitsForHeldReset(String code, String resetToken) throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		messages.holdMail(release);
		Future<?> sent = threads.submit(() -> mail.sendPasswordResetEmail(ADDRESS, code, resetToken));
		messages.awaitMailHeld();

		Future<String> token = threads.submit(() -> messages.awaitResetToken(ADDRESS));
		assertThrows(TimeoutException.class, () -> token.get(200, MILLISECONDS), "the reset mail is still held");

		release.countDown();
		sent.get(5, SECONDS);
		assertEquals(resetToken, token.get(5, SECONDS));
		assertEquals(code, messages.awaitCode(ADDRESS));
	}
}
