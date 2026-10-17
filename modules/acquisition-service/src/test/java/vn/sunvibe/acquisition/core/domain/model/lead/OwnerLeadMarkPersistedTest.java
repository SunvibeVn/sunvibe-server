package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The version the repository gives back after a save (optimistic lock of the lead queue). */
class OwnerLeadMarkPersistedTest {

    private static OwnerLead newLead() {
        OwnerLead.SubmitFields fields = new OwnerLead.SubmitFields(
                "Nguyen Van A", PhoneNumber.parse("0909 123 456"), null, OwnerLeadIntent.LONG_TERM_RENT,
                "48", "Da Nang", "20194", "Hai Chau 1", "12 Bach Dang", OwnerLead.addressKey("12 Bach Dang"),
                Optional.empty(), null, Optional.empty(), (LocalDate) null, null, null);
        return OwnerLead.submit(fields, ConsentRecordId.generate(), Optional.empty(), "{}",
                RequestCode.of("SV-7K3M-Q9TX"));
    }

    @Test
    void markPersisted_newLead_takesTheGivenVersion() {
        OwnerLead lead = newLead();
        assertNull(lead.getVersion());

        lead.markPersisted(0L);

        assertEquals(0L, lead.getVersion());
    }

    @Test
    void markPersisted_laterVersion_moves() {
        OwnerLead lead = newLead();
        lead.markPersisted(0L);

        lead.markPersisted(1L);

        assertEquals(1L, lead.getVersion());
    }

    @Test
    void markPersisted_sameVersion_keepsIt() {
        OwnerLead lead = newLead();
        lead.markPersisted(2L);

        lead.markPersisted(2L);

        assertEquals(2L, lead.getVersion());
    }

    @Test
    void markPersisted_olderVersion_isRefusedAndKeepsTheCurrentOne() {
        OwnerLead lead = newLead();
        lead.markPersisted(3L);

        assertThrows(IllegalArgumentException.class, () -> lead.markPersisted(2L));

        assertEquals(3L, lead.getVersion());
    }

    @Test
    void markPersisted_null_isRefused() {
        OwnerLead lead = newLead();

        assertThrows(IllegalArgumentException.class, () -> lead.markPersisted(null));

        assertNull(lead.getVersion());
    }
}
