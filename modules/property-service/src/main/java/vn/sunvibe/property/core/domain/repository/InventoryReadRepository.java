package vn.sunvibe.property.core.domain.repository;

import java.util.Collection;
import java.util.List;
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

    /** Every property of a ward, whoever owns it: by {@code ward_code}, or by normalized name on a legacy row (spec 4.10). */
    List<PropertyFacts> propertiesOfWard(String wardCode, String normalizedWardName);

    /** The property with this code when {@code ownerId} owns it. */
    Optional<PropertyFacts> ownedByCode(String ownerId, String propertyCode);

    /** A page of the owner's properties, by id; {@code q} is matched as an escaped substring of street and unit codes. */
    List<PropertyFacts> ownedPage(String ownerId, String wardCodeOrNull, String qOrNull, int offset, int limit);

    long ownedCount(String ownerId, String wardCodeOrNull, String qOrNull);

    /** An OWNER ownership row of this user, valid today, on this property (spec 4.7; SV-034). */
    boolean ownsNow(String propertyId, String userId);

    /** The next number of an intake property code (A-3). */
    long nextIntakeCodeNumber();
}
