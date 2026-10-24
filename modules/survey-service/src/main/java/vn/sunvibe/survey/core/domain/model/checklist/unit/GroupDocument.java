package vn.sunvibe.survey.core.domain.model.checklist.unit;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.TemplateV1;

import java.util.Map;

/**
 * The stored document of one group (survey-checklist spec PD-1): the content and, per unit key, the metadata of units
 * that hold or held a value, plus the tombstones of units that left the content while their key can come back: units
 * the scope no longer allows (spec A-19) and removed G6 items, other items and hazards, kept by the identity they can
 * come back under (spec A-29, survey-checklist BR-28..BR-30). The G1 document also keeps the province and ward names
 * of its observed address (null elsewhere and while the address is unknown).
 */
public record GroupDocument(ChecklistGroup group, GroupContent content, Map<UnitKey, UnitMeta> meta,
                            Map<UnitKey, Tombstone> tombstones, ObservedPlaceNames placeNamesOrNull) {

    public GroupDocument(ChecklistGroup group, GroupContent content, Map<UnitKey, UnitMeta> meta,
                         Map<UnitKey, Tombstone> tombstones) {
        this(group, content, meta, tombstones, null);
    }

    public GroupDocument {
        if (content == null || content.group() != group) {
            throw new IllegalArgumentException("The content must be of the document's group.");
        }
        meta = Map.copyOf(meta);
        tombstones = Map.copyOf(tombstones);
    }

    /** The document of a new draft: the empty template content, no metadata. */
    public static GroupDocument initial(ChecklistGroup group) {
        return new GroupDocument(group, TemplateV1.empty(group), Map.of(), Map.of());
    }

    /** The same document with the place names a G1 save got from the intake location rules. */
    public GroupDocument withPlaceNames(ObservedPlaceNames names) {
        return new GroupDocument(group, content, meta, tombstones, names);
    }

    /** The metadata of the unit, else the metadata its tombstone holds, else null. */
    public UnitMeta metaOrTombstone(UnitKey key) {
        UnitMeta stored = meta.get(key);
        if (stored != null) {
            return stored;
        }
        Tombstone tombstone = tombstones.get(key);
        return tombstone == null ? null : tombstone.asMeta();
    }

    /** The tombstone of a unit that left the content, else null. */
    public Tombstone tombstone(UnitKey key) {
        return tombstones.get(key);
    }

    @Override
    public String toString() {
        return "GroupDocument[" + group + ", " + meta.size() + " meta]";
    }
}
