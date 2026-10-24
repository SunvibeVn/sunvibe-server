package vn.sunvibe.survey.core.domain.model.checklist;

/** How a deposit is given (survey-checklist BR-5, D-AI-24). */
public enum DepositBasis {
    /** A number of months of rent, 0 to 12. */
    MONTHS,
    /** An amount in VND; 0 means no deposit. */
    AMOUNT
}
