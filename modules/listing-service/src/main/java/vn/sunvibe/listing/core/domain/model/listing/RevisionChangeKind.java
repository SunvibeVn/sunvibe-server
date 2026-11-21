package vn.sunvibe.listing.core.domain.model.listing;

/**
 * What a command did to a revision, so the repository writes only that (listing-publish E06-S2 spec E06S2-D19): a new
 * revision, a changed one, or a changed one whose terms snapshot and fee rows were replaced.
 */
public enum RevisionChangeKind {
    ADDED,
    UPDATED,
    TERMS_REPLACED
}
