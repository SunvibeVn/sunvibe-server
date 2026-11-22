package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The three moments an approved revision tries to go live (listing-publish US-5 AC-3; E06-S6 spec E06S6-D1, D6): the
 * Sale's command, the reviewer's approval and the Sale's valid freshness confirmation. Each records its own history
 * action; only the Sale's command has an actor.
 */
public enum PutLiveMoment {
    SALE_COMMAND,
    APPROVAL,
    VALID_CONFIRMATION
}
