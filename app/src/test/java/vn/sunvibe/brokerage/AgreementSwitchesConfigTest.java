package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The switch on sending agreements to owners as shipped (brokerage-agreement BR-19, evidence gap Q-1 / SV-180; E05-S4
 * spec S4-D8, SD-7): off in the base profile, which the prod profile does not override, and on in the dev and test
 * profiles. The raw values are compared, as OwnerIntakeEnabledConfigTest does, so an env var set on the machine running
 * the tests does not change the result.
 */
class AgreementSwitchesConfigTest {

	private static final String KEY = "app.brokerage.owner-sending.enabled";

	@Test
	void ownerSending_profiles_offInBaseAndProdOnInDevAndTest() throws IOException {
		assertEquals("${BROKERAGE_OWNER_SENDING_ENABLED:false}", value("application.yml"));
		assertNull(value("application-prod.yml"), "prod keeps the base profile's value");
		assertEquals("${BROKERAGE_OWNER_SENDING_ENABLED:true}", value("application-dev.yml"));
		assertEquals("${BROKERAGE_OWNER_SENDING_ENABLED:true}", value("application-test.yml"));
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
