package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.OwnerLeadActivityEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.OwnerLeadActivityJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository Implementation - OwnerLeadActivityRepositoryAdapter
 * <p>
 * Implements the domain OwnerLeadActivityDomainRepository. The history is insert-only, so this adapter offers no way
 * to update or delete an activity.
 */
@Service
@RequiredArgsConstructor
public class OwnerLeadActivityRepositoryAdapter implements OwnerLeadActivityDomainRepository {

    private final OwnerLeadActivityJpaRepository jpaRepository;
    private final OwnerLeadActivityEntityMapper mapper;

    @Override
    public void save(OwnerLeadActivity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("OwnerLeadActivity cannot be null");
        }
        jpaRepository.save(mapper.toEntity(activity));
    }

    @Override
    public List<OwnerLeadActivity> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId) {
        return jpaRepository.findByLeadIdOrderByOccurredAtAscIdAsc(leadId.getValue()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Map<String, Instant> findLatestOccurredAt(Collection<String> leadIds) {
        if (leadIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findLatestOccurredAtPerLead(leadIds).stream()
                .collect(Collectors.toMap(OwnerLeadActivityJpaRepository.LatestActivity::getLeadId,
                        OwnerLeadActivityJpaRepository.LatestActivity::getLatest));
    }
}
