package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;

/**
 * A stored owner lead keeps every value in its own field through the row and back (cleanup spec A-5, SV-169): each
 * parameter of {@link OwnerLead#load} gets a value no other parameter has, so two arguments swapped in the mapper or
 * in {@code load} fail here.
 */
class OwnerLeadEntityMapperTest {

    private static final BigDecimal LATITUDE = new BigDecimal("16.054321");
    private static final BigDecimal LONGITUDE = new BigDecimal("108.212345");

    private final OwnerLeadEntityMapper mapper = new OwnerLeadEntityMapper();

    @Test
    void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
        OwnerLead stored = OwnerLead.load(OwnerLeadId.of("lead-1"), RequestCode.of("SV-7K3M-Q9TX"),
                OwnerLeadStatus.SURVEY_SCHEDULED, OwnerLeadIntent.LONG_TERM_RENT, OwnerLeadSource.REFERRAL,
                "name-1", PhoneNumber.parse("0909123456"), "mail-1@example.com", "48", "province-1", "20194",
                "ward-1", "address-1", "norm-1", GeoPoint.of(LATITUDE, LONGITUDE, false),
                OwnerLeadPropertyType.VILLA,
                Optional.of(AskingPrice.of(12_500_000L, AskingPrice.CURRENCY, PriceUnit.MONTH,
                        OwnerLeadIntent.LONG_TERM_RENT)),
                LocalDate.of(2026, 12, 1), "notes-1", "callback-1", ConsentRecordId.of("consent-1"),
                "{\"snapshot\":1}", "assignee-1", at(1), OwnerLeadId.of("lead-2"), "close-1", at(2), at(3), 4L, 5);

        OwnerLead back = mapper.toDomain(mapper.toEntity(stored));

        assertEquals("lead-1", back.getId().getValue());
        assertEquals("SV-7K3M-Q9TX", back.getRequestCode().getValue());
        assertEquals(OwnerLeadStatus.SURVEY_SCHEDULED, back.getStatus());
        assertEquals(OwnerLeadIntent.LONG_TERM_RENT, back.getIntent());
        assertEquals(OwnerLeadSource.REFERRAL, back.getSource());
        assertEquals("name-1", back.getContactName());
        assertEquals("+84909123456", back.getPhone().getValue());
        assertEquals("mail-1@example.com", back.getEmail());
        assertEquals("48", back.getProvinceCode());
        assertEquals("province-1", back.getProvinceName());
        assertEquals("20194", back.getWardCode());
        assertEquals("ward-1", back.getWardName());
        assertEquals("address-1", back.getAddressDetail());
        assertEquals("norm-1", back.getAddressKey());
        assertEquals(LATITUDE, back.getGeoPoint().orElseThrow().getLatitude());
        assertEquals(LONGITUDE, back.getGeoPoint().orElseThrow().getLongitude());
        assertEquals(OwnerLeadPropertyType.VILLA, back.getPropertyType());
        assertEquals(12_500_000L, back.getAskingPrice().orElseThrow().getAmount());
        assertEquals(PriceUnit.MONTH, back.getAskingPrice().orElseThrow().getUnit());
        assertEquals(LocalDate.of(2026, 12, 1), back.getAvailableFrom());
        assertEquals("notes-1", back.getNotes());
        assertEquals("callback-1", back.getPreferredCallbackTime());
        assertEquals("consent-1", back.getConsentRecordId().getValue());
        assertEquals("{\"snapshot\":1}", back.getSubmissionSnapshot());
        assertEquals("assignee-1", back.getAssignedUserId());
        assertEquals(at(1), back.getNextActionAt());
        assertEquals("lead-2", back.getDuplicateOfLeadId().getValue());
        assertEquals("close-1", back.getCloseReason());
        assertEquals(at(2), back.getCreatedAt());
        assertEquals(at(3), back.getUpdatedAt());
        assertEquals(4L, back.getVersion());
        assertEquals(5, back.getIntentCycle());
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-11-01T01:00:00Z").plusSeconds(60L * minute);
    }
}
