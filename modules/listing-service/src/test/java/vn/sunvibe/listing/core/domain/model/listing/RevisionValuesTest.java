package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The features and the unit snapshot of a revision (listing-publish BR-3, BR-4; E06-S2 spec E06S2-D5, E06S2-D12), and
 * the redacted text of every value that carries unpublished content or E05's terms (US-10 AC-4, BR-17; E06S2-D21).
 */
class RevisionValuesTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:15:00.123456Z");

    @Test
    void features_noneChosen_falseAndNull() {
        RevisionFeatures none = RevisionFeatures.NONE_CHOSEN;

        assertAll(
                () -> assertNull(none.furnishingOrNull()),
                () -> assertFalse(none.airConditioner()),
                () -> assertFalse(none.waterHeater()),
                () -> assertFalse(none.fridge()),
                () -> assertNull(none.washingMachineOrNull()),
                () -> assertNull(none.kitchenOrNull()),
                () -> assertFalse(none.balcony()),
                () -> assertFalse(none.elevator()),
                () -> assertFalse(none.motorbikeParking()),
                () -> assertFalse(none.carParking()),
                () -> assertFalse(none.internet()),
                () -> assertNull(none.petsOrNull()),
                () -> assertFalse(none.noSharedLandlord()));
    }

    @Test
    void unitSnapshot_halfPlace_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot("APARTMENT", "48", null, null, null, 1, 1, 25.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot("APARTMENT", "48", "Đà Nẵng", "20194", null, 1, 1, 25.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot("APARTMENT", null, null, "20194", "Phường Hải Châu", 1, 1, 25.0)),
                () -> assertDoesNotThrow(() -> new UnitSnapshot("APARTMENT", null, null, null, null, 1, 1, 25.0)),
                () -> assertDoesNotThrow(
                        () -> new UnitSnapshot("APARTMENT", "48", "Đà Nẵng", "20194", "Phường Hải Châu", 1, 1, 25.0)));
    }

    @Test
    void unitSnapshot_unknownAssetType_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot("CASTLE", null, null, null, null, 1, 1, 25.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot("apartment", null, null, null, null, 1, 1, 25.0)),
                () -> assertDoesNotThrow(() -> new UnitSnapshot(null, null, null, null, null, 1, 1, 25.0)));
        for (String code : List.of("HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL", "APARTMENT")) {
            assertDoesNotThrow(() -> new UnitSnapshot(code, null, null, null, null, 1, 1, 25.0), code);
        }
        assertEquals(6, UnitSnapshot.ASSET_TYPES.size());
    }

    @Test
    void unitSnapshot_negativeRoomsOrArea_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot(null, null, null, null, null, -1, 1, 25.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot(null, null, null, null, null, 1, -1, 25.0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot(null, null, null, null, null, 1, 1, -0.5)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot(null, null, null, null, null, 1, 1, Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnitSnapshot(null, null, null, null, null, 1, 1, Double.POSITIVE_INFINITY)),
                () -> assertDoesNotThrow(() -> new UnitSnapshot(null, null, null, null, null, 0, 0, 0.0)));
    }

    @Test
    void toString_contentFeaturesTermsUnit_hidden() {
        RevisionFeatures features = new RevisionFeatures(Furnishing.FULL, true, true, true, ShareMode.PRIVATE,
                ShareMode.SHARED, true, true, true, true, true, Pets.ALLOWED, true);
        RevisionContent content = new RevisionContent("Phòng trọ secret-title", "Mô tả secret-description", features);
        RevisionTerms.Deposit deposit = new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 7_654_321L);
        RevisionTerms.Fee fee = new RevisionTerms.Fee("secret-fee-name", FeeBasis.AMOUNT, 123_456L, ChargeUnit.PER_KWH);
        RevisionTerms terms = new RevisionTerms(TermsSource.IN_EFFECT, null, NOW, 8_765_432L, deposit, 6, 1,
                List.of(fee));
        UnitSnapshot unit = new UnitSnapshot("APARTMENT", "48", "Secret Province", "20194", "Secret Ward", 3, 2, 45.5);

        assertAll(
                () -> assertEquals("RevisionContent[hidden]", content.toString()),
                () -> assertEquals("RevisionFeatures[hidden]", features.toString()),
                () -> assertEquals("RevisionTerms[hidden]", terms.toString()),
                () -> assertEquals("Deposit[hidden]", deposit.toString()),
                () -> assertEquals("Fee[hidden]", fee.toString()),
                () -> assertEquals("UnitSnapshot[hidden]", unit.toString()));
        String all = content + " " + features + " " + terms + " " + deposit + " " + fee + " " + unit;
        for (String secret : List.of("secret", "Secret", "7654321", "8765432", "123456", "20194", "45.5", "FULL")) {
            assertFalse(all.contains(secret), secret);
        }
    }
}
