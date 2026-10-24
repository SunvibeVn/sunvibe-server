package vn.sunvibe.survey.core.domain.model.checklist;

/** What a charge or fee amount is per (survey-checklist spec D-1, BR-5). */
public enum ChargeUnit {
    /** Per month. */
    PER_MONTH,
    /** Per kWh. */
    PER_KWH,
    /** Per cubic metre. */
    PER_M3,
    /** Per person per month. */
    PER_PERSON_PER_MONTH,
    /** Per vehicle per month. */
    PER_VEHICLE_PER_MONTH,
    /** Once. */
    ONE_OFF
}
