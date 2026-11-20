package vn.sunvibe.property.core.domain.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scalar reads of properties and units for the intake (property-create intake spec 4.7, 4.10, 5.1). Plain values, never the
 * aggregates: an odd legacy row with an unknown code or status must not fail a whole ward, and no entity may stay managed.
 */
public interface InventoryReadRepository {

    /** One property as the intake reads it. Text columns are private: never logged. */
    record PropertyFacts(String id, String code, String title, String ownerId, String assetTypeOrNull, String layoutOrNull,
                         String wardCodeOrNull, String wardName, String street, String addressNormOrNull, Double latitude,
                         Double longitude, long version, String intakeIdOrNull) {

        @Override
        public String toString() {
            return "PropertyFacts[" + id + "]";
        }
    }

    /** One ownership row of a property, its values as stored (brokerage-agreement BR-4, E05-S1). */
    record HoldingFacts(String userId, String relationType, String verificationState) {
    }

    /** One unit as the intake reads it. */
    record UnitFacts(String id, String propertyId, String code, String locationLabelOrNull, long version,
                     String availabilityState) {

        @Override
        public String toString() {
            return "UnitFacts[" + id + "]";
        }
    }

    Optional<PropertyFacts> property(String propertyId);

    List<UnitFacts> unitsOf(String propertyId);

    List<UnitFacts> units(Collection<String> unitIds);

    /** Every property of a ward, whoever owns it: by {@code ward_code}, or by normalized name on a legacy row (property-create intake spec 4.10). */
    List<PropertyFacts> propertiesOfWard(String wardCode, String normalizedWardName);

    /** The property with this code when {@code ownerId} owns it. */
    Optional<PropertyFacts> ownedByCode(String ownerId, String propertyCode);

    /** A page of the owner's properties, by id; {@code q} is matched as an escaped substring of street and unit codes. */
    List<PropertyFacts> ownedPage(String ownerId, String wardCodeOrNull, String qOrNull, int offset, int limit);

    long ownedCount(String ownerId, String wardCodeOrNull, String qOrNull);

    /** An OWNER ownership row of this user, valid today, on this property (property-create intake spec 4.7; SV-034). */
    boolean ownsNow(String propertyId, String userId);

    /**
     * The distinct users with an OWNER ownership row valid on {@code today} on this property, sorted
     * (brokerage-agreement BR-3 (e), SV-215). A CO_OWNER or AUTHORIZED_REPRESENTATIVE row does not count.
     */
    List<String> currentOwnerIds(String propertyId, LocalDate today);

    /**
     * Every {@code property_ownerships} row of this property valid on {@code today}, any relation and verification,
     * ordered by user and relation (brokerage-agreement BR-4, E05-S1).
     */
    List<HoldingFacts> holdingsOn(String propertyId, LocalDate today);

    /** The properties of these ids that exist, by id, in one statement; an empty input reads nothing. */
    List<PropertyFacts> properties(Collection<String> propertyIds);

    /**
     * {@link #currentOwnerIds} of each property in one statement: per property that has any, its distinct users with an
     * OWNER row valid on {@code today}, sorted; a property with none is absent. An empty input reads nothing.
     */
    Map<String, List<String>> currentOwnerIdsOf(Collection<String> propertyIds, LocalDate today);

    /**
     * {@link #holdingsOn} of each property in one statement: per property that has any, its rows valid on {@code today},
     * ordered by user and relation; a property with none is absent. An empty input reads nothing.
     */
    Map<String, List<HoldingFacts>> holdingsOf(Collection<String> propertyIds, LocalDate today);

    /** The next number of an intake property code (A-3). */
    long nextIntakeCodeNumber();

    /**
     * One unit's profile (listing-publish E06-S2 spec E06S2-D12): the property's asset type, the catalog ward of the
     * property's ward code with its catalog province (all four null when the catalog lacks the code), and the unit's
     * rooms and area. Unpublished values: never logged.
     */
    record UnitProfileRow(String unitId, String assetTypeOrNull, String provinceCodeOrNull, String provinceNameOrNull,
                          String wardCodeOrNull, String wardNameOrNull, int bedrooms, int bathrooms, double areaSqm) {

        @Override
        public String toString() {
            return "UnitProfileRow[hidden]";
        }
    }

    /** The profile of each of these units that exists, by unit id, in one statement; an empty input reads nothing. */
    List<UnitProfileRow> unitProfiles(Collection<String> unitIds);
}
