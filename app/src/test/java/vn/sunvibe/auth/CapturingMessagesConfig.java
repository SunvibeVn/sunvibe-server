package vn.sunvibe.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.sms.SmsSenderPort;

/**
 * Replaces the SMS and e-mail senders with ones that keep the last message per destination, so an HTTP test can read
 * the code it was sent. Imported only by the test classes that need it ({@code @Import}), so the {@code @Primary}
 * beans stay out of every other test context. Messages are never printed.
 */
@TestConfiguration(proxyBeanMethods = false)
class CapturingMessagesConfig {

	private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

	private final Map<String, String> lastMessages = new ConcurrentHashMap<>();

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
				lastMessages.put(email, "Password reset code " + otpCode);
			}

			@Override
			public void sendPasswordResetConfirmationEmail(String email) {
				lastMessages.put(email, "Password reset");
			}

			@Override
			public void sendVerificationCodeEmail(String email, String code) {
				lastMessages.put(email, "Verification code " + code);
			}
		};
	}

	/** The 6-digit code of the last message sent to {@code destination}; fails when there is none. */
	String lastCode(String destination) {
		String message = lastMessages.get(destination);
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
		return lastMessages.containsKey(destination);
	}
}
