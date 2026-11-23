package vn.sunvibe.listing.core.domain.policy.listing;

import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The display conditions of a revision (listing BR-4, listing-publish BR-11; E06-S6 spec E06S6-D3): the reasons that
 * stop it, in the fixed order of {@link DisplayReason}, each at most once; an empty answer means every condition holds.
 * Pure: the caller reads the facts and passes {@code now}. A listing read as archived never reaches the rule (its callers
 * refuse it first); the rule would answer {@code LISTING_STATE} for it like any other state that does not allow display.
 */
public final class ListingDisplayPolicy {

    private ListingDisplayPolicy() {
    }

    /** The reasons that stop an approved revision from going live: the listing must be Unpublished or Published. */
    public static List<DisplayReason> putLiveReasons(DisplayConditions conditions, RevisionTerms snapshotOrNull,
                                                     Instant now) {
        boolean stateAllows = conditions.state() == ListingState.UNPUBLISHED
                || conditions.state() == ListingState.PUBLISHED;
        return reasons(stateAllows, conditions, snapshotOrNull, now);
    }

    /** The reasons that stop the live revision from being shown: the listing must be Published. */
    public static List<DisplayReason> liveReasons(DisplayConditions conditions, RevisionTerms snapshotOrNull,
                                                  Instant now) {
        return reasons(conditions.state() == ListingState.PUBLISHED, conditions, snapshotOrNull, now);
    }

    private static List<DisplayReason> reasons(boolean stateAllows, DisplayConditions conditions,
                                               RevisionTerms snapshotOrNull, Instant now) {
        List<DisplayReason> reasons = new ArrayList<>();
        // listing section 3.2, listing-publish BR-11: Paused and Urgently unpublished listings show nothing
        if (!stateAllows) {
            reasons.add(DisplayReason.LISTING_STATE);
        }
        // listing BR-4 (b): the listing's own agreement allows advertising its unit now
        if (conditions.agreementEnded() || !conditions.advertisingAllowed()) {
            reasons.add(DisplayReason.ADVERTISING_RIGHT);
        }
        // listing BR-4 (c), listing-publish BR-4: the snapshot equals E05's terms in effect by value
        if (RevisionTermsMatchPolicy.match(snapshotOrNull, conditions.termsInEffectOrNull(), null)
                != TermsMatch.IN_EFFECT) {
            reasons.add(DisplayReason.TERMS_NOT_IN_EFFECT);
        }
        // listing BR-4 (d), listing-publish BR-15: the unit is available and no intake entry says otherwise
        if (conditions.unitAvailabilityOrNull() != UnitAvailability.AVAILABLE || conditions.availabilityDiffers()) {
            reasons.add(DisplayReason.UNIT_NOT_AVAILABLE);
        }
        // listing BR-4 (e), listing-publish BR-10, BR-11: a valid confirmation whose deadline has not passed
        if (!FreshnessPolicy.holds(conditions.freshness(), now)) {
            reasons.add(DisplayReason.FRESHNESS);
        }
        return List.copyOf(reasons);
    }
}
