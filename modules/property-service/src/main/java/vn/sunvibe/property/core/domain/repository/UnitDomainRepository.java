package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.unit.Unit;

/**
 * Domain Repository Interface - UnitDomainRepository
 * <p>
 * Implementation lives in the Infrastructure layer. Only what W8 uses is declared.
 */
public interface UnitDomainRepository {

    /**
     * Save a unit.
     */
    void save(Unit unit);
}
