package vn.sunvibe.survey.core.domain.model.checklist;

/** How a charge applies (survey-checklist BR-5). */
public enum ChargeBasis {
    /** An amount per charge unit. */
    AMOUNT,
    /** Included in the rent. */
    INCLUDED,
    /** The provider's tariff. */
    PROVIDER_TARIFF,
    /** Not offered. */
    NOT_OFFERED
}
