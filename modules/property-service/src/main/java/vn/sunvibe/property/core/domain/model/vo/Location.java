package vn.sunvibe.property.core.domain.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing Location (property location).
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class Location extends ValueObject<Location> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String street;          // Street/road
    private String ward;            // Ward/commune
    private String district;        // District
    private String city;            // City (default: Da Nang)
    private String postalCode;      // Postal code
    private Double latitude;        // Latitude
    private Double longitude;       // Longitude

    private Location(String street, String ward, String district, String city,
                     String postalCode, Double latitude, Double longitude) {
        if (street == null || street.isBlank()) {
            throw new IllegalArgumentException("Street cannot be null or empty");
        }
        if (ward == null || ward.isBlank()) {
            throw new IllegalArgumentException("Ward cannot be null or empty");
        }
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("District cannot be null or empty");
        }

        this.street = street;
        this.ward = ward;
        this.district = district;
        this.city = city != null ? city : "Đà Nẵng";
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Location of(String street, String ward, String district,
                              String city, String postalCode,
                              Double latitude, Double longitude) {
        return new Location(street, ward, district, city, postalCode, latitude, longitude);
    }

    public static Location of(String street, String ward, String district,
                              String postalCode) {
        return new Location(street, ward, district, "Đà Nẵng", postalCode, null, null);
    }

    public String getFullAddress() {
        return String.format("%s, %s, %s, %s", street, ward, district, city);
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    @Override
    protected boolean equalsCore(Location location) {
        if (this == location) return true;
        if (location == null || getClass() != location.getClass()) return false;
        return Objects.equals(street, location.street) &&
                Objects.equals(ward, location.ward) &&
                Objects.equals(district, location.district) &&
                Objects.equals(city, location.city) &&
                Objects.equals(postalCode, location.postalCode) &&
                Objects.equals(latitude, location.latitude) &&
                Objects.equals(longitude, location.longitude);
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(street, ward, district, city, postalCode, latitude, longitude);
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}
