package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * The owner's answer to a version sent to them, stored on the version (brokerage-agreement US-2 AC-2, AC-3; E05-S5 spec
 * S5-D11).
 */
public enum OwnerAnswerType {
    CONFIRMED,
    REJECTED;

    /** The type of a stored code; empty for any other text. */
    public static Optional<OwnerAnswerType> of(String codeOrNull) {
        return Arrays.stream(values()).filter(type -> type.name().equals(codeOrNull)).findFirst();
    }
}
