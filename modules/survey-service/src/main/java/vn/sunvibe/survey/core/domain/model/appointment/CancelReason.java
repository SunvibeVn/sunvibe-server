package vn.sunvibe.survey.core.domain.model.appointment;

/**
 * Why an appointment was cancelled (survey-schedule US-2 AC-2, BR-3): five reasons staff pick and three the system
 * writes when a lead command cancels it.
 */
public enum CancelReason {
    /** The owner cancelled. */
    OWNER_CANCELLED(true),
    /** Staff cancelled. */
    STAFF_CANCELLED(true),
    /** The owner was not there at the appointment. */
    OWNER_ABSENT(true),
    /** Staff could not get into the property. */
    NO_ACCESS(true),
    /** Another reason, described by staff. */
    OTHER(true),
    /** System: the lead was put on hold. */
    LEAD_ON_HOLD(false),
    /** System: the lead was closed. */
    LEAD_CLOSED(false),
    /** System: the lead's requirement changed. */
    INTENT_CHANGED(false);

    private final boolean userChoice;

    CancelReason(boolean userChoice) {
        this.userChoice = userChoice;
    }

    /** True for the five reasons a user may pick. */
    public boolean isUserChoice() {
        return userChoice;
    }
}
