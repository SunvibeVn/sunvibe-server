package vn.sunvibe.survey.core.domain.model.checklist;

/** A deposit as a number of months (0..12) or an amount; 0 means no deposit (survey-checklist BR-5, D-AI-24). */
public record Deposit(DepositBasis basis, Integer months, Money amount) {

    /** Survey terms are private: never print the amount or months. */
    @Override
    public String toString() {
        return "Deposit[" + basis + "]";
    }
}
