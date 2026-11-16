package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.util.List;
import java.util.Objects;

/**
 * How a brokerage fee, or the fee of a renewal, is set (brokerage-agreement BR-7; E05-S4 spec S4-D3). The first failing
 * rule refuses it with the field path inside the formula:
 * <ul>
 *     <li>{@code PERCENT_OF_RENT}: 1 to 5 tiers; the first from a term of 1 month and the starts strictly increasing, so
 *     the tiers never overlap and cover every term; no amount and no reason. The percentages are not ordered: the brief
 *     orders the terms only.</li>
 *     <li>{@code FIXED_AMOUNT}: a whole positive VND amount up to {@link VersionTerms#MAX_AMOUNT} (ADR-006); no tier and
 *     no reason.</li>
 *     <li>{@code FREE}: a reason, trimmed, of 1 to 500 UTF-16 units, without a control or a bidirectional formatting
 *     character (the owner confirms the text as shown); no tier and no amount.</li>
 *     <li>{@code NOT_CHARGED}: nothing else.</li>
 * </ul>
 * Which place takes which type is {@link BrokerageFee}'s rule.
 *
 * @param tiers the tiers, empty unless {@code PERCENT_OF_RENT}; never null
 */
public record FeeFormula(FeeFormulaType type, List<FeeTier> tiers, Long amountOrNull, String freeReasonOrNull) {

    /** brokerage-agreement BR-7: at most five tiers by lease term. */
    public static final int MAX_TIERS = 5;
    /** Technical bound of the reason of a fee free of charge, in UTF-16 units as Bean Validation counts them. */
    public static final int MAX_FREE_REASON_LENGTH = 500;

    public FeeFormula {
        if (type == null) {
            throw new AgreementContentException("type");
        }
        tiers = checkedTiers(type, tiers);
        if (type == FeeFormulaType.FIXED_AMOUNT) {
            // brokerage-agreement BR-7 and E-13: a fixed fee is a positive whole amount; 0 never passes.
            if (amountOrNull == null || amountOrNull < 1 || amountOrNull > VersionTerms.MAX_AMOUNT) {
                throw new AgreementContentException("amount");
            }
        } else if (amountOrNull != null) {
            throw new AgreementContentException("amount");
        }
        if (type == FeeFormulaType.FREE) {
            freeReasonOrNull = freeReason(freeReasonOrNull);
        } else if (freeReasonOrNull != null) {
            throw new AgreementContentException("freeReason");
        }
    }

    /** The formula of this type; {@code tiersOrNull} null reads as no tier. */
    public static FeeFormula of(FeeFormulaType type, List<FeeTier> tiersOrNull, Long amountOrNull,
                                String freeReasonOrNull) {
        return new FeeFormula(type, tiersOrNull, amountOrNull, freeReasonOrNull);
    }

    /** The tiers copied and checked: required and ordered for a percentage, absent for every other type. */
    private static List<FeeTier> checkedTiers(FeeFormulaType type, List<FeeTier> tiersOrNull) {
        List<FeeTier> given = tiersOrNull == null ? List.of() : tiersOrNull;
        if (type != FeeFormulaType.PERCENT_OF_RENT) {
            if (!given.isEmpty()) {
                throw new AgreementContentException("tiers");
            }
            return List.of();
        }
        // contains(null) throws on an immutable list, so the elements are checked one by one
        if (given.isEmpty() || given.size() > MAX_TIERS || given.stream().anyMatch(Objects::isNull)) {
            throw new AgreementContentException("tiers");
        }
        // brokerage-agreement BR-7: the first tier starts at a term of 1 month and the tiers rise without overlapping,
        // so every start is above the one before it.
        if (given.getFirst().fromMonths() != 1) {
            throw new AgreementContentException("tiers[0].fromMonths");
        }
        for (int index = 1; index < given.size(); index++) {
            if (given.get(index).fromMonths() <= given.get(index - 1).fromMonths()) {
                throw new AgreementContentException("tiers[" + index + "].fromMonths");
            }
        }
        return List.copyOf(given);
    }

    /**
     * brokerage-agreement BR-7: "free of charge" is an explicit choice with a reason. The reason is trimmed, then holds 1
     * to 500 UTF-16 units and no character that can hide or reorder the text the owner confirms (E05-S4 spec S4-D3).
     */
    private static String freeReason(String reasonOrNull) {
        if (reasonOrNull == null) {
            throw new AgreementContentException("freeReason");
        }
        String reason = reasonOrNull.strip();
        if (reason.isEmpty() || reason.length() > MAX_FREE_REASON_LENGTH
                || reason.codePoints().anyMatch(FeeFormula::hidesText)) {
            throw new AgreementContentException("freeReason");
        }
        return reason;
    }

    /**
     * A control character (U+0000 to U+001F, U+007F to U+009F; PostgreSQL cannot store U+0000) or a bidirectional
     * formatting character (U+200E, U+200F, U+202A to U+202E, U+2066 to U+2069).
     */
    private static boolean hidesText(int codePoint) {
        return codePoint <= 0x1F || (codePoint >= 0x7F && codePoint <= 0x9F) || codePoint == 0x200E
                || codePoint == 0x200F || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    /** brokerage-agreement BR-17: the fee policy is private, so the type and which parts it holds, never a value. */
    @Override
    public String toString() {
        String part = switch (type) {
            case PERCENT_OF_RENT -> ", tiers";
            case FIXED_AMOUNT -> ", amount";
            case FREE -> ", reason";
            case NOT_CHARGED -> "";
        };
        return "FeeFormula[" + type + part + "]";
    }
}
