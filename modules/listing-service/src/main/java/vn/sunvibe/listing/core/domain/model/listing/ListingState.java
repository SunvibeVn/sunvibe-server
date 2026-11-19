package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The state of a listing (listing section 3.2): the whole vocabulary of the brief, of which the listing create
 * reaches {@code UNPUBLISHED} only.
 */
public enum ListingState {
    UNPUBLISHED,
    PUBLISHED,
    PAUSED,
    URGENTLY_UNPUBLISHED,
    ARCHIVED;

    /**
     * The state a read answers (listing-publish E06-S1 spec E06S1-D5; ADR-012 decision 2): a listing whose agreement has
     * ended is treated as archived from that moment, before its stored transition; otherwise the stored state.
     */
    public ListingState effective(boolean agreementEnded) {
        return agreementEnded ? ARCHIVED : this;
    }
}
