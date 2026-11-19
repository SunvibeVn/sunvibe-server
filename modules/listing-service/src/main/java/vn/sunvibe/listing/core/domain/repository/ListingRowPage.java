package vn.sunvibe.listing.core.domain.repository;

import java.util.List;

/**
 * The newest listings of an agreement, newest first, and whether it has older ones (listing-publish E06-S1 spec
 * E06S1-D17).
 */
public record ListingRowPage(List<ListingRow> rows, boolean hasOlder) {

    public ListingRowPage {
        rows = List.copyOf(rows);
    }
}
