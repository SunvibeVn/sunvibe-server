package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.OwnerReport;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ProvenanceUnit;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;

import java.time.Instant;
import java.util.Optional;

/**
 * The metadata a changed unit gets (survey-checklist spec 4.5, A-18, A-19, A-26), without the correcting save, which
 * the save sets. Entered by the caller now. Observed: by the visit's performer on the arrival date for an on-site value
 * and every hazard; by the receiver on the day the owner said it for an owner statement; by nobody, on the submission
 * date and with its form field, for the owner's form; by nobody for an authored note. A cleared unit keeps the source,
 * form field, observer and date of the value it last held; a removed one leaves the tombstone of {@link #removed}.
 */
public final class MetaRules {

    private MetaRules() {
    }

    public static UnitMeta next(UnitChange change, String callerUserId, Instant now, VisitFacts visit,
                                Optional<SubmittedForm> form) {
        if (change.kind() == ChangeKind.REMOVED) {
            throw new IllegalArgumentException("A removed unit has no metadata.");
        }
        if (change.kind() == ChangeKind.CLEARED) {
            UnitMeta stored = change.storedMeta();
            return stored == null
                    ? new UnitMeta(callerUserId, now, null, null, null, null, null)
                    : new UnitMeta(callerUserId, now, stored.observedByUserId(), stored.observedOn(),
                    stored.formField(), stored.lastSource(), null);
        }
        ProvenanceUnit unit = change.after();
        if (unit.kind() == UnitKind.NOTE) {
            return new UnitMeta(callerUserId, now, null, null, null, null, null);
        }
        ChecklistSource source = unit.kind() == UnitKind.HAZARD ? null : unit.source();
        if (source == ChecklistSource.OWNER_STATED) {
            return new UnitMeta(callerUserId, now, unit.receivedByUserId(), unit.confirmedOn(), null, source, null);
        }
        if (source == ChecklistSource.OWNER_FORM) {
            return new UnitMeta(callerUserId, now, null, form.map(SubmittedForm::submittedOn).orElse(null),
                    formField(unit.key().name()), source, null);
        }
        return new UnitMeta(callerUserId, now, visit.performerUserId(), visit.arrivalDate(), null, source, null);
    }

    /**
     * The tombstone a removal stamps (survey-checklist BR-29, BR-30, spec A-29), without the correcting save, which
     * the save sets: the remover as last editor at the removal time, never as observer; the observer, date, last
     * source and form field of the value the unit held; for a hazard with an owner fix report, who heard it and when.
     * A unit already cleared to UNKNOWN holds no value, so it keeps the metadata of its clearing.
     */
    public static Tombstone removed(UnitChange change, String callerUserId, Instant now) {
        if (change.kind() != ChangeKind.REMOVED) {
            throw new IllegalArgumentException("Only a removed unit leaves a tombstone.");
        }
        UnitMeta stored = change.storedMeta();
        ProvenanceUnit before = change.before();
        if (before.isUnknown()) {
            return Tombstone.of(stored);
        }
        OwnerReport report = before.ownerReportOrNull();
        Tombstone.OwnerReportFacts facts = report == null ? null
                : new Tombstone.OwnerReportFacts(report.receivedByUserId(), report.reportedOn());
        return stored == null
                ? new Tombstone(callerUserId, now, null, null, null, null, null, facts)
                : new Tombstone(callerUserId, now, stored.observedByUserId(), stored.observedOn(), stored.formField(),
                stored.lastSource(), null, facts);
    }

    /** The submission field an owner's-form unit comes from (spec 7.2). */
    static String formField(String fieldName) {
        return switch (fieldName) {
            case "observedAddress" -> "address";
            case "monthlyRent" -> "askingPrice";
            case "availableFrom" -> "availableFrom";
            default -> throw new IllegalArgumentException("No owner's form field for " + fieldName);
        };
    }
}
