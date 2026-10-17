package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * What the owner wants to do with the property. The intent fixes the unit of the asking price
 * (acquisition-owner-intake BR-4).
 */
public enum OwnerLeadIntent {
    /** Rent by the night, for example holiday stays. */
    SHORT_TERM_RENT(PriceUnit.NIGHT),
    /** Rent by the month to a resident tenant. */
    LONG_TERM_RENT(PriceUnit.MONTH),
    /** Sell the property for a single total price. */
    SALE(PriceUnit.TOTAL);

    private final PriceUnit requiredPriceUnit;

    OwnerLeadIntent(PriceUnit requiredPriceUnit) {
        this.requiredPriceUnit = requiredPriceUnit;
    }

    /**
     * The only price unit that fits this intent: night, month or total.
     */
    public PriceUnit requiredPriceUnit() {
        return requiredPriceUnit;
    }
}
