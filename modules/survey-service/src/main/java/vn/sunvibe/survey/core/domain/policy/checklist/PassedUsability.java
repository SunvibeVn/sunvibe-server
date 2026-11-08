package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.util.Collection;

/**
 * Whether a PASSED revision can still be used (survey-checklist D3-2, survey-checklist submission spec 4.7): it is not requalified, its
 * cycle is the lead's current one, the lead is not closed, and every frozen photo has its content and is not held.
 * Computed on every read, never stored (I-6).
 */
public final class PassedUsability {

    private PassedUsability() {
    }

    /**
     * @param requalified  the revision carries the mark, which outlasts every other reason
     * @param frozenPhotos the photos of the revision's frozen evidence, as read
     */
    public static Usability of(boolean requalified, int revisionIntentCycle, int leadIntentCycle, boolean leadClosed,
                               Collection<SurveyMedia> frozenPhotos) {
        if (requalified) {
            return Usability.not(UsabilityReason.REQUALIFIED);
        }
        if (revisionIntentCycle != leadIntentCycle) {
            return Usability.not(UsabilityReason.INTENT_CHANGED);
        }
        if (leadClosed) {
            return Usability.not(UsabilityReason.LEAD_CLOSED);
        }
        // a release cannot bring lost content back, so the permanent reason comes first
        if (frozenPhotos.stream().anyMatch(photo -> !photo.isContentPresent())) {
            return Usability.not(UsabilityReason.EVIDENCE_GONE);
        }
        if (frozenPhotos.stream().anyMatch(SurveyMedia::isHeld)) {
            return Usability.not(UsabilityReason.EVIDENCE_HELD);
        }
        return Usability.USABLE;
    }
}
