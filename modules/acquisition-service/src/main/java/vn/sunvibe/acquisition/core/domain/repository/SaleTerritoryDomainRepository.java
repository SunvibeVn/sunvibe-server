package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository of the ward-to-person assignments.
 */
public interface SaleTerritoryDomainRepository {

    Optional<SaleTerritory> findByWard(String wardCode);

    /** The assignments of the given wards; a ward nobody looks after has no entry. */
    List<SaleTerritory> findByWardCodes(Collection<String> wardCodes);

    /**
     * Give the ward to the person of the territory: insert the assignment, or replace the one the ward has. Two calls
     * at the same time on one ward both succeed; the last one to commit wins.
     */
    void upsert(SaleTerritory territory);

    /** Leave the ward unassigned. Does nothing when the ward has no assignment. */
    void deleteByWard(String wardCode);
}
