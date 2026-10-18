package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.util.Set;

/**
 * Query - LeadListQuery
 * <p>
 * One page of the lead queue: the scope, the statuses to keep (empty keeps all), an optional ward, an optional request
 * code (already in its canonical form), an optional assignee (counts only with scope ALL,
 * acquisition-owner-lead-queue-ui C-27) and the zero-based page of {@code size} leads.
 */
public record LeadListQuery(LeadScope scope, Set<OwnerLeadStatus> statuses, String wardCodeOrNull,
                            String requestCodeOrNull, String assigneeUserIdOrNull, int page, int size) {

    public LeadListQuery {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required.");
        }
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be 0 or more and size 1 or more.");
        }
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }
}
