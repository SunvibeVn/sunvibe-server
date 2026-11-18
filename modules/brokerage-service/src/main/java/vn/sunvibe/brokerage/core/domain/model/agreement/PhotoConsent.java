package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

/**
 * The public photo consent of a version (brokerage-agreement BR-9): photos of the interior and common areas within the
 * unit's scope (yes, no, or null while a draft has no answer yet), and the exterior (front, entrance, street view) as a
 * separate choice that defaults to no. It never covers survey photos.
 *
 * @param interiorOrNull the interior and common areas; null = not answered yet
 * @param exterior       the exterior; true only with an interior yes
 */
public record PhotoConsent(Boolean interiorOrNull, boolean exterior) {

    /** Nothing answered yet: no interior answer, no exterior. */
    public static final PhotoConsent NONE = new PhotoConsent(null, false);

    public PhotoConsent {
        // brokerage-agreement BR-9 and BR-12: the exterior is a choice inside the interior yes, so it is never alone.
        if (exterior && !Boolean.TRUE.equals(interiorOrNull)) {
            throw new AgreementContentException("exteriorPhotoConsent");
        }
    }

    @Override
    public String toString() {
        return "PhotoConsent[hidden]";
    }
}
