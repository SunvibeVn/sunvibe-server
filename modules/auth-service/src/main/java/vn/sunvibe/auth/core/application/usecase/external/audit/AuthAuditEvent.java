package vn.sunvibe.auth.core.application.usecase.external.audit;

import java.util.Objects;

/**
 * One entry of the security audit log. The client's IP and User-Agent are added
 * by the adapter from the current request.
 *
 * @param type    what happened
 * @param userId  the account concerned, or null when none is known (e.g. unknown username)
 * @param subject the identifier the client used (phone number or e-mail), may be null
 * @param details short machine-readable detail such as a reason code; never secrets
 */
public record AuthAuditEvent(AuditEventType type, String userId, String subject, String details) {

    public AuthAuditEvent {
        Objects.requireNonNull(type, "type");
    }

    public static AuthAuditEvent of(AuditEventType type, String userId, String subject, String details) {
        return new AuthAuditEvent(type, userId, subject, details);
    }

    public static AuthAuditEvent of(AuditEventType type, String userId, String subject) {
        return new AuthAuditEvent(type, userId, subject, null);
    }
}
