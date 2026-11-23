package vn.sunvibe.listing.core.domain.policy.listing;

import java.util.Collections;
import java.util.Set;

/**
 * Who may read and create listings, over the caller's expanded authorities (role hierarchy applied) and the current
 * assignee of the agreement's basis lead (listing BR-5, BR-16; listing-publish section 2; E06-S1 spec E06S1-D2,
 * E06S1-D8). The listing stores no Sale: the assignee is read at every request, so a reassignment moves the rights at
 * once (listing BR-16).
 */
public final class ListingAccessPolicy {

    /** listing BR-5: listing reviewers and managers see every listing. */
    private static final Set<String> EVERY_LISTING =
            Set.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER");

    /**
     * The scope of E05's own agreement page (brokerage-agreement brief section 2: LISTING_REVIEWER has no right in the
     * agreement feature), so the agreement's listing section shows no role anything of E05 that E05's page does not.
     */
    private static final Set<String> EVERY_AGREEMENT = Set.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN");

    /** listing-publish section 2 "Sale phụ trách tin": a SALE or SALE_LEADER who holds the basis lead. */
    private static final Set<String> CREATORS = Set.of("ROLE_SALE", "ROLE_SALE_LEADER");

    /** listing-publish E06-S4 spec E06S4-D2: who decides a revision and opens its real location. */
    private static final Set<String> REVIEWERS = Set.of("ROLE_LISTING_REVIEWER", "ROLE_ADMIN");

    private static final String SALE = "ROLE_SALE";

    private ListingAccessPolicy() {
    }

    /** Every listing for reviewers and managers; otherwise a SALE only while the current assignee (listing BR-5, BR-16). */
    public static boolean mayReadListing(Set<String> authorities, String viewerId, String assigneeIdOrNull) {
        return holdsAny(authorities, EVERY_LISTING) || isAssignedSale(authorities, viewerId, assigneeIdOrNull);
    }

    /** Every agreement's listing section for managers; otherwise a SALE only while the current assignee. */
    public static boolean mayReadAgreementListings(Set<String> authorities, String viewerId, String assigneeIdOrNull) {
        return holdsAny(authorities, EVERY_AGREEMENT) || isAssignedSale(authorities, viewerId, assigneeIdOrNull);
    }

    /**
     * Only the assigned Sale creates (listing BR-16: managers do not create in their place): a SALE or SALE_LEADER who is
     * the current assignee of the agreement's basis lead.
     */
    public static boolean mayCreate(Set<String> authorities, String viewerId, String assigneeIdOrNull) {
        return holdsAny(authorities, CREATORS) && isAssignee(viewerId, assigneeIdOrNull);
    }

    /**
     * Only the assigned Sale drafts a listing's revisions (listing BR-16; listing-publish section 2 "Không soạn thay":
     * managers do not draft in the Sale's place): a SALE or SALE_LEADER who is the current assignee of the agreement's
     * basis lead, the rule of {@link #mayCreate}.
     */
    public static boolean mayDraft(Set<String> authorities, String viewerId, String assigneeIdOrNull) {
        return holdsAny(authorities, CREATORS) && isAssignee(viewerId, assigneeIdOrNull);
    }

    /**
     * Only the assigned Sale records a contact with the person letting the unit (listing BR-16; listing-publish E06-S3
     * spec E06S3-D2): a SALE or SALE_LEADER who is the current assignee of the agreement's basis lead, the rule of
     * {@link #mayDraft}.
     */
    public static boolean mayRecordFreshness(Set<String> authorities, String viewerId, String assigneeIdOrNull) {
        return holdsAny(authorities, CREATORS) && isAssignee(viewerId, assigneeIdOrNull);
    }

    /**
     * Only the assigned Sale puts an approved revision live (listing BR-16; listing-publish US-5 AC-1; E06-S6 spec
     * E06S6-D2): a SALE or SALE_LEADER who is the current assignee of the agreement's basis lead, the rule of
     * {@link #mayDraft}.
     */
    public static boolean mayPutLive(Set<String> authorities, String viewerId, String assigneeIdOrNull) {
        return holdsAny(authorities, CREATORS) && isAssignee(viewerId, assigneeIdOrNull);
    }

    /**
     * Who sees every listing to reconfirm (listing-publish E06-S3 spec E06S3-D2): the managers of E05's agreement scope;
     * a SALE sees only the listings whose basis lead they hold.
     */
    public static boolean seesEveryReconfirmation(Set<String> authorities) {
        return holdsAny(authorities, EVERY_AGREEMENT);
    }

    /**
     * The review queue and the review read (listing-publish E06-S4 spec E06S4-D2): the roles that read every listing
     * (listing BR-5); a SALE alone reads neither.
     */
    public static boolean mayReadReviews(Set<String> authorities) {
        return holdsAny(authorities, EVERY_LISTING);
    }

    /**
     * A listing reviewer (listing-publish section 2 "Người duyệt tin"; E06-S4 spec E06S4-D2): LISTING_REVIEWER or ADMIN
     * (SUPER_ADMIN through the hierarchy); a manager decides only when also a listing reviewer.
     */
    public static boolean holdsReviewerRole(Set<String> authorities) {
        return holdsAny(authorities, REVIEWERS);
    }

    private static boolean isAssignedSale(Set<String> authorities, String viewerId, String assigneeIdOrNull) {
        return authorities != null && authorities.contains(SALE) && isAssignee(viewerId, assigneeIdOrNull);
    }

    private static boolean isAssignee(String viewerId, String assigneeIdOrNull) {
        return assigneeIdOrNull != null && assigneeIdOrNull.equals(viewerId);
    }

    private static boolean holdsAny(Set<String> authorities, Set<String> roles) {
        return authorities != null && !Collections.disjoint(authorities, roles);
    }
}
