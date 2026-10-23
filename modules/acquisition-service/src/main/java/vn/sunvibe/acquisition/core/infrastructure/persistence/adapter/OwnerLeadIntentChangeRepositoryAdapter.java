package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntentChange;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadIntentChangeDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.OwnerLeadIntentChangeEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.OwnerLeadIntentChangeJpaRepository;

import java.util.List;

/**
 * Repository Implementation - OwnerLeadIntentChangeRepositoryAdapter
 * <p>
 * Implements the domain OwnerLeadIntentChangeDomainRepository. The requirement history is insert-only, so this
 * adapter offers no way to update or delete a change.
 */
@Service
@RequiredArgsConstructor
public class OwnerLeadIntentChangeRepositoryAdapter implements OwnerLeadIntentChangeDomainRepository {

    private final OwnerLeadIntentChangeJpaRepository jpaRepository;
    private final OwnerLeadIntentChangeEntityMapper mapper;

    @Override
    public void save(OwnerLeadIntentChange change) {
        if (change == null) {
            throw new IllegalArgumentException("OwnerLeadIntentChange cannot be null");
        }
        jpaRepository.save(mapper.toEntity(change));
    }

    @Override
    public List<OwnerLeadIntentChange> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId) {
        return jpaRepository.findByLeadIdOrderByOccurredAtAscIdAsc(leadId.getValue()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
