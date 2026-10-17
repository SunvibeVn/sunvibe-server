package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.SaleTerritoryEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.SaleTerritoryJpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository Implementation - SaleTerritoryRepositoryAdapter
 * <p>
 * Implements the domain SaleTerritoryDomainRepository. A territory is written with one upsert statement, so two
 * assignments of the same ward at the same time never fail on the primary key.
 */
@Service
@RequiredArgsConstructor
public class SaleTerritoryRepositoryAdapter implements SaleTerritoryDomainRepository {

    private final SaleTerritoryJpaRepository jpaRepository;
    private final SaleTerritoryEntityMapper mapper;

    @Override
    public Optional<SaleTerritory> findByWard(String wardCode) {
        return jpaRepository.findById(wardCode).map(mapper::toDomain);
    }

    @Override
    public List<SaleTerritory> findByWardCodes(Collection<String> wardCodes) {
        return jpaRepository.findAllById(wardCodes).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void upsert(SaleTerritory territory) {
        if (territory == null) {
            throw new IllegalArgumentException("SaleTerritory cannot be null");
        }
        jpaRepository.upsert(territory.getWardCode(), territory.getUserId(), territory.getAssignedBy(),
                territory.getAssignedAt());
    }

    @Override
    public void deleteByWard(String wardCode) {
        // deleteById does nothing for a ward without a row
        jpaRepository.deleteById(wardCode);
    }
}
