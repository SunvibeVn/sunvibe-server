package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.image.PropertyImage;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyImageEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** A stored image comes back with its own id and upload time, not new ones, and maps back to the same row values. */
class PropertyImageEntityMapperTest {

    private static final String PROPERTY_ID = "3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61";
    private static final LocalDateTime UPLOADED_AT = LocalDateTime.of(2026, 10, 16, 8, 0);

    private final PropertyImageEntityMapper mapper = new PropertyImageEntityMapper();

    @Test
    void toDomain_storedImage_keepsIdUploadTimeAndValues() {
        PropertyImage image = mapper.toDomain(entity());

        assertEquals("img-1", image.getId());
        assertEquals(UPLOADED_AT, image.getUploadedAt());
        assertEquals(PROPERTY_ID, image.getPropertyId().getValue());
        assertEquals("https://img/1.jpg", image.getImageUrl());
        assertEquals("key-1", image.getImageKey());
        assertEquals("Front", image.getCaption());
        assertTrue(image.isPrimary());
        assertEquals(3, image.getDisplayOrder());
        assertEquals("0900000001", image.getUploadedBy());
    }

    @Test
    void toEntity_loadedImage_givesTheStoredValuesBack() {
        PropertyImageEntity again = mapper.toEntity(mapper.toDomain(entity()));

        assertEquals("img-1", again.getId());
        assertEquals(PROPERTY_ID, again.getPropertyId());
        assertEquals(UPLOADED_AT, again.getUploadedAt());
        assertEquals(Boolean.TRUE, again.getIsPrimary());
        assertEquals(3, again.getDisplayOrder());
    }

    @Test
    void load_storedValues_keepsIdAndUploadTime() {
        PropertyImage image = PropertyImage.load("img-2", PropertyId.of(PROPERTY_ID), "https://img/2.jpg", null, null, false, 0, UPLOADED_AT, "0900000001");

        assertEquals("img-2", image.getId());
        assertEquals(UPLOADED_AT, image.getUploadedAt());
        assertFalse(image.isPrimary());
    }

    @Test
    void load_blankStoredId_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> PropertyImage.load(" ", PropertyId.of(PROPERTY_ID),
                "https://img/2.jpg", null, null, false, 0, UPLOADED_AT, "0900000001"));
    }

    private static PropertyImageEntity entity() {
        return PropertyImageEntity.builder()
                .id("img-1")
                .propertyId(PROPERTY_ID)
                .imageUrl("https://img/1.jpg")
                .imageKey("key-1")
                .caption("Front")
                .isPrimary(true)
                .displayOrder(3)
                .uploadedAt(UPLOADED_AT)
                .uploadedBy("0900000001")
                .build();
    }
}
