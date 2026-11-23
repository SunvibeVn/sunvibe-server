package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The freshness of a listing (listing-publish US-6 AC-2, BR-10, BR-17; E06-S3 spec 3.2, E06S3-D8): the latest valid
 * confirmation's date, its reconfirmation day and last valid day, the two flags, and the latest record with its private
 * note. No user id, no history and no data version. Its text names only the listing.
 *
 * @param confirmedOnOrNull the Vietnam date of the latest valid confirmation; null until one
 * @param latestOrNull      the latest record; null before any
 */
public record ListingFreshnessView(String listingId, LocalDate confirmedOnOrNull, LocalDate reconfirmFromOrNull,
                                   LocalDate validThroughOrNull, boolean reconfirmationDue, boolean deadlinePassed,
                                   LatestRecord latestOrNull) {

    /** listing-publish BR-17: the listing id only; never the note, the outcome or an answer. */
    @Override
    public String toString() {
        return "ListingFreshnessView[listingId=" + listingId + "]";
    }

    /**
     * The latest record of a listing: the greatest row version (E06S3-D6).
     *
     * @param valid whether it is a valid confirmation (BR-9)
     */
    public record LatestRecord(LocalDate contactDate, Instant recordedAt, FreshnessChannel channel,
                               FreshnessOutcome outcome, Boolean termsStillApplyOrNull, Boolean unitUnchangedOrNull,
                               boolean valid, String noteOrNull) {

        /** listing-publish BR-17: never the note, the outcome or an answer. */
        @Override
        public String toString() {
            return "ListingFreshnessView.LatestRecord[hidden]";
        }
    }
}
