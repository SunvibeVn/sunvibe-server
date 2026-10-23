package vn.sunvibe.survey.core.application.query;

import java.time.Instant;

/** The lead after a survey command: status, version and latest activity time (survey-schedule spec 7.2, A-23). */
public record LeadState(String id, String status, long version, Instant lastActivityAt) {
}
