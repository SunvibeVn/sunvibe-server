package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChecklistUnits;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.OwnerReport;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ProvenanceUnit;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The correction rule (survey-checklist BR-22, BR-27, spec 4.5, A-19), in every group: replacing, clearing or removing
 * a recorded unit whose observer (the performer for on-site values, the receiver for owner statements, also the report's
 * receiver when a hazard's owner fix report is edited or removed) or last editor is not the caller needs a correction.
 * Free: units never recorded, new list entries, authored notes, owner's-form units, a new owner statement (a later
 * date, or the same date with another receiver and another business value), a first or newer owner fix report with the
 * hazard otherwise unchanged. Adding a charge override applies the rule to the recorded common charge too.
 * A G6 item, other item or hazard sent again under the identity it had is no new entry: its tombstone is its stored
 * metadata, so its observer and its remover protect it, and so does the receiver of a removed hazard's fix report
 * (BR-28..BR-30, spec A-29). A data test, not proof of a conversation (BR-27).
 */
public final class CorrectionPolicy {

    private static final Set<ChangeKind> PROTECTED = Set.of(ChangeKind.REPLACED, ChangeKind.CLEARED,
            ChangeKind.REMOVED);

    private CorrectionPolicy() {
    }

    /**
     * The keys of the units that need a correction, in change order. A new override under a common charge someone
     * else recorded brings both: the override, which the correction covers (spec 4.5: it points to the correcting
     * save), and the common charge it overrides.
     */
    public static List<UnitKey> requiring(GroupDocument stored, List<UnitChange> changes, String callerUserId,
                                          VisitFacts visit) {
        Set<UnitKey> requiring = new LinkedHashSet<>();
        Map<UnitKey, ProvenanceUnit> storedUnits = null;
        for (UnitChange change : changes) {
            if (PROTECTED.contains(change.kind()) && needsCorrection(stored, change, callerUserId)) {
                requiring.add(change.key());
            }
            if (addsOverride(change)) {
                if (storedUnits == null) {
                    storedUnits = ChecklistUnits.of(stored.content());
                }
                UnitKey common = UnitKey.commonTerm(change.key().name());
                if (commonNeedsCorrection(storedUnits.get(common), stored.meta().get(common), change.after(),
                        callerUserId)) {
                    requiring.add(change.key());
                    requiring.add(common);
                }
            }
        }
        return new ArrayList<>(requiring);
    }

    private static boolean needsCorrection(GroupDocument stored, UnitChange change, String callerUserId) {
        UnitMeta meta = change.storedMeta();
        if (meta == null || meta.lastSource() == ChecklistSource.OWNER_FORM) {
            return false;
        }
        ProvenanceUnit before = change.before();
        ProvenanceUnit after = change.after();
        UnitKind kind = (before != null ? before : after).kind();
        if (kind == UnitKind.NOTE) {
            return false;
        }
        if (change.kind() == ChangeKind.REPLACED) {
            if (isNewStatement(before, after, meta)) {
                return false;
            }
            // survey-checklist BR-30, spec A-29: only on a hazard the stored content holds; a hazard back from its
            // tombstone has no old content to show that only the report changed
            if (kind == UnitKind.HAZARD && before != null && isOnlyANewFixReport(before, after)) {
                return false;
            }
        }
        Set<String> protectors = new LinkedHashSet<>();
        protectors.add(meta.observedByUserId());
        protectors.add(meta.enteredByUserId());
        if (kind == UnitKind.HAZARD && before != null && before.ownerReportOrNull() != null
                && !Objects.equals(before.ownerReportOrNull(), after == null ? null : after.ownerReportOrNull())) {
            protectors.add(before.ownerReportOrNull().receivedByUserId());
        }
        // survey-checklist BR-30: a hazard coming back is also protected by who heard the fix report it carried when
        // it was removed; the rule reads the tombstone alone, never an old unit that is not there
        Tombstone tombstone = before == null ? stored.tombstone(change.key()) : null;
        if (kind == UnitKind.HAZARD && tombstone != null && tombstone.ownerReportOrNull() != null) {
            protectors.add(tombstone.ownerReportOrNull().receivedByUserId());
        }
        return protectors.stream().anyMatch(id -> id != null && !id.equals(callerUserId));
    }

