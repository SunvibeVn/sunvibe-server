package vn.sunvibe.property.core.domain.model.amenity;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Amenity master data as the seed stores it (R__property_master_data.sql): top-level types (ids 1-4) have no parent
 * category, the others name theirs. A property's amenity keeps a reference with the names its row stores.
 */
class AmenityTypeTest {

    private static final PropertyId PROPERTY_ID = PropertyId.of("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61");

    @Test
    void load_topLevelTypeWithoutCategory_isAccepted() {
        AmenityType general = AmenityType.load(1, "General Amenities", null, null, null, true, "STANDARD", null, null);

        assertNull(general.getCategoryId());
        assertNull(general.getCategoryName());
    }

    @Test
    void load_typeWithCategory_keepsItsParent() {
        AmenityType wifi = wifi();

        assertEquals(1, wifi.getCategoryId());
        assertEquals("General Amenities", wifi.getCategoryName());
    }

    @Test
    void load_blankNameOrLevel_isRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> AmenityType.load(10, " ", 1, "General Amenities", null, true, "STANDARD", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> AmenityType.load(10, "WiFi", 1, "General Amenities", null, true, " ", null, null));
    }

    @Test
    void included_wifi_referencesTheTypeWithItsNamesAndLevel() {
        PropertyAmenityApply apply = PropertyAmenityApply.included(PROPERTY_ID, wifi());

        assertEquals(10, apply.getAmenityTypeId());
        assertEquals("WiFi", apply.getAmenityType().getName());
        assertEquals("General Amenities", apply.getAmenityType().getCategoryName());
        assertEquals("STANDARD", apply.getLevelOfNeed());
        assertTrue(apply.isIncluded());
    }

    private static AmenityType wifi() {
        return AmenityType.load(10, "WiFi", 1, "General Amenities", "Wireless internet connection", true, "STANDARD",
                null, "📶");
    }
}
