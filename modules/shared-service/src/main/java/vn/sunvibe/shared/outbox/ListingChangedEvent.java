package vn.sunvibe.shared.outbox;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * The listing's event (listing-publish E06-S6 spec E06S6-D9; ADR-014 decisions 3, 4): what a listing shows may have
 * changed. It carries the listing id only, the same id as its aggregate's; a consumer reads the listing again, so a
 * late, lost or repeated event never makes what it shows wrong. E06-S7 subscribes its first consumer.
 */
public final class ListingChangedEvent {

    public static final String TYPE = "listing.listing-changed";
    public static final int VERSION = 1;
    public static final String LISTING_ID = "listingId";
    public static final String SOURCE_MODULE = "listing";
    public static final String AGGREGATE_TYPE = "listing";

    private static final Pattern ULID = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    private ListingChangedEvent() {}

    /** The event, due now. */
    public static OutboxEvent now(String listingId) {
        if (listingId == null || !ULID.matcher(listingId).matches()) {
            throw new IllegalArgumentException("listingId must be a ULID");
        }
        return new OutboxEvent(TYPE, VERSION, SOURCE_MODULE, AGGREGATE_TYPE, listingId, Map.of(LISTING_ID, listingId),
                null);
    }
}
