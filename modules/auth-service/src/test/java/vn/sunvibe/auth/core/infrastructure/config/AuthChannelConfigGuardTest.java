package vn.sunvibe.auth.core.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** auth-email-access spec 5.7: a configuration that could open phone by mistake does not start. */
class AuthChannelConfigGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final Set<String> PROD = Set.of("prod");

    private static void check(String mode, String until, String provider, Set<String> profiles) {
        AuthChannelConfigGuard.check(mode, until, provider, profiles, NOW);
    }

    @Test
    void guard_legacyWithoutDate_failsStart() {
        assertThrows(IllegalStateException.class, () -> check("legacy-login", "", "none", PROD));
        assertThrows(IllegalStateException.class, () -> check("legacy-login", "not-a-date", "none", PROD));
    }

    @Test
    void guard_legacyDateOver60Days_failsStart() {
        String tooFar = NOW.plus(Duration.ofDays(60)).plusSeconds(1).toString();

        assertThrows(IllegalStateException.class, () -> check("legacy-login", tooFar, "none", PROD));
    }

    @Test
    void guard_legacyDateWithin60Days_starts() {
        assertDoesNotThrow(() -> check("legacy-login", NOW.plus(Duration.ofDays(60)).toString(), "none", PROD));
    }

    @Test
    void guard_unknownMode_failsStart() {
        assertThrows(IllegalStateException.class, () -> check("true", "", "none", PROD));
    }

    @Test
    void guard_blankMode_isOff() {
        assertDoesNotThrow(() -> check("", "", "none", PROD));
    }

    /** So prod with on cannot start until a real provider exists (BR-19, G-4). */
    @Test
    void guard_onWithNoneProvider_failsStart() {
        assertThrows(IllegalStateException.class, () -> check("on", "", "none", Set.of("dev")));
    }

    @Test
    void guard_logOutsideDevTestLan_failsStart() {
        assertThrows(IllegalStateException.class, () -> check("off", "", "log", PROD));
        assertThrows(IllegalStateException.class, () -> check("on", "", "log", Set.of()));
    }

    /** Spring matches the provider in any case, so LOG creates the logging sender too. */
    @Test
    void guard_logInAnyCase_failsStart() {
        assertThrows(IllegalStateException.class, () -> check("on", "", "LOG", PROD));
        assertThrows(IllegalStateException.class, () -> check("on", "", " Log ", PROD));
    }

    /** A dev profile beside prod does not make OTPs in the log acceptable. */
    @Test
    void guard_logBesideProd_failsStart() {
        assertThrows(IllegalStateException.class, () -> check("on", "", "log", Set.of("prod", "dev")));
        assertThrows(IllegalStateException.class, () -> check("off", "", "log", Set.of("prod", "lan")));
    }

    @Test
    void guard_noneInAnyCase_isStillNone() {
        assertThrows(IllegalStateException.class, () -> check("on", "", "NONE", Set.of("dev")));
    }

    @Test
    void guard_logInDevTestOrLan_starts() {
        assertDoesNotThrow(() -> check("on", "", "log", Set.of("dev")));
        assertDoesNotThrow(() -> check("on", "", "log", Set.of("test")));
        assertDoesNotThrow(() -> check("on", "", "log", Set.of("dev", "lan")));
    }

    /** auth-email-access S-30: a reset link may only point at an origin of ours. */
    @Test
    void guard_baseUrlOutsideAllowList_failsStart() {
        assertThrows(IllegalStateException.class, () -> AuthChannelConfigGuard.checkFrontendUrl(
                "https://evil.example", "https://www.sunvibe.vn", Set.of("prod")));
    }

    @Test
    void guard_prodHttp_failsStart() {
        assertThrows(IllegalStateException.class, () -> AuthChannelConfigGuard.checkFrontendUrl(
                "http://www.sunvibe.vn", "http://www.sunvibe.vn", Set.of("prod")));
    }

    @Test
    void guard_baseUrlInAllowList_starts() {
        assertDoesNotThrow(() -> AuthChannelConfigGuard.checkFrontendUrl(
                "https://www.sunvibe.vn", "https://www.sunvibe.vn", Set.of("prod")));
        assertDoesNotThrow(() -> AuthChannelConfigGuard.checkFrontendUrl(
                "http://192.168.1.20:3000", "http://localhost:3000, http://192.168.1.20:3000", Set.of("dev", "lan")));
    }

    @Test
    void offStartsWithoutSmsProvider() {
        assertDoesNotThrow(() -> check("off", "", "none", PROD));
    }
}
