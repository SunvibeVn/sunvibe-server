package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import vn.sunvibe.property.core.domain.model.property.Property;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.PropertyStatus;
import vn.sunvibe.property.core.domain.model.property.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.property.vo.Location;
import vn.sunvibe.property.core.domain.model.property.vo.Money;
import vn.sunvibe.property.core.domain.model.property.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.property.vo.PropertyId;

/**
 * A stored property keeps every value in its own field through the row and back (cleanup spec A-5, SV-169): each
 * parameter of {@link Property#load} gets a value no other parameter has, so two arguments swapped in the mapper or in
 * {@code load} fail here. The child collections (images, amenities, rules, documents, prices) are not {@code load}
 * parameters, so they stay empty in this trip.
 */
class PropertyEntityMapperTest {

    private final PropertyEntityMapper mapper = new PropertyEntityMapper(new PropertyImageEntityMapper(),
            new PropertyAmenityEntityMapper(), new PropertyRuleEntityMapper(), new PropertyDocumentEntityMapper(),
            new PropertyPriceEntityMapper());

    @Test
    void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
        Property stored = Property.load(PropertyId.of("property-1"), PropertyCode.of("DA-APT-001"), "title-1",
                "description-1", Location.of("street-1", "ward-1", "district-1", "city-1", "postal-1", 16.05, 108.21),
                PropertyType.TWO_BEDROOM, 3, 4, 55.5, Money.vnd(9_000_000L), Money.vnd(18_000_000L), 6, "owner-1",
                PropertyStatus.RENTED, at(1), at(2), "creator-1", "updater-1", 7L,
                new Property.IntakeFields(AssetType.VILLA, RentalLayout.PARTS, "intake-1", "20194", "addr-1"));

        Property back = mapper.toDomain(mapper.toEntity(stored));

        assertEquals("property-1", back.getId().getValue());
        assertEquals("DA-APT-001", back.getPropertyCode().getValue());
        assertEquals("title-1", back.getTitle());
        assertEquals("description-1", back.getDescription());
        assertEquals("street-1", back.getLocation().getStreet());
        assertEquals("ward-1", back.getLocation().getWard());
        assertEquals("district-1", back.getLocation().getDistrict());
        assertEquals("city-1", back.getLocation().getCity());
        assertEquals("postal-1", back.getLocation().getPostalCode());
        assertEquals(16.05, back.getLocation().getLatitude());
        assertEquals(108.21, back.getLocation().getLongitude());
        assertEquals(PropertyType.TWO_BEDROOM, back.getType());
        assertEquals(3, back.getBedrooms());
        assertEquals(4, back.getBathrooms());
        assertEquals(55.5, back.getSquareMeters());
        assertEquals(new BigDecimal("9000000"), back.getMonthlyPrice().getAmount());
        assertEquals(new BigDecimal("18000000"), back.getDepositPrice().getAmount());
        assertEquals(6, back.getMinRentMonths());
        assertEquals("owner-1", back.getOwnerId());
        assertEquals(PropertyStatus.RENTED, back.getStatus());
        assertEquals(at(1), back.getCreatedAt());
        assertEquals(at(2), back.getUpdatedAt());
        assertEquals("creator-1", back.getCreatedBy());
        assertEquals("updater-1", back.getUpdatedBy());
        assertEquals(7L, back.getVersion());
        assertEquals(AssetType.VILLA, back.getAssetType());
        assertEquals(RentalLayout.PARTS, back.getRentalLayout());
        assertEquals("intake-1", back.getIntakeId());
        assertEquals("20194", back.getWardCode());
        assertEquals("addr-1", back.getAddressNorm());
    }

    private static LocalDateTime at(int minute) {
        return LocalDateTime.of(2026, 11, 1, 8, 0).plusMinutes(minute);
    }
}
