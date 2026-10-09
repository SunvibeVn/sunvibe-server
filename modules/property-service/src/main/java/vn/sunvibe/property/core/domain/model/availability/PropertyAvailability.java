package vn.sunvibe.property.core.domain.model.availability;

import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model - PropertyAvailability
 * Represents the rental availability status of a property.
 */
public class PropertyAvailability {

    private String id;
    private PropertyId propertyId;
    private LocalDate availableFromDate;    // Date from which the property can be rented
    private LocalDate reservedUntilDate;    // Date until which the property is reserved
    private String bookingId;               // Current booking ID (if any)
    private String status;                  // AVAILABLE, RESERVED, BOOKED

    // ==================== Constructor ====================

    private PropertyAvailability() {
    }

    public PropertyAvailability(PropertyId propertyId, LocalDate availableFromDate,
                                LocalDate reservedUntilDate, String bookingId,
                                String status) {
        if (propertyId == null) throw new IllegalArgumentException("PropertyId cannot be null");
        if (availableFromDate == null) {
            throw new IllegalArgumentException("AvailableFromDate cannot be null");
        }

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.availableFromDate = availableFromDate;
        this.reservedUntilDate = reservedUntilDate;
        this.bookingId = bookingId;
        this.status = status != null ? status : "AVAILABLE";
    }

    // ==================== Factory Methods ====================

    /**
     * Create an immediately available status.
     */
    public static PropertyAvailability createAvailable(PropertyId propertyId) {
        return new PropertyAvailability(propertyId, LocalDate.now(), null, null, "AVAILABLE");
    }

    /**
     * Create an availability status starting from a specific date.
     */
    public static PropertyAvailability createAvailableFrom(PropertyId propertyId,
                                                           LocalDate availableDate) {
        return new PropertyAvailability(propertyId, availableDate, null, null, "AVAILABLE");
    }

    /**
     * Create a reserved status.
     */
    public static PropertyAvailability createReserved(PropertyId propertyId,
                                                      LocalDate reservedUntil,
                                                      String bookingId) {
        return new PropertyAvailability(propertyId, LocalDate.now(), reservedUntil,
                bookingId, "RESERVED");
    }

    // ==================== Business Methods ====================

    /**
     * Check whether the property is available for rent.
     */
    public boolean isAvailable() {
        if ("AVAILABLE".equals(status)) {
            return !LocalDate.now().isBefore(availableFromDate);
        }
        return false;
    }

    /**
     * Check whether the property is available for a specific time period.
     */
    public boolean isAvailableForPeriod(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null || checkOutDate == null || checkInDate.isAfter(checkOutDate)) {
            throw new IllegalArgumentException("Invalid time period");
        }

        // If currently available
        if ("AVAILABLE".equals(status)) {
            return !checkInDate.isBefore(availableFromDate);
        }

        // If reserved, check whether the requested period overlaps
        if ("RESERVED".equals(status) && reservedUntilDate != null) {
            return checkInDate.isAfter(reservedUntilDate);
        }

        return false;
    }

    /**
     * Set the status to reserved.
     */
    public void reserve(LocalDate reservedUntil, String bookingId) {
        if (reservedUntil == null || reservedUntil.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("ReservedUntil must be after today");
        }
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("BookingId cannot be null");
        }

        this.reservedUntilDate = reservedUntil;
        this.bookingId = bookingId;
        this.status = "RESERVED";
    }

    /**
     * Set the status to booked (booking completed).
     */
    public void book(LocalDate bookUntil, String bookingId) {
        if (bookUntil == null || bookUntil.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("BookUntil must be after today");
        }
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("BookingId cannot be null");
        }

        this.reservedUntilDate = bookUntil;
        this.bookingId = bookingId;
        this.status = "BOOKED";
    }

    /**
     * Release the reservation and revert to AVAILABLE status.
     */
    public void releaseReservation() {
        this.reservedUntilDate = null;
        this.bookingId = null;
        this.status = "AVAILABLE";
        this.availableFromDate = LocalDate.now();
    }

    /**
     * Update the available-from date.
     */
    public void updateAvailableFromDate(LocalDate newDate) {
        if (newDate == null || newDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("NewDate must be today or in the future");
        }
        this.availableFromDate = newDate;
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public LocalDate getAvailableFromDate() {
        return availableFromDate;
    }

    public LocalDate getReservedUntilDate() {
        return reservedUntilDate;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyAvailability that = (PropertyAvailability) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PropertyAvailability{" +
                "id='" + id + '\'' +
                ", status='" + status + '\'' +
                ", availableFromDate=" + availableFromDate +
                ", reservedUntilDate=" + reservedUntilDate +
                '}';
    }
}