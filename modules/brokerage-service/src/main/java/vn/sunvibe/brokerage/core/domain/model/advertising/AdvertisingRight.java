package vn.sunvibe.brokerage.core.domain.model.advertising;

import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Whether Sunvibe may advertise a unit now under one agreement, computed at every read and never stored
 * (brokerage-agreement BR-12, BR-13, US-4 AC-1, AC-2; E05-S6 spec S6-D7). Nothing else of the agreement: no contract
 * number, fee policy, owner, lead, intake, confirmation or activation date.
 *
 * @param allowed                exactly when {@code groups} is empty
 * @param groups                 the reason groups in the order of {@link AdvertisingReasonGroup}
 * @param endDateOrNull          the end date of the version that counts; null without one
 * @param endsAtOrNull           the start of the day after that end date in Vietnam; null without one
 * @param interiorPhotos         the interior and common-area photos may be public now (allowed and given)
 * @param exteriorPhotos         the exterior photos may be public now (allowed and given)
 * @param termsInEffectOrNull    the rental terms of the version in effect; null without one or once ended
 * @param pendingStartDateOrNull the start date of an activated version not in effect yet
 * @param pendingStartsAtOrNull  the start of that date in Vietnam
 * @param pendingTermsOrNull     that version's rental terms
 * @param ended                  the agreement reached a final state or the end of its end date has passed
 */
public record AdvertisingRight(boolean allowed, List<AdvertisingReasonGroup> groups, LocalDate endDateOrNull,
                               Instant endsAtOrNull, boolean interiorPhotos, boolean exteriorPhotos,
                               VersionTerms termsInEffectOrNull, LocalDate pendingStartDateOrNull,
                               Instant pendingStartsAtOrNull, VersionTerms pendingTermsOrNull, boolean ended) {

    public AdvertisingRight {
        groups = List.copyOf(groups);
    }

    /** brokerage-agreement BR-17: the groups and flags only; the terms and dates stay out of every log line. */
    @Override
    public String toString() {
        return "AdvertisingRight[allowed=" + allowed + ", groups=" + groups + ", ended=" + ended + "]";
    }
}
