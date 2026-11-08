package vn.sunvibe.survey.core.domain.model.media;

/**
 * What a photo shows (survey-media spec 4.1, 4.6, A-12), in the table order of survey-media spec 4.6, which orders coverage and
 * the missing items.
 */
public enum EvidenceCategory {
    /** The exterior and the access of the asset; required once for the asset. */
    EXTERIOR_ACCESS(CategoryLevel.ASSET, false, false),
    /** The main space of one observed space; required for each space. */
    MAIN_SPACE(CategoryLevel.SPACE, false, false),
    /** The kitchen of one space; required unless G4 marks it not applicable. */
    KITCHEN(CategoryLevel.SPACE, false, false),
    /** The bathroom of one space; required unless G4 marks it not applicable. */
    BATHROOM(CategoryLevel.SPACE, false, false),
    /** The shared kitchen; PARTS only, required unless G4 marks it not applicable. */
    SHARED_KITCHEN(CategoryLevel.ASSET, true, false),
    /** The shared bathroom; PARTS only, required unless G4 marks it not applicable. */
    SHARED_BATHROOM(CategoryLevel.ASSET, true, false),
    /** A shared amenity; PARTS only, never required (survey-media spec A-12). */
    SHARED_AMENITY(CategoryLevel.ASSET, true, true),
    /** A detail (damage, a hazard, a fixture) of the asset or a space; never required (survey-media spec A-12). */
    DETAIL(CategoryLevel.ASSET_OR_SPACE, false, true);

    private final CategoryLevel level;
    private final boolean partsOnly;
    private final boolean optional;

    EvidenceCategory(CategoryLevel level, boolean partsOnly, boolean optional) {
        this.level = level;
        this.partsOnly = partsOnly;
        this.optional = optional;
    }

    public CategoryLevel level() {
        return level;
    }

    /** survey-media spec A-9: a SHARED_* photo exists only while the rental scope is PARTS. */
    public boolean partsOnly() {
        return partsOnly;
    }

    /** survey-media spec A-12: never required and never satisfying a required category. */
    public boolean optional() {
        return optional;
    }

    public boolean allowsAsset() {
        return level != CategoryLevel.SPACE;
    }

    public boolean allowsSpace() {
        return level != CategoryLevel.ASSET;
    }
}
