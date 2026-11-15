package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * The transaction an agreement brokers (brokerage-agreement BR-1: one agreement is of one unit and one transaction type;
 * only long-term rental now, owner decision G2 D-45). The value name is acquisition's lead intent of the same meaning.
 */
public enum TransactionType {
    LONG_TERM_RENT;

    /** The type of this exact name; empty for null or any other text, a different case included. */
    public static Optional<TransactionType> of(String codeOrNull) {
        return Arrays.stream(values()).filter(type -> type.name().equals(codeOrNull)).findFirst();
    }
}
