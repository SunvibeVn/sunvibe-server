package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * Guards ADR-002: secrets come only from env vars. The base profile (used by prod) must hold
 * placeholders without defaults, so it never falls back to a committed value. Assertions never
 * print the configured value.
 */
class ConfigSecretsGuardTest {

	private static final String MAIL_USERNAME = "spring.mail.username";
	private static final String MAIL_PASSWORD = "spring.mail.password";
	private static final String JWT_SECRET = "app.jwt.secret";

	private static final Map<String, String> BASE_PLACEHOLDERS = Map.of(
			MAIL_USERNAME, "${MAIL_USERNAME}",
			MAIL_PASSWORD, "${MAIL_PASSWORD}",
			JWT_SECRET, "${JWT_SECRET}");

	@Test
	void baseProfile_secretKeys_arePlaceholdersWithoutDefaults() throws IOException {
		List<PropertySource<?>> base = load("application.yml");

		BASE_PLACEHOLDERS.forEach((key, expected) -> {
			Object raw = rawValue(base, key);
			assertTrue(expected.equals(String.valueOf(raw)), key + " must be the " + expected + " placeholder");
		});
	}

	@Test
	void baseProfile_envVarsAbsent_failsToResolve() throws IOException {
		// Strict resolution, as @Value does. Built from the yml only (no system environment),
		// so a developer's own env vars cannot hide a failure.
		PropertySourcesPropertyResolver resolver = resolver(load("application.yml"));

		BASE_PLACEHOLDERS.keySet().forEach(key -> assertThrows(IllegalArgumentException.class,
				() -> resolver.getProperty(key), key + " must not resolve without its env var"));
	}

	@Test
	void prodProfile_secretKeys_areNotOverridden() throws IOException {
		List<PropertySource<?>> prod = load("application-prod.yml");

		BASE_PLACEHOLDERS.keySet().forEach(key -> assertNull(rawValue(prod, key),
				key + " must come from application.yml (env var), not application-prod.yml"));
	}

	@Test
	void testProfile_noEnvVars_resolvesEverySecret() throws IOException {
		PropertySourcesPropertyResolver resolver = resolver(load("application-test.yml"), load("application.yml"));

		BASE_PLACEHOLDERS.keySet().forEach(key -> {
			String value = assertDoesNotThrow(() -> resolver.getProperty(key), key + " must resolve in the test profile");
			assertNotNull(value, key + " must be set in the test profile");
			assertFalse(value.isBlank(), key + " must not be blank in the test profile");
		});
		assertBase64(resolver.getProperty(JWT_SECRET), "test");
	}

	@Test
	void devProfile_noEnvVars_resolvesEverySecret() throws IOException {
		PropertySourcesPropertyResolver resolver = resolver(load("application-dev.yml"), load("application.yml"));

		BASE_PLACEHOLDERS.keySet().forEach(key -> assertDoesNotThrow(() -> resolver.getProperty(key),
				key + " must resolve in the dev profile"));
		// No committed mailbox: dev mail credentials come only from env vars
		assertTrue(resolver.getProperty(MAIL_USERNAME).isEmpty(), MAIL_USERNAME + " must default to empty in dev");
		assertTrue(resolver.getProperty(MAIL_PASSWORD).isEmpty(), MAIL_PASSWORD + " must default to empty in dev");
		assertBase64(resolver.getProperty(JWT_SECRET), "dev");
	}

	/** JWTProvider base64-decodes the secret; HS256 needs at least 256 bits. */
	private static void assertBase64(String secret, String profile) {
		assertNotNull(secret, JWT_SECRET + " must be set in the " + profile + " profile");
		byte[] key;
		try {
			key = Base64.getDecoder().decode(secret);
		} catch (IllegalArgumentException e) {
			// The decoder message would quote part of the key
			key = fail(JWT_SECRET + " must be base64 in the " + profile + " profile");
		}
		assertTrue(key.length >= 32, JWT_SECRET + " must be at least 256 bits in the " + profile + " profile");
	}

	private static List<PropertySource<?>> load(String name) throws IOException {
		return new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
	}

	private static Object rawValue(List<PropertySource<?>> sources, String key) {
		return sources.stream()
				.map(source -> source.getProperty(key))
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	/** Earlier lists win, like a profile file over application.yml. */
	@SafeVarargs
	private static PropertySourcesPropertyResolver resolver(List<PropertySource<?>>... sourceLists) {
		MutablePropertySources sources = new MutablePropertySources();
		for (List<PropertySource<?>> list : sourceLists) {
			list.forEach(sources::addLast);
		}
		return new PropertySourcesPropertyResolver(sources);
	}
}
