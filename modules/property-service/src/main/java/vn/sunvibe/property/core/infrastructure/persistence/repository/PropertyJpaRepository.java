package vn.sunvibe.property.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository Interface
 *
 * This is a low-level repository that communicates directly with the database.
 * Used by PropertyRepositoryAdapter (implementation of PropertyDomainRepository).
 */
@Repository
public interface PropertyJpaRepository extends JpaRepository<PropertyEntity, String> {

    /**
     * Find a property by property_code.
     */
    Optional<PropertyEntity> findByPropertyCode(String propertyCode);

    /**
     * Find all properties belonging to an owner.
     */
    List<PropertyEntity> findByOwnerId(String ownerId);

    /**
     * Find all properties with a specific status.
     */
    List<PropertyEntity> findByStatus(String status);

    /**
     * Find all available properties in a district.
     */
    List<PropertyEntity> findByDistrictAndStatus(String district, String status);

    /**
     * Find all properties within a price range.
     */
    @Query("SELECT p FROM PropertyEntity p " +
            "WHERE p.monthlyPrice >= :minPrice AND p.monthlyPrice <= :maxPrice " +
            "ORDER BY p.monthlyPrice ASC")
    List<PropertyEntity> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                          @Param("maxPrice") BigDecimal maxPrice);

    /**
     * Find properties by number of bedrooms.
     */
    List<PropertyEntity> findByBedrooms(Integer bedrooms);

    /**
     * Find properties by district.
     */
    List<PropertyEntity> findByDistrict(String district);

    /**
     * Check whether a property code already exists.
     */
    boolean existsByPropertyCode(String propertyCode);

    /**
     * Get the number of properties belonging to an owner.
     */
    long countByOwnerId(String ownerId);

    /**
     * Get the number of properties by status.
     */
    long countByStatus(String status);

    /**
     * Custom query: Find the top N newest properties.
     */
    @Query("SELECT p FROM PropertyEntity p ORDER BY p.createdAt DESC LIMIT :limit")
    List<PropertyEntity> findLatestProperties(@Param("limit") int limit);

    /**
     * Custom query: Find properties by multiple criteria.
     */
    @Query("SELECT p FROM PropertyEntity p " +
            "WHERE (:ownerId IS NULL OR p.ownerId = :ownerId) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:district IS NULL OR p.district = :district) " +
            "AND (:minPrice IS NULL OR p.monthlyPrice >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.monthlyPrice <= :maxPrice) " +
            "AND (:bedrooms IS NULL OR p.bedrooms = :bedrooms)")
    List<PropertyEntity> findByMultipleCriteria(
            @Param("ownerId") String ownerId,
            @Param("status") String status,
            @Param("district") String district,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("bedrooms") Integer bedrooms
    );

    /**
     * Custom query: Find properties available for rent.
     */
    @Query("SELECT DISTINCT p FROM PropertyEntity p " +
            "JOIN p.availability a " +
            "WHERE p.status = 'AVAILABLE' " +
            "AND a.status = 'AVAILABLE' " +
            "AND a.availableFromDate <= CURRENT_DATE")
    List<PropertyEntity> findAvailableForRent();

    /**
     * Custom query: Find currently rented properties.
     */
    @Query("SELECT p FROM PropertyEntity p " +
            "WHERE p.status = 'RENTED'")
    List<PropertyEntity> findRentedProperties();

    /**
     * Custom query: Find properties under maintenance.
     */
    @Query("SELECT p FROM PropertyEntity p " +
            "WHERE p.status = 'MAINTENANCE'")
    List<PropertyEntity> findMaintenanceProperties();
}
