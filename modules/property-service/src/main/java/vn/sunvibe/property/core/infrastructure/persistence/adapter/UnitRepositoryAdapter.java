package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.property.core.domain.repository.UnitDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.UnitEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.UnitJpaRepository;

/**
 * Repository Implementation - UnitRepositoryAdapter
 * <p>
 * Implements the domain UnitDomainRepository: maps the Unit domain model to UnitEntity and saves it.
 * Only save is needed so far.
 */
@Service
@RequiredArgsConstructor
public class UnitRepositoryAdapter implements UnitDomainRepository {

    private final UnitJpaRepository jpaRepository;
    private final UnitEntityMapper mapper;

    @Override
    public void save(Unit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Unit cannot be null");
        }
        var entity = mapper.toEntity(unit);
        jpaRepository.save(entity);
    }
}
