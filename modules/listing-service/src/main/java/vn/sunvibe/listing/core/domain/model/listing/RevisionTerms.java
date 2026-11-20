package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A revision's terms snapshot, copied by value from one of E05's versions (listing-publish US-2 AC-2, BR-4; E06-S2 spec
 * E06S2-D6): the monthly rent in whole VND, the deposit, the minimum term, the payment period and the mandatory fees in
 * E05's order, with the source, the pending start date and the time taken. The bounds are E05's own CHECKs, and E05
 * activates only complete terms, so a value outside them is a broken contract: the snapshot is not built. E05's terms
 * are private (brokerage-agreement BR-17): no text of these records names a value.
 */
public record RevisionTerms(TermsSource source, LocalDate pendingStartDateOrNull, Instant capturedAt, long monthlyRent,
                            Deposit deposit, int minimumTermMonths, int paymentPeriodMonths, List<Fee> fees) {

    /** NUMERIC(15,0): the largest whole VND amount a column holds (ADR-006), E05's upper bound. */
    public static final long AMOUNT_MAX = 999_999_999_999_999L;
    public static final int DEPOSIT_MONTHS_MAX = 12;
    public static final int MINIMUM_TERM_MAX = 120;
    public static final int PAYMENT_PERIOD_MAX = 12;
    public static final int FEES_MAX = 20;
    public static final int FEE_NAME_MAX = 100;

    public RevisionTerms {
        if (source == null || capturedAt == null || deposit == null || fees == null) {
            throw new IllegalArgumentException("a terms snapshot needs its source, time, deposit and fees");
        }
        // the pending start date belongs to a snapshot of the version waiting to start, and only to it
        if ((source == TermsSource.PENDING_START) != (pendingStartDateOrNull != null)) {
            throw new IllegalArgumentException("a pending start date is set exactly for a pending version");
        }
        if (monthlyRent < 1 || monthlyRent > AMOUNT_MAX) {
            throw new IllegalArgumentException("the monthly rent is outside its bounds");
        }
        if (minimumTermMonths < 1 || minimumTermMonths > MINIMUM_TERM_MAX) {
            throw new IllegalArgumentException("the minimum term is outside its bounds");
        }
        if (paymentPeriodMonths < 1 || paymentPeriodMonths > PAYMENT_PERIOD_MAX) {
            throw new IllegalArgumentException("the payment period is outside its bounds");
        }
        if (fees.size() > FEES_MAX) {
            throw new IllegalArgumentException("a snapshot holds at most " + FEES_MAX + " fees");
        }
        fees = List.copyOf(fees);
    }

    /**
     * The deposit: a number of months of rent from 0 to 12 without an amount, or an amount from 0 without months
     * (0 is no deposit, brokerage-agreement BR-8).
     */
    public record Deposit(DepositBasis basis, Integer monthsOrNull, Long amountOrNull) {

        public Deposit {
            if (basis == null) {
                throw new IllegalArgumentException("a deposit needs its basis");
            }
            boolean valid = switch (basis) {
                case MONTHS -> amountOrNull == null && monthsOrNull != null && monthsOrNull >= 0
                        && monthsOrNull <= DEPOSIT_MONTHS_MAX;
                case AMOUNT -> monthsOrNull == null && amountOrNull != null && amountOrNull >= 0
                        && amountOrNull <= AMOUNT_MAX;
            };
            if (!valid) {
                throw new IllegalArgumentException("the deposit does not have its basis's shape");
            }
        }

        @Override
        public String toString() {
            return "Deposit[hidden]";
        }
    }

    /**
     * One mandatory fee: a name of 1 to 100 characters, and an amount of at least 1 with its charge unit for
     * {@code AMOUNT}, neither for {@code INCLUDED} or {@code PROVIDER_TARIFF}.
     */
    public record Fee(String name, FeeBasis basis, Long amountOrNull, ChargeUnit chargeUnitOrNull) {

        public Fee {
            if (name == null || basis == null) {
                throw new IllegalArgumentException("a fee needs its name and basis");
            }
            int length = name.codePointCount(0, name.length());
            if (length < 1 || length > FEE_NAME_MAX) {
                throw new IllegalArgumentException("a fee name is 1 to " + FEE_NAME_MAX + " characters");
            }
            boolean valid = basis == FeeBasis.AMOUNT
                    ? amountOrNull != null && amountOrNull >= 1 && amountOrNull <= AMOUNT_MAX && chargeUnitOrNull != null
                    : amountOrNull == null && chargeUnitOrNull == null;
            if (!valid) {
                throw new IllegalArgumentException("the fee does not have its basis's shape");
            }
        }

        @Override
        public String toString() {
            return "Fee[hidden]";
        }
    }

    @Override
    public String toString() {
        return "RevisionTerms[hidden]";
    }
}
