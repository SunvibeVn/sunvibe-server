package vn.sunvibe.property.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyEntity;

/**
 * Spring Data JPA Repository Interface
 *
 * This is a low-level repository that communicates directly with the database.
 * Used by PropertyRepositoryAdapter (implementation of PropertyDomainRepository).
 */
@Repository
public interface PropertyJpaRepository extends JpaRepository<PropertyEntity, String> {

    /**
     * Check whether a property code already exists.
     */
    boolean existsByPropertyCode(String propertyCode);
}
