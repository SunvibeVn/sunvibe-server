package vn.sunvibe.property.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.UnitEntity;

/**
 * Spring Data JPA Repository Interface - used by UnitRepositoryAdapter (implementation of UnitDomainRepository).
 */
@Repository
public interface UnitJpaRepository extends JpaRepository<UnitEntity, String> {
}
