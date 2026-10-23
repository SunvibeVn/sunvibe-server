package vn.sunvibe.survey.core.domain.model.appointment;

/**
 * The lead's requirement an appointment was booked for; only rental leads are surveyed (survey-schedule C-1, US-1
 * AC-1). An appointment keeps it, whatever the lead's requirement becomes later (spec I-5).
 */
public enum SurveyIntent {
    /** Rent by the night. */
    SHORT_TERM_RENT,
    /** Rent by the month. */
    LONG_TERM_RENT
}
