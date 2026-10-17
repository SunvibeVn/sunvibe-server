package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Startup guard of the {@code lan} profile (auth-browser-session spec §3.10, US-5 AC-2, W10-12): prod never runs with
 * {@code lan}, nor with a refresh cookie that is not Secure.
 */
class LanProfileGuardTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(LanProfileGuard.class);

	@Test
	void prodAndLan_failsToStart() {
		withProfiles("prod", "lan")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().rootCause()
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("The lan profile must never run together with prod"));
	}

	@Test
	void prodWithInsecureCookie_failsToStart() {
		withProfiles("prod")
				.withPropertyValues("app.auth.refresh-cookie.secure=false")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().rootCause()
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("prod needs a Secure refresh cookie"));
	}

	@Test
	void lanAlone_starts() {
		withProfiles("dev", "lan")
				.withPropertyValues("app.auth.refresh-cookie.secure=false")
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void prodWithSecureCookie_starts() {
		withProfiles("prod")
				.withPropertyValues("app.auth.refresh-cookie.secure=true")
				.run(context -> assertThat(context).hasNotFailed());
	}

	private ApplicationContextRunner withProfiles(String... profiles) {
		return runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles));
	}
}
