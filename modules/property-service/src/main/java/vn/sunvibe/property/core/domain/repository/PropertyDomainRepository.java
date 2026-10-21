package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.util.Optional;

/**
 * Domain Repository Interface - PropertyDomainRepository
 *
 * This interface defines the persistence behaviors required by the domain.
 * Implementation lives in the Infrastructure layer.
 *
 * Repository is one of the Domain Driven Design patterns.
 * It hides persistence details from the domain layer. Only what the use cases call is declared: a list or search
 * read gets its own query model when a feature needs one (each loaded property reads five child collections).
 */
public interface PropertyDomainRepository {

    /**
     * Insert a new property (version null) or update a loaded one. A property whose version is no longer the stored
     * one is refused with an optimistic locking failure. On success the property carries the saved version
     * ({@link Property#markPersisted}): 0 after an insert, one more after an update. A property code another property
     * already has is refused with {@link vn.sunvibe.property.core.domain.exception.PropertyAlreadyExistsException},
     * also when two creates race past {@link #existsByCode}.
     */
    void save(Property property);

    /**
     * Find a property by ID.
     */
    Optional<Property> findById(PropertyId id);

    /**
     * Check whether a property code already exists.
     */
    boolean existsByCode(PropertyCode code);
}
