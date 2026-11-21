package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.ListingState;

import java.time.LocalDate;
import java.util.List;

/**
 * The listings to reconfirm (listing-publish US-6 AC-3, BR-10; E06-S3 spec 3.3, E06S3-D7): at most 50, oldest
 * confirmation first, then by id; {@code hasMore} when the list does not show every one.
 */
public record ReconfirmationListView(List<Item> items, boolean hasMore) {

    public ReconfirmationListView {
        items = List.copyOf(items);
    }

    /**
     * One listing to reconfirm, in its stored state (an archived listing or one whose agreement ended is never listed).
     *
     * @param latestRecordValid whether the latest record is a valid confirmation
     */
    public record Item(String listingId, String code, ListingState state, LocalDate confirmedOn,
                       LocalDate reconfirmFrom, LocalDate validThrough, boolean deadlinePassed,
                       boolean latestRecordValid) {
    }
}
