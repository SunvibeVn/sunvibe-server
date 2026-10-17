package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryDomainRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written fake of the ward-to-person assignments, keyed by ward code. */
public class InMemorySaleTerritoryRepository implements SaleTerritoryDomainRepository {

    private final Map<String, SaleTerritory> territories = new HashMap<>();

    public InMemorySaleTerritoryRepository with(SaleTerritory territory) {
        territories.put(territory.getWardCode(), territory);
        return this;
    }

    public List<SaleTerritory> all() {
        return List.copyOf(territories.values());
    }

    @Override
    public Optional<SaleTerritory> findByWard(String wardCode) {
        return Optional.ofNullable(territories.get(wardCode));
    }

    @Override
    public List<SaleTerritory> findByWardCodes(Collection<String> wardCodes) {
        return wardCodes.stream()
                .map(territories::get)
                .filter(territory -> territory != null)
                .toList();
    }

    @Override
    public void upsert(SaleTerritory territory) {
        territories.put(territory.getWardCode(), territory);
    }

    @Override
    public void deleteByWard(String wardCode) {
        territories.remove(wardCode);
    }
}
