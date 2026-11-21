package vn.sunvibe.listing.core.application.command;

/**
 * A reviewer opens the real location of a revision's unit (listing-publish US-4 AC-2; E06-S4 spec section 4.5,
 * E06S4-D12). The revision number is null when the path's number is malformed.
 */
public record UnitLocationOpenCommand(String listingId, Integer revisionNoOrNull) {
}
