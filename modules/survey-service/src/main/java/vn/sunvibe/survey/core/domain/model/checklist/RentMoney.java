package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * A monthly rent in whole VND in the ADR-006 price shape {@code {amount, currency, unit}} with unit MONTH
 * (survey-checklist spec D-1); at least 1.
 */
public record RentMoney(Long amount, String currency, String unit) {

    public static final String MONTH = "MONTH";

    public static RentMoney monthly(long amount) {
        return new RentMoney(amount, Money.VND, MONTH);
    }

    /** Survey terms are private: never print the amount. */
    @Override
    public String toString() {
        return "RentMoney[" + currency + "/" + unit + "]";
    }
}
