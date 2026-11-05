package vn.sunvibe.auth.core.domain.exception.user;

import java.io.Serial;

/**
 * A write lost the race for a login identifier: a unique index refused it (auth-email-access spec 5.4, E-17). The
 * transaction is already rolled back by the database, so the caller only lets this leave. It names the kind of
 * identifier and never its value.
 */
public class IdentifierConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Which login identifier was already held. */
    public enum Kind {
        EMAIL,
        PHONE
    }

    private final Kind kind;

    public IdentifierConflictException(Kind kind) {
        super("Login identifier already in use: " + kind);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

}
