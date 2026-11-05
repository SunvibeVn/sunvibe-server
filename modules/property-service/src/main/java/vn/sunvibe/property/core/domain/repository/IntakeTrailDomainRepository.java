package vn.sunvibe.property.core.domain.repository;

import java.time.Instant;
import java.util.Set;

/**
 * The insert-only trail of an intake (property-create intake I-4, spec 4.12): the assignee commands, which make up the actor
 * set of D5-7 and D5-15, and the access log. Neither holds a phone, email, code, phrase, password or address.
 */
public interface IntakeTrailDomainRepository {

    void recordEvent(String intakeId, String actorUserIdOrNull, String action, Instant occurredAt);

    void recordAccess(String userId, String leadId, String intakeIdOrNull, String caseIdOrNull, String action,
                      Instant accessedAt);

    /** Everyone who ran an assignee command on any intake of the lead. */
    Set<String> actorsOfLead(String leadId);
}
