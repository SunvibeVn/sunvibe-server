package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.command.UnitLocationOpenCommand;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.query.UnitLocationResult;

/**
 * Use Case - the review's commands (listing-publish US-4 AC-2 to AC-5; E06-S4 spec sections 3.3, 3.4): the one decision
 * of a Pending review revision, and the opening of its unit's real location with an access log. The caller's role is
 * the controller's gate; the scope, the order of the checks and the reviewer's independence are checked here.
 */
public interface ListingReviewCommandUseCase {

    /** Decides the revision once; answers the revision's new status and the listing's new data version. */
    RevisionCommandResult decide(RevisionDecisionCommand command, ListingViewer viewer);

    /** Answers the real location of the revision's unit to a reviewer who may decide it now, and logs the opening. */
    UnitLocationResult openUnitLocation(UnitLocationOpenCommand command, ListingViewer viewer);
}
