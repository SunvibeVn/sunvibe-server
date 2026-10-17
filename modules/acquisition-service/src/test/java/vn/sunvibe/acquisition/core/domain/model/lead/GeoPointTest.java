package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coordinates (acquisition-owner-intake): both or neither, inside the Da Nang box, none for a special zone.
 */
class GeoPointTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static InvalidIntakeValueException rejected(String lat, String lon, boolean specialZone) {
        return assertThrows(InvalidIntakeValueException.class,
                () -> GeoPoint.of(lat == null ? null : d(lat), lon == null ? null : d(lon), specialZone));
    }

    @ParameterizedTest
    @CsvSource({"15.85,107.75", "15.85,108.40", "16.25,107.75", "16.25,108.40", "16.0544,108.2022"})
    void of_insideBoxIncludingCorners_isAccepted(String lat, String lon) {
        Optional<GeoPoint> point = GeoPoint.of(d(lat), d(lon), false);

        assertTrue(point.isPresent());
        assertEquals(0, d(lat).compareTo(point.get().getLatitude()));
        assertEquals(0, d(lon).compareTo(point.get().getLongitude()));
    }

    @ParameterizedTest
    @CsvSource({"15.849999,108.0", "16.250001,108.0", "16.0,107.749999", "16.0,108.400001"})
    void of_justOutsideEachEdge_throwsWithFieldLatitude(String lat, String lon) {
        assertEquals("latitude", rejected(lat, lon, false).getField());
    }

    @Test
    void of_onlyLatitude_throwsWithFieldLatitude() {
        assertEquals("latitude", rejected("16.0", null, false).getField());
    }

    @Test
    void of_onlyLongitude_throwsWithFieldLatitude() {
        assertEquals("latitude", rejected(null, "108.0", false).getField());
    }

    @Test
    void of_bothAbsent_isEmpty() {
        assertTrue(GeoPoint.of(null, null, false).isEmpty());
    }

    @Test
    void of_bothAbsentInSpecialZone_isEmpty() {
        assertTrue(GeoPoint.of(null, null, true).isEmpty());
    }

    @Test
    void of_specialZoneWithCoordinates_throwsWithFieldLatitude() {
        assertEquals("latitude", rejected("16.0", "108.0", true).getField());
    }

    @Test
    void of_sevenDecimals_roundsToSixBeforeTheBoxCheck() {
        Optional<GeoPoint> point = GeoPoint.of(d("15.8499996"), d("108.0000004"), false);

        assertTrue(point.isPresent());
        assertEquals(d("15.850000"), point.get().getLatitude());
        assertEquals(d("108.000000"), point.get().getLongitude());
    }

    @Test
    void of_sevenDecimalsRoundingDown_staysOutside() {
        assertEquals("latitude", rejected("15.8499994", "108.0", false).getField());
    }

    @Test
    void of_halfwayValue_roundsHalfUp() {
        GeoPoint point = GeoPoint.of(d("16.0000005"), d("108.0000005"), false).orElseThrow();

        assertEquals(d("16.000001"), point.getLatitude());
        assertEquals(d("108.000001"), point.getLongitude());
    }

    /**
     * Security gate F1: a scale or precision that no real coordinate has is refused before any rounding, because
     * rounding 1E+999999999 or 1E-999999999 to 6 decimals takes minutes or all the memory.
     */
    @ParameterizedTest
    @CsvSource({"1E+999999999,108.0", "-1E+999999999,108.0", "1E-999999999,108.0", "16.0,1E+999999999",
            "16.0,-1E+999999999", "16.0,1E-999999999"})
    void of_absurdScale_isRefusedQuicklyOnLatitude(String lat, String lon) {
        InvalidIntakeValueException error = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> rejected(lat, lon, false));

        assertEquals("latitude", error.getField());
        assertFalse(error.getMessage().contains("999999999"), error.getMessage());
    }

    @Test
    void of_scaleOver100_isRefusedOnLatitude() {
        assertEquals("latitude", rejected("1E-101", "108.0", false).getField());
        assertEquals("latitude", rejected("16.0", "1E+101", false).getField());
    }

    /** 41 significant digits, inside the box: refused on the digits, before the rounding. */
    @Test
    void of_precisionOver40_isRefusedOnLatitude() {
        String fortyOneDigits = "16." + "0".repeat(38) + "1";

        assertEquals(41, d(fortyOneDigits).precision());
        assertEquals("latitude", rejected(fortyOneDigits, "108.0", false).getField());
        assertEquals("latitude", rejected("16.0", "108." + "0".repeat(37) + "1", false).getField());
    }

    /** 40 significant digits and a scale of 38 are still a position: rounded to 6 decimals and kept. */
    @Test
    void of_precision40_isRoundedAndAccepted() {
        String fortyDigits = "16." + "0".repeat(37) + "1";

        GeoPoint point = GeoPoint.of(d(fortyDigits), d("108.2208"), false).orElseThrow();

        assertEquals(40, d(fortyDigits).precision());
        assertEquals(d("16.000000"), point.getLatitude());
    }

    @Test
    void of_outOfRange_messageDoesNotContainTheValue() {
        InvalidIntakeValueException ex = rejected("21.0285", "105.8542", false);

        assertFalse(ex.getMessage().contains("21.0285"));
        assertFalse(ex.getMessage().contains("105.8542"));
    }
}
