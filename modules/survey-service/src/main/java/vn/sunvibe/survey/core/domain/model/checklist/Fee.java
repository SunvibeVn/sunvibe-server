package vn.sunvibe.survey.core.domain.model.checklist;

/** One fee of a fee list: its name, amount and what it is per (survey-checklist BR-5). */
public record Fee(String name, Money amount, ChargeUnit chargeUnit) {

    @Override
    public String toString() {
        return "Fee[" + chargeUnit + "]";
    }
}
