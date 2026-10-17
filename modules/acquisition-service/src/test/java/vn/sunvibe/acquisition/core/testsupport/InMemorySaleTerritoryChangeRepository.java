package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritoryChange;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryChangeDomainRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hand-written fake of the ward assignment changes; keeps changes in the order they were saved. */
public class InMemorySaleTerritoryChangeRepository implements SaleTerritoryChangeDomainRepository {

    private final List<SaleTerritoryChange> changes = new ArrayList<>();

    public List<SaleTerritoryChange> all() {
        return new ArrayList<>(changes);
    }

    @Override
    public void save(SaleTerritoryChange change) {
        changes.add(change);
    }

    @Override
    public List<SaleTerritoryChange> findByWardCodeOrderByOccurredAt(String wardCode) {
        return changes.stream()
                .filter(change -> change.getWardCode().equals(wardCode))
                .sorted(Comparator.comparing(SaleTerritoryChange::getOccurredAt)
                        .thenComparing(SaleTerritoryChange::getId))
                .toList();
    }
}
