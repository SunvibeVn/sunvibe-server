package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The part of a revision a submission rule names (listing-publish US-2 AC-4; E06-S2 spec E06S2-D7): the title, the
 * description, the terms snapshot, and one fee name of the snapshot (E06-S7 spec E06S7-D10, LP-15).
 */
public enum RevisionPart {
    TITLE,
    DESCRIPTION,
    TERMS,
    FEE_NAME
}
