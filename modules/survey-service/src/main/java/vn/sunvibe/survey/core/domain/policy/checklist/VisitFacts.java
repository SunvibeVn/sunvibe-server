package vn.sunvibe.survey.core.domain.policy.checklist;

import java.time.LocalDate;

/**
 * The visit a checklist belongs to, as provenance needs it (survey-checklist spec 4.5): who performed it, the Vietnam
 * date of the arrival and the appointment's number within the lead.
 */
public record VisitFacts(String performerUserId, LocalDate arrivalDate, int appointmentNo) {
}
