package vn.sunvibe.auth.core.shared.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The browser-session refresh cookie ({@code app.auth.refresh-cookie.*}).
 *
 * @param name   {@code sv_refresh}; {@code __Secure-sv_refresh} in prod, so the browser only accepts it over HTTPS
 * @param secure sent over HTTPS only; off only in the {@code lan} profile, where the phone reaches the API over
 *               plain HTTP (auth-browser-session US-5)
 */
@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
public record RefreshCookieProperties(@DefaultValue("sv_refresh") String name, @DefaultValue("true") boolean secure) {
}
