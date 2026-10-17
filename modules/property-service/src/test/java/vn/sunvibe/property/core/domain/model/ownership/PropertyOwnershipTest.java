package vn.sunvibe.property.core.domain.model.ownership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PropertyOwnership aggregate (property-units BR-9, BR-10, spec section 5): property create writes one
 * OWNER / UNVERIFIED relation without an end date, and a relation that cannot exist is refused.
 */
class PropertyOwnershipTest {

    private static final String ULID = "^[0-9A-HJKMNP-TV-Z]{26}$";
    private static final PropertyId PROPERTY_ID = PropertyId.of("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61");
    private static final String USER_ID = "01OWNER0000000000000000000";
    /** A 36-character owner id from older data (property-units E-3): it must be accepted and kept as is. */
    private static final String LEGACY_USER_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final LocalDate VALID_FROM = LocalDate.of(2026, 3, 15);
    private static final Instant CREATED_AT = Instant.parse("2026-10-16T08:00:00Z");

    /** property-units BR-10 */
    @Test
    void createUnverifiedOwner_values_isOwnerUnverifiedWithoutEnd() {
        Instant before = Instant.now();

        PropertyOwnership ownership = PropertyOwnership.createUnverifiedOwner(PROPERTY_ID, USER_ID, VALID_FROM);

        Instant after = Instant.now();
        assertEquals(OwnershipRelationType.OWNER, ownership.getRelationType());
        assertEquals(OwnershipVerificationState.UNVERIFIED, ownership.getVerificationState());
        assertEquals(VALID_FROM, ownership.getValidFrom());
        assertNull(ownership.getValidTo());
        assertEquals(PROPERTY_ID, ownership.getPropertyId());
        assertEquals(USER_ID, ownership.getUserId());
        assertTrue(ownership.getId().getValue().matches(ULID));
        assertNotNull(ownership.getCreatedAt());
        // Brief rule: new timestamps are an Instant from AppClock.currentUTC(), not the JVM-zone local time.
        assertFalse(ownership.getCreatedAt().isBefore(before));
        assertFalse(ownership.getCreatedAt().isAfter(after));
    }

    @Test
    void createUnverifiedOwner_twice_givesDifferentIds() {
        PropertyOwnership first = PropertyOwnership.createUnverifiedOwner(PROPERTY_ID, USER_ID, VALID_FROM);
        PropertyOwnership second = PropertyOwnership.createUnverifiedOwner(PROPERTY_ID, USER_ID, VALID_FROM);

        assertNotEquals(first.getId().getValue(), second.getId().getValue());
    }

    /** property-units BR-9: the relation links a user, so an empty user id is refused. */
    @Test
    void createUnverifiedOwner_blankUserId_throws() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PropertyOwnership.createUnverifiedOwner(PROPERTY_ID, "   ", VALID_FROM)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PropertyOwnership.createUnverifiedOwner(PROPERTY_ID, "", VALID_FROM)));
    }

    /** property-units BR-9: valid_to is empty or not before valid_from. */
    @Test
    void load_validToBeforeValidFrom_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> load(VALID_FROM, VALID_FROM.minusDays(1)));
    }

    /** property-units BR-9: a relation that ends on the day it starts is valid (the end is not before the start). */
    @Test
    void load_validToSameDayAsValidFrom_isAccepted() {
        PropertyOwnership ownership = load(VALID_FROM, VALID_FROM);

        assertEquals(VALID_FROM, ownership.getValidTo());
    }

    private enum RequiredField {
        ID, PROPERTY_ID, USER_ID, RELATION_TYPE, VERIFICATION_STATE, VALID_FROM, CREATED_AT
    }

    /** Spec section 5: every field except valid_to is required. */
    @ParameterizedTest
    @EnumSource(RequiredField.class)
    void load_missingRequiredField_throws(RequiredField missing) {
        assertThrows(IllegalArgumentException.class, () -> PropertyOwnership.load(
                missing == RequiredField.ID ? null : PropertyOwnershipId.generate(),
                missing == RequiredField.PROPERTY_ID ? null : PROPERTY_ID,
                missing == RequiredField.USER_ID ? null : USER_ID,
                missing == RequiredField.RELATION_TYPE ? null : OwnershipRelationType.OWNER,
                missing == RequiredField.VERIFICATION_STATE ? null : OwnershipVerificationState.UNVERIFIED,
                missing == RequiredField.VALID_FROM ? null : VALID_FROM,
                null,
                missing == RequiredField.CREATED_AT ? null : CREATED_AT));
    }

    /** Distinct values everywhere, so a swapped or dropped field in {@code load} shows. */
    @Test
    void load_validValues_exposesEveryFieldUnchanged() {
        PropertyOwnershipId id = PropertyOwnershipId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ1");

        PropertyOwnership ownership = PropertyOwnership.load(id, PROPERTY_ID, LEGACY_USER_ID,
                OwnershipRelationType.CO_OWNER, OwnershipVerificationState.VERIFIED,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 12, 31), CREATED_AT);

        assertEquals(id, ownership.getId());
        assertEquals(PROPERTY_ID, ownership.getPropertyId());
        assertEquals(LEGACY_USER_ID, ownership.getUserId());
        assertEquals(OwnershipRelationType.CO_OWNER, ownership.getRelationType());
        assertEquals(OwnershipVerificationState.VERIFIED, ownership.getVerificationState());
        assertEquals(LocalDate.of(2026, 1, 10), ownership.getValidFrom());
        assertEquals(LocalDate.of(2026, 12, 31), ownership.getValidTo());
        assertEquals(CREATED_AT, ownership.getCreatedAt());
    }

    /**
     * property-units BR-9: these names are the values stored in property_ownerships; renaming one breaks stored rows.
     */
    @Test
    void relationAndVerificationValues_areTheStoredNames() {
        assertAll(
                () -> assertEquals(Set.of("OWNER", "CO_OWNER", "AUTHORIZED_REPRESENTATIVE"),
                        names(OwnershipRelationType.values())),
                () -> assertEquals(Set.of("UNVERIFIED", "VERIFIED", "REJECTED"),
                        names(OwnershipVerificationState.values())));
    }

    @Test
    void ownershipIdOf_nullOrBlankValue_throws() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> PropertyOwnershipId.of(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> PropertyOwnershipId.of("")),
                () -> assertThrows(IllegalArgumentException.class, () -> PropertyOwnershipId.of("   ")));
    }

    @Test
    void ownershipIdOf_sameValue_isEqualAndPrintsItsValue() {
        PropertyOwnershipId first = PropertyOwnershipId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ1");
        PropertyOwnershipId same = PropertyOwnershipId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ1");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, PropertyOwnershipId.of("01J9ZK3Q7M8N2P4R6T8V0WXYZ2"));
        assertEquals("01J9ZK3Q7M8N2P4R6T8V0WXYZ1", first.getValue());
        assertEquals("01J9ZK3Q7M8N2P4R6T8V0WXYZ1", first.toString());
    }

    private static PropertyOwnership load(LocalDate validFrom, LocalDate validTo) {
        return PropertyOwnership.load(PropertyOwnershipId.generate(), PROPERTY_ID, USER_ID,
                OwnershipRelationType.OWNER, OwnershipVerificationState.UNVERIFIED, validFrom, validTo, CREATED_AT);
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
