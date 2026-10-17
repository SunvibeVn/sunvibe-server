package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import vn.sunvibe.auth.core.infrastructure.security.ratelimit.RateLimitProperties;

/**
 * The rate-limit rules shipped in {@code application.yml} (acquisition-owner-intake spec 5.4; W9-12): the public
 * intake form allows 10 submissions per client IP per hour. The limiter itself is covered by RateLimitFilterTest.
 */
class RateLimitRulesConfigTest {

	private static final String RULES = "app.security.rate-limit.rules";

	@Test
	void baseProfile_ownerIntakeRule_isTenPerHour() throws IOException {
		List<String> rules = rules(load("application.yml"));

		assertTrue(rules.contains("POST /api/v1/public/owner-intake-requests 10 1h"), rules.toString());
	}

	@Test
	void baseProfile_ownerIntakeRule_isDeclaredOnce() throws IOException {
		long count = rules(load("application.yml")).stream()
				.filter(rule -> rule.contains("/api/v1/public/owner-intake-requests"))
				.count();

		assertEquals(1, count);
	}

	/**
	 * Auth-browser-session spec §3.1 rate-limit column and §3.8 (W10-11): each v1 path has the limit and window of its
	 * legacy pair and shares its bucket. Columns: bucket, legacy path, v1 path, limit, window.
	 */
	private static final List<List<String>> AUTH_PAIRS = List.of(
			List.of("login", "/api/auth/login", "/api/v1/auth/login", "10", "1m"),
			List.of("refresh", "/api/auth/refresh-token", "/api/v1/auth/session/refresh", "30", "1m"),
			List.of("registration-send-code", "/api/auth/registration/phone/send",
					"/api/v1/auth/registration/phone/send-code", "5", "1h"),
			List.of("registration-verify-code", "/api/auth/registration/phone/verify",
					"/api/v1/auth/registration/phone/verify-code", "10", "15m"),
			List.of("registration", "/api/users/command/register", "/api/v1/auth/registration", "5", "1h"),
			List.of("reset-email", "/api/auth/forgot-password", "/api/v1/auth/password-reset/email", "5", "1h"),
			List.of("reset-sms", "/api/auth/forgot-password/sms", "/api/v1/auth/password-reset/sms", "5", "1h"),
			List.of("reset-complete", "/api/auth/reset-password", "/api/v1/auth/password-reset/complete", "10", "15m"),
			List.of("verify-phone-send", "/api/auth/verification/phone/send",
					"/api/v1/me/verification/phone/send-code", "5", "1h"),
			List.of("verify-email-send", "/api/auth/verification/email/send",
					"/api/v1/me/verification/email/send-code", "5", "1h"),
			List.of("verify-phone-confirm", "/api/auth/verification/phone/confirm",
					"/api/v1/me/verification/phone/confirm-code", "10", "15m"),
			List.of("verify-email-confirm", "/api/auth/verification/email/confirm",
					"/api/v1/me/verification/email/confirm-code", "10", "15m"),
			List.of("password-change", "/api/auth/change-password", "/api/v1/me/password/change", "10", "15m"));

	/** W10-11 */
	@Test
	void v1AuthRules_matchTheirLegacyPairs() throws IOException {
		List<String> rules = rulesWithBucket(load("application.yml"));

		for (List<String> pair : AUTH_PAIRS) {
			String bucket = pair.get(0);
			String expectedLegacy = "POST " + pair.get(1) + " " + pair.get(3) + " " + pair.get(4) + " " + bucket;
			String expectedV1 = "POST " + pair.get(2) + " " + pair.get(3) + " " + pair.get(4) + " " + bucket;
			assertTrue(rules.contains(expectedLegacy), expectedLegacy + " in " + rules);
			assertTrue(rules.contains(expectedV1), expectedV1 + " in " + rules);
			assertEquals(2, rules.stream().filter(rule -> rule.endsWith(" " + bucket)).count(),
					"bucket " + bucket + " is shared by exactly its pair");
			assertEquals(1, rules.stream().filter(rule -> rule.contains(" " + pair.get(2) + " ")).count(),
					pair.get(2) + " is declared once");
		}
	}

	/** Spec §3.8, brief §6: no new limits; logout, logout-all and GET /api/v1/me stay unlimited. */
	@Test
	void logoutLogoutAllAndMe_haveNoRule() throws IOException {
		List<String> rules = rulesWithBucket(load("application.yml"));

		for (String path : List.of("/api/v1/auth/session/logout", "/api/v1/me/sessions/logout-all", "/api/v1/me",
				"/api/auth/logout", "/api/auth/logout-all")) {
			assertTrue(rules.stream().noneMatch(rule -> rule.split(" ")[1].equals(path)), path + " in " + rules);
		}
	}

	/** The rules bind to RateLimitProperties as the application binds them: the bucket is read, or null. */
	@Test
	void baseProfile_rules_bindWithTheirBuckets() throws IOException {
		RateLimitProperties properties = new Binder(ConfigurationPropertySources.from(load("application.yml")))
				.bind("app.security.rate-limit", RateLimitProperties.class)
				.orElseThrow(() -> new AssertionError("rate-limit properties not bound"));

		RateLimitProperties.Rule v1Login = properties.rules().stream()
				.filter(rule -> rule.path().equals("/api/v1/auth/login"))
				.findFirst().orElseThrow();
		assertEquals("login", v1Login.bucket());
		assertEquals(10, v1Login.limit());
		assertEquals(Duration.ofMinutes(1), v1Login.window());
		RateLimitProperties.Rule ownerIntake = properties.rules().stream()
				.filter(rule -> rule.path().equals("/api/v1/public/owner-intake-requests"))
				.findFirst().orElseThrow();
		assertNull(ownerIntake.bucket());
		assertEquals(rulesWithBucket(load("application.yml")).size(), properties.rules().size());
	}

	/** Each rule as "METHOD path limit window bucket", in file order; the bucket is "null" when absent. */
	private static List<String> rulesWithBucket(List<PropertySource<?>> sources) {
		List<String> rules = rules(sources);
		List<String> withBucket = new ArrayList<>();
		for (int i = 0; i < rules.size(); i++) {
			withBucket.add(rules.get(i) + " " + value(sources, RULES + "[" + i + "].bucket"));
		}
		return withBucket;
	}

	/** Each rule as "METHOD path limit window", in file order. */
	private static List<String> rules(List<PropertySource<?>> sources) {
		List<String> rules = new ArrayList<>();
		for (int i = 0; value(sources, RULES + "[" + i + "].path") != null; i++) {
			String prefix = RULES + "[" + i + "].";
			rules.add(value(sources, prefix + "method") + " " + value(sources, prefix + "path") + " "
					+ value(sources, prefix + "limit") + " " + value(sources, prefix + "window"));
		}
		return rules;
	}

	private static List<PropertySource<?>> load(String name) throws IOException {
		return new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
	}

	private static String value(List<PropertySource<?>> sources, String key) {
		return sources.stream()
				.map(source -> source.getProperty(key))
				.filter(Objects::nonNull)
				.map(String::valueOf)
				.findFirst()
				.orElse(null);
	}
}
