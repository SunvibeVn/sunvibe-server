package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.EnumSet;
import java.util.Set;

/** The sources a sourced field accepts (survey-checklist spec 4.6). */
public enum SourceFamily {
    /** observedAddress: seen on site, owner said, owner's form. */
    LOCATION(EnumSet.of(ChecklistSource.OBSERVED_ON_SITE, ChecklistSource.OWNER_STATED, ChecklistSource.OWNER_FORM)),
    /** area: measured, estimated, owner said. */
    AREA(EnumSet.of(ChecklistSource.MEASURED, ChecklistSource.ESTIMATED, ChecklistSource.OWNER_STATED)),
    /** floors, rooms, bathrooms, shared bathroom use: seen on site, owner said. */
    COUNT(EnumSet.of(ChecklistSource.OBSERVED_ON_SITE, ChecklistSource.OWNER_STATED)),
    /** dates and rental terms: seen on site, owner said, owner's form (only where spec A-18 allows it). */
    TERMS(EnumSet.of(ChecklistSource.OBSERVED_ON_SITE, ChecklistSource.OWNER_STATED, ChecklistSource.OWNER_FORM)),
    /** furnishing and amenity items: seen on site, owner said. */
    AMENITY(EnumSet.of(ChecklistSource.OBSERVED_ON_SITE, ChecklistSource.OWNER_STATED));

    private final Set<ChecklistSource> sources;

    SourceFamily(Set<ChecklistSource> sources) {
        this.sources = sources;
    }

    public boolean allows(ChecklistSource source) {
        return sources.contains(source);
    }
}
