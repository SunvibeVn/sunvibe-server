package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

/**
 * The deposit of the confirmed terms (brokerage-agreement BR-8): a number of months' rent from 0 to 12, or an amount in
 * whole VND from 0 to {@link VersionTerms#MAX_AMOUNT}; 0 means no deposit. The first failing rule refuses it with the
 * field path inside the terms.
 */
public record TermsDeposit(DepositBasis basis, Integer monthsOrNull, Long amountOrNull) {

    private static final int MAX_MONTHS = 12;

    public TermsDeposit {
        if (basis == null) {
            throw new AgreementContentException("deposit.basis");
        }
        if (basis == DepositBasis.MONTHS) {
            if (monthsOrNull == null || monthsOrNull < 0 || monthsOrNull > MAX_MONTHS) {
                throw new AgreementContentException("deposit.months");
            }
            if (amountOrNull != null) {
                throw new AgreementContentException("deposit.amount");
            }
        } else {
            if (amountOrNull == null || amountOrNull < 0 || amountOrNull > VersionTerms.MAX_AMOUNT) {
                throw new AgreementContentException("deposit.amount");
            }
            if (monthsOrNull != null) {
                throw new AgreementContentException("deposit.months");
            }
        }
    }

    /** brokerage-agreement BR-17: the terms are private, so never the months or the amount. */
    @Override
    public String toString() {
        return "TermsDeposit[" + basis + "]";
    }
}
