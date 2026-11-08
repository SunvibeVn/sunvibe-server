package vn.sunvibe.survey.core.application.shared.external.lead;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.time.Instant;

/** The lead after a survey move: status, new version, latest activity time and the History entry written. */
public record LeadMoveResult(String statusName, long version, Instant lastActivityAt, LeadHistoryEntry entry) {

    /** The status as an enum; a name it does not know fails here, loudly. */
    public LeadStatus status() {
        return LeadStatus.valueOf(statusName);
    }
}
