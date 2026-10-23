package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/** A lead move asked by a survey command: event, wanted status, actor, the appointment it concerns and when. */
public record LeadMove(LeadMoveEvent event, LeadMoveTarget target, String actorUserId, String referenceId,
                       int referenceNo, Instant occurredAt) {
}
