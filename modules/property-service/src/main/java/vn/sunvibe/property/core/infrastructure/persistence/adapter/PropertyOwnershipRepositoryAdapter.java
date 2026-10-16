package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnership;
import vn.sunvibe.property.core.domain.repository.PropertyOwnershipDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.PropertyOwnershipEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.PropertyOwnershipJpaRepository;

/**
 * Repository Implementation - PropertyOwnershipRepositoryAdapter
 * <p>
 * Implements the domain PropertyOwnershipDomainRepository: maps the PropertyOwnership domain model to
 * PropertyOwnershipEntity and saves it. Only save is needed in W8.
 */
@Service
@RequiredArgsConstructor
public class PropertyOwnershipRepositoryAdapter implements PropertyOwnershipDomainRepository {

    private final PropertyOwnershipJpaRepository jpaRepository;
    private final PropertyOwnershipEntityMapper mapper;

    @Override
    public void save(PropertyOwnership ownership) {
        if (ownership == null) {
            throw new IllegalArgumentException("PropertyOwnership cannot be null");
        }
        var entity = mapper.toEntity(ownership);
        jpaRepository.save(entity);
    }
}
