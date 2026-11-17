package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.util.List;
import java.util.Objects;

/**
 * The rental terms of a version that the owner confirms (brokerage-agreement BR-8): monthly rent, deposit, minimum term,
 * payment period and the mandatory fees besides the rent. Every part may stay null in a draft (E05-S3 spec S3-D8);
 * refusing to send a version with a missing part is E05-S4's. Amounts are whole VND (ADR-006).
 *
 * @param mandatoryFeesOrNull null = not stated yet; empty = stated: no mandatory fee besides the rent
 */
public record VersionTerms(Long monthlyRentOrNull, TermsDeposit depositOrNull, Integer minimumTermMonthsOrNull,
                           Integer paymentPeriodMonthsOrNull, List<MandatoryFee> mandatoryFeesOrNull) {

    /** The largest amount of a NUMERIC(15,0) column (ADR-006), survey's own bound. */
    public static final long MAX_AMOUNT = 999_999_999_999_999L;
    /** Technical input bound of the fee list. */
    public static final int MAX_FEES = 20;
    /** Nothing entered yet. */
    public static final VersionTerms NONE = new VersionTerms(null, null, null, null, null);

    private static final int MAX_MINIMUM_TERM_MONTHS = 120;
    private static final int MAX_PAYMENT_PERIOD_MONTHS = 12;

    public VersionTerms {
        // brokerage-agreement BR-8: the monthly rent is greater than zero.
        if (monthlyRentOrNull != null && (monthlyRentOrNull < 1 || monthlyRentOrNull > MAX_AMOUNT)) {
            throw new AgreementContentException("monthlyRent.amount");
        }
        if (minimumTermMonthsOrNull != null
                && (minimumTermMonthsOrNull < 1 || minimumTermMonthsOrNull > MAX_MINIMUM_TERM_MONTHS)) {
            throw new AgreementContentException("minimumTermMonths");
        }
        if (paymentPeriodMonthsOrNull != null
                && (paymentPeriodMonthsOrNull < 1 || paymentPeriodMonthsOrNull > MAX_PAYMENT_PERIOD_MONTHS)) {
            throw new AgreementContentException("paymentPeriodMonths");
        }
        if (mandatoryFeesOrNull != null) {
            // contains(null) throws on an immutable list, so the elements are checked one by one
            if (mandatoryFeesOrNull.size() > MAX_FEES || mandatoryFeesOrNull.stream().anyMatch(Objects::isNull)) {
                throw new AgreementContentException("mandatoryFees");
            }
            mandatoryFeesOrNull = List.copyOf(mandatoryFeesOrNull);
        }
    }

    /** brokerage-agreement BR-17: the terms are private, so no amount and no fee name. */
    @Override
    public String toString() {
        return "VersionTerms[hidden]";
    }
}
