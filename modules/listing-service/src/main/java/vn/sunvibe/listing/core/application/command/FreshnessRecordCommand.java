package vn.sunvibe.listing.core.application.command;

import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

/**
 * Record a contact of the assigned Sale with the person letting the unit (listing-publish US-6 AC-1, BR-9; E06-S3 spec
 * 3.1, E06S3-D4). No date: the server sets the Vietnam date; no actor: it is the principal.
 *
 * @param dataVersion the listing's row version the caller read (listing BR-3)
 * @param noteOrNull  the private note as typed (BR-17); the command stores it normalized
 */
public record FreshnessRecordCommand(String listingId, long dataVersion, FreshnessChannel channel,
                                     FreshnessOutcome outcome, Boolean termsStillApplyOrNull,
                                     Boolean unitUnchangedOrNull, String noteOrNull) {

    /** listing-publish BR-17: identifiers only; never the note, the outcome or an answer. */
    @Override
    public String toString() {
        return "FreshnessRecordCommand[listingId=" + listingId + ", dataVersion=" + dataVersion + "]";
    }
}
