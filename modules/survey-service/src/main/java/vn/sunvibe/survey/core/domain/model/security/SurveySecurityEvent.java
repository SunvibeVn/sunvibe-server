package vn.sunvibe.survey.core.domain.model.security;

import java.time.Instant;

/**
 * An insert-only security event (survey-checklist BR-20, E-2, spec 4.10, A-14): an authenticated staff caller refused
 * at the checklist boundary on a resource that exists. Ids, codes and the correlation id only; never a body, content,
 * address, phone, IP or user agent.
 */
public record SurveySecurityEvent(long id, String userId, String leadId, SecurityResourceType resourceType,
                                  String resourceId, SecurityAction action, SecurityOutcome outcome,
                                  String correlationIdOrNull, Instant occurredAt, String requestIdOrNull) {

    public SurveySecurityEvent {
        if (userId == null || leadId == null || resourceType == null || resourceId == null || action == null
                || outcome == null || occurredAt == null) {
            throw new IllegalArgumentException("A security event needs its user, lead, resource, action, outcome "
                    + "and time.");
        }
    }

    /** An event without the server request id (the checklist events, survey-media spec A-19). */
    public SurveySecurityEvent(long id, String userId, String leadId, SecurityResourceType resourceType,
                               String resourceId, SecurityAction action, SecurityOutcome outcome,
                               String correlationIdOrNull, Instant occurredAt) {
        this(id, userId, leadId, resourceType, resourceId, action, outcome, correlationIdOrNull, occurredAt, null);
    }
}
