package vn.sunvibe.survey.core.application.shared;

import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.OwnerReport;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKind;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rules of save step 9 that need stored data (survey-checklist spec 5.5), besides the source rules: spaces G1 does
 * not know, links to earlier hazards, the units only separate parts have against the scope read under the G1 lock,
 * and the owner fix-report dates. The space and scope rules run on the entries as the request sent them, with the
 * request's own paths (spec PD-5); links and dates on created or changed units only.
 */
public final class StoredDataRules {

    private static final Set<ChangeKind> CHANGED = Set.of(ChangeKind.FIRST_FILL, ChangeKind.ADDED_ENTRY,
            ChangeKind.REPLACED);

    private StoredDataRules() {
    }

    /** A space id the stored G1 does not hold, on per-space entries, hazards and other items. */
    public static void unknownSpaces(GroupContent content, IdentificationContent g1, List<Violation> violations) {
        Set<String> known = g1.spaces().stream().map(ObservedSpace::spaceId).collect(Collectors.toSet());
        switch (content) {
            case PartsContent g -> entries(g.spaces(), PartsSpace::spaceId, known, violations);
            case DimensionsContent g -> entries(g.spaces(), DimensionsSpace::spaceId, known, violations);
            case ConditionContent g -> entries(g.spaces(), ConditionSpace::spaceId, known, violations);
            case AmenitiesContent g -> {
                entries(g.spaces(), AmenitiesSpace::spaceId, known, violations);
                for (int i = 0; i < g.otherItems().size(); i++) {
                    OtherItem item = g.otherItems().get(i);
                    unknown("content.otherItems." + i + ".spaceId", item.spaceId(), known, violations);
                }
            }
            case TermsContent g -> entries(g.spaces(), TermsSpace::spaceId, known, violations);
            case SafetyContent g -> {
                if (g.hazards().isValue()) {
                    for (int i = 0; i < g.hazards().value().size(); i++) {
                        unknown("content.hazards.value." + i + ".spaceId", g.hazards().value().get(i).spaceId(),
                                known, violations);
                    }
                }
            }
            case IdentificationContent g -> {
            }
        }
    }

    /**
     * survey-checklist spec 4.6, PD-4: shared wet areas, charge overrides and space fees exist only in PARTS scope;
     * there a shared wet area is always a wrapper and the overrides always an object.
     */
    public static void scopeNulls(GroupContent content, RentalScope scope, List<Violation> violations) {
        boolean parts = scope == RentalScope.PARTS;
        if (content instanceof ConditionContent g) {
            sharedArea("content.sharedKitchen", g.sharedKitchen() != null, parts, violations);
            sharedArea("content.sharedBathroom", g.sharedBathroom() != null, parts, violations);
        }
        if (content instanceof TermsContent g) {
            for (int i = 0; i < g.spaces().size(); i++) {
                TermsSpace space = g.spaces().get(i);
                String at = "content.spaces." + i;
                if (parts && space.chargeOverrides() == null) {
                    violations.add(new Violation(at + ".chargeOverrides", at
                            + ".chargeOverrides is required for separate parts; send null members to keep the "
                            + "common charges."));
                }
                if (!parts && space.chargeOverrides() != null) {
                    violations.add(new Violation(at + ".chargeOverrides", at
                            + ".chargeOverrides must be null unless the property is in separate parts."));
                }
                if (!parts && space.spaceFees() != null) {
                    violations.add(new Violation(at + ".spaceFees", at
                            + ".spaceFees must be null unless the property is in separate parts."));
                }
            }
        }
    }

    /**
     * survey-checklist spec 4.6.1: a link names a hazard of an earlier checklist of the same lead and intent cycle;
     * another lead, cycle or an unknown id get the same answer.
     */
    public static void previousLinks(List<UnitChange> changes, Map<String, Set<String>> earlierHazards,
                                     List<Violation> violations) {
        for (UnitChange change : changes) {
            if (!CHANGED.contains(change.kind()) || change.after().kind() != UnitKind.HAZARD) {
                continue;
            }
            HazardLink link = ((Hazard) change.after().wholeValue()).previous();
            if (link != null && !earlierHazards.getOrDefault(link.checklistId(), Set.of()).contains(link.hazardId())) {
                violations.add(new Violation(change.path() + ".previous", change.path()
                        + ".previous must name a hazard of an earlier checklist of this lead and requirement."));
            }
        }
    }

    /**
     * survey-checklist spec A-7: an owner fix report the save creates or changes is dated from the arrival date of the
     * visit where its hazard was first recorded (the first hazard of its chain) to today.
     */
    public static void reportDates(List<UnitChange> changes, Function<Hazard, LocalDate> firstSightingArrival,
                                   LocalDate today, List<Violation> violations) {
        for (UnitChange change : changes) {
            if (!CHANGED.contains(change.kind()) || change.after().kind() != UnitKind.HAZARD) {
                continue;
            }
            OwnerReport report = change.after().ownerReportOrNull();
            OwnerReport before = change.before() == null ? null : change.before().ownerReportOrNull();
            if (report != null && !report.equals(before)) {
                ChecklistDateRules.requireReportDate(change.path() + ".ownerReportedOn", report.reportedOn(),
                        firstSightingArrival.apply((Hazard) change.after().wholeValue()), today)
                        .ifPresent(violations::add);
            }
        }
    }

    private static <T> void entries(List<T> entries, Function<T, String> idOf, Set<String> known,
                                    List<Violation> violations) {
        for (int i = 0; i < entries.size(); i++) {
            unknown("content.spaces." + i + ".spaceId", idOf.apply(entries.get(i)), known, violations);
        }
    }

    private static void unknown(String path, String spaceId, Set<String> known, List<Violation> violations) {
        if (spaceId != null && !known.contains(spaceId)) {
            violations.add(new Violation(path, path + " is not a space of this checklist."));
        }
    }

    private static void sharedArea(String path, boolean present, boolean parts, List<Violation> violations) {
        if (parts && !present) {
            violations.add(new Violation(path, path
                    + " is required for separate parts; send UNKNOWN when it was not checked."));
        } else if (!parts && present) {
            violations.add(new Violation(path, path + " must be null unless the property is in separate parts."));
        }
    }
}
