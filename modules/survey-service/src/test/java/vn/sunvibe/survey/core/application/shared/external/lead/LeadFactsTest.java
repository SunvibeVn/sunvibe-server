package vn.sunvibe.survey.core.application.shared.external.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link LeadFacts#status()} reads the provider's name, and a name survey does not know fails loudly (SV-272). */
class LeadFactsTest {

    private static final Instant AT = Instant.parse("2026-11-08T03:00:00Z");

    @Test
    void status_unknownName_throws() {
        LeadFacts facts = facts("ARCHIVED");

        assertThrows(IllegalArgumentException.class, facts::status);
    }

    @Test
    void status_knownName_isTheConstant() {
        assertEquals(LeadStatus.SURVEY_SCHEDULED, facts("SURVEY_SCHEDULED").status());
    }

    private static LeadFacts facts(String statusName) {
        return new LeadFacts("lead-1", "REQ-1", statusName, "RENT", 1, "user-1", 3L, AT, null, null, AT, null);
    }
}
