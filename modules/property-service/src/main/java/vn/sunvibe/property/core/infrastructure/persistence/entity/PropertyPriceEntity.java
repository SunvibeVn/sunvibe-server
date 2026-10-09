package vn.sunvibe.property.core.infrastructure.persistence.entity;


import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA Entity - PropertyPriceEntity
 * Table: property_prices
 * Stores the rental price history of a property.
 */
@Entity
@Table(name = "property_prices", indexes = {
        @Index(name = "idx_property_id_04", columnList = "property_id"),
        @Index(name = "idx_effective_date", columnList = "effective_date"),
        @Index(name = "idx_is_current", columnList = "is_current"),
        @Index(name = "idx_date_range", columnList = "effective_date,end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyPriceEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "monthly_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal monthlyPrice;

    @Column(name = "deposit_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal depositPrice;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;  // Date the price takes effect

    @Column(name = "end_date")
    private LocalDate endDate;  // NULL if this is the current price

    @Column(name = "reason", length = 255)
    private String reason;  // Reason for price change

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent;  // Is this the current price?

    // ==================== Relationships ====================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false, insertable = false, updatable = false)
    private PropertyEntity property;

    // ==================== Lifecycle Callbacks ====================
    @PrePersist
    public void onPrePersist() {
        if (this.isCurrent == null) {
            this.isCurrent = false;
        }
    }

    @Override
    public String toString() {
        return "PropertyPriceEntity{" +
                "id='" + id + '\'' +
                ", monthlyPrice=" + monthlyPrice +
                ", depositPrice=" + depositPrice +
                ", effectiveDate=" + effectiveDate +
                ", isCurrent=" + isCurrent +
                '}';
    }
}