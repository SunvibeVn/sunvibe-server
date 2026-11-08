package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;
import vn.sunvibe.property.core.domain.policy.intake.IntakeGate.Facts;
import vn.sunvibe.property.core.domain.model.lead.LeadStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.CONTACTED;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.DUPLICATE;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.ON_HOLD;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.QUALIFIED;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.REJECTED;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.SURVEY_SCHEDULED;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.WITHDRAWN;

/** The gate of property-create intake spec 4.4 and the layouts of 4.8 (D5-4, D5-12, D5-13). */
class IntakeGateTest {

    private static final Facts READY = new Facts("LONG_TERM_RENT", QUALIFIED, true, true, AssetType.HOUSE,
            RentalLayout.WHOLE, false, false);

    @Test
    void gate_ready_noReason() {
        assertEquals(List.of(), IntakeGate.reasons(READY));
    }

    @Test
    void gate_eachReason() {
        assertEquals(List.of(GateReason.NOT_LONG_TERM), IntakeGate.reasons(new Facts("SHORT_TERM_RENT", QUALIFIED, true,
                true, AssetType.HOUSE, RentalLayout.WHOLE, false, false)));
        for (LeadStatus status : List.of(ON_HOLD, CONTACTED, SURVEY_SCHEDULED, REJECTED, WITHDRAWN, DUPLICATE)) {
            assertEquals(List.of(GateReason.NOT_QUALIFIED), IntakeGate.reasons(new Facts("LONG_TERM_RENT", status, true, true,
                    AssetType.HOUSE, RentalLayout.WHOLE, false, false)), status.name());
        }
        assertEquals(List.of(GateReason.NO_PASSED), IntakeGate.reasons(new Facts("LONG_TERM_RENT", QUALIFIED, false, false,
                null, null, false, false)));
        assertEquals(List.of(GateReason.PASSED_NOT_USABLE), IntakeGate.reasons(new Facts("LONG_TERM_RENT", QUALIFIED, true,
                false, AssetType.HOUSE, RentalLayout.WHOLE, false, false)));
        assertEquals(List.of(GateReason.TYPE_SCOPE_MISMATCH), IntakeGate.reasons(new Facts("LONG_TERM_RENT", QUALIFIED,
                true, true, AssetType.HOUSE, RentalLayout.PARTS, false, false)));
        assertEquals(List.of(GateReason.RECONCILIATION_REQUIRED), IntakeGate.reasons(new Facts("LONG_TERM_RENT", QUALIFIED,
                true, true, AssetType.HOUSE, RentalLayout.WHOLE, true, false)));
        assertEquals(List.of(GateReason.RESTRICTED), IntakeGate.reasons(new Facts("LONG_TERM_RENT", QUALIFIED, true, true,
                AssetType.HOUSE, RentalLayout.WHOLE, false, true)));
    }

    @Test
    void gate_severalReasons_allListed() {
        assertEquals(List.of(GateReason.NOT_LONG_TERM, GateReason.NOT_QUALIFIED, GateReason.NO_PASSED, GateReason.RESTRICTED),
                IntakeGate.reasons(new Facts("SALE", ON_HOLD, false, false, null, null, false, true)));
    }

    /** spec 4.4: a unit suspension leaves the gate open and blocks that unit; only a whole-lead one stops it. */
    @Test
    void gate_unitSuspension_stayOpen() {
        assertEquals(List.of(), IntakeGate.reasons(new Facts("LONG_TERM_RENT", QUALIFIED, true, true, AssetType.HOUSE,
                RentalLayout.WHOLE, false, false)));
    }

    /** spec 4.8 table. */
    @Test
    void layout_table() {
        for (AssetType type : List.of(AssetType.HOUSE, AssetType.VILLA, AssetType.APARTMENT, AssetType.CONDOTEL)) {
            assertTrue(AssetLayoutRule.allows(type, RentalLayout.WHOLE), type.name());
            assertFalse(AssetLayoutRule.allows(type, RentalLayout.PARTS), type.name());
        }
        assertTrue(AssetLayoutRule.allows(AssetType.WHOLE_BUILDING, RentalLayout.PARTS));
        assertFalse(AssetLayoutRule.allows(AssetType.WHOLE_BUILDING, RentalLayout.WHOLE));
        assertTrue(AssetLayoutRule.allows(AssetType.HOMESTAY, RentalLayout.WHOLE));
        assertTrue(AssetLayoutRule.allows(AssetType.HOMESTAY, RentalLayout.PARTS));
    }

    @Test
    void layout_noType_notAllowed() {
        assertFalse(AssetLayoutRule.allows(null, RentalLayout.WHOLE));
        assertFalse(AssetLayoutRule.allows(AssetType.HOUSE, null));
    }
}
