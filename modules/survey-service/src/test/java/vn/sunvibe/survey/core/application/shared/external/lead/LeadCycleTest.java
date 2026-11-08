package vn.sunvibe.survey.core.application.shared.external.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link LeadCycle#status()} reads the provider's name, and a name survey does not know fails loudly (SV-272). */
class LeadCycleTest {

    @Test
    void status_unknownName_throws() {
        LeadCycle cycle = new LeadCycle("lead-1", 1, "ARCHIVED", "user-1", "REQ-1");

        assertThrows(IllegalArgumentException.class, cycle::status);
    }

    @Test
    void status_knownName_isTheConstant() {
        assertEquals(LeadStatus.QUALIFIED, new LeadCycle("lead-1", 1, "QUALIFIED", null, "REQ-1").status());
    }
}
