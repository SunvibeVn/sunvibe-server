package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/**
 * One allowed (placement, category) pair of the evidence coverage (survey-media spec 4.6, 7.2): whether it is required,
 * the G4 reason of an exemption, its usable and processing photo counts and its state.
 */
public record CoveragePair(String spaceIdOrNull, EvidenceCategory category, boolean required,
                           String notRequiredReasonOrNull, int readyCount, int processingCount, CoverageState state) {

    /** A required pair that is not COVERED: one readiness item (spec 4.6). */
    public boolean missing() {
        return required && state != CoverageState.COVERED;
    }

    /** The G4 reason is staff text: print ids and counts only. */
    @Override
    public String toString() {
        return "CoveragePair[" + (spaceIdOrNull == null ? "asset" : spaceIdOrNull) + "/" + category + " " + state
                + "]";
    }
}
