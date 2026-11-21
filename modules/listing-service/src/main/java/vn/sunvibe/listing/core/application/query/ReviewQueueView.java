package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * The review queue (listing-publish US-4 AC-1; E06-S4 spec section 4.1, E06S4-D10): at most 50 Pending review revisions,
 * oldest submission first, each with whether the viewer may review it and why not; {@code hasMore} when more may wait.
 * Its text names ids only.
 */
public record ReviewQueueView(List<Item> items, boolean hasMore) {

    public ReviewQueueView {
        items = List.copyOf(items);
    }

    /**
     * One revision waiting for review.
     *
     * @param reasons why the viewer may not review it, in contract order; empty exactly when {@code viewerMayReview}
     */
    public record Item(String listingId, String code, int revisionNo, Instant submittedAt, String title,
                       UnitSnapshot unitOrNull, boolean viewerMayReview, List<ReviewIneligibility> reasons) {

        public Item {
            reasons = List.copyOf(reasons);
        }

        @Override
        public String toString() {
            return "Item[" + listingId + ", " + revisionNo + "]";
        }
    }

    @Override
    public String toString() {
        return "ReviewQueueView[" + items.size() + ", " + hasMore + "]";
    }
}
