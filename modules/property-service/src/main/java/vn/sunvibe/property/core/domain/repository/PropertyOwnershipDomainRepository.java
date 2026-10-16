package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnership;

/**
 * Domain Repository Interface - PropertyOwnershipDomainRepository
 * <p>
 * Implementation lives in the Infrastructure layer. Only what W8 uses is declared.
 */
public interface PropertyOwnershipDomainRepository {

    /**
     * Save an ownership.
     */
    void save(PropertyOwnership ownership);
}
