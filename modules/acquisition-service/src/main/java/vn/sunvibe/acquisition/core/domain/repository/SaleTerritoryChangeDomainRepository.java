package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritoryChange;

import java.util.List;

/**
 * Domain repository of the ward assignment changes. Insert-only: a change is never updated or deleted.
 */
public interface SaleTerritoryChangeDomainRepository {

    void save(SaleTerritoryChange change);

    /** The changes of one ward, oldest first. */
    List<SaleTerritoryChange> findByWardCodeOrderByOccurredAt(String wardCode);
}
