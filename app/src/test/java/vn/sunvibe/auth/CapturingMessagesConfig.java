package vn.sunvibe.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.shared.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.shared.external.sms.SmsSenderPort;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;

/**
 * Replaces the SMS and e-mail senders with ones that keep the last message per destination, so an HTTP test can read
 * the code it was sent. Imported only by the test classes that need it ({@code @Import}), so the {@code @Primary}
 * beans stay out of every other test context. Messages are never printed.
 */
@TestConfiguration(proxyBeanMethods = false)
class CapturingMessagesConfig {

	private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

	private final Map<String, String> lastMessages = new ConcurrentHashMap<>();
	private final Map<String, String> lastResetTokens = new ConcurrentHashMap<>();
	private volatile boolean mailDown;
	private final Map<String, AtomicInteger> mailCounts = new ConcurrentHashMap<>();
	private final AtomicInteger mailsHeld = new AtomicInteger();
	private volatile CountDownLatch mailHeld;

	/** While down, every mail with a code or a notice fails. */
	void mailDown(boolean down) {
		mailDown = down;
	}

	/** Holds the reset, sign-up and bind mails until the latch opens, as a slow mail server would; null lets them go. */
	void holdMail(CountDownLatch until) {
		mailHeld = until;
		mailsHeld.set(0);
	}

	private void waitIfHeld() {
		CountDownLatch held = mailHeld;
		if (held == null) {
			return;
		}
		mailsHeld.incrementAndGet();
		try {
			held.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Returns once a mail stands held. */
	void awaitMailHeld() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (mailsHeld.get() == 0) {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("No mail reached the hold");
			}
			Thread.sleep(5);
		}
	}

	/** How many sign-up mails left for {@code destination}. */
	int mailsTo(String destination) {
		AtomicInteger count = mailCounts.get(destination);
		return count == null ? 0 : count.get();
	}

	private void failIfDown() {
		if (mailDown) {
			throw new SendingMailFailedException("The mail server is down in this test");
		}
	}

	@Bean
	@Primary
	SmsSenderPort capturingSmsSender() {
		return lastMessages::put;
	}

	@Bean
	@Primary
	EmailServicePort capturingEmailService() {
		return new EmailServicePort() {
			@Override
			public void sendPasswordResetEmail(String email, String otpCode, String resetToken) {
				failIfDown();
				waitIfHeld();
				// The message first: a caller reads the token and then the code, so the code must be there once the
				// token is.
				lastMessages.put(email, "Password reset code " + otpCode);
				lastResetTokens.put(email, resetToken);
			}

			@Override
			public void sendPasswordResetConfirmationEmail(String email) {
				lastMessages.put(email, "Password reset");
			}

			@Override
			public void sendRegistrationCodeEmail(String to, String code) {
				failIfDown();
				waitIfHeld();
				mailCounts.computeIfAbsent(to, key -> new AtomicInteger()).incrementAndGet();
				lastMessages.put(to, "Sign-up code " + code);
			}

			@Override
			public void sendEmailBindCodeEmail(String to, String code) {
				failIfDown();
				waitIfHeld();
				lastMessages.put(to, "Bind code " + code);
			}

			@Override
			public void sendEmailChangedNotice(String to) {
				failIfDown();
				lastMessages.put(to, "Email changed");
			}

			@Override
			public void sendOwnerNotice(String to, String kind, java.time.Instant occurredAt) {
				failIfDown();
				lastMessages.put(to, "Owner notice " + kind);
			}
		};
	}

	/** A phone is sent to its canonical key, so a national number finds its messages too. */
	private String messageOf(String destination) {
		String message = lastMessages.get(destination);
		return message == null && destination.startsWith("0")
				? lastMessages.get(PhoneNumber.create(destination).key())
				: message;
	}

	/** The 6-digit code of the last message sent to {@code destination}; fails when there is none. */
	String lastCode(String destination) {
		String message = messageOf(destination);
		if (message == null) {
			throw new IllegalStateException("No message was sent to this destination");
		}
		Matcher matcher = SIX_DIGITS.matcher(message);
		if (!matcher.find()) {
			throw new IllegalStateException("The last message to this destination has no 6-digit code");
		}
		return matcher.group();
	}

	/** Whether any message was sent to {@code destination}. */
	boolean hasMessage(String destination) {
		return messageOf(destination) != null;
	}

	/** Recovery mail leaves after the answer: true once a message reached {@code destination}. */
	boolean awaitMessage(String destination) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!hasMessage(destination) && System.nanoTime() < deadline) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return hasMessage(destination);
	}

	/** The code of a message that leaves after the answer. */
	String awaitCode(String destination) {
		awaitMessage(destination);
		return lastCode(destination);
	}

	/**
	 * The reset token mailed to {@code destination}; fails when none arrives. It waits for a reset mail, not for any
	 * message, since the address may already hold a sign-up mail, and it takes the token, so a second reset waits for
	 * its own mail.
	 */
	String awaitResetToken(String destination) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		String token = lastResetTokens.remove(destination);
		while (token == null && System.nanoTime() < deadline) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			token = lastResetTokens.remove(destination);
		}
		if (token == null) {
			throw new IllegalStateException("No reset mail reached this destination");
		}
		return token;
	}
}
