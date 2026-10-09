package vn.sunvibe.property.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.AmenityTypeEntity;

@Repository
public interface AmenityTypeJpaRepository extends JpaRepository<AmenityTypeEntity, Integer> {
}