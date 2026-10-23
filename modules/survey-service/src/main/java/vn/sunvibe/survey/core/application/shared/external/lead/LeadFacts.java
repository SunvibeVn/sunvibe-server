package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/**
 * What survey needs to know about a visible lead (survey-schedule spec 5.4): status and intent (enum names), the
 * current intent cycle, the assignee (null for a NEW lead), the version a command must name, the latest activity
 * time, the request code and owner name, the lead's place, when it was created and, for a held lead, when it last
 * went on hold. Owner name and place are private: never logged.
 */
public record LeadFacts(String leadId, String requestCode, String status, String intent, int intentCycle,
                        String assignedUserId, long version, Instant lastActivityAt, String contactName,
                        LeadPlace place, Instant createdAt, Instant onHoldSince) {

    /** The same lead after a survey move: its new status, version and latest activity time. */
    public LeadFacts moved(String newStatus, long newVersion, Instant newLastActivityAt) {
        return new LeadFacts(leadId, requestCode, newStatus, intent, intentCycle, assignedUserId, newVersion,
                newLastActivityAt, contactName, place, createdAt, onHoldSince);
    }

    @Override
    public String toString() {
        return "LeadFacts[" + leadId + "]";
    }
}
