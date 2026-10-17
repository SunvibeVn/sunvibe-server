package vn.sunvibe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start a production instance with the LAN test settings (auth-browser-session spec §3.10, US-5 AC-2):
 * the {@code lan} profile allows a plain-HTTP origin and drops the Secure flag of the refresh cookie, so it must
 * never be active together with {@code prod}, and prod never runs with a refresh cookie that is not Secure.
 */
@Configuration(proxyBeanMethods = false)
public class LanProfileGuard {

	public LanProfileGuard(Environment environment) {
		if (!environment.acceptsProfiles(Profiles.of("prod"))) {
			return;
		}
		if (environment.acceptsProfiles(Profiles.of("lan"))) {
			throw new IllegalStateException("The lan profile must never run together with prod");
		}
		if (!environment.getProperty("app.auth.refresh-cookie.secure", Boolean.class, true)) {
			throw new IllegalStateException("prod needs a Secure refresh cookie (app.auth.refresh-cookie.secure=true)");
		}
	}
}
