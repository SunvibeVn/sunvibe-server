package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What is refunded of the brokerage fee when the lease ends early (brokerage-agreement BR-7; E05-S4 spec S4-D3): nothing,
 * or a part of it.
 */
public enum EarlyLeaseEndType {
    NO_REFUND,
    PARTIAL_REFUND;

    /** The names in the order a refusal lists them. */
    public static final List<String> NAMES = Arrays.stream(values()).map(Enum::name).toList();

    /** The type of this exact name; empty for null or any other text, a different case included. */
    public static Optional<EarlyLeaseEndType> of(String codeOrNull) {
        return Arrays.stream(values()).filter(type -> type.name().equals(codeOrNull)).findFirst();
    }
}
