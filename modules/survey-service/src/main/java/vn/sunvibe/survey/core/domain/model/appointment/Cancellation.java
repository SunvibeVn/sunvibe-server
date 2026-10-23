package vn.sunvibe.survey.core.domain.model.appointment;

import java.time.Instant;

/**
 * How an appointment was cancelled (survey-schedule US-2 AC-2): the reason, its description (required for OTHER; for a
 * system cancellation the hold note or the close reason), who and when. Staff text: never logged.
 */
public record Cancellation(CancelReason reason, String description, String cancelledBy, Instant cancelledAt) {

    public Cancellation {
        if (reason == null || cancelledBy == null || cancelledAt == null) {
            throw new IllegalArgumentException("A cancellation needs its reason, actor and time.");
        }
    }

    @Override
    public String toString() {
        return "Cancellation[" + reason + "]";
    }
}
