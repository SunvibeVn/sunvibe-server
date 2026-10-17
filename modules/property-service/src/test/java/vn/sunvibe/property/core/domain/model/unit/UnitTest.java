package vn.sunvibe.property.core.domain.model.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit aggregate (property-units BR-2..BR-8, spec A-6, A-7): the canonical unit copies its property
 * and starts AVAILABLE, and a unit that can never exist (negative rooms, no area, no code) is refused.
 */
class UnitTest {

    private static final String ULID = "^[0-9A-HJKMNP-TV-Z]{26}$";
    private static final PropertyId PROPERTY_ID = PropertyId.of("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61");
    private static final Instant CREATED_AT = Instant.parse("2026-10-16T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-17T09:30:00Z");

    /** property-units BR-4, BR-5, BR-8 */
    @Test
    void createCanonical_property_copiesCodeTypeRoomsAndAreaAndStartsAvailable() {
        Property property = property(PropertyType.TWO_BEDROOM, 2, 1, 55.5);

        Unit unit = Unit.createCanonical(property);

        assertEquals("DA-APT-001", unit.getUnitCode());
        assertEquals(UnitType.TWO_BEDROOM, unit.getUnitType());
        assertEquals(2, unit.getBedrooms());
        assertEquals(1, unit.getBathrooms());
        assertEquals(55.5, unit.getAreaSqm());
        assertEquals(UnitAvailabilityState.AVAILABLE, unit.getAvailabilityState());
        assertEquals(property.getId(), unit.getPropertyId());
        assertNull(unit.getVersion());
        assertEquals(unit.getCreatedAt(), unit.getUpdatedAt());
        assertTrue(unit.getId().getValue().matches(ULID));
    }

    /** Brief rule: new timestamps are an Instant from AppClock.currentUTC(), not the JVM-zone local time. */
    @Test
    void createCanonical_property_stampsCreatedAtWithTheCurrentInstant() {
        Property property = property(PropertyType.STUDIO, 1, 1, 30.0);
        Instant before = Instant.now();

        Unit unit = Unit.createCanonical(property);

        Instant after = Instant.now();
        assertFalse(unit.getCreatedAt().isBefore(before));
        assertFalse(unit.getCreatedAt().isAfter(after));
    }

    @Test
    void createCanonical_nullProperty_throws() {
        assertThrows(IllegalArgumentException.class, () -> Unit.createCanonical(null));
    }

    @Test
    void createCanonical_twice_givesDifferentIds() {
        Property property = property(PropertyType.STUDIO, 1, 1, 30.0);

        Unit first = Unit.createCanonical(property);
        Unit second = Unit.createCanonical(property);

        assertNotEquals(first.getId().getValue(), second.getId().getValue());
    }

    /** Spec A-7: a legacy property with 0 bedrooms and 0 bathrooms must load as a unit. */
    @Test
    void load_zeroBedroomsAndBathrooms_isAccepted() {
        Unit unit = unit(0, 0, 20.0, "DA-APT-001");

        assertEquals(0, unit.getBedrooms());
        assertEquals(0, unit.getBathrooms());
    }

    /** Spec A-7 */
    @Test
    void load_negativeBedrooms_throws() {
        assertThrows(IllegalArgumentException.class, () -> unit(-1, 1, 30.0, "DA-APT-001"));
    }

    /** Spec A-7 */
    @Test
    void load_negativeBathrooms_throws() {
        assertThrows(IllegalArgumentException.class, () -> unit(1, -1, 30.0, "DA-APT-001"));
    }

    /** Spec A-7 */
    @Test
    void load_zeroArea_throws() {
        assertThrows(IllegalArgumentException.class, () -> unit(1, 1, 0.0, "DA-APT-001"));
    }

