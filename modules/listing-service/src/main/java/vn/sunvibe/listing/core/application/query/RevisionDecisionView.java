package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;

import java.time.Instant;
import java.util.List;

/**
 * The decision of a revision as the listing's readers see it (listing-publish US-4 AC-6; E06-S4 spec section 4.4,
 * E06S4-D15): outcome, time, checklist version, reasons, group and note; never who decided. The reasons, group and note
 * are private: its text names the outcome only.
 */
public record RevisionDecisionView(ReviewOutcome outcome, Instant decidedAt, Integer checklistVersionOrNull,
                                   List<ChangeReason> changeReasons, RejectionGroup rejectionGroupOrNull,
                                   String noteOrNull) {

    public RevisionDecisionView {
        changeReasons = List.copyOf(changeReasons);
    }

    @Override
    public String toString() {
        return "RevisionDecisionView[" + outcome + "]";
    }
}
