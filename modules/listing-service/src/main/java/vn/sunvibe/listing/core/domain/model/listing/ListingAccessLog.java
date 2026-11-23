package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;

/**
 * One access to a listing's private data (listing-publish US-4 AC-2; listing BR-5; E06-S4 spec E06S4-D12, D18): who
 * opened what of which revision and when, never the value opened. Its id is a Snowflake drawn by the application
 * (ADR-006). Insert-only; it outlives what it names.
 */
public record ListingAccessLog(long id, String userId, String listingId, int revisionNo, String action,
                               Instant accessedAt) {

    /** A reviewer opened the real location of a revision's unit (E06S4-D12). */
    public static final String UNIT_LOCATION_OPENED = "UNIT_LOCATION_OPENED";

    public ListingAccessLog {
        if (userId == null || listingId == null || action == null || accessedAt == null) {
            throw new IllegalArgumentException("an access log needs its user, listing, action and time");
        }
        if (revisionNo < 1) {
            throw new IllegalArgumentException("an access log names a revision numbered from 1");
        }
    }

    /** The opening of a revision's real location by the reviewer, at {@code now}. */
    public static ListingAccessLog unitLocationOpened(long id, String userId, String listingId, int revisionNo,
                                                      Instant now) {
        return new ListingAccessLog(id, userId, listingId, revisionNo, UNIT_LOCATION_OPENED, now);
    }
}
