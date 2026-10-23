package vn.sunvibe.survey.core.domain.model.appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A map pin with 6 decimals, as stored ({@code NUMERIC(9,6)}); two pins are equal when they round to the same
 * values. Private location data: never logged.
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

    private static final int SCALE = 6;

    public Coordinates {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("latitude and longitude come together.");
        }
        latitude = latitude.setScale(SCALE, RoundingMode.HALF_UP);
        longitude = longitude.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** The pin of the two values, or null when both are null. */
    public static Coordinates ofNullable(BigDecimal latitude, BigDecimal longitude) {
        return latitude == null && longitude == null ? null : new Coordinates(latitude, longitude);
    }

    @Override
    public String toString() {
        return "Coordinates[hidden]";
    }
}
