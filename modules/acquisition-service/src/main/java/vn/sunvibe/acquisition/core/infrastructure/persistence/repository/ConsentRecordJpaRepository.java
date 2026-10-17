package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.ConsentRecordEntity;

/**
 * Spring Data JPA Repository Interface - used by ConsentRecordRepositoryAdapter
 * (implementation of ConsentRecordDomainRepository).
 */
@Repository
public interface ConsentRecordJpaRepository extends JpaRepository<ConsentRecordEntity, String> {
}
