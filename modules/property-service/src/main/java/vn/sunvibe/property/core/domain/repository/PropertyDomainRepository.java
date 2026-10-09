package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.util.List;
import java.util.Optional;

/**
 * Domain Repository Interface - PropertyDomainRepository
 *
 * This interface defines the persistence behaviors required by the domain.
 * Implementation lives in the Infrastructure layer.
 *
 * Repository is one of the Domain Driven Design patterns.
 * It hides persistence details from the domain layer.
 */
public interface PropertyDomainRepository {

    /**
     * Save a property (insert or update).
     */
    void save(Property property);

    /**
     * Save multiple properties at once.
     */
    void saveAll(List<Property> properties);

    /**
     * Find a property by ID.
     */
    Optional<Property> findById(PropertyId id);

    /**
     * Find a property by code (unique).
     */
    Optional<Property> findByCode(PropertyCode code);

    /**
     * Find all properties belonging to an owner.
     */
    List<Property> findByOwnerId(String ownerId);

    /**
     * Find all properties with a specific status.
     */
    List<Property> findByStatus(String status);

    /**
     * Find all available properties in a district.
     */
    List<Property> findAvailableByDistrict(String district);

    /**
     * Find all properties within a price range.
     */
    List<Property> findByPriceRange(long minPrice, long maxPrice);

    /**
     * Find all properties by number of bedrooms.
     */
    List<Property> findByBedrooms(Integer bedrooms);

    /**
     * Delete a property by ID.
     */
    void deleteById(PropertyId id);

    /**
     * Check whether a property exists.
     */
    boolean existsById(PropertyId id);

    /**
     * Check whether a property code already exists.
     */
    boolean existsByCode(PropertyCode code);

    /**
     * Get all properties.
     */
    List<Property> findAll();

    /**
     * Get the total number of properties.
     */
    long count();

    /**
     * Get the number of properties by owner.
     */
    long countByOwnerId(String ownerId);

    /**
     * Get the number of properties by status.
     */
    long countByStatus(String status);
}