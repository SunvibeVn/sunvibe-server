package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The switch of the public intake form as shipped (acquisition-owner-intake BR-15, spec C-7): off unless the
 * environment turns it on, on in the dev and test profiles. The raw values are compared, so an env var set on the
 * machine running the tests does not change the result.
 */
class OwnerIntakeEnabledConfigTest {

	private static final String KEY = "app.acquisition.owner-intake.enabled";

	@Test
	void baseProfile_ownerIntake_isOffUnlessTheEnvironmentTurnsItOn() throws IOException {
		assertEquals("${ACQUISITION_OWNER_INTAKE_ENABLED:false}", value("application.yml"));
	}

	@Test
	void devProfile_ownerIntake_isOn() throws IOException {
		assertEquals("${ACQUISITION_OWNER_INTAKE_ENABLED:true}", value("application-dev.yml"));
	}

	@Test
	void testProfile_ownerIntake_isOn() throws IOException {
		assertEquals("${ACQUISITION_OWNER_INTAKE_ENABLED:true}", value("application-test.yml"));
	}

	private static String value(String name) throws IOException {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
		return sources.stream()
				.map(source -> source.getProperty(KEY))
				.filter(Objects::nonNull)
				.map(String::valueOf)
				.findFirst()
				.orElse(null);
	}
}
