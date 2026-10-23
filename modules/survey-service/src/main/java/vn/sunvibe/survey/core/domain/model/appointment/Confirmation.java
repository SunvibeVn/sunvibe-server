package vn.sunvibe.survey.core.domain.model.appointment;

import java.time.Instant;

/**
 * The owner's confirmation of an appointment (survey-schedule US-1 AC-5, spec A-28): its state, the cause when
 * unconfirmed (null exactly when confirmed), whether it was ever confirmed, who changed it last and when, and the note
 * of that change. The note is staff text: never logged.
 */
public record Confirmation(ConfirmationState state, ConfirmationCause cause, boolean everConfirmed, String changedBy,
                           Instant changedAt, String note) {

    public Confirmation {
        if (state == null || changedBy == null || changedAt == null) {
            throw new IllegalArgumentException("A confirmation needs its state, actor and time.");
        }
        if ((state == ConfirmationState.CONFIRMED) == (cause != null)) {
            throw new IllegalArgumentException("A cause is given exactly when unconfirmed.");
        }
        if (state == ConfirmationState.CONFIRMED && !everConfirmed) {
            throw new IllegalArgumentException("A confirmed appointment was confirmed.");
        }
    }

    /** "Needs reconfirmation": unconfirmed after an earlier confirmation (survey-schedule spec 4.1). */
    public boolean needsReconfirmation() {
        return state == ConfirmationState.UNCONFIRMED && everConfirmed;
    }

    @Override
    public String toString() {
        return "Confirmation[" + state + ", " + cause + "]";
    }
}
