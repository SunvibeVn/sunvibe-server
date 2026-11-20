package vn.sunvibe.listing.core.domain.model.listing;

/** A revision a command added or changed, as the repository writes it (listing-publish E06-S2 spec E06S2-D19). */
public record RevisionChange(ListingRevision revision, RevisionChangeKind kind) {

    public RevisionChange {
        if (revision == null || kind == null) {
            throw new IllegalStateException("a revision change needs its revision and kind");
        }
    }
}
