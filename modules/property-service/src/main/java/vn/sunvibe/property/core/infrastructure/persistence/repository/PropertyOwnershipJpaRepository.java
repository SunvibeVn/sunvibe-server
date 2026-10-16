package vn.sunvibe.property.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyOwnershipEntity;

/**
 * Spring Data JPA Repository Interface - used by PropertyOwnershipRepositoryAdapter
 * (implementation of PropertyOwnershipDomainRepository).
 */
@Repository
public interface PropertyOwnershipJpaRepository extends JpaRepository<PropertyOwnershipEntity, String> {
}
