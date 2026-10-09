package vn.sunvibe.property.core.infrastructure.persistence.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * JPA Entity - PropertyAvailabilityEntity
 * Table: property_availability
 */
@Entity
@Table(name = "property_availability", indexes = {
        @Index(name = "idx_property_id_01", columnList = "property_id", unique = true),
        @Index(name = "idx_availability_status", columnList = "status"),
        @Index(name = "idx_available_from_date", columnList = "available_from_date"),
        @Index(name = "idx_reserved_until_date", columnList = "reserved_until_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyAvailabilityEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36, unique = true)
    private String propertyId;

    @Column(name = "available_from_date", nullable = false)
    private LocalDate availableFromDate;  // Date from which the property can be rented

    @Column(name = "reserved_until_date")
    private LocalDate reservedUntilDate;  // Date until which the property is reserved

    @Column(name = "booking_id", length = 36)
    private String bookingId;  // Current booking ID (if any)

    @Column(name = "status", nullable = false, length = 50)
    private String status;  // AVAILABLE, RESERVED, BOOKED

    // ==================== Relationships ====================
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false, insertable = false, updatable = false)
    private PropertyEntity property;

    // ==================== Lifecycle Callbacks ====================
    @PrePersist
    public void onPrePersist() {
        if (this.status == null) {
            this.status = "AVAILABLE";
        }
    }

    @Override
    public String toString() {
        return "PropertyAvailabilityEntity{" +
                "id='" + id + '\'' +
                ", propertyId='" + propertyId + '\'' +
                ", status='" + status + '\'' +
                ", availableFromDate=" + availableFromDate +
                '}';
    }
}