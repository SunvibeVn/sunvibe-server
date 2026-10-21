package vn.sunvibe.property.core.domain.model.vo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
