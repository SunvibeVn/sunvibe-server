package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.amenity.PropertyAmenityApply;
import vn.sunvibe.property.core.domain.model.document.PropertyDocument;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyAmenityEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyDocumentEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyRuleEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The master id columns of property_rules, property_amenity and property_document are nullable and have no foreign
 * key (V202610150221__create_property_tables.sql), so a stored row may have none. Such a row still loads, with its
 * stored names, and maps back to the same values: a property must not become unreadable because of it.
 */
class StoredChildWithoutMasterIdTest {

    private static final String PROPERTY_ID = "3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 16, 8, 0);

    @Test
    void rule_withoutCategoryId_loadsAndMapsBack() {
        PropertyRuleEntity row = PropertyRuleEntity.builder().id("rule-1").propertyId(PROPERTY_ID)
                .ruleCategoryId(null).ruleName("PETS").category("PETS").description("Pet policy")
                .severity("HIGH").isMandatory(true).createdAt(AT).build();
        PropertyRuleEntityMapper mapper = new PropertyRuleEntityMapper();

        PropertyRuleApply rule = mapper.toDomain(row);

        assertNull(rule.getRuleCategoryId());
        assertEquals("PETS", rule.getRuleCategory().getName());
        PropertyRuleEntity again = mapper.toEntity(rule);
        assertNull(again.getRuleCategoryId());
        assertEquals("HIGH", again.getSeverity());
    }

    @Test
    void amenity_withoutTypeId_loadsAndMapsBack() {
        PropertyAmenityEntity row = PropertyAmenityEntity.builder().id("amenity-1").propertyId(PROPERTY_ID)
                .amenityTypeId(null).amenityName("WiFi").category("General Amenities").isIncluded(true)
                .levelOfNeed("STANDARD").createdAt(AT).build();
        PropertyAmenityEntityMapper mapper = new PropertyAmenityEntityMapper();

        PropertyAmenityApply amenity = mapper.toDomain(row);

        assertNull(amenity.getAmenityTypeId());
        assertEquals("WiFi", mapper.toEntity(amenity).getAmenityName());
    }

    @Test
    void document_withoutTypeId_loadsAndMapsBack() {
        PropertyDocumentEntity row = PropertyDocumentEntity.builder().id("doc-1").propertyId(PROPERTY_ID)
                .documentTypeId(null).documentName("Deed").documentType("Deed").filePath("/docs/deed.pdf")
                .issueDate(LocalDate.of(2026, 1, 5)).uploadedAt(AT).uploadedBy("0900000001").build();
        PropertyDocumentEntityMapper mapper = new PropertyDocumentEntityMapper();

        PropertyDocument document = mapper.toDomain(row);

        assertNull(document.getDocumentTypeId());
        assertEquals("Deed", mapper.toEntity(document).getDocumentType());
    }
}
