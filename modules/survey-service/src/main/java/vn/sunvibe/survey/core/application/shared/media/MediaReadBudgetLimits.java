package vn.sunvibe.survey.core.application.shared.media;

import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;

/**
 * The per-user hourly read budgets (survey-media spec A-26), set by {@code app.survey.media.budgets.*} (spec 9
 * configuration); {@link #SPEC} holds the spec values, which are also the property defaults.
 */
public record MediaReadBudgetLimits(long full, long preview, long thumbnail, long list) {

    /** survey-media spec A-26: FULL 600, PREVIEW 1,200, THUMBNAIL 6,000 and the media list 1,200 an hour. */
    public static final MediaReadBudgetLimits SPEC = new MediaReadBudgetLimits(600, 1_200, 6_000, 1_200);

    public MediaReadBudgetLimits {
        if (full < 1 || preview < 1 || thumbnail < 1 || list < 1) {
            throw new IllegalArgumentException("Every read budget allows at least one read an hour.");
        }
    }

    /** The hourly budget of a kind of read. */
    public long of(MediaCounterKind kind) {
        return switch (kind) {
            case FULL -> full;
            case PREVIEW -> preview;
            case THUMBNAIL -> thumbnail;
            case LIST -> list;
            case EXPENSIVE, PROC_MS -> throw new IllegalArgumentException(kind + " is no read budget");
        };
    }
}
