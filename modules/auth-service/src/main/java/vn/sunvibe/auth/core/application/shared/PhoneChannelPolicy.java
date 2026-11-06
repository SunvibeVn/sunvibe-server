package vn.sunvibe.auth.core.application.shared;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.exception.auth.AuthChannelDisabledException;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.shared.external.audit.AuthAuditEvent;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;

/**
 * Which phone operations are open (auth-email-access spec 4.5, BR-17..BR-19). The check runs in the use case, after DTO
 * validation and before any lookup, so a closed operation answers the same for everyone and a client flag cannot pass
 * it. The mode is read at start-up; only the end date of {@code legacy-login} takes effect without a restart.
 */
@Component
public class PhoneChannelPolicy {

    /** {@code app.auth.phone.mode}. */
    public enum Mode {
        /** Every phone operation is closed: the production default. */
        OFF,
        /** Only phone + password, for accounts without a verified email, until the end date. */
        LEGACY_LOGIN,
        /** Every phone operation is open: local and test; production only in a later release. */
        ON;

        /** Blank means off; anything unknown fails the start-up. */
        public static Mode parse(String value) {
            if (value == null || value.isBlank()) {
                return OFF;
            }
            return switch (value.trim()) {
                case "off" -> OFF;
                case "legacy-login" -> LEGACY_LOGIN;
                case "on" -> ON;
                default -> throw new IllegalStateException("app.auth.phone.mode must be off, legacy-login or on");
            };
        }
    }

    /** A phone operation a use case asks about. */
    public enum Operation {
        SIGN_UP,
        SIGN_IN,
        VERIFY,
        SMS_RESET
    }

    private final Mode mode;
    private final Instant legacyLoginUntil;
    private final AuditLogPort auditLog;

    @Autowired
    public PhoneChannelPolicy(@Value("${app.auth.phone.mode:off}") String mode,
                              @Value("${app.auth.phone.legacy-login-until:}") String legacyLoginUntil,
                              AuditLogPort auditLog) {
        this(Mode.parse(mode), legacyLoginUntil == null || legacyLoginUntil.isBlank() ? null : Instant.parse(legacyLoginUntil.trim()),
                auditLog);
    }

    public PhoneChannelPolicy(Mode mode, Instant legacyLoginUntil, AuditLogPort auditLog) {
        this.mode = mode;
        this.legacyLoginUntil = legacyLoginUntil;
        this.auditLog = auditLog;
    }

    public boolean allows(Operation operation) {
        return mode == Mode.ON || (operation == Operation.SIGN_IN && isLegacyLoginOpen());
    }

    /** Every phone operation is open, so an account can verify its number. */
    public boolean isOn() {
        return mode == Mode.ON;
    }

    /** {@code legacy-login} before its end date; after it the mode behaves as off, with no restart. */
    public boolean isLegacyLoginOpen() {
        return mode == Mode.LEGACY_LOGIN && legacyLoginUntil != null && AppClock.currentUTC().isBefore(legacyLoginUntil);
    }

    /** @throws AuthChannelDisabledException if the operation is closed */
    public void require(Operation operation) {
        if (!allows(operation)) {
            auditLog.record(AuthAuditEvent.of(AuditEventType.AUTH_CHANNEL_REFUSED, null, null, operation.name()));
            throw new AuthChannelDisabledException();
        }
    }
}
