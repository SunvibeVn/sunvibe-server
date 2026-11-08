package vn.sunvibe.property.core.application.shared.external.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.lead.LeadStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link IntakeLead#status()} reads the provider's name, and a name property does not know fails loudly (SV-272). */
class IntakeLeadTest {

    @Test
    void status_unknownName_throws() {
        IntakeLead lead = lead("ARCHIVED");

        assertThrows(IllegalArgumentException.class, lead::status);
    }

    @Test
    void status_knownName_isTheConstant() {
        assertEquals(LeadStatus.AGREEMENT_PENDING, lead("AGREEMENT_PENDING").status());
    }

    private static IntakeLead lead(String statusName) {
        return new IntakeLead("lead-1", "REQ-1", statusName, "RENT", 1, "user-1", Set.of("user-1"), "Ward 1", false);
    }
}
