package vn.sunvibe.survey.core.domain.model.checklist.unit;

import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.policy.checklist.InputForm;
import vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1Rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The units a save changes (survey-checklist spec 4.5, A-19, PD-5). The stored side is read as GET shows it
 * ({@link InputForm#ofStored}) with the G1 content that was stored; the next side is canonical with the new G1, so a
 * null unit only separate parts have and an UNKNOWN one are the same, the entries of a removed space are REMOVED, and
 * a reorder lists no unit. A key coming back from its tombstone (a shared wet area, or a G6 item, other item or hazard
 * sent again under its identity, spec A-29) is REPLACED with the tombstone as its stored metadata. Unchanged units are
 * not listed.
 */
public final class UnitDiff {

    private static final Set<UnitKind> ENTRIES = Set.of(UnitKind.SPACE, UnitKind.HAZARD, UnitKind.ITEM);

    private UnitDiff() {
    }

    /** The changes of a save of the group itself, G1 unchanged (any group but G1) or the G1 save's own diff. */
    public static List<UnitChange> between(GroupDocument stored, GroupContent next, IdentificationContent g1) {
        return between(stored, g1, next, g1);
    }

    /**
     * The changes from the stored document read with the stored G1 to the next content canonical with the next G1
     * (a cascade of a G1 save). Paths are those of the next content as given.
     */
    public static List<UnitChange> between(GroupDocument stored, IdentificationContent storedG1, GroupContent next,
                                           IdentificationContent nextG1) {
        Map<UnitKey, ProvenanceUnit> before = ChecklistUnits.of(InputForm.ofStored(stored, storedG1));
        Map<UnitKey, String> paths = new HashMap<>();
        for (ChecklistUnits.Located located : ChecklistUnits.walk(next)) {
            paths.putIfAbsent(located.unit().key(), located.path());
        }
        LinkedHashMap<UnitKey, ProvenanceUnit> after = ChecklistUnits.of(TemplateV1Rules.canonical(next, nextG1));
        List<UnitChange> changes = new ArrayList<>();
        for (ProvenanceUnit unit : after.values()) {
            UnitKey key = unit.key();
            ProvenanceUnit old = before.get(key);
            UnitMeta storedMeta = stored.metaOrTombstone(key);
            ChangeKind kind = kindOf(old, unit, storedMeta);
            if (kind != null) {
                changes.add(new UnitChange(key, kind, old, unit, storedMeta, paths.get(key)));
            }
        }
        for (ProvenanceUnit old : before.values()) {
            UnitMeta storedMeta = stored.metaOrTombstone(old.key());
            if (!after.containsKey(old.key()) && (!old.isUnknown() || storedMeta != null)) {
                changes.add(new UnitChange(old.key(), ChangeKind.REMOVED, old, null, storedMeta, null));
            }
        }
        return changes;
    }

    private static ChangeKind kindOf(ProvenanceUnit old, ProvenanceUnit next, UnitMeta storedMeta) {
        if (old != null && old.state() == next.state() && Objects.equals(old.wholeValue(), next.wholeValue())) {
            return null;
        }
        if (next.isUnknown()) {
            return old == null || old.isUnknown() ? null : ChangeKind.CLEARED;
        }
        if (storedMeta == null && (old == null || old.isUnknown())) {
            return old == null && ENTRIES.contains(next.kind()) ? ChangeKind.ADDED_ENTRY : ChangeKind.FIRST_FILL;
        }
        return ChangeKind.REPLACED;
    }
}
