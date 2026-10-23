package vn.sunvibe.survey.core.domain.model.appointment;

import java.time.Instant;

/**
 * The visit of an appointment (survey-schedule US-3 AC-1, AC-1a, spec 5.2): own or proxy record, who reached the
 * property and when, the revision in force then, the note, who entered it and when the server stored it, and for a
 * proxy record its reason and basis. Staff text: never logged.
 */
public record VisitRecord(VisitRecordKind kind, String performedBy, Instant arrivedAt, int revisionNo, String note,
                          String recordedBy, Instant recordedAt, String proxyReason, Basis basis) {

    public VisitRecord {
        if (kind == null || performedBy == null || arrivedAt == null || note == null || recordedBy == null
                || recordedAt == null) {
            throw new IllegalArgumentException("A visit needs kind, performer, arrival, note, recorder and time.");
        }
        if (kind == VisitRecordKind.OWN && (!performedBy.equals(recordedBy) || proxyReason != null || basis != null)) {
            throw new IllegalArgumentException("An own record is entered by the performer, without reason or basis.");
        }
        if (kind == VisitRecordKind.PROXY && (proxyReason == null || basis == null)) {
            throw new IllegalArgumentException("A proxy record has a reason and a basis.");
        }
    }

    @Override
    public String toString() {
        return "VisitRecord[" + kind + "]";
    }
}
