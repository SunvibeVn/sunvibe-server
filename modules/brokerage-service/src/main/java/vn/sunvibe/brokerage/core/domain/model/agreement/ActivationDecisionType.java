package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * A manager's decision on a version awaiting activation, stored on the version (brokerage-agreement US-3 AC-1 to AC-3;
 * E05-S6 spec S6-D14).
 */
public enum ActivationDecisionType {
    ACTIVATED,
    REFUSED;

    /** The type of a stored code; empty for any other text. */
    public static Optional<ActivationDecisionType> of(String codeOrNull) {
        return Arrays.stream(values()).filter(type -> type.name().equals(codeOrNull)).findFirst();
    }
}
