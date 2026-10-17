package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;
import vn.sunvibe.acquisition.core.testsupport.InMemoryIdempotencyKeyRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Daily cleanup of the owner intake idempotency keys (acquisition-owner-intake spec 5.1 cleanup; W9-26): a key is
 * kept 24 hours, then deleted. Keys are technical data; the leads they point to are never touched.
 */
class IdempotencyKeyCleanupTest {

    private static final String HASH = "a".repeat(64);

    private InMemoryIdempotencyKeyRepository keys;
    private InMemoryOwnerLeadRepository leads;
    private IdempotencyKeyCleanupService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        keys = new InMemoryIdempotencyKeyRepository();
        leads = new InMemoryOwnerLeadRepository();
        service = new IdempotencyKeyCleanupService(keys);
        now = AppClock.currentUTC();
    }

    @Test
    void purgeExpired_rowsOlderThan24h_areDeletedAndCounted() {
        OwnerLeadId lead = OwnerLeadId.generate();
        keys.save(new IdempotencyRecord("old-key-0001", HASH, lead, now.minus(Duration.ofHours(25))));
        keys.save(new IdempotencyRecord("old-key-0002", HASH, lead, now.minus(Duration.ofHours(24).plusMinutes(5))));
        keys.save(new IdempotencyRecord("new-key-0001", HASH, lead, now.minus(Duration.ofMinutes(1))));

        int deleted = service.purgeExpired();

        assertEquals(2, deleted);
        assertTrue(keys.findByKey("old-key-0001").isEmpty());
        assertTrue(keys.findByKey("old-key-0002").isEmpty());
        assertTrue(keys.findByKey("new-key-0001").isPresent());
    }

    @Test
    void purgeExpired_rowsYoungerThan24h_areKept() {
        OwnerLeadId lead = OwnerLeadId.generate();
        keys.save(new IdempotencyRecord("young-key-01", HASH, lead, now.minus(Duration.ofHours(23).plusMinutes(55))));
        keys.save(new IdempotencyRecord("young-key-02", HASH, lead, now.minus(Duration.ofMinutes(1))));

        int deleted = service.purgeExpired();

        assertEquals(0, deleted);
        assertEquals(2, keys.size());
    }

    @Test
    void purgeExpired_leadsAreNeverTouched() {
        OwnerLead lead = lead(now.minus(Duration.ofDays(3)));
        leads.save(lead);
        int savesBefore = leads.saveCount;
        keys.save(new IdempotencyRecord("old-key-0001", HASH, lead.getId(), now.minus(Duration.ofDays(3))));

        int deleted = service.purgeExpired();

        assertEquals(1, deleted);
        assertEquals(0, keys.size());
        assertEquals(1, leads.all().size());
        assertTrue(leads.findById(lead.getId()).isPresent());
        assertEquals(savesBefore, leads.saveCount);
    }

    private static OwnerLead lead(Instant createdAt) {
        String address = "12 Bạch Đằng";
        return OwnerLead.load(OwnerLeadId.generate(), RequestCode.of("SV-ABCD-EFGH"), OwnerLeadStatus.NEW,
                OwnerLeadIntent.LONG_TERM_RENT, OwnerLeadSource.WEB, "Old Owner", PhoneNumber.parse("0909123456"),
                null, "48", "Đà Nẵng", "20242", "Hải Châu", address, OwnerLead.addressKey(address),
                Optional.empty(), null, Optional.empty(), null, null, null, ConsentRecordId.generate(), "{}",
                null, null, null, null, createdAt, createdAt, null);
    }
}
