package vn.sunvibe.property.core.application.usecase.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The unit profile read carries a property's unpublished values to listing's snapshot (listing-publish E06-S2 spec
 * E06S2-D21): its view and its row print their name and [hidden], never a value.
 */
class UnitProfileViewTest {

    @Test
    void toString_viewAndRow_hidden() {
        UnitProfileView view = new UnitProfileView("01UNIT", "APARTMENT", "48", "Secret Province", "20242",
                "Secret Ward", 3, 2, 45.5);
        InventoryReadRepository.UnitProfileRow row = new InventoryReadRepository.UnitProfileRow("01UNIT", "APARTMENT",
                "48", "Secret Province", "20242", "Secret Ward", 3, 2, 45.5);

        assertEquals("UnitProfileView[hidden]", view.toString());
        assertEquals("UnitProfileRow[hidden]", row.toString());
        for (String value : List.of("01UNIT", "APARTMENT", "Secret", "20242", "45.5")) {
            assertFalse((view + " " + row).contains(value), value);
        }
    }
}
