package vn.sunvibe.listing.core.application.exception;

/**
 * The listing or agreement is unknown, malformed, of another transaction type or out of the caller's scope; one answer
 * for all of them, the same as for one that does not exist (listing BR-5; listing-publish US-1 AC-3, E-3). It names
 * nothing.
 */
public class ListingNotFoundException extends RuntimeException {

    public ListingNotFoundException() {
        super("The resource was not found.");
    }
}
