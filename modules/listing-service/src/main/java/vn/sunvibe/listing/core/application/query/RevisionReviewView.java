package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;

import java.time.LocalDate;
import java.util.List;

/**
 * What a reviewer reads beside a revision's content (listing-publish US-4 AC-2, AC-4, AC-5; E06-S4 spec section 4.2,
 * E06S4-D11): the listing's code and data version, whether the viewer may decide and why not, E05's answer, the terms
 * check, the unit's code and availability, the freshness summary and the checklist. Never the real location. E05's terms
 * are private: its text names ids only.
 */
public record RevisionReviewView(String listingId, String code, int revisionNo, RevisionStatus status, long dataVersion,
                                 boolean viewerMayReview, List<ReviewIneligibility> reasons,
                                 Advertising advertisingOrNull, TermsMatch termsMatch, Unit unit, Freshness freshness,
                                 List<ReviewCheck> checklist) {

    public RevisionReviewView {
        reasons = List.copyOf(reasons);
        checklist = List.copyOf(checklist);
    }

    /** E05's answer for the listing's pair: allowed or the groups in E05's order, its end, consents and terms. */
    public record Advertising(boolean allowed, boolean ended, List<AgreementReasonGroup> reasonGroups,
                              LocalDate endDateOrNull, boolean interiorPhotos, boolean exteriorPhotos,
                              RevisionTerms termsInEffectOrNull, LocalDate pendingStartDateOrNull,
                              RevisionTerms pendingOrNull) {

        public Advertising {
            reasonGroups = List.copyOf(reasonGroups);
        }

        @Override
        public String toString() {
            return "Advertising[hidden]";
        }
    }

    /** The listing's unit as property answers it now; no availability when property answers no unit. */
    public record Unit(String unitCodeOrNull, UnitAvailability availabilityOrNull, boolean availabilityDiffers) {
    }

    /** The listing's freshness summary (E06-S3): the latest valid confirmation, its last valid day, the deadline. */
    public record Freshness(LocalDate confirmedOnOrNull, LocalDate validThroughOrNull, boolean deadlinePassed,
                            Boolean latestValidOrNull) {
    }

    @Override
    public String toString() {
        return "RevisionReviewView[" + listingId + ", " + revisionNo + "]";
    }
}
