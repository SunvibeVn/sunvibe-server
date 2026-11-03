package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * The sticky removal mark of a photo (survey-media spec 4.7, A-9): when, by whom, how, the correction of a holder
 * removal that needed one, and the G1 save of a cascade (whose own correction stands for it).
 */
public record MediaRemoval(Instant at, String byUserId, RemovalKind kind, MediaRemovalCorrection correctionOrNull,
                           Long bySaveIdOrNull) {

    public MediaRemoval {
        if (at == null || byUserId == null || kind == null) {
            throw new IllegalArgumentException("A removal has its time, actor and kind.");
        }
        if ((kind == RemovalKind.CASCADE) != (bySaveIdOrNull != null)) {
            throw new IllegalArgumentException("Exactly a cascade removal names its G1 save.");
        }
        if (kind == RemovalKind.CASCADE && correctionOrNull != null) {
            throw new IllegalArgumentException("A cascade removal carries no correction of its own.");
        }
    }

    @Override
    public String toString() {
        return "MediaRemoval[" + kind + "]";
    }
}
