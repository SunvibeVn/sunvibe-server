package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.policy.intake.DuplicateSignal.Place;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The duplicate signal of property-create intake D5-14, example 4; the ward is matched by the caller. */
class DuplicateSignalTest {

    private static final Double LAT = 16.0678;
    private static final Double LNG = 108.2208;

    @Test
    void signal_sameAddress_hits() {
        assertEquals(Optional.of(CaseSignal.ADDRESS), DuplicateSignal.hit(
                place(AssetType.HOUSE, "1 bach dang", null, null, "A1"),
                place(AssetType.HOUSE, "1 bach dang", null, null, "B9")));
    }

    @Test
    void signal_otherAddressNoPin_noHit() {
        assertEquals(Optional.empty(), DuplicateSignal.hit(
                place(AssetType.HOUSE, "1 bach dang", null, null, "A1"),
                place(AssetType.HOUSE, "2 bach dang", null, null, "A1")));
    }

    /** 0.0004 degrees of latitude is about 44 m, 0.0005 about 56 m. */
    @Test
    void signal_pin50m_inAndOut() {
        Place here = place(AssetType.HOUSE, "1 bach dang", LAT, LNG, "A1");

        assertEquals(Optional.of(CaseSignal.PIN), DuplicateSignal.hit(here,
                place(AssetType.HOUSE, "2 bach dang", LAT + 0.0004, LNG, "A1")));
        assertEquals(Optional.empty(), DuplicateSignal.hit(here,
                place(AssetType.HOUSE, "2 bach dang", LAT + 0.0005, LNG, "A1")));
    }

    @Test
    void signal_addressGoesBeforePin() {
        assertEquals(Optional.of(CaseSignal.ADDRESS), DuplicateSignal.hit(
                place(AssetType.HOUSE, "1 bach dang", LAT, LNG, "A1"),
                place(AssetType.HOUSE, "1 bach dang", LAT, LNG, "A1")));
    }

    /** example 4: A1205 and A1206 of one building do not meet; an equal code does, however it was typed. */
    @Test
    void signal_apartmentNeedsEqualNormalizedCode() {
        Place a1205 = place(AssetType.APARTMENT, "1 bach dang", null, null, "A1205");

        assertEquals(Optional.empty(), DuplicateSignal.hit(a1205, place(AssetType.APARTMENT, "1 bach dang", null, null, "A1206")));
        assertEquals(Optional.of(CaseSignal.ADDRESS), DuplicateSignal.hit(a1205,
                place(AssetType.APARTMENT, "1 bach dang", null, null, "a-1205")));
        assertEquals(Optional.empty(), DuplicateSignal.hit(
                place(AssetType.CONDOTEL, "1 bach dang", null, null, "C1"),
                place(AssetType.CONDOTEL, "1 bach dang", null, null, "C2")));
    }

    /** D5-14: the code rule needs both sides to be the same apartment kind; any other pair hits on place alone. */
    @Test
    void signal_otherTypesHitOnPlace() {
        assertEquals(Optional.of(CaseSignal.ADDRESS), DuplicateSignal.hit(
                place(AssetType.WHOLE_BUILDING, "1 bach dang", null, null, "P103"),
                place(AssetType.WHOLE_BUILDING, "1 bach dang", null, null, "P101")));
        assertEquals(Optional.of(CaseSignal.ADDRESS), DuplicateSignal.hit(
                place(AssetType.APARTMENT, "1 bach dang", null, null, "A1"),
                place(AssetType.CONDOTEL, "1 bach dang", null, null, "A2")));
        assertEquals(Optional.of(CaseSignal.ADDRESS), DuplicateSignal.hit(
                place(AssetType.APARTMENT, "1 bach dang", null, null, "A1"),
                place(AssetType.HOUSE, "1 bach dang", null, null, "B")));
    }

    @Test
    void signal_nullAssetType_hitsOnPlace() {
        assertEquals(Optional.of(CaseSignal.ADDRESS), DuplicateSignal.hit(
                place(AssetType.APARTMENT, "1 bach dang", null, null, "A1205"),
                place(null, "1 bach dang", null, null, "OTHER")));
    }

    @Test
    void signal_noAddressOnALegacyRow_noCrash() {
        assertEquals(Optional.empty(), DuplicateSignal.hit(
                place(AssetType.HOUSE, "1 bach dang", null, null, "A1"),
                place(null, null, null, null)));
    }

    @Test
    void normalizedCode_keepsLettersAndDigitsUppercased() {
        assertEquals("A1205", DuplicateSignal.normalizedCode(" a-12.05 "));
    }

    private static Place place(AssetType type, String address, Double latitude, Double longitude, String... codes) {
        return new Place(type, address, latitude, longitude, Set.of(codes));
    }
}
