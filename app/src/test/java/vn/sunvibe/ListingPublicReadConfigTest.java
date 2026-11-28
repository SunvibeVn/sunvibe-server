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
 * The switch of the anonymous public listing read as shipped (listing-publish E06-S7 spec E06S7-D7; criterion PB-13):
 * off unless the environment turns it on, on in the dev and test profiles, as {@link OwnerIntakeEnabledConfigTest}
 * holds the owner intake switch. The raw values are compared, so an env var set on the machine running the tests does
 * not change the result.
 */
class ListingPublicReadConfigTest {

	private static final String KEY = "app.listing.public-read.enabled";

	@Test
	void baseProfile_publicRead_isOffUnlessTheEnvironmentTurnsItOn() throws IOException {
		assertEquals("${LISTING_PUBLIC_READ_ENABLED:false}", value("application.yml"));
	}

	@Test
	void devProfile_publicRead_isOn() throws IOException {
		assertEquals("${LISTING_PUBLIC_READ_ENABLED:true}", value("application-dev.yml"));
	}

	@Test
	void testProfile_publicRead_isOn() throws IOException {
		assertEquals("${LISTING_PUBLIC_READ_ENABLED:true}", value("application-test.yml"));
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
