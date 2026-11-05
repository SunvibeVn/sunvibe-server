package vn.sunvibe.auth.core.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy.Mode;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Stops the start-up on a configuration that could open phone by mistake or send a reset link elsewhere
 * (auth-email-access spec 5.7), so {@code prod} with {@code on} cannot start until a real SMS provider exists
 * (auth-email-access BR-19, G-4).
 */
@Component
@RequiredArgsConstructor
public class AuthChannelConfigGuard {

    private static final Duration MAX_LEGACY_WINDOW = Duration.ofDays(60);
    private static final Set<String> LOG_SMS_PROFILES = Set.of("dev", "test", "lan");

    private final Environment environment;

    @PostConstruct
    void checkOnStart() {
        check(environment.getProperty("app.auth.phone.mode", "off"),
                environment.getProperty("app.auth.phone.legacy-login-until", ""),
                environment.getProperty("app.sms.provider", "none"),
                Set.of(environment.getActiveProfiles()),
                AppClock.currentUTC());
        checkFrontendUrl(environment.getProperty("app.auth.frontend-base-url", ""),
                environment.getProperty("app.cors.allowed-origins", ""),
                Set.of(environment.getActiveProfiles()));
    }

    /**
     * A reset link carries a token, so it may only point at an origin of ours, over https in production.
     *
     * @throws IllegalStateException if this base URL must not start
     */
    static void checkFrontendUrl(String baseUrl, String allowedOrigins, Set<String> profiles) {
        String origin = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (Arrays.stream(allowedOrigins.split(",")).map(String::trim).noneMatch(origin::equals)) {
            throw new IllegalStateException("app.auth.frontend-base-url is not in app.cors.allowed-origins");
        }
        if (profiles.contains("prod") && !origin.startsWith("https://")) {
            throw new IllegalStateException("app.auth.frontend-base-url must be https in the prod profile");
        }
    }

    /** @throws IllegalStateException if this configuration must not start */
    static void check(String mode, String legacyLoginUntil, String smsProvider, Set<String> profiles, Instant now) {
        Mode parsed = Mode.parse(mode);
        if (parsed == Mode.LEGACY_LOGIN) {
            checkLegacyEndDate(legacyLoginUntil, now);
        }
        // Spring matches the property of the sender in any case, so this must too
        String provider = smsProvider.trim().toLowerCase(Locale.ROOT);
        if (parsed == Mode.ON && "none".equals(provider)) {
            throw new IllegalStateException("app.auth.phone.mode=on needs an SMS provider");
        }
        if ("log".equals(provider) && (profiles.contains("prod") || profiles.stream().noneMatch(LOG_SMS_PROFILES::contains))) {
            throw new IllegalStateException("app.sms.provider=log runs only in the dev, test and lan profiles, never with prod");
        }
    }

    private static void checkLegacyEndDate(String legacyLoginUntil, Instant now) {
        Instant until;
        try {
            until = Instant.parse(legacyLoginUntil.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("app.auth.phone.legacy-login-until must be an instant", e);
        }
        if (until.isAfter(now.plus(MAX_LEGACY_WINDOW))) {
            throw new IllegalStateException("app.auth.phone.legacy-login-until is more than 60 days away");
        }
    }
}