    /**
     * BR-27, spec A-19: a stored owner statement replaced by an owner statement with a later date, or with the same date,
     * another receiver and another business value. A cleared statement keeps no business value, so only a later date.
     */
    private static boolean isNewStatement(ProvenanceUnit before, ProvenanceUnit after, UnitMeta meta) {
        boolean recorded = before != null && !before.isUnknown();
        ChecklistSource storedSource = recorded ? before.source() : meta.lastSource();
        if (storedSource != ChecklistSource.OWNER_STATED || after == null
                || after.source() != ChecklistSource.OWNER_STATED) {
            return false;
        }
        LocalDate storedDate = recorded ? before.confirmedOn() : meta.observedOn();
        if (storedDate == null || after.confirmedOn() == null) {
            return false;
        }
        if (after.confirmedOn().isAfter(storedDate)) {
            return true;
        }
        return recorded && after.confirmedOn().equals(storedDate)
                && !Objects.equals(after.receivedByUserId(), before.receivedByUserId())
                && !Objects.equals(after.businessValue(), before.businessValue());
    }

    /**
     * spec A-19: a hazard whose only change is its owner fix report, a first one (from OPEN) or a newer one under the
     * same test as owner statements, with description, severity, space and link unchanged. Only for a hazard the
     * stored content holds ({@code before} not null, spec A-29).
     */
    private static boolean isOnlyANewFixReport(ProvenanceUnit before, ProvenanceUnit after) {
        Hazard old = (Hazard) before.wholeValue();
        Hazard next = (Hazard) after.wholeValue();
        if (!Objects.equals(old.description(), next.description()) || old.severity() != next.severity()
                || !Objects.equals(old.spaceId(), next.spaceId()) || !Objects.equals(old.previous(), next.previous())) {
            return false;
        }
        OwnerReport oldReport = before.ownerReportOrNull();
        OwnerReport report = after.ownerReportOrNull();
        if (report == null) {
            return false;
        }
        if (oldReport == null) {
            return old.status() == HazardStatus.OPEN;
        }
        if (report.reportedOn() == null || oldReport.reportedOn() == null) {
            return false;
        }
        return report.reportedOn().isAfter(oldReport.reportedOn())
                || report.reportedOn().equals(oldReport.reportedOn())
                && !Objects.equals(report.receivedByUserId(), oldReport.receivedByUserId())
                && !Objects.equals(report.statusNote(), oldReport.statusNote());
    }

    /** A charge override a space did not have, now with a value (a first fill or a key back from its tombstone). */
    private static boolean addsOverride(UnitChange change) {
        return change.after() != null && change.after().kind() == UnitKind.OVERRIDE && !change.after().isUnknown()
                && (change.before() == null || change.before().isUnknown());
    }

    /**
     * spec A-19: the override replaces what the recorded common charge gives that space, so the rule applies to the
     * common unit, unless the override is a new owner statement against an owner-stated common value.
     */
    private static boolean commonNeedsCorrection(ProvenanceUnit common, UnitMeta meta, ProvenanceUnit override,
                                                 String callerUserId) {
        if (common == null || common.isUnknown() || meta == null) {
            return false;
        }
        if (isNewStatement(common, override, meta)) {
            return false;
        }
        return isSomeoneElse(meta.observedByUserId(), callerUserId)
                || isSomeoneElse(meta.enteredByUserId(), callerUserId);
    }

    private static boolean isSomeoneElse(String userId, String callerUserId) {
        return userId != null && !userId.equals(callerUserId);
    }
}
