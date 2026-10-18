package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.util.Optional;
import java.util.Set;

/**
 * The filters of a lead search. An empty value means no filter on that field; filters combine with AND, and a lead
 * matches the status filter when it is in any of the statuses.
 */
public record LeadSearchCriteria(Optional<String> assignedUserId, Set<OwnerLeadStatus> statuses,
                                 Optional<String> wardCode, Optional<String> requestCode) {

    public LeadSearchCriteria {
        statuses = Set.copyOf(statuses);
    }
}
