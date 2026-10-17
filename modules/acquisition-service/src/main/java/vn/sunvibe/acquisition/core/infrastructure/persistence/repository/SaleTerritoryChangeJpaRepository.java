package vn.sunvibe.acquisition.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.SaleTerritoryChangeEntity;

import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by SaleTerritoryChangeRepositoryAdapter
 * (implementation of SaleTerritoryChangeDomainRepository).
 */
@Repository
public interface SaleTerritoryChangeJpaRepository extends JpaRepository<SaleTerritoryChangeEntity, Long> {

    // The Snowflake id grows with time, so it orders changes of the same instant
    List<SaleTerritoryChangeEntity> findByWardCodeOrderByOccurredAtAscIdAsc(String wardCode);
}
