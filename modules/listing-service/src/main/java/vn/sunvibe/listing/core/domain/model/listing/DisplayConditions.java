package vn.sunvibe.listing.core.domain.model.listing;

import java.util.List;

/**
 * The facts the display conditions of listing BR-4 and listing-publish BR-11 are decided on (listing-publish E06-S6
 * spec E06S6-D3, D4), read by the caller: the listing's state, E05's answer for the listing's own agreement (ended,
 * allowed and its reason groups in E05's order; no answer reads as not allowed with {@code NO_AGREEMENT}), E05's terms
 * in effect as a snapshot or none, the unit's availability or none when property knows no such unit, whether an intake
 * entry of the unit lists another availability, and the freshness summary. Its {@code toString} names no term or group.
 *
 * @param state                  the listing's stored state
 * @param agreementEnded         E05 reads the listing's agreement as ended
 * @param advertisingAllowed     E05 allows advertising the unit under the listing's agreement
 * @param agreementGroups        E05's reason groups, in E05's order
 * @param termsInEffectOrNull    E05's terms in effect as a snapshot, or null when E05 has none
 * @param unitAvailabilityOrNull the unit's availability, or null when property knows no such unit
 * @param availabilityDiffers    an intake entry of the unit lists {@code AVAILABILITY_DIFFERS}
 * @param freshness              the listing's freshness summary
 */
public record DisplayConditions(ListingState state, boolean agreementEnded, boolean advertisingAllowed,
                                List<AgreementReasonGroup> agreementGroups, RevisionTerms termsInEffectOrNull,
                                UnitAvailability unitAvailabilityOrNull, boolean availabilityDiffers,
                                FreshnessSummary freshness) {

    public DisplayConditions {
        if (state == null || agreementGroups == null || freshness == null) {
            throw new IllegalArgumentException("display conditions need the state, E05's groups and the freshness");
        }
        agreementGroups = List.copyOf(agreementGroups);
    }

    @Override
    public String toString() {
        return "DisplayConditions[" + state + "]";
    }
}
