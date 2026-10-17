package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

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
