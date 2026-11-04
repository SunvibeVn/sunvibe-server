package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.util.Collection;

/**
 * Whether a PASSED revision can still be used (survey-checklist D3-2, submission spec 4.7): its cycle is the lead's
 * current one, the lead is not closed, and every frozen photo has its content and is not held. Computed on every read,
 * never stored (I-6).
 */
public final class PassedUsability {

    private PassedUsability() {
    }

    /**
     * @param frozenPhotos the photos of the revision's frozen evidence, as read
     */
    public static Usability of(int revisionIntentCycle, int leadIntentCycle, boolean leadClosed,
                               Collection<SurveyMedia> frozenPhotos) {
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
