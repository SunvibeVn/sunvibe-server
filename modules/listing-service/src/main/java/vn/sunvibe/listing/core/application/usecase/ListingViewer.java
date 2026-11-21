package vn.sunvibe.listing.core.application.usecase;

import java.util.Set;

/**
 * The signed-in staff member who reads or acts on listings.
 *
 * @param userId              the user, from the security principal
 * @param expandedAuthorities the user's authorities, expanded through the role hierarchy
 */
public record ListingViewer(String userId, Set<String> expandedAuthorities) {

    public ListingViewer {
        expandedAuthorities = Set.copyOf(expandedAuthorities);
    }
}
