package vn.sunvibe.brokerage.core.application.usecase;

import java.util.Set;

/**
 * The signed-in staff member who reads.
 *
 * @param userId              the user, from the security principal
 * @param expandedAuthorities the user's authorities, expanded through the role hierarchy
 */
public record StaffViewer(String userId, Set<String> expandedAuthorities) {

    public StaffViewer {
        expandedAuthorities = Set.copyOf(expandedAuthorities);
    }
}
