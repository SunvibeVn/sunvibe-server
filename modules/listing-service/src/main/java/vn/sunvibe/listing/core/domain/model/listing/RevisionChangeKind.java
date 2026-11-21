package vn.sunvibe.listing.core.domain.model.listing;

/**
 * What a command did to a revision, so the repository writes only that (listing-publish E06-S2 spec E06S2-D19): a new
 * revision, a changed one, or a changed one whose terms snapshot and fee rows were replaced; or a new revision copied
 * from another with its terms, whose fee rows are written with it (E06-S4 spec E06S4-D14).
 */
public enum RevisionChangeKind {
    ADDED,
    UPDATED,
    TERMS_REPLACED,
    COPIED
}
