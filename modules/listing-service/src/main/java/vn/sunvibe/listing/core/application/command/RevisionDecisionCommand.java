package vn.sunvibe.listing.core.application.command;

import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;

import java.util.List;

/**
 * A reviewer's decision on a revision (listing-publish US-4 AC-3; E06-S4 spec section 4.3, E06S4-D6): the data version
 * the reviewer read, the outcome, and the checks, reasons, group and note of that outcome, codes already mapped. The
 * revision number is null when the path's number is malformed. The reasons, group and note are private: its text names
 * the listing and the outcome only.
 */
public record RevisionDecisionCommand(String listingId, Integer revisionNoOrNull, long dataVersion, ReviewOutcome outcome,
                                      List<ReviewCheck> checks, List<ChangeReason> changeReasons,
                                      RejectionGroup rejectionGroupOrNull, String noteOrNull) {

    public RevisionDecisionCommand {
        checks = checks == null ? List.of() : List.copyOf(checks);
        changeReasons = changeReasons == null ? List.of() : List.copyOf(changeReasons);
    }

    @Override
    public String toString() {
        return "RevisionDecisionCommand[" + listingId + ", " + outcome + "]";
    }
}
