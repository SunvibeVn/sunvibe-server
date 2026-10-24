package vn.sunvibe.survey.core.domain.model.checklist.unit;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The unit keys of survey-checklist plan Task A6 (they reach the API in {@code corrections[].units} and the UI tests
 * its own table against them): one assertion per row.
 */
class UnitKeyTest {

    private static final String S = "01JSPACE000000000000000001";
    private static final String H = "01JHAZARD00000000000000001";
    private static final String I = "01JTEM000000000000000000A1";

    @Test
    void identificationRow() {
        assertEquals("identification/observedAddress", UnitKey.field(ChecklistGroup.IDENTIFICATION, "observedAddress")
                .value());
        for (String name : new String[]{"propertyType", "fit", "rentalScope", "differenceNote", "otherPartsNote",
                "generalNote"}) {
            assertEquals("identification/" + name, UnitKey.field(ChecklistGroup.IDENTIFICATION, name).value());
        }
        assertEquals("identification/spaces/" + S, UnitKey.space(S).value());
        assertEquals(S, UnitKey.space(S).spaceId());
    }

    @Test
    void partsRow() {
        assertEquals("parts/accessRoute", UnitKey.field(ChecklistGroup.PARTS, "accessRoute").value());
        assertEquals("parts/totalFloors", UnitKey.field(ChecklistGroup.PARTS, "totalFloors").value());
        assertEquals("parts/spaces/" + S + "/actualAccess",
                UnitKey.spaceField(ChecklistGroup.PARTS, S, "actualAccess").value());
    }

    @Test
    void dimensionsAndConditionRows() {
        assertEquals("dimensions/spaces/" + S + "/area", UnitKey.spaceField(ChecklistGroup.DIMENSIONS, S, "area")
                .value());
        assertEquals("dimensions/spaces/" + S + "/layoutNote",
                UnitKey.spaceField(ChecklistGroup.DIMENSIONS, S, "layoutNote").value());
        assertEquals("condition/sharedKitchen", UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen").value());
        assertEquals("condition/spaces/" + S + "/availableFrom",
                UnitKey.spaceField(ChecklistGroup.CONDITION, S, "availableFrom").value());
        assertEquals(S, UnitKey.spaceField(ChecklistGroup.CONDITION, S, "kitchen").spaceId());
    }

    @Test
    void safetyRow() {
        assertEquals("safety/egress", UnitKey.field(ChecklistGroup.SAFETY, "egress").value());
        assertEquals("safety/hazards", UnitKey.hazardList().value());
        assertEquals("safety/hazards/" + H, UnitKey.hazard(H).value());
        assertNull(UnitKey.hazard(H).spaceId());
    }

    @Test
    void amenitiesRow() {
        assertEquals("amenities/sharedItems/CCTV", UnitKey.sharedItem(AmenityCode.CCTV).value());
        assertEquals("amenities/spaces/" + S + "/furnishing",
                UnitKey.spaceField(ChecklistGroup.AMENITIES, S, "furnishing").value());
        assertEquals("amenities/spaces/" + S + "/items/BED", UnitKey.spaceItem(S, AmenityCode.BED).value());
        assertEquals(S, UnitKey.spaceItem(S, AmenityCode.BED).spaceId());
        assertEquals("amenities/otherItems/" + I, UnitKey.otherItem(I).value());
    }

    @Test
    void termsRow() {
        assertEquals("terms/common/electricity", UnitKey.commonTerm("electricity").value());
        assertEquals("terms/common/handoverMaintenance", UnitKey.commonTerm("handoverMaintenance").value());
        assertEquals("terms/spaces/" + S + "/monthlyRent", UnitKey.spaceField(ChecklistGroup.TERMS, S, "monthlyRent")
                .value());
        assertEquals("terms/spaces/" + S + "/spaceFees", UnitKey.spaceField(ChecklistGroup.TERMS, S, "spaceFees")
                .value());
        assertEquals("terms/spaces/" + S + "/chargeOverrides/water", UnitKey.override(S, "water").value());
        assertEquals(S, UnitKey.override(S, "water").spaceId());
    }

    @Test
    void of_parsesBackAndKnowsTheGroup() {
        UnitKey key = UnitKey.of("terms/spaces/" + S + "/chargeOverrides/water");

        assertEquals(UnitKey.override(S, "water"), key);
        assertEquals(ChecklistGroup.TERMS, key.group());
        assertEquals(ChecklistGroup.SAFETY, UnitKey.hazard(H).group());
        assertThrows(IllegalArgumentException.class, () -> UnitKey.of("evidence/photos"));
        assertThrows(IllegalArgumentException.class, () -> UnitKey.of("nonsense"));
    }
}
