package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.OwnerReport;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ProvenanceUnit;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKind;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The source rules of save step 9 (survey-checklist BR-21, BR-25, spec A-18, A-25), on created or changed units only:
 * an owner statement or owner fix report the save creates or changes names a receiver of the lead's staff and carries
 * a relay basis exactly when the receiver is not the caller; an owner's-form value is the submitted one, of a lead with
 * an owner's form, and a per-space one only in WHOLE scope. Unchanged units keep what they store.
 */
public final class SourceRules {

    /** spec A-25: one message for an unknown id and for a staff id with no trace on the lead. */
    public static final String RECEIVER_MESSAGE = "Choose who heard it from the lead's staff.";
    private static final Set<ChangeKind> CHECKED = EnumSet.of(ChangeKind.FIRST_FILL, ChangeKind.ADDED_ENTRY,
            ChangeKind.REPLACED);

    private SourceRules() {
    }

    public static List<Violation> check(List<UnitChange> changes, String callerUserId, Set<String> leadStaff,
                                        Optional<SubmittedForm> form, RentalScope scopeUnderLock) {
        List<Violation> violations = new ArrayList<>();
        for (UnitChange change : changes) {
            if (!CHECKED.contains(change.kind())) {
                continue;
            }
            ProvenanceUnit unit = change.after();
            String path = change.path() == null ? "content" : change.path();
            if (unit.kind() == UnitKind.HAZARD) {
                OwnerReport report = unit.ownerReportOrNull();
                OwnerReport before = change.before() == null ? null : change.before().ownerReportOrNull();
                if (report != null && !report.equals(before)) {
                    statement(violations, path, report.receivedByUserId(), report.relayBasis(), callerUserId,
                            leadStaff);
                }
            } else if (unit.source() == ChecklistSource.OWNER_STATED) {
                statement(violations, path, unit.receivedByUserId(), unit.relayBasis(), callerUserId, leadStaff);
            } else if (unit.source() == ChecklistSource.OWNER_FORM) {
                ownerForm(violations, path, unit, form, scopeUnderLock);
            }
        }
        return violations;
    }

    private static void statement(List<Violation> violations, String path, String receiver, String relayBasis,
                                  String callerUserId, Set<String> leadStaff) {
        if (receiver == null || !leadStaff.contains(receiver)) {
            violations.add(new Violation(path + ".receivedByUserId", RECEIVER_MESSAGE));
        } else if (!receiver.equals(callerUserId) && relayBasis == null) {
            violations.add(new Violation(path + ".relayBasis",
                    path + ".relayBasis is required when someone else heard the owner."));
        } else if (receiver.equals(callerUserId) && relayBasis != null) {
            violations.add(new Violation(path + ".relayBasis",
                    path + ".relayBasis must be empty when you heard the owner yourself."));
        }
    }

    private static void ownerForm(List<Violation> violations, String path, ProvenanceUnit unit,
                                  Optional<SubmittedForm> form, RentalScope scope) {
        String sourcePath = path + ".source";
        if (form.isEmpty()) {
            violations.add(new Violation(sourcePath, sourcePath + " cannot be OWNER_FORM: the lead has no owner's form."));
            return;
        }
        if (unit.key().spaceId() != null && scope != RentalScope.WHOLE) {
            violations.add(new Violation(sourcePath, sourcePath
                    + " cannot be OWNER_FORM for a single part: the owner's form gives one value for the whole "
                    + "property."));
            return;
        }
        SubmittedForm submitted = form.get();
        boolean matches = switch (unit.key().name()) {
            case "observedAddress" -> OwnerFormMatch.matchesAddress((ObservedLocation) unit.businessValue(), submitted);
            case "monthlyRent" -> OwnerFormMatch.matchesRent((RentMoney) unit.businessValue(), submitted);
            case "availableFrom" -> OwnerFormMatch.matchesAvailableFrom((LocalDate) unit.businessValue(), submitted);
            default -> false;
        };
        if (!matches) {
            violations.add(new Violation(sourcePath, sourcePath
                    + " cannot be OWNER_FORM: the owner's form does not hold this value."));
        }
    }
}
