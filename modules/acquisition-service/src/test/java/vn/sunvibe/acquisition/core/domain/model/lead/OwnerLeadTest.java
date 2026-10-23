package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner lead aggregate (acquisition-owner-intake BR-1..BR-5, BR-10): submission creates a web lead, assigned at once
 * when an eligible person is given.
 */
class OwnerLeadTest {

    private static final RequestCode CODE = RequestCode.of("SV-7K3M-Q9TX");
    private static final ConsentRecordId CONSENT_ID = ConsentRecordId.generate();
    private static final String SNAPSHOT = "{\"phone\":\"0909 123 456\"}";
    private static final String ASSIGNEE = "01JUSERAAAAAAAAAAAAAAAAAAA";

    private static OwnerLead.SubmitFields fields() {
        return new OwnerLead.SubmitFields(
                "Nguyen Van A", PhoneNumber.parse("0909 123 456"), "a@example.vn", OwnerLeadIntent.LONG_TERM_RENT,
                "48", "Da Nang", "20194", "Hai Chau 1", "12 Bach Dang", OwnerLead.addressKey("12 Bach Dang"),
                GeoPoint.of(new BigDecimal("16.0544"), new BigDecimal("108.2022"), false),
                OwnerLeadPropertyType.APARTMENT,
                Optional.of(AskingPrice.of(12_000_000L, "VND", PriceUnit.MONTH, OwnerLeadIntent.LONG_TERM_RENT)),
                LocalDate.parse("2026-11-01"), "Near the river", "Evening");
    }

    @Test
    void submit_noAssignee_isNewWithSourceWeb() {
        OwnerLead lead = OwnerLead.submit(fields(), CONSENT_ID, Optional.empty(), SNAPSHOT, CODE);

        assertEquals(OwnerLeadStatus.NEW, lead.getStatus());
        assertEquals(OwnerLeadSource.WEB, lead.getSource());
        assertNull(lead.getAssignedUserId());
        assertEquals(CODE, lead.getRequestCode());
        assertEquals(26, lead.getId().getValue().length());
        assertEquals(CONSENT_ID, lead.getConsentRecordId());
        assertEquals(SNAPSHOT, lead.getSubmissionSnapshot());
    }

    @Test
    void submit_withAssignee_isAssignedToThem() {
        OwnerLead lead = OwnerLead.submit(fields(), CONSENT_ID, Optional.of(ASSIGNEE), SNAPSHOT, CODE);

        assertEquals(OwnerLeadStatus.ASSIGNED, lead.getStatus());
        assertEquals(ASSIGNEE, lead.getAssignedUserId());
        assertEquals(OwnerLeadSource.WEB, lead.getSource());
    }

    @Test
    void submit_copiesTheValidatedFields() {
        OwnerLead lead = OwnerLead.submit(fields(), CONSENT_ID, Optional.empty(), SNAPSHOT, CODE);

        assertEquals("Nguyen Van A", lead.getContactName());
        assertEquals("+84909123456", lead.getPhone().getValue());
        assertEquals("a@example.vn", lead.getEmail());
        assertEquals(OwnerLeadIntent.LONG_TERM_RENT, lead.getIntent());
        assertEquals("48", lead.getProvinceCode());
        assertEquals("Da Nang", lead.getProvinceName());
        assertEquals("20194", lead.getWardCode());
        assertEquals("Hai Chau 1", lead.getWardName());
        assertEquals("12 Bach Dang", lead.getAddressDetail());
        assertEquals("12 bach dang", lead.getAddressKey());
        assertTrue(lead.getGeoPoint().isPresent());
        assertEquals(OwnerLeadPropertyType.APARTMENT, lead.getPropertyType());
        assertEquals(12_000_000L, lead.getAskingPrice().orElseThrow().getAmount());
        assertEquals(LocalDate.parse("2026-11-01"), lead.getAvailableFrom());
        assertEquals("Near the river", lead.getNotes());
        assertEquals("Evening", lead.getPreferredCallbackTime());
        assertNull(lead.getNextActionAt());
        assertNull(lead.getDuplicateOfLeadId());
        assertNull(lead.getCloseReason());
    }

    @Test
    void submit_setsTimestampsAndNullVersion() {
        Instant before = Instant.now();

        OwnerLead lead = OwnerLead.submit(fields(), CONSENT_ID, Optional.empty(), SNAPSHOT, CODE);

        assertNull(lead.getVersion());
        assertEquals(lead.getCreatedAt(), lead.getUpdatedAt());
        assertFalse(lead.getCreatedAt().isBefore(before));
        assertFalse(lead.getCreatedAt().isAfter(Instant.now()));
    }

    @Test
    void submit_startsTheFirstIntentCycle() {
        assertEquals(1, OwnerLead.submit(fields(), CONSENT_ID, Optional.empty(), SNAPSHOT, CODE).getIntentCycle());
    }

    @Test
    void addressKey_caseAndSpaceVariants_areEqual() {
        assertEquals(OwnerLead.addressKey("12 Bạch  Đằng"), OwnerLead.addressKey(" 12 bạch đằng "));
    }

    @Test
    void addressKey_nfcAndNfdInput_areEqual() {
        String nfc = Normalizer.normalize("12 Bạch Đằng", Normalizer.Form.NFC);
        String nfd = Normalizer.normalize("12 Bạch Đằng", Normalizer.Form.NFD);
        assertNotEquals(nfc, nfd);

        assertEquals(OwnerLead.addressKey(nfc), OwnerLead.addressKey(nfd));
        assertEquals(nfc.toLowerCase(java.util.Locale.ROOT), OwnerLead.addressKey(nfd));
    }

