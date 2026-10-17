package vn.sunvibe.acquisition.core.domain.model.consent;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consent record (acquisition-owner-intake BR-9): only the current notice version is accepted; hashes are kept as given.
 */
class ConsentRecordTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-10-16T08:00:00Z");

    @Test
    void create_currentVersion_keepsHashesAsGiven() {
        ConsentRecord record = ConsentRecord.create(ConsentRecord.CURRENT_NOTICE_VERSION,
                ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, "ip-hash", "ua-hash", ACCEPTED_AT);

        assertEquals("owner-intake-2026-10", record.getNoticeVersion());
        assertEquals(ConsentPurpose.OWNER_INTAKE_CONTACT, record.getPurpose());
        assertEquals(ConsentChannel.WEB, record.getChannel());
        assertEquals("ip-hash", record.getIpHash());
        assertEquals("ua-hash", record.getUserAgentHash());
        assertEquals(ACCEPTED_AT, record.getAcceptedAt());
        assertEquals(26, record.getId().getValue().length());
    }

    @Test
    void create_wrongNoticeVersion_throwsWithFieldConsentNoticeVersion() {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class,
                () -> ConsentRecord.create("owner-intake-2020-01", ConsentPurpose.OWNER_INTAKE_CONTACT,
                        ConsentChannel.WEB, "ip-hash", "ua-hash", ACCEPTED_AT));

        assertEquals("consent.noticeVersion", ex.getField());
        assertFalse(ex.getMessage().contains("owner-intake-2020-01"));
    }

    @Test
    void create_nullVersion_throws() {
        assertThrows(InvalidIntakeValueException.class,
                () -> ConsentRecord.create(null, ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, null, null,
                        ACCEPTED_AT));
    }

    @Test
    void create_missingHashes_areKeptEmpty() {
        ConsentRecord record = ConsentRecord.create(ConsentRecord.CURRENT_NOTICE_VERSION,
                ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, null, null, ACCEPTED_AT);

        assertNull(record.getIpHash());
        assertNull(record.getUserAgentHash());
    }

    @Test
    void load_olderVersion_roundTripsThroughGetters() {
        ConsentRecordId id = ConsentRecordId.generate();

        ConsentRecord record = ConsentRecord.load(id, "older-version", ConsentPurpose.OWNER_INTAKE_CONTACT,
                ConsentChannel.WEB, "ip", "ua", ACCEPTED_AT);

        assertEquals(id, record.getId());
        assertEquals("older-version", record.getNoticeVersion());
        assertEquals("ip", record.getIpHash());
        assertEquals("ua", record.getUserAgentHash());
        assertEquals(ACCEPTED_AT, record.getAcceptedAt());
    }
}
