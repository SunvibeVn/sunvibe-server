package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;

import java.util.List;

/**
 * The newest revisions of a listing, newest first by number, and whether it has older ones (listing-publish E06-S1
 * spec E06S1-D17).
 */
public record ListingRevisionPage(List<ListingRevision> revisions, boolean hasOlder) {

    public ListingRevisionPage {
        revisions = List.copyOf(revisions);
    }
}
