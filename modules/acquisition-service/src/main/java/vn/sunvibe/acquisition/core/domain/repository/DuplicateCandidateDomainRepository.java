package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Domain repository of the "may be the same owner" links between leads (acquisition-owner-intake brief US-4).
 */
public interface DuplicateCandidateDomainRepository {

    /** Record that the lead may repeat each of the candidates, all detected at the same time. */
    void saveAll(OwnerLeadId leadId, List<OwnerLeadId> candidateIds, Instant detectedAt);

    /**
     * Leads that have at least one candidate row (either direction) whose other lead is visible to the viewer. A
     * viewer is given as the id of the person who sees only the leads assigned to them, or empty for a viewer who
     * sees every lead.
     */
    Set<String> leadIdsWithVisibleCandidate(Collection<String> leadIds, Optional<String> visibleToUserIdOrEmptyForAll);

    /**
     * Candidate leads of one lead, either direction, only those visible to the viewer (see
     * {@link #leadIdsWithVisibleCandidate}); oldest first, ties by id.
     */
    List<OwnerLead> findVisibleCandidates(OwnerLeadId leadId, Optional<String> visibleToUserIdOrEmptyForAll);
}
