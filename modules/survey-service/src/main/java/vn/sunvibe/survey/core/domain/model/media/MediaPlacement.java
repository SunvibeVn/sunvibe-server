package vn.sunvibe.survey.core.domain.model.media;

/**
 * Where a photo belongs (survey-media spec 4.1, A-9): the asset (no space) or one observed space of G1, with the
 * space's code at upload time so a later rename shows "uploaded as P101". Never changes (I-1).
 */
public record MediaPlacement(String spaceIdOrNull, String spaceCodeAtUploadOrNull) {

    public MediaPlacement {
        if ((spaceIdOrNull == null) != (spaceCodeAtUploadOrNull == null)) {
            throw new IllegalArgumentException("A space placement has its id and code; the asset has neither.");
        }
    }

    public static MediaPlacement asset() {
        return new MediaPlacement(null, null);
    }

    public static MediaPlacement space(String spaceId, String spaceCodeAtUpload) {
        return new MediaPlacement(spaceId, spaceCodeAtUpload);
    }

    public boolean isAsset() {
        return spaceIdOrNull == null;
    }

    /** The space code is checklist text: print the id only. */
    @Override
    public String toString() {
        return "MediaPlacement[" + (spaceIdOrNull == null ? "asset" : spaceIdOrNull) + "]";
    }
}
