package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** property's availability as the review read shows it (listing-publish E06-S4 plan E06S4-P7): unknown fails closed. */
class UnitAvailabilityTest {

    @Test
    void ofCode_fourValues_known() {
        assertEquals(UnitAvailability.AVAILABLE, UnitAvailability.ofCode("AVAILABLE"));
        assertEquals(UnitAvailability.OCCUPIED, UnitAvailability.ofCode("OCCUPIED"));
        assertEquals(UnitAvailability.MAINTENANCE, UnitAvailability.ofCode("MAINTENANCE"));
        assertEquals(UnitAvailability.BLOCKED, UnitAvailability.ofCode("BLOCKED"));
    }

    @Test
    void ofCode_otherValue_illegalState() {
        for (String code : new String[] {"VACANT", "available", "", null}) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> UnitAvailability.ofCode(code));
            if (code != null && !code.isEmpty()) {
                assertFalse(refused.getMessage().contains(code), refused.getMessage());
            }
        }
    }
}
