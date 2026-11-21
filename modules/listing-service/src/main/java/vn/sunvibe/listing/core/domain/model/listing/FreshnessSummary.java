package vn.sunvibe.listing.core.domain.model.listing;

import java.time.LocalDate;

/**
 * What a listing's row keeps of its freshness records (listing-publish BR-10, BR-11, listing BR-11 KT-3; E06-S3 spec
 * E06S3-D6): the Vietnam date of the latest valid confirmation and whether the latest record is valid. The pairs are
 * those {@code ck_listings_freshness} admits: no record (both null), records none of them valid (no date, false), or a
 * valid confirmation with a known latest record (a date and a flag); never "latest valid" without a date.
 *
 * @param confirmedOnOrNull the date of the latest valid confirmation, if any
 * @param latestValidOrNull whether the latest record is valid; null before any record
 */
public record FreshnessSummary(LocalDate confirmedOnOrNull, Boolean latestValidOrNull) {

    public FreshnessSummary {
        if (confirmedOnOrNull == null ? Boolean.TRUE.equals(latestValidOrNull) : latestValidOrNull == null) {
            throw new IllegalArgumentException("a freshness summary is a date with its flag, or no valid date");
        }
    }

    /** A listing with no record. */
    public static FreshnessSummary none() {
        return new FreshnessSummary(null, null);
    }

    /**
     * The summary after a record (listing-publish BR-11): a valid record sets the date and marks the latest valid; an
     * invalid one keeps the date, never moving it back or forward, and marks the latest invalid.
     */
    public FreshnessSummary after(FreshnessRecord record) {
        if (record.valid()) {
            return new FreshnessSummary(record.contactDate(), true);
        }
        return new FreshnessSummary(confirmedOnOrNull, false);
    }
}
