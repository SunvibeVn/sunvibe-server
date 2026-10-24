package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * A charge (survey-checklist BR-5): its basis and, exactly for AMOUNT, the amount and what it is per; an optional
 * note. Note: never printed.
 */
public record Charge(ChargeBasis basis, Money amount, ChargeUnit chargeUnit, String note) {

    @Override
    public String toString() {
        return "Charge[" + basis + "]";
    }
}
