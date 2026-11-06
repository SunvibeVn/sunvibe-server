package vn.sunvibe.property.core.domain.model.property.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Value semantics of the property value objects: equal by value, normalized when built, refused when invalid. They
 * guard the move from Lombok to plain Java (the objects have no setters and no empty constructor).
 */
class PropertyValueObjectsTest {

    @Test
    void propertyId_sameValue_isEqualAndPrintsTheValue() {
        PropertyId id = PropertyId.of("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61");

        assertEquals(PropertyId.of("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61"), id);
        assertEquals(id.hashCode(), PropertyId.of(id.getValue()).hashCode());
        assertEquals("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61", id.toString());
        assertNotEquals(PropertyId.generate(), PropertyId.generate());
        assertThrows(IllegalArgumentException.class, () -> PropertyId.of(" "));
    }

    @Test
    void propertyCode_validOrInvalidFormat_isKeptOrRefused() {
        assertEquals(PropertyCode.of("DA-APT-001"), PropertyCode.of("DA-APT-001"));
        assertEquals("DA-APT-0012", PropertyCode.of("DA-APT-0012").getValue());
        assertThrows(IllegalArgumentException.class, () -> PropertyCode.of("da-apt-001"));
        assertThrows(IllegalArgumentException.class, () -> PropertyCode.of("DA-APT-01"));
    }

    @Test
    void money_vndAmount_isRoundedToWholeDongAndEqualByValue() {
        Money money = Money.vnd(new BigDecimal("5000000.50"));

        assertEquals(new BigDecimal("5000001"), money.getAmount());
        assertEquals("VND", money.getCurrency());
        assertEquals(Money.vnd(5_000_001), money);
        assertEquals(Money.vnd(5_000_001).hashCode(), money.hashCode());
        assertNotEquals(Money.of(5_000_001, "USD"), money);
        assertThrows(IllegalArgumentException.class, () -> Money.vnd(-1));
    }

    @Test
    void location_withoutCity_defaultsToDaNangAndIsEqualByValue() {
        Location location = Location.of("1 Bach Dang", "Thach Thang", "Hai Chau", null, null, 16.07, 108.22);

        assertEquals("Đà Nẵng", location.getCity());
        assertEquals(Location.of("1 Bach Dang", "Thach Thang", "Hai Chau", null, null, 16.07, 108.22), location);
        assertNotEquals(Location.of("2 Bach Dang", "Thach Thang", "Hai Chau", null, null, 16.07, 108.22), location);
        assertTrue(location.hasCoordinates());
        assertThrows(IllegalArgumentException.class, () -> Location.of(" ", "Thach Thang", "Hai Chau", null));
    }

    /** property-create intake A-4: an intake location has no district, and the address never reads "null". */
    @Test
    void fullAddress_nullDistrict_noNullText() {
        Location location = Location.of("1 Bạch Đằng", "Hải Châu", null, "Đà Nẵng", null, null, null);

        assertEquals("1 Bạch Đằng, Hải Châu, Đà Nẵng", location.getFullAddress());
        assertEquals("1 Bach Dang, Thach Thang, Hai Chau, Đà Nẵng",
                Location.of("1 Bach Dang", "Thach Thang", "Hai Chau", null).getFullAddress());
    }

    @Test
    void location_blankDistrict_refused() {
        assertThrows(IllegalArgumentException.class, () -> Location.of("1 Bach Dang", "Thach Thang", " ", null));
    }

    /** property-create intake A-3: one prefix per asset type, six digits, and a higher number keeps every digit. */
    @Test
    void intakePropertyCode_format() {
        assertEquals("SV-HOU-000001", IntakePropertyCode.of(AssetType.HOUSE, 1).getValue());
        assertEquals("SV-BLD-000012", IntakePropertyCode.of(AssetType.WHOLE_BUILDING, 12).getValue());
        assertEquals("SV-VIL-000123", IntakePropertyCode.of(AssetType.VILLA, 123).getValue());
        assertEquals("SV-HMS-001234", IntakePropertyCode.of(AssetType.HOMESTAY, 1234).getValue());
        assertEquals("SV-CDT-012345", IntakePropertyCode.of(AssetType.CONDOTEL, 12345).getValue());
        assertEquals("SV-APT-1234567", IntakePropertyCode.of(AssetType.APARTMENT, 1234567).getValue());
    }

    /** property-create intake A-3: the legacy endpoint refuses these six prefixes, so a client cannot take one. */
    @Test
    void intakePropertyCode_reservedPrefixes() {
        for (String code : List.of("SV-HOU-001", "SV-BLD-001", "SV-VIL-001", "SV-HMS-001", "SV-CDT-001", "SV-APT-001")) {
            assertTrue(IntakePropertyCode.isReserved(code), code);
        }
        assertFalse(IntakePropertyCode.isReserved("DA-APT-001"));
        assertFalse(IntakePropertyCode.isReserved("SV-XXX-001"));
    }
}
