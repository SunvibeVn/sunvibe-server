package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.log.OwnerLeadAccessLog;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadAccessLogDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.OwnerLeadAccessLogEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.OwnerLeadAccessLogJpaRepository;

import java.util.List;

/**
 * Repository Implementation - OwnerLeadAccessLogRepositoryAdapter
 * <p>
 * Implements the domain OwnerLeadAccessLogDomainRepository. The log is insert-only, so this adapter offers no way to
 * update or delete an entry.
 */
@Service
@RequiredArgsConstructor
public class OwnerLeadAccessLogRepositoryAdapter implements OwnerLeadAccessLogDomainRepository {

    private final OwnerLeadAccessLogJpaRepository jpaRepository;
    private final OwnerLeadAccessLogEntityMapper mapper;

    @Override
    public void save(OwnerLeadAccessLog log) {
        if (log == null) {
            throw new IllegalArgumentException("OwnerLeadAccessLog cannot be null");
        }
        jpaRepository.save(mapper.toEntity(log));
    }

    @Override
    public List<OwnerLeadAccessLog> findByLeadId(OwnerLeadId id) {
        return jpaRepository.findByLeadIdOrderByAccessedAtAscIdAsc(id.getValue()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
