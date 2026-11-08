package vn.sunvibe.survey.core.application.shared.external.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link LeadMoveResult#status()} reads the provider's name, and a name survey does not know fails loudly (SV-272). */
class LeadMoveResultTest {

    private static final Instant AT = Instant.parse("2026-11-08T03:00:00Z");

    @Test
    void status_unknownName_throws() {
        LeadMoveResult result = new LeadMoveResult("ARCHIVED", 4L, AT, null);

        assertThrows(IllegalArgumentException.class, result::status);
    }

    @Test
    void status_knownName_isTheConstant() {
        assertEquals(LeadStatus.ON_HOLD, new LeadMoveResult("ON_HOLD", 4L, AT, null).status());
    }
}
