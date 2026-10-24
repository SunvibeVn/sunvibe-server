package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.LocalDate;

/**
 * One hazard of G5 (survey-checklist spec 4.6.1, BR-18): a client ULID, an optional space, an optional link to an
 * earlier hazard, what was seen, severity and status. An owner fix report carries the day the owner reported it, the
 * staff member who heard it and the relay basis (spec A-7, A-18). Texts: never printed.
 */
public record Hazard(String hazardId, String spaceId, HazardLink previous, String description,
                     HazardSeverity severity, HazardStatus status, String statusNote, LocalDate ownerReportedOn,
                     String receivedByUserId, String relayBasis) {

    @Override
    public String toString() {
        return "Hazard[" + hazardId + ", " + severity + ", " + status + "]";
    }
}
