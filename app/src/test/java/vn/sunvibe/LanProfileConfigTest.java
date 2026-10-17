package vn.sunvibe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * The refresh cookie and the LAN origin as shipped (auth-browser-session spec §3.5, §3.10, W10-12): Secure with the
 * plain name by default, the {@code __Secure-} name in prod, Secure off and the LAN origin added in {@code lan}.
 * The raw values are compared, so an env var set on the machine running the tests does not change the result.
 */
class LanProfileConfigTest {

	private static final String NAME = "app.auth.refresh-cookie.name";
	private static final String SECURE = "app.auth.refresh-cookie.secure";
	private static final String ORIGINS = "app.cors.allowed-origins";

	@Test
	void lanProfile_turnsSecureOffAndAddsTheLanOrigin() throws IOException {
		assertThat(value("application-lan.yml", SECURE)).isEqualTo("false");
		assertThat(value("application-lan.yml", ORIGINS)).isEqualTo("http://localhost:3000,${LAN_FE_ORIGIN}");
		assertThat(value("application-lan.yml", NAME)).isNull();
	}

	@Test
	void prodProfile_usesTheSecurePrefixedName() throws IOException {
		assertThat(value("application-prod.yml", NAME)).isEqualTo("__Secure-sv_refresh");
		assertThat(value("application-prod.yml", SECURE)).isNull();
	}

	@Test
	void baseProfile_isSecureWithThePlainName() throws IOException {
		assertThat(value("application.yml", NAME)).isEqualTo("sv_refresh");
		assertThat(value("application.yml", SECURE)).isEqualTo("true");
	}

	private static String value(String name, String key) throws IOException {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
		return sources.stream()
				.map(source -> source.getProperty(key))
				.filter(Objects::nonNull)
				.map(String::valueOf)
				.findFirst()
				.orElse(null);
	}
}
