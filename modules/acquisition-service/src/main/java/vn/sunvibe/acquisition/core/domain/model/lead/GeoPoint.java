package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.domain.model.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Optional map position of the property: latitude and longitude, both given or both left out. The position is private
 * location data and must lie inside the area Sunvibe serves.
 */
public final class GeoPoint extends ValueObject<GeoPoint> {

    // Owner decision 2026-10-16: the box covers mainland old Da Nang including Hoa Vang; the limits are approximate.
    public static final BigDecimal MIN_LATITUDE = new BigDecimal("15.85");
    public static final BigDecimal MAX_LATITUDE = new BigDecimal("16.25");
    public static final BigDecimal MIN_LONGITUDE = new BigDecimal("107.75");
    public static final BigDecimal MAX_LONGITUDE = new BigDecimal("108.40");

    private static final int SCALE = 6;
    private static final int MAX_SCALE = 100;
    private static final int MAX_PRECISION = 40;

    private final BigDecimal latitude;
    private final BigDecimal longitude;

    private GeoPoint(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Build the position, or return empty when both coordinates are absent. Both must be given together, none is
     * allowed for a ward that is a special zone (island ward outside the box), and the position must be inside the
     * box. Values are rounded to 6 decimals (half up) before the box check, as stored in the database; a value with
     * a scale beyond ±100 or more than 40 digits is refused before that rounding.
     * Every failure is reported on the field {@code latitude}.
     */
    public static Optional<GeoPoint> of(BigDecimal latitude, BigDecimal longitude, boolean specialZone) {
        if (latitude == null && longitude == null) {
            return Optional.empty();
        }
        if (specialZone) {
            throw new InvalidIntakeValueException("latitude", "Coordinates are not accepted for this ward.");
        }
        if (latitude == null || longitude == null) {
            throw new InvalidIntakeValueException("latitude", "Latitude and longitude must be given together.");
        }
        if (!plausibleDigits(latitude) || !plausibleDigits(longitude)) {
            throw new InvalidIntakeValueException("latitude", "Coordinates have too many digits.");
        }
        BigDecimal lat = latitude.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal lon = longitude.setScale(SCALE, RoundingMode.HALF_UP);
        if (lat.compareTo(MIN_LATITUDE) < 0 || lat.compareTo(MAX_LATITUDE) > 0
                || lon.compareTo(MIN_LONGITUDE) < 0 || lon.compareTo(MAX_LONGITUDE) > 0) {
            throw new InvalidIntakeValueException("latitude", "Coordinates are outside the area Sunvibe serves.");
        }
        return Optional.of(new GeoPoint(lat, lon));
    }

    /**
     * Rounding a number like 1E+999999999 or 1E-999999999 to 6 decimals takes minutes or all the memory, and no
     * real coordinate has such a scale or that many digits, so those are refused before any rounding.
     */
    private static boolean plausibleDigits(BigDecimal value) {
        return Math.abs((long) value.scale()) <= MAX_SCALE && value.precision() <= MAX_PRECISION;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    @Override
    protected boolean equalsCore(GeoPoint other) {
        return other != null && latitude.compareTo(other.latitude) == 0 && longitude.compareTo(other.longitude) == 0;
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(latitude.stripTrailingZeros(), longitude.stripTrailingZeros());
    }

    /** The position is private data: never print it. */
    @Override
    public String toString() {
        return "GeoPoint[hidden]";
    }
}