    /** Spec A-7: the area must be greater than zero, so a negative or non-numeric value fails as well. */
    @Test
    void load_negativeOrNotANumberArea_throws() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> unit(1, 1, -0.5, "DA-APT-001")),
                () -> assertThrows(IllegalArgumentException.class, () -> unit(1, 1, Double.NaN, "DA-APT-001")));
    }

    /** property-units BR-4, spec A-7 */
    @Test
    void load_blankUnitCode_throws() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> unit(1, 1, 30.0, "  ")),
                () -> assertThrows(IllegalArgumentException.class, () -> unit(1, 1, 30.0, "")));
    }

    private enum RequiredField {
        ID, PROPERTY_ID, UNIT_CODE, UNIT_TYPE, AVAILABILITY_STATE, CREATED_AT, UPDATED_AT
    }

    /** Spec A-7: every reference field is required (only {@code version} may be null, for a new unit). */
    @ParameterizedTest
    @EnumSource(RequiredField.class)
    void load_missingRequiredField_throws(RequiredField missing) {
        assertThrows(IllegalArgumentException.class, () -> Unit.load(
                missing == RequiredField.ID ? null : UnitId.generate(),
                missing == RequiredField.PROPERTY_ID ? null : PROPERTY_ID,
                missing == RequiredField.UNIT_CODE ? null : "DA-APT-001",
                missing == RequiredField.UNIT_TYPE ? null : UnitType.STUDIO,
                1, 1, 30.0,
                missing == RequiredField.AVAILABILITY_STATE ? null : UnitAvailabilityState.AVAILABLE,
                missing == RequiredField.CREATED_AT ? null : CREATED_AT,
                missing == RequiredField.UPDATED_AT ? null : UPDATED_AT,
                null));
    }

    /** Distinct values everywhere, so a swapped or dropped field in {@code load} shows. */
    @Test
    void load_validValues_exposesEveryFieldUnchanged() {
        UnitId id = UnitId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ1");

        Unit unit = Unit.load(id, PROPERTY_ID, "DA-APT-007", UnitType.PENTHOUSE, 3, 2, 120.25,
                UnitAvailabilityState.OCCUPIED, CREATED_AT, UPDATED_AT, 4L);

        assertEquals(id, unit.getId());
        assertEquals(PROPERTY_ID, unit.getPropertyId());
        assertEquals("DA-APT-007", unit.getUnitCode());
        assertEquals(UnitType.PENTHOUSE, unit.getUnitType());
        assertEquals(3, unit.getBedrooms());
        assertEquals(2, unit.getBathrooms());
        assertEquals(120.25, unit.getAreaSqm());
        assertEquals(UnitAvailabilityState.OCCUPIED, unit.getAvailabilityState());
        assertEquals(CREATED_AT, unit.getCreatedAt());
        assertEquals(UPDATED_AT, unit.getUpdatedAt());
        assertEquals(4L, unit.getVersion());
    }

    /** Spec A-6: the unit type list is mapped from the property type by name, so the two lists cannot drift. */
    @Test
    void from_everyPropertyType_mapsToTheSameName() {
        for (PropertyType type : PropertyType.values()) {
            assertEquals(type.name(), UnitType.from(type).name());
        }
        assertEquals(7, UnitType.values().length);
    }

    @Test
    void from_nullPropertyType_throws() {
        assertThrows(IllegalArgumentException.class, () -> UnitType.from(null));
    }

    /**
     * property-units BR-6: these names are the values stored in units.availability_state; renaming one breaks stored
     * rows.
     */
    @Test
    void availabilityState_values_areTheStoredNames() {
        Set<String> names = Arrays.stream(UnitAvailabilityState.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(Set.of("AVAILABLE", "OCCUPIED", "MAINTENANCE", "BLOCKED"), names);
    }

    @Test
    void unitIdOf_nullOrBlankValue_throws() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> UnitId.of(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> UnitId.of("")),
                () -> assertThrows(IllegalArgumentException.class, () -> UnitId.of("   ")));
    }

    @Test
    void unitIdOf_sameValue_isEqualAndPrintsItsValue() {
        UnitId first = UnitId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ1");
        UnitId same = UnitId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ1");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, UnitId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ2"));
        assertEquals("01J9ZK3Q7M8N2P4R6T8V0WXYZ1", first.getValue());
        assertEquals("01J9ZK3Q7M8N2P4R6T8V0WXYZ1", first.toString());
    }

    private static Unit unit(int bedrooms, int bathrooms, double areaSqm, String unitCode) {
        return Unit.load(UnitId.generate(), PROPERTY_ID, unitCode, UnitType.STUDIO, bedrooms, bathrooms, areaSqm,
                UnitAvailabilityState.AVAILABLE, CREATED_AT, UPDATED_AT, 0L);
    }

    private static Property property(PropertyType type, int bedrooms, int bathrooms, double areaSqm) {
        return Property.create(PropertyCode.of("DA-APT-001"), "Sea view flat", "Close to the beach",
                Location.of("1 Bach Dang", "Thach Thang", "Hai Chau", null),
                type, bedrooms, bathrooms, areaSqm,
                Money.vnd(5_000_000), Money.vnd(5_000_000), 6,
                "01OWNER0000000000000000000", "0912345678");
    }
}
