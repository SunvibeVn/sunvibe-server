package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * An amount in whole VND that is not a price per period (deposit, charge, fee; survey-checklist spec D-1, approved by
 * the owner as a feature-local exception to ADR-006, C-4): 0 means confirmed free or no deposit, never unknown.
 */
public record Money(Long amount, String currency) {

    public static final String VND = "VND";
    public static final long MAX_AMOUNT = 999_999_999_999_999L;

    public static Money vnd(long amount) {
        return new Money(amount, VND);
    }

    /** Survey terms are private: never print the amount. */
    @Override
    public String toString() {
        return "Money[" + currency + "]";
    }
}
