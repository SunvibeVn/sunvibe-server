package vn.sunvibe.listing.core.application.command;

/**
 * Create a listing under this agreement (listing-publish E06-S1 spec E06S1-D1). The unit, the transaction type, the
 * code, the state and the actor never come from the request: the agreement's standing, the draw and the principal
 * give them.
 */
public record ListingCreateCommand(String agreementId) {
}
