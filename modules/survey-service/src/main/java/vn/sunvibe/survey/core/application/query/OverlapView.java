package vn.sunvibe.survey.core.application.query;

import java.time.Instant;

/**
 * Another scheduled appointment of the same surveyor whose time overlaps (survey-schedule US-1 AC-4, spec A-10): only
 * the lead's request code and the time, never a place or a contact.
 */
public record OverlapView(String appointmentId, String leadId, String requestCode, Instant startAt,
                          int durationMinutes) {
}
