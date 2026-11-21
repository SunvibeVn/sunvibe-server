package vn.sunvibe.listing.core.domain.exception;

/**
 * Another listing took the same public code at the same time (listing-detail BR-15: a code is unique among every code
 * ever issued; listing-publish E06-S1 spec E06S1-D6). It names no code.
 */
public class ListingCodeTakenException extends RuntimeException {

    public ListingCodeTakenException() {
        super("Another listing took the same code at the same time.");
    }
}
