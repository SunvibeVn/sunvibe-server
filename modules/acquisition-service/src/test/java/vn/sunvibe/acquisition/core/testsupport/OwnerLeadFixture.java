package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;

import java.time.Instant;
import java.util.Optional;

/** Builds leads in any state for the lead queue tests (the aggregate has no public way to reach every state). */
public final class OwnerLeadFixture {

    /** A moment in the past, so a real change always moves {@code updatedAt} forward. */
    public static final Instant LONG_AGO = Instant.parse("2026-10-01T08:00:00Z");

    private OwnerLeadFixture() {
    }

    public static OwnerLead lead(OwnerLeadStatus status, String assignedUserId, Instant nextActionAt, Long version) {
        return lead(status, assignedUserId, nextActionAt, version, OwnerLeadIntent.LONG_TERM_RENT, Optional.empty());
    }

    public static OwnerLead lead(OwnerLeadStatus status, String assignedUserId) {
        return lead(status, assignedUserId, null, 3L);
    }

    /** A lead in the given state with the given intent and asking price (intent cycle 1, version 3). */
    public static OwnerLead lead(OwnerLeadStatus status, String assignedUserId, OwnerLeadIntent intent,
                                 Optional<AskingPrice> askingPrice) {
        return lead(status, assignedUserId, null, 3L, intent, askingPrice);
    }

    private static OwnerLead lead(OwnerLeadStatus status, String assignedUserId, Instant nextActionAt, Long version,
                                  OwnerLeadIntent intent, Optional<AskingPrice> askingPrice) {
        String address = "12 Bach Dang";
        return OwnerLead.load(OwnerLeadId.generate(), RequestCode.of("SV-7K3M-Q9TX"), status, intent,
                OwnerLeadSource.WEB, "Nguyen Van A", PhoneNumber.parse("0909123456"), null, "48", "Da Nang", "20194",
                "Hai Chau 1", address, OwnerLead.addressKey(address), Optional.empty(), null, askingPrice, null,
                "Owner note", null, ConsentRecordId.generate(), "{}", assignedUserId, nextActionAt, null, null,
                LONG_AGO, LONG_AGO, version, 1);
    }

    /**
     * A lead not saved yet (no version) in the given ward, created at the given moment, with a fresh request code,
     * for the use case tests that store leads in the in-memory repository.
     */
    public static OwnerLead newLead(OwnerLeadStatus status, String assignedUserId, String wardCode,
                                    Instant createdAt) {
        String address = "12 Bach Dang";
        return OwnerLead.load(OwnerLeadId.generate(), RequestCode.generate(new java.security.SecureRandom()), status,
                OwnerLeadIntent.LONG_TERM_RENT, OwnerLeadSource.WEB, "Nguyen Van A",
                PhoneNumber.parse("0909123456"), "owner@example.com", "48", "Da Nang", wardCode, "Hai Chau 1",
                address, OwnerLead.addressKey(address), Optional.empty(), null, Optional.empty(), null, "Owner note",
                null, ConsentRecordId.generate(), "{}", assignedUserId, null, null, null, createdAt, createdAt, null,
                1);
    }
}
