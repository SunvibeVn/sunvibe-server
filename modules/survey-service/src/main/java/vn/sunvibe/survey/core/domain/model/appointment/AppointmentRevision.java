package vn.sunvibe.survey.core.domain.model.appointment;

import java.time.Instant;

/**
 * One version of an appointment's editable values (survey-schedule US-2 AC-1, spec 5.2): revision 1 at booking, one
 * more per change, each with the full values, the reason (null only for revision 1 at the lead's place), who and when
 * it took effect. Insert-only (survey BR-5).
 */
public record AppointmentRevision(int revisionNo, Instant validFrom, AppointmentDetails details, String reason,
                                  String actorUserId) {

    public AppointmentRevision {
        if (revisionNo < 1 || validFrom == null || details == null || actorUserId == null) {
            throw new IllegalArgumentException("A revision needs its number, time, values and actor.");
        }
    }

    @Override
    public String toString() {
        return "AppointmentRevision[" + revisionNo + "]";
    }
}
