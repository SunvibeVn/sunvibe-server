package vn.sunvibe.survey.core.domain.model.checklist.unit;

import java.time.LocalDate;

/**
 * The owner fix report of a hazard (survey-checklist spec 4.6.1, A-19): the day the owner reported it, who heard it,
 * how it reached the person entering it, and the report's note. Texts: never printed.
 */
public record OwnerReport(LocalDate reportedOn, String receivedByUserId, String relayBasis, String statusNote) {

    @Override
    public String toString() {
        return "OwnerReport[" + reportedOn + "]";
    }
}