    @Test
    void normalizeText_tabsAndRepeatedSpaces_collapseToOneSpace() {
        assertEquals("a b c", OwnerLead.normalizeText("  a \t b\n\n c  "));
    }

    @Test
    void load_everyColumn_roundTripsThroughGetters() {
        OwnerLeadId id = OwnerLeadId.generate();
        Instant created = Instant.parse("2026-10-16T08:00:00Z");
        Instant updated = Instant.parse("2026-10-16T09:00:00Z");
        Instant nextAction = Instant.parse("2026-10-17T09:00:00Z");
        OwnerLeadId duplicateOf = OwnerLeadId.generate();
        GeoPoint geo = GeoPoint.of(new BigDecimal("16.0544"), new BigDecimal("108.2022"), false).orElseThrow();
        AskingPrice price = AskingPrice.of(5_000_000_000L, "VND", PriceUnit.TOTAL, OwnerLeadIntent.SALE);

        OwnerLead lead = OwnerLead.load(id, CODE, OwnerLeadStatus.DUPLICATE, OwnerLeadIntent.SALE,
                OwnerLeadSource.REFERRAL, "Tran B", PhoneNumber.parse("0909123456"), "b@example.vn", "48", "Da Nang",
                "20194", "Hai Chau 1", "1 Le Loi", "1 le loi", Optional.of(geo), OwnerLeadPropertyType.VILLA,
                Optional.of(price), LocalDate.parse("2026-12-01"), "note", "morning", CONSENT_ID, SNAPSHOT,
                ASSIGNEE, nextAction, duplicateOf, "same phone", created, updated, 7L, 4);

        assertEquals(id, lead.getId());
        assertEquals(CODE, lead.getRequestCode());
        assertEquals(OwnerLeadStatus.DUPLICATE, lead.getStatus());
        assertEquals(OwnerLeadIntent.SALE, lead.getIntent());
        assertEquals(OwnerLeadSource.REFERRAL, lead.getSource());
        assertEquals("Tran B", lead.getContactName());
        assertEquals("+84909123456", lead.getPhone().getValue());
        assertEquals("b@example.vn", lead.getEmail());
        assertEquals("48", lead.getProvinceCode());
        assertEquals("Da Nang", lead.getProvinceName());
        assertEquals("20194", lead.getWardCode());
        assertEquals("Hai Chau 1", lead.getWardName());
        assertEquals("1 Le Loi", lead.getAddressDetail());
        assertEquals("1 le loi", lead.getAddressKey());
        assertEquals(Optional.of(geo), lead.getGeoPoint());
        assertEquals(OwnerLeadPropertyType.VILLA, lead.getPropertyType());
        assertEquals(Optional.of(price), lead.getAskingPrice());
        assertEquals(LocalDate.parse("2026-12-01"), lead.getAvailableFrom());
        assertEquals("note", lead.getNotes());
        assertEquals("morning", lead.getPreferredCallbackTime());
        assertEquals(CONSENT_ID, lead.getConsentRecordId());
        assertEquals(SNAPSHOT, lead.getSubmissionSnapshot());
        assertEquals(ASSIGNEE, lead.getAssignedUserId());
        assertEquals(nextAction, lead.getNextActionAt());
        assertEquals(duplicateOf, lead.getDuplicateOfLeadId());
        assertEquals("same phone", lead.getCloseReason());
        assertEquals(created, lead.getCreatedAt());
        assertEquals(updated, lead.getUpdatedAt());
        assertEquals(7L, lead.getVersion());
        assertEquals(4, lead.getIntentCycle());
    }

    @Test
    void activitySubmitted_forLead_hasNoActor() {
        OwnerLeadId leadId = OwnerLeadId.generate();

        OwnerLeadActivity activity = OwnerLeadActivity.submitted(101L, leadId);

        assertEquals(101L, activity.getId());
        assertEquals(leadId, activity.getLeadId());
        assertEquals(OwnerLeadActivityType.SUBMITTED, activity.getType());
        assertNull(activity.getActorUserId());
        assertNull(activity.getFromStatus());
        assertNull(activity.getToStatus());
        assertNotNull(activity.getOccurredAt());
    }

    @Test
    void activityAutoAssigned_forLead_movesNewToAssigned() {
        OwnerLeadId leadId = OwnerLeadId.generate();

        OwnerLeadActivity activity = OwnerLeadActivity.autoAssigned(102L, leadId, ASSIGNEE);

        assertEquals(OwnerLeadActivityType.AUTO_ASSIGNED, activity.getType());
        assertEquals(OwnerLeadStatus.NEW, activity.getFromStatus());
        assertEquals(OwnerLeadStatus.ASSIGNED, activity.getToStatus());
        assertEquals(ASSIGNEE, activity.getToAssigneeId());
        assertNull(activity.getActorUserId());
    }

    @Test
    void idempotencyRecordIsOlderThan_afterTheDuration_isTrue() {
        Instant created = Instant.parse("2026-10-16T08:00:00Z");
        IdempotencyRecord record = new IdempotencyRecord("key-1", "a".repeat(64), OwnerLeadId.generate(), created);

        assertTrue(record.isOlderThan(Duration.ofHours(24), created.plus(Duration.ofHours(24)).plusSeconds(1)));
        assertFalse(record.isOlderThan(Duration.ofHours(24), created.plus(Duration.ofHours(24))));
        assertFalse(record.isOlderThan(Duration.ofHours(24), created.plus(Duration.ofHours(1))));
    }
}
