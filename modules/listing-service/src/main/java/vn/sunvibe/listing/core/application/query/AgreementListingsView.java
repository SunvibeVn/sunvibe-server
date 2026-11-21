package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;

import java.time.Instant;
import java.util.List;

/**
 * The listing section of an agreement's page (listing-publish E06-S1 spec section 4, E06S1-D1, E06S1-D17): its
 * listings and whether the viewer may create one now.
 *
 * @param listings at most the 20 newest listings of the agreement, newest first
 * @param hasOlder the agreement has older listings than those answered
 */
public record AgreementListingsView(String agreementId, List<Item> listings, boolean hasOlder, Creation creation) {

    public AgreementListingsView {
        listings = List.copyOf(listings);
    }

    /** One listing of the agreement; its state is {@code ARCHIVED} when the agreement ended (E06S1-D5). */
    public record Item(String listingId, String code, ListingState state, Instant createdAt) {
    }

    /**
     * Whether a create would pass for this viewer now; a hint, the command decides.
     *
     * @param advertisingNotStarted E05's groups are exactly {@code [NOT_STARTED]}
     * @param reasonGroups          the groups that stop a create, in E05's order; empty when E05's answer allows one
     */
    public record Creation(boolean viewerMayCreate, boolean advertisingNotStarted,
                           List<AgreementReasonGroup> reasonGroups) {

        public Creation {
            reasonGroups = List.copyOf(reasonGroups);
        }
    }
}
