package vn.sunvibe.survey.core.domain.model.media;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;

/**
 * The correction of a holder's removal of another uploader's committed photo (survey-media spec 4.7, BR-12;
 * survey-checklist BR-22 shape: why, and the date and text of the basis). Texts: never printed.
 */
public record MediaRemovalCorrection(String reason, Basis basis) {

    public MediaRemovalCorrection {
        if (reason == null || basis == null) {
            throw new IllegalArgumentException("A correction has its reason and basis.");
        }
    }

    @Override
    public String toString() {
        return "MediaRemovalCorrection[]";
    }
}
