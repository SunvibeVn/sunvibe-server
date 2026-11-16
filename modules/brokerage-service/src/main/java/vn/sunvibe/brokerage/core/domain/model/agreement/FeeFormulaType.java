package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * How a brokerage fee, or the fee of a renewal, is set (brokerage-agreement BR-7; E05-S4 spec S4-D3). The fee is a
 * percentage of one month's rent, a fixed amount or free of charge with a reason; the renewal with the same tenant is not
 * charged, or charged by a percentage or a fixed amount. One type serves both places; each place takes its own values.
 */
public enum FeeFormulaType {
    PERCENT_OF_RENT,
    FIXED_AMOUNT,
    FREE,
    NOT_CHARGED;

    /** The values the fee itself takes, in the order a refusal lists them. */
    public static final List<String> FEE_TYPES = List.of(PERCENT_OF_RENT.name(), FIXED_AMOUNT.name(), FREE.name());
    /** The values the renewal takes, in the order a refusal lists them. */
    public static final List<String> RENEWAL_TYPES =
            List.of(NOT_CHARGED.name(), PERCENT_OF_RENT.name(), FIXED_AMOUNT.name());

    /** brokerage-agreement BR-7: a fee is set by a percentage, an amount or as free of charge, never "not charged". */
    public boolean forFee() {
        return FEE_TYPES.contains(name());
    }

    /** brokerage-agreement BR-7: a renewal is not charged, or charged by a percentage or an amount, never "free". */
    public boolean forRenewal() {
        return RENEWAL_TYPES.contains(name());
    }

    /** The type of this exact name; empty for null or any other text, a different case included. */
    public static Optional<FeeFormulaType> of(String codeOrNull) {
        return Arrays.stream(values()).filter(type -> type.name().equals(codeOrNull)).findFirst();
    }
}
