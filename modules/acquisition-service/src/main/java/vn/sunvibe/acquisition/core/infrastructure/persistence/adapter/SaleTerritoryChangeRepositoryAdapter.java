package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritoryChange;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryChangeDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.SaleTerritoryChangeEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.SaleTerritoryChangeJpaRepository;

import java.util.List;

/**
 * Repository Implementation - SaleTerritoryChangeRepositoryAdapter
 * <p>
 * Implements the domain SaleTerritoryChangeDomainRepository. The history is insert-only, so this adapter offers no
 * way to update or delete a change.
 */
@Service
@RequiredArgsConstructor
public class SaleTerritoryChangeRepositoryAdapter implements SaleTerritoryChangeDomainRepository {

    private final SaleTerritoryChangeJpaRepository jpaRepository;
    private final SaleTerritoryChangeEntityMapper mapper;

    @Override
    public void save(SaleTerritoryChange change) {
        if (change == null) {
            throw new IllegalArgumentException("SaleTerritoryChange cannot be null");
        }
        jpaRepository.save(mapper.toEntity(change));
    }

    @Override
    public List<SaleTerritoryChange> findByWardCodeOrderByOccurredAt(String wardCode) {
        return jpaRepository.findByWardCodeOrderByOccurredAtAscIdAsc(wardCode).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
