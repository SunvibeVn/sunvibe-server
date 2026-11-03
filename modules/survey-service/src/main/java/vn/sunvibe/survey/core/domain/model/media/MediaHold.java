package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * The ADMIN security hold of a photo (survey-media spec A-23). Held is defined once: {@code heldAt} set and
 * {@code releasedAt} not set. A hold sets the hold trio and clears the release pair; a release sets the release pair at
 * {@code max(now, heldAt)} and nulls nothing, so the last hold stays on the row until a later hold overwrites it.
 */
public record MediaHold(Instant heldAt, String heldByUserId, HoldReason reason, Instant releasedAt,
                        String releasedByUserId) {

    /** A photo that was never held. */
    public static final MediaHold NONE = new MediaHold(null, null, null, null, null);

    public MediaHold {
        if ((heldAt == null) != (heldByUserId == null) || (heldAt == null) != (reason == null)) {
            throw new IllegalArgumentException("A hold has its time, actor and reason together.");
        }
        if ((releasedAt == null) != (releasedByUserId == null)) {
            throw new IllegalArgumentException("A release has its time and actor together.");
        }
        if (releasedAt != null && (heldAt == null || releasedAt.isBefore(heldAt))) {
            throw new IllegalArgumentException("A release follows its hold.");
        }
    }

    public boolean isHeld() {
        return heldAt != null && releasedAt == null;
    }

    /**
     * A new hold by the ADMIN. A photo whose last hold was CREDENTIAL_EXPOSURE keeps that reason (spec A-23), so its
     * caption stays hidden after the next release too.
     *
     * @throws IllegalStateException when already held (the caller answers the no-op first)
     */
    public MediaHold hold(String byUserId, HoldReason newReason, Instant now) {
        if (isHeld()) {
            throw new IllegalStateException("The photo is already held.");
        }
        HoldReason kept = reason == HoldReason.CREDENTIAL_EXPOSURE ? HoldReason.CREDENTIAL_EXPOSURE : newReason;
        return new MediaHold(now, byUserId, kept, null, null);
    }

    /**
     * The release at {@code max(now, heldAt)} (spec A-23: {@code GREATEST(:appNow, held_at)}).
     *
     * @throws IllegalStateException when not held (the caller answers the no-op first)
     */
    public MediaHold release(String byUserId, Instant now) {
        if (!isHeld()) {
            throw new IllegalStateException("The photo is not held.");
        }
        Instant at = now.isBefore(heldAt) ? heldAt : now;
        return new MediaHold(heldAt, heldByUserId, reason, at, byUserId);
    }

    /**
     * spec 7.2, A-23: the caption is hidden from a viewer without hold rights while held, and after a release while the
     * last reason is CREDENTIAL_EXPOSURE.
     */
    public boolean captionHidden(boolean viewerCanHold) {
        return !viewerCanHold && (isHeld() || reason == HoldReason.CREDENTIAL_EXPOSURE);
    }

    @Override
    public String toString() {
        return "MediaHold[" + (isHeld() ? "held" : heldAt == null ? "never" : "released") + "]";
    }
}
