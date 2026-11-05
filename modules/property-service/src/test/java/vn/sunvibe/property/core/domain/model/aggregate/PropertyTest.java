package vn.sunvibe.property.core.domain.model.aggregate;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.exception.PropertyValidationException;
import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.enums.PropertyStatus;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.enums.RentalLayout;
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
    private static final Location INTAKE_LOCATION = Location.of("1 Bạch Đằng", "Hải Châu", null, "Đà Nẵng", null,
            16.07, 108.22);
    private static final String INTAKE_ID = "01INTAKE000000000000000000";
    private static final String WARD_CODE = "20242";

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

    /** property-create intake spec 5.1: the legacy endpoint keeps every check, a district included. */
    @Test
    void create_legacyChecksHold() {
        Location noDistrict = Location.of("1 Bach Dang", "Thach Thang", null, "Đà Nẵng", null, null, null);

        assertEquals("location", fieldOf(() -> Property.create(CODE, "Studio", null, noDistrict, PropertyType.STUDIO, 1,
                1, 35.5, Money.vnd(5_000_000), Money.vnd(5_000_000), 6, OWNER_ID, CREATED_BY)));
        assertEquals("bedrooms", fieldOf(() -> create("Studio", 0, 1, OWNER_ID)));
        assertEquals("monthlyPrice", fieldOf(() -> Property.create(CODE, "Studio", null, LOCATION, PropertyType.STUDIO,
                1, 1, 35.5, null, Money.vnd(5_000_000), 6, OWNER_ID, CREATED_BY)));
    }

    /** property-create intake spec 5.1, 4.8, D5-18: an intake row carries no legacy value and no fake one. */
    @Test
    void createFromIntake_validValues_writesNoLegacyValue() {
        Property property = Property.createFromIntake(intake(AssetType.HOUSE, RentalLayout.WHOLE, WARD_CODE));

        assertNull(property.getStatus());
        assertNull(property.getType());
        assertNull(property.getBedrooms());
        assertNull(property.getBathrooms());
        assertNull(property.getSquareMeters());
        assertNull(property.getMonthlyPrice());
        assertNull(property.getDepositPrice());
        assertNull(property.getMinRentMonths());
        assertNull(property.getVersion());
        assertEquals(AssetType.HOUSE, property.getAssetType());
        assertEquals(RentalLayout.WHOLE, property.getRentalLayout());
        assertEquals(INTAKE_ID, property.getIntakeId());
        assertEquals(WARD_CODE, property.getWardCode());
        assertEquals("1 bach dang", property.getAddressNorm());
        assertEquals(OWNER_ID, property.getOwnerId());
    }

    @Test
    void createFromIntake_requiresAssetLayoutWard() {
        assertThrows(IllegalArgumentException.class,
                () -> Property.createFromIntake(intake(null, RentalLayout.WHOLE, WARD_CODE)));
        assertThrows(IllegalArgumentException.class,
                () -> Property.createFromIntake(intake(AssetType.HOUSE, null, WARD_CODE)));
        assertThrows(IllegalArgumentException.class,
                () -> Property.createFromIntake(intake(AssetType.HOUSE, RentalLayout.WHOLE, null)));
        assertThrows(IllegalArgumentException.class,
                () -> Property.createFromIntake(intake(AssetType.HOUSE, RentalLayout.WHOLE, " ")));
    }

    /** property-create intake spec 3, 5.1: load checks neither factory's rules, so a stored row always reads back. */
    @Test
    void load_nullLegacyColumns_ok() {
        Property property = Property.load(ID, CODE, "Whole house, Hải Châu", null, INTAKE_LOCATION, null, null, null,
                null, null, null, null, OWNER_ID, null, CREATED_AT, UPDATED_AT, CREATED_BY, CREATED_BY, 2L,
                new Property.IntakeFields(AssetType.VILLA, RentalLayout.WHOLE, INTAKE_ID, WARD_CODE, "1 bach dang"));

        assertNull(property.getStatus());
        assertNull(property.getMonthlyPrice());
        assertEquals(AssetType.VILLA, property.getAssetType());
        assertEquals(2L, property.getVersion());
    }

    /** A legacy row from before the checks: 0 rooms were stored (property-units E-2), and load keeps them. */
    @Test
    void load_oddLegacyValues_ok() {
        Property property = Property.load(ID, CODE, "Odd", null, LOCATION, PropertyType.STUDIO, 0, 0, 30.0,
                Money.vnd(5_000_000), Money.vnd(5_000_000), 6, OWNER_ID, PropertyStatus.AVAILABLE, CREATED_AT,
                UPDATED_AT, CREATED_BY, CREATED_BY, 0L, null);

        assertEquals(0, property.getBedrooms());
        assertNull(property.getAssetType());
    }

    private static IntakeProperty intake(AssetType assetType, RentalLayout layout, String wardCode) {
        return new IntakeProperty(PropertyCode.of("SV-HOU-000001"), "Whole house, Hải Châu", INTAKE_LOCATION, assetType,
                layout, INTAKE_ID, wardCode, "1 bach dang", OWNER_ID, CREATED_BY);
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
                CREATED_BY, "0900000002", version, null);
    }
}
