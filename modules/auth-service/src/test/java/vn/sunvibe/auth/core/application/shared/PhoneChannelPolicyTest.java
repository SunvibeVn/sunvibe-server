package vn.sunvibe.auth.core.application.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.sunvibe.auth.core.application.exception.auth.AuthChannelDisabledException;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy.Mode;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy.Operation;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access spec 4.5: which phone operation each mode lets through. */
class PhoneChannelPolicyTest {

    private final RecordingAuditLog audit = new RecordingAuditLog();

    private PhoneChannelPolicy policy(Mode mode, Instant until) {
        return new PhoneChannelPolicy(mode, until, audit);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void on_allowsEveryOperation(Operation operation) {
        assertTrue(policy(Mode.ON, null).allows(operation));
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void off_allowsNothing(Operation operation) {
        assertFalse(policy(Mode.OFF, null).allows(operation));
    }

    @Test
    void legacyLogin_beforeTheDate_allowsOnlySignIn() {
        PhoneChannelPolicy policy = policy(Mode.LEGACY_LOGIN, Instant.now().plus(Duration.ofDays(1)));

        assertTrue(policy.allows(Operation.SIGN_IN));
        assertTrue(policy.isLegacyLoginOpen());
        assertFalse(policy.allows(Operation.SIGN_UP));
        assertFalse(policy.allows(Operation.VERIFY));
        assertFalse(policy.allows(Operation.SMS_RESET));
    }

    /** After the date it behaves as off, with no restart. */
    @Test
    void legacyLogin_afterTheDate_behavesAsOff() {
        PhoneChannelPolicy policy = policy(Mode.LEGACY_LOGIN, Instant.now().minus(Duration.ofMinutes(1)));

        assertFalse(policy.allows(Operation.SIGN_IN));
        assertFalse(policy.isLegacyLoginOpen());
    }

    @Test
    void require_closedOperation_refusesAndAudits() {
        PhoneChannelPolicy policy = policy(Mode.OFF, null);

        assertThrows(AuthChannelDisabledException.class, () -> policy.require(Operation.SIGN_UP));

        assertEquals(java.util.List.of(AuditEventType.AUTH_CHANNEL_REFUSED), audit.types());
        assertEquals("SIGN_UP", audit.last().details());
    }

    @Test
    void require_openOperation_passes() {
        assertDoesNotThrow(() -> policy(Mode.ON, null).require(Operation.VERIFY));
        assertTrue(audit.events.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void parse_blank_isOff(String value) {
        assertEquals(Mode.OFF, Mode.parse(value));
    }

    @Test
    void parse_theThreeNames() {
        assertEquals(Mode.OFF, Mode.parse("off"));
        assertEquals(Mode.LEGACY_LOGIN, Mode.parse("legacy-login"));
        assertEquals(Mode.ON, Mode.parse("on"));
    }

    @Test
    void parse_unknownName_refused() {
        assertThrows(IllegalStateException.class, () -> Mode.parse("true"));
    }
}
