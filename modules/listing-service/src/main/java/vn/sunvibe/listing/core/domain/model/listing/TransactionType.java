package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The transaction a listing advertises (listing BR-1: one unit, one transaction type, one agreement for life; long-term
 * rental only, owner decision G2 D-45). The code is the one brokerage-service stores and publishes for its agreements.
 */
public enum TransactionType {
    LONG_TERM_RENT;

    /** The stored and exposed value. */
    public String code() {
        return name();
    }
}
