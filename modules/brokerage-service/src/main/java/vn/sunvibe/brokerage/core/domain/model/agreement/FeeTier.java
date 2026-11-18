package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

/**
 * One tier of a percentage fee (brokerage-agreement BR-7; E05-S4 spec S4-D3): from a lease term of {@code fromMonths}
 * months, {@code percent} percent of one month's rent of the signed lease. It covers the terms up to the month before the
 * next tier's start; the last one is open-ended. The order of the tiers is the formula's rule ({@link FeeFormula}).
 *
 * @param fromMonths 1 to 120: the technical bound of a lease term in months
 * @param percent    1 to 300
 */
public record FeeTier(int fromMonths, int percent) {

    /** Technical input bound of a tier's start, the bound of a minimum term (brokerage-agreement BR-8). */
    public static final int MAX_FROM_MONTHS = 120;
    /** brokerage-agreement BR-7: a percentage of one month's rent, from 1 to 300. */
    public static final int MAX_PERCENT = 300;

    public FeeTier {
        if (fromMonths < 1 || fromMonths > MAX_FROM_MONTHS) {
            throw new AgreementContentException("fromMonths");
        }
        if (percent < 1 || percent > MAX_PERCENT) {
            throw new AgreementContentException("percent");
        }
    }

    /** brokerage-agreement BR-17: the fee policy is private, so never the start or the percentage. */
    @Override
    public String toString() {
        return "FeeTier[hidden]";
    }
}
