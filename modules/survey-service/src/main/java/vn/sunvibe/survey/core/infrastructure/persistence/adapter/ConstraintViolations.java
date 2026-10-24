package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

/** Which named constraint a failed write broke, read from the Hibernate cause. */
final class ConstraintViolations {

    private static final int MAX_CAUSE_DEPTH = 20;

    private ConstraintViolations() {
    }

    static boolean violatesAny(DataIntegrityViolationException e, Set<String> constraintNames) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && constraintNames.stream().anyMatch(name -> name.equalsIgnoreCase(violation.getConstraintName()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
