package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.DuplicateCandidateEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.OwnerLeadEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.OwnerLeadDuplicateCandidateJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository Implementation - DuplicateCandidateRepositoryAdapter
 * <p>
 * Implements the domain DuplicateCandidateDomainRepository: one row per pair of leads. The visibility of the viewer
 * is part of the queries: a viewer who sees every lead (no user id) sees all candidates, any other viewer only the
 * leads assigned to them.
 */
@Service
@RequiredArgsConstructor
public class DuplicateCandidateRepositoryAdapter implements DuplicateCandidateDomainRepository {

    private final OwnerLeadDuplicateCandidateJpaRepository jpaRepository;
    private final DuplicateCandidateEntityMapper mapper;
    private final OwnerLeadEntityMapper leadMapper;

    @Override
    public void saveAll(OwnerLeadId leadId, List<OwnerLeadId> candidateIds, Instant detectedAt) {
        jpaRepository.saveAll(candidateIds.stream()
                .map(candidateId -> mapper.toEntity(leadId, candidateId, detectedAt))
                .toList());
    }

    @Override
    public Set<String> leadIdsWithVisibleCandidate(Collection<String> leadIds,
                                                   Optional<String> visibleToUserIdOrEmptyForAll) {
        if (leadIds.isEmpty()) {
            return Set.of();
        }
        boolean allVisible = visibleToUserIdOrEmptyForAll.isEmpty();
        String userId = visibleToUserIdOrEmptyForAll.orElse("");
        Set<String> found = new HashSet<>(jpaRepository.findLeadIdsPointingToVisibleCandidate(leadIds, allVisible, userId));
        found.addAll(jpaRepository.findLeadIdsPointedToByVisibleLead(leadIds, allVisible, userId));
        return found;
    }

    @Override
    public List<OwnerLead> findVisibleCandidates(OwnerLeadId leadId, Optional<String> visibleToUserIdOrEmptyForAll) {
        return jpaRepository.findVisibleCandidates(leadId.getValue(), visibleToUserIdOrEmptyForAll.isEmpty(),
                        visibleToUserIdOrEmptyForAll.orElse("")).stream()
                .map(leadMapper::toDomain)
                .toList();
    }
}
