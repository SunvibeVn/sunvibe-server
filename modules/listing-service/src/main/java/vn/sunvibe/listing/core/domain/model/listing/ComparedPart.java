package vn.sunvibe.listing.core.domain.model.listing;

/**
 * A part of a revision that differs from the live revision (listing-publish US-4 AC-2; E06-S6 spec E06S6-D12), in this
 * fixed order: the title, the description, the thirteen features, and the terms compared by value.
 */
public enum ComparedPart {
    TITLE,
    DESCRIPTION,
    FEATURES,
    TERMS
}
