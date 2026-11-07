package vn.sunvibe.auth.core.application.shared.external.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access 4.10: the raw identifier never reaches the subject, whatever a caller passes. */
class AuthAuditEventTest {

    @Test
    void subject_isMasked_whateverTheCallerPasses() {
        assertEquals("091****678", AuthAuditEvent.of(AuditEventType.LOGIN_FAILED, null, "0912345678").subject());
        assertEquals("a***@example.com",
                AuthAuditEvent.of(AuditEventType.LOGIN_FAILED, null, "an.nguyen@example.com", "BAD_CREDENTIALS").subject());
        assertEquals("a***@example.com",
                new AuthAuditEvent(AuditEventType.LOGIN_FAILED, null, "an.nguyen@example.com", null).subject());
    }

    @Test
    void subject_null_staysNull() {
        assertNull(AuthAuditEvent.of(AuditEventType.LOGOUT, "user-1", null).subject());
    }
}
