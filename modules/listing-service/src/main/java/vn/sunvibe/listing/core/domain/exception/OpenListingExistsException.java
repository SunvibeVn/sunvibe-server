package vn.sunvibe.listing.core.domain.exception;

/**
 * The agreement already has a listing whose stored state is not archived (listing BR-1: at most one unarchived listing
 * per unit and type, stored per agreement, listing-publish E06-S1 spec E06S1-D4). It names no listing and no agreement.
 */
public class OpenListingExistsException extends RuntimeException {

    public OpenListingExistsException() {
        super("This agreement already has a listing.");
    }
}
