package vn.sunvibe.listing.core.domain.exception;

/**
 * Another command changed the listing since this one loaded it (listing BR-3; listing-publish E06-S2 spec E06S2-D19):
 * the listing row's version moved, or a revision index refused a row as the backstop. The caller reads again.
 */
public class ListingChangedException extends RuntimeException {

    public ListingChangedException() {
        super("The listing changed since it was read.");
    }
}
