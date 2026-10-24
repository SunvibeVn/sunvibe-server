package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChecklistUnits;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ProvenanceUnit;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The next document of a group (survey-checklist spec A-19, A-29, PD-1): the changed units get their new metadata. A
 * unit that leaves the content while its key can come back keeps a tombstone, the stamp its removal got
 * ({@link MetaRules#removed}: the remover as last editor, survey-checklist BR-29): units the scope no longer allows
 * (shared wet areas, charge overrides, space fees), G6 items (shared, or of a space), other items and hazards
 * (survey-checklist BR-28, BR-30). Tombstones of a space's units go with the space, since a space id never comes back,
 * except other items, whose identity is their itemId. Tombstones a save does not touch stay as they are; a key coming
 * back takes its tombstone as metadata. Other units that leave the content lose their metadata.
 */
public final class GroupDocuments {

    private static final Pattern CAN_COME_BACK = Pattern.compile(
            "condition/shared(Kitchen|Bathroom)|terms/spaces/[^/]+/(chargeOverrides/[a-zA-Z]+|spaceFees)"
                    + "|amenities/(sharedItems/[A-Z_]+|spaces/[^/]+/items/[A-Z_]+|otherItems/[^/]+)"
                    + "|safety/hazards/[^/]+");

    private GroupDocuments() {
    }

    /**
     * @param changedMeta   the new metadata of every changed unit that stays
     * @param removalStamps the tombstone of every removed unit, as the save stamped it; a removed unit with metadata
     *                      but no stamp keeps its metadata as its tombstone
     */
    public static GroupDocument apply(GroupDocument stored, GroupContent next, Map<UnitKey, UnitMeta> changedMeta,
                                      Map<UnitKey, Tombstone> removalStamps) {
        Map<UnitKey, ProvenanceUnit> units = ChecklistUnits.of(next);
        Set<String> spaces = new HashSet<>();
        units.keySet().forEach(key -> {
            if (key.spaceId() != null) {
                spaces.add(key.spaceId());
            }
        });
        Map<UnitKey, UnitMeta> meta = new LinkedHashMap<>();
        Map<UnitKey, Tombstone> tombstones = new LinkedHashMap<>();
        stored.tombstones().forEach((key, tombstone) -> {
            if (key.spaceId() == null || spaces.contains(key.spaceId())) {
                tombstones.put(key, tombstone);
            }
        });
        stored.meta().forEach((key, value) -> {
            if (units.containsKey(key)) {
                meta.put(key, value);
            } else if (canComeBack(key, spaces)) {
                tombstones.put(key, Tombstone.of(value));
            }
        });
        removalStamps.forEach((key, stamp) -> {
            if (!units.containsKey(key) && canComeBack(key, spaces)) {
                tombstones.put(key, stamp);
            }
        });
        for (UnitKey key : units.keySet()) {
            if (!meta.containsKey(key) && tombstones.containsKey(key)) {
                meta.put(key, tombstones.remove(key).asMeta());
            }
        }
        changedMeta.forEach((key, value) -> {
            meta.put(key, value);
            tombstones.remove(key);
        });
        return new GroupDocument(stored.group(), next, meta, tombstones, stored.placeNamesOrNull());
    }

    private static boolean canComeBack(UnitKey key, Set<String> spaces) {
        return CAN_COME_BACK.matcher(key.value()).matches()
                && (key.spaceId() == null || spaces.contains(key.spaceId()));
    }
}
