package vn.sunvibe.property.core.domain.model.price;

import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model - PropertyPrice
 * Represents the rental price history of a property (can change over time).
 */
public class PropertyPrice {

    private String id;
    private final PropertyId propertyId;
    private final Money monthlyPrice;        // Monthly rent
    private final Money depositPrice;        // Security deposit
    private final LocalDate effectiveDate;   // Date the price takes effect
    private LocalDate endDate;         // End date (null = current)
    private final String reason;             // Reason for price change
    private boolean isCurrent;         // Is this the current price?

    // ==================== Constructor ====================

    private PropertyPrice(PropertyId propertyId, Money monthlyPrice, Money depositPrice,
                          LocalDate effectiveDate, LocalDate endDate, String reason,
                          boolean isCurrent) {
        if (propertyId == null) throw new IllegalArgumentException("PropertyId cannot be null");
        if (monthlyPrice == null) throw new IllegalArgumentException("MonthlyPrice cannot be null");
        if (depositPrice == null) throw new IllegalArgumentException("DepositPrice cannot be null");
        if (effectiveDate == null) throw new IllegalArgumentException("EffectiveDate cannot be null");

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.monthlyPrice = monthlyPrice;
        this.depositPrice = depositPrice;
        this.effectiveDate = effectiveDate;
        this.endDate = endDate;
        this.reason = reason;
        this.isCurrent = isCurrent;
    }

    // ==================== Factory Methods ====================

    /**
     * Create the current price.
     */
    public static PropertyPrice createCurrent(PropertyId propertyId, Money monthlyPrice,
                                              Money depositPrice, LocalDate effectiveDate) {
        return new PropertyPrice(propertyId, monthlyPrice, depositPrice, effectiveDate,
                null, null, true);
    }

    /**
     * Create the current price with a reason.
     */
    public static PropertyPrice createCurrent(PropertyId propertyId, Money monthlyPrice,
                                              Money depositPrice, LocalDate effectiveDate,
                                              String reason) {
        return new PropertyPrice(propertyId, monthlyPrice, depositPrice, effectiveDate,
                null, reason, true);
    }

    /**
     * Create a historical price (past record).
     */
    public static PropertyPrice createHistorical(PropertyId propertyId, Money monthlyPrice,
                                                 Money depositPrice, LocalDate effectiveDate,
                                                 LocalDate endDate, String reason) {
        return new PropertyPrice(propertyId, monthlyPrice, depositPrice, effectiveDate,
                endDate, reason, false);
    }

    /**
     * Load from database (reconstruct): same checks as a new price, with the stored id.
     */
    public static PropertyPrice load(String id, PropertyId propertyId, Money monthlyPrice, Money depositPrice,
                                     LocalDate effectiveDate, LocalDate endDate, String reason,
                                     boolean isCurrent) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be blank");
        PropertyPrice price = new PropertyPrice(propertyId, monthlyPrice, depositPrice, effectiveDate, endDate,
                reason, isCurrent);
        price.id = id;
        return price;
    }

    // ==================== Business Methods ====================

    /**
     * Mark this price as ended (replaced by a new price).
     */
    public void markAsEnded(LocalDate endDate) {
        if (endDate == null || endDate.isBefore(effectiveDate)) {
            throw new IllegalArgumentException("EndDate must be after EffectiveDate");
        }
        this.endDate = endDate;
        this.isCurrent = false;
    }

    /**
     * Check whether this price is active on a given date.
     */
    public boolean isActiveOn(LocalDate date) {
        if (date.isBefore(effectiveDate)) return false;
        return endDate == null || !date.isAfter(endDate);
    }

    /**
     * Check whether this is the current price.
     */
    public boolean isCurrentPrice() {
        return isCurrent && endDate == null;
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public Money getMonthlyPrice() {
        return monthlyPrice;
    }

    public Money getDepositPrice() {
        return depositPrice;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyPrice that = (PropertyPrice) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PropertyPrice{" +
                "id='" + id + '\'' +
                ", monthlyPrice=" + monthlyPrice +
                ", depositPrice=" + depositPrice +
                ", effectiveDate=" + effectiveDate +
                ", isCurrent=" + isCurrent +
                '}';
    }
}