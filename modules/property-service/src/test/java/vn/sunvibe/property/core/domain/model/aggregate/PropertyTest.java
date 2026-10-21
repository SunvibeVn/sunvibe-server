package vn.sunvibe.property.core.domain.model.aggregate;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.exception.PropertyValidationException;
import vn.sunvibe.property.core.domain.model.enums.PropertyStatus;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.document.DocumentType;
import vn.sunvibe.property.core.domain.model.document.PropertyDocument;
import vn.sunvibe.property.core.domain.model.image.PropertyImage;
import vn.sunvibe.property.core.domain.model.price.PropertyPrice;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.domain.model.rule.RuleCategory;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property aggregate: what create and load set, which field an invalid value is reported on (the API returns that
 * field, property-create as-built), and that the related lists change only through the aggregate's methods.
 */
class PropertyTest {

    private static final PropertyId ID = PropertyId.of("3f2b8c1e-6d4a-4b7e-9a10-5c8d2e7f4a61");
    private static final PropertyCode CODE = PropertyCode.of("DA-APT-001");
    private static final Location LOCATION = Location.of("1 Bach Dang", "Thach Thang", "Hai Chau", null);
    private static final String OWNER_ID = "01OWNER0000000000000000000";
    private static final String CREATED_BY = "0900000001";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 16, 8, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 10, 17, 9, 30);

    @Test
    void create_validValues_startsAvailableWithoutVersion() {
        Property property = create("Studio", 1, 1, OWNER_ID);

        assertEquals(PropertyStatus.AVAILABLE, property.getStatus());
        assertNull(property.getVersion());
        assertEquals(CREATED_BY, property.getCreatedBy());
        assertEquals(CREATED_BY, property.getUpdatedBy());
        assertNotNull(property.getCreatedAt());
        assertNotNull(property.getId());
        assertEquals(CODE, property.getPropertyCode());
        assertEquals(Money.vnd(5_000_000), property.getMonthlyPrice());
        assertTrue(property.getImages().isEmpty());
    }

    @Test
    void load_storedValues_keepsStatusTimestampsUpdaterAndVersion() {
        Property property = load(ID, "Stored title", PropertyStatus.RENTED, 7L);

        assertEquals(ID, property.getId());
        assertEquals("Stored title", property.getTitle());
        assertEquals(PropertyStatus.RENTED, property.getStatus());
        assertEquals(CREATED_AT, property.getCreatedAt());
        assertEquals(UPDATED_AT, property.getUpdatedAt());
        assertEquals(CREATED_BY, property.getCreatedBy());
        assertEquals("0900000002", property.getUpdatedBy());
        assertEquals(7L, property.getVersion());
    }

    /** The first invalid value in constructor order is the one reported, on its API field name. */
    @Test
    void create_invalidValues_reportsTheFirstFieldInOrder() {
        assertEquals("title", fieldOf(() -> create(" ", 0, 0, " ")));
        assertEquals("bedrooms", fieldOf(() -> create("Studio", 0, 0, " ")));
        assertEquals("bathrooms", fieldOf(() -> create("Studio", 1, 0, " ")));
        assertEquals("ownerId", fieldOf(() -> create("Studio", 1, 1, " ")));
    }

    @Test
    void getImages_changedFromOutside_isRefused() {
        Property property = create("Studio", 1, 1, OWNER_ID);
        PropertyImage image = PropertyImage.create(property.getId(), "https://img/1.jpg", null, null, 1, CREATED_BY);

        assertThrows(UnsupportedOperationException.class, () -> property.getImages().add(image));
        assertThrows(UnsupportedOperationException.class, () -> property.getAppliedRules().clear());
        assertThrows(UnsupportedOperationException.class, () -> property.getAmenities().clear());
        assertThrows(UnsupportedOperationException.class, () -> property.getDocuments().clear());
        assertThrows(UnsupportedOperationException.class, () -> property.getPriceHistories().clear());
    }

    @Test
    void addImage_image_isListed() {
        Property property = create("Studio", 1, 1, OWNER_ID);
        PropertyImage image = PropertyImage.createPrimary(property.getId(), "https://img/1.jpg", null, null, CREATED_BY);

        property.addImage(image);

        assertEquals(1, property.getImages().size());
        assertEquals(image, property.getPrimaryImage());
    }

    @Test
    void applyRule_ruleOfAnotherProperty_isRefusedOnAppliedRules() {
        Property property = create("Studio", 1, 1, OWNER_ID);
        RuleCategory pets = RuleCategory.load(1, "PETS", "No pets", RuleCategory.RuleSeverity.HIGH, false, null);
        PropertyRuleApply foreign = PropertyRuleApply.createDefault(ID, pets);

        assertEquals("appliedRules", fieldOf(() -> property.applyRule(foreign)));
        assertTrue(property.getAppliedRules().isEmpty());
    }

    /** Every child of the aggregate belongs to it: one built for another property is refused on its list. */
    @Test
    void addChild_ofAnotherProperty_isRefusedOnItsList() {
        Property property = create("Studio", 1, 1, OWNER_ID);
        DocumentType deed = DocumentType.load(1, "TITLE_DEED", "Title deed", false, null, false, null);

        assertEquals("images", fieldOf(() -> property.addImage(
                PropertyImage.create(ID, "https://img/1.jpg", null, null, 1, CREATED_BY))));
        assertEquals("documents", fieldOf(() -> property.addDocument(
                PropertyDocument.create(ID, deed, "/docs/deed.pdf", null, LocalDate.of(2026, 1, 5), null, CREATED_BY))));
        assertEquals("priceHistories", fieldOf(() -> property.addPriceRecord(
                PropertyPrice.createCurrent(ID, Money.vnd(5_000_000), Money.vnd(5_000_000), LocalDate.of(2026, 10, 1)))));
        assertTrue(property.getImages().isEmpty());
        assertTrue(property.getDocuments().isEmpty());
        assertTrue(property.getPriceHistories().isEmpty());
    }

    /** property-units BR-14: the repository hands back the saved version; it never goes back and is never null. */
    @Test
    void markPersisted_savedVersion_isKeptAndNeverGoesBack() {
        Property property = create("Studio", 1, 1, OWNER_ID);

        property.markPersisted(0L);
        property.markPersisted(1L);

        assertEquals(1L, property.getVersion());
        assertThrows(IllegalArgumentException.class, () -> property.markPersisted(0L));
        assertThrows(IllegalArgumentException.class, () -> property.markPersisted(null));
        assertEquals(1L, property.getVersion());
    }

    @Test
    void equals_sameIdDifferentValues_isEqual() {
        Property first = load(ID, "First", PropertyStatus.AVAILABLE, 0L);
        Property second = load(ID, "Second", PropertyStatus.RENTED, 3L);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, create("Studio", 1, 1, OWNER_ID));
    }

    private static String fieldOf(Runnable action) {
        return assertThrows(PropertyValidationException.class, action::run).getFieldName();
    }

    private static Property create(String title, int bedrooms, int bathrooms, String ownerId) {
        return Property.create(CODE, title, "Near the beach", LOCATION, PropertyType.STUDIO, bedrooms, bathrooms,
                35.5, Money.vnd(5_000_000), Money.vnd(5_000_000), 6, ownerId, CREATED_BY);
    }

    private static Property load(PropertyId id, String title, PropertyStatus status, Long version) {
        return Property.load(id, CODE, title, "Near the beach", LOCATION, PropertyType.STUDIO, 1, 1, 35.5,
                Money.vnd(5_000_000), Money.vnd(5_000_000), 6, OWNER_ID, status, CREATED_AT, UPDATED_AT,
                CREATED_BY, "0900000002", version);
    }
}
