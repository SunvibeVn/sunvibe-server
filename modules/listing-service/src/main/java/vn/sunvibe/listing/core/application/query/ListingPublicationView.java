package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;

import java.time.Instant;
import java.util.List;

/**
 * The publication of a listing (listing-publish US-2 AC-7, US-5 AC-3, BR-11; E06-S6 spec E06S6-D11): its effective state
 * and data version, the live revision with the answer of the rule of a shown revision, the Approved revision waiting with
 * its terms source and the answer of the rule of a put live, and what the viewer may do. A listing read as archived has
 * neither revision and permits nothing. A hint: the command decides.
 *
 * @param liveOrNull     the live revision; none when no revision is live or the listing reads archived
 * @param approvedOrNull the Approved revision waiting; none when no revision waits or the listing reads archived
 */
public record ListingPublicationView(String listingId, ListingState state, long dataVersion, Live liveOrNull,
                                     Approved approvedOrNull, boolean viewerMayPutLive, boolean viewerMayCopy) {

    /** The live revision: its number, the time it went live and whether its display conditions hold. */
    public record Live(int revisionNo, Instant wentLiveAt, DisplayAnswer display) {
    }

    /** The Approved revision waiting: its number, the source of its terms snapshot and whether it may go live now. */
    public record Approved(int revisionNo, TermsSource termsSource, DisplayAnswer display) {
    }

    /**
     * The answer of the display rule (E06S6-D3): {@code holds} exactly when no reason stops it; the reasons in their fixed
     * order; E05's groups only when the advertising right is one of the reasons. Its text names no reason or group
     * (listing-publish BR-17), as the review's {@code Advertising} hides E05's answer.
     */
    public record DisplayAnswer(boolean holds, List<DisplayReason> reasons, List<AgreementReasonGroup> reasonGroups) {

        public DisplayAnswer {
            reasons = List.copyOf(reasons);
            reasonGroups = List.copyOf(reasonGroups);
        }

        @Override
        public String toString() {
            return "DisplayAnswer[" + holds + "]";
        }
    }
}
