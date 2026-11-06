package vn.sunvibe.auth.core.infrastructure.persistence.support;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;
import java.util.Optional;

/** Which named constraint a failed write broke, read from the Hibernate cause. */
public final class AuthConstraintViolations {

    private static final int MAX_CAUSE_DEPTH = 20;

    private AuthConstraintViolations() {
    }

    /** The lower-case name of the constraint, when the driver reported one. */
    public static Optional<String> constraintName(DataIntegrityViolationException e) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return Optional.of(violation.getConstraintName().toLowerCase(Locale.ROOT));
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
