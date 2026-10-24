package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheckResult;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.Observation;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourceFamily;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The body rules of G4 and G5 (survey-checklist spec 4.6, 4.6.1; BR-16, BR-17, BR-18). */
final class ConditionSafetyRules {

    static final int MAX_HAZARDS = 30;

    private ConditionSafetyRules() {
    }

    // ==================== G4 condition ====================

    /** Shared wet areas may be null here: whether the scope asks for them is a rule of save step 9 (spec 5.5). */
    static ConditionContent condition(BodyChecker check, ConditionContent g) {
        Field<AreaCheck> sharedKitchen = g.sharedKitchen() == null ? null
                : areaCheck(check, "content.sharedKitchen", g.sharedKitchen(), true);
        Field<AreaCheck> sharedBathroom = g.sharedBathroom() == null ? null
                : areaCheck(check, "content.sharedBathroom", g.sharedBathroom(), true);
        List<ConditionSpace> spaces = TemplateV1Rules.entries(check, g.spaces(), ConditionSpace::spaceId,
                (at, space) -> new ConditionSpace(space.spaceId(),
                        check.field(at + ".occupancy", space.occupancy(), false, (path, value) -> value),
                        futureDate(check, at + ".expectedVacantFrom", space.expectedVacantFrom(), false),
                        futureDate(check, at + ".availableFrom", space.availableFrom(), true),
                        areaCheck(check, at + ".mainSpace", space.mainSpace(), false),
                        areaCheck(check, at + ".kitchen", space.kitchen(), true),
                        areaCheck(check, at + ".bathroom", space.bathroom(), true)));
        return new ConditionContent(sharedKitchen, sharedBathroom, spaces);
    }

    /**
     * {@code expectedVacantFrom} and {@code availableFrom}: sourced dates from 2000-01-01 to three years ahead; an
     * owner's-form {@code availableFrom} only has to equal the submitted date, checked at save step 9 (spec A-7).
     */
    private static SourcedField<LocalDate> futureDate(BodyChecker check, String path, SourcedField<LocalDate> field,
                                                      boolean ownerFormAllowed) {
        boolean ownerForm = field != null && field.source() == ChecklistSource.OWNER_FORM && ownerFormAllowed;
        return check.sourced(path, field, SourceFamily.TERMS, ownerFormAllowed, false, (valuePath, value) -> {
            if (!ownerForm) {
                ChecklistDateRules.requireFutureDate(valuePath, value, check.today())
                        .ifPresent(violation -> check.add(violation.path(), violation.message()));
            }
            return value;
        });
    }

    /**
     * survey-checklist BR-17, spec 4.6: INSPECTED needs {@code noneObserved} and a note when something was seen;
     * NOT_ENTERED (not entered or only partly entered) needs what was not reached and why and may carry a note.
     */
    private static Field<AreaCheck> areaCheck(BodyChecker check, String path, Field<AreaCheck> field,
                                              boolean naAllowed) {
        return check.field(path, field, naAllowed, (valuePath, value) -> {
            AreaCheckResult result = check.present(valuePath + ".result", value.result());
            if (result == AreaCheckResult.NOT_ENTERED) {
                if (value.noneObserved() != null) {
                    check.mustBeEmpty(valuePath + ".noneObserved", "unless the area was inspected");
                }
                return new AreaCheck(result, null, check.optionalText(valuePath + ".note", value.note(), 1000),
                        check.text(valuePath + ".notEnteredReason", value.notEnteredReason(), 500));
            }
            if (value.notEnteredReason() != null) {
                check.mustBeEmpty(valuePath + ".notEnteredReason", "unless the area was not entered");
            }
            Observation observation = observation(check, valuePath, new Observation(value.noneObserved(),
                    value.note()));
            return new AreaCheck(result, observation.noneObserved(), observation.note(), null);
        });
    }

    /** {@code noneObserved} required; a note of 1..1000 when something was seen (spec 4.6). */
    private static Observation observation(BodyChecker check, String path, Observation value) {
        Boolean noneObserved = check.present(path + ".noneObserved", value.noneObserved());
        String note = Boolean.FALSE.equals(noneObserved)
                ? check.text(path + ".note", value.note(), 1000)
                : check.optionalText(path + ".note", value.note(), 1000);
        return new Observation(noneObserved, note);
    }

    // ==================== G5 safety ====================

    static SafetyContent safety(BodyChecker check, SafetyContent g) {
        Field<String> egress = check.field("content.egress", g.egress(), false,
                (path, value) -> check.text(path, value, 1000));
        Field<Observation> restrictions = check.field("content.accessRestrictions", g.accessRestrictions(), false,
                (path, value) -> observation(check, path, value));
        Field<List<Hazard>> hazards = check.field("content.hazards", g.hazards(), false,
                (path, value) -> hazards(check, path, value));
        return new SafetyContent(egress, restrictions, hazards);
    }

    /**
     * survey-checklist spec 4.6.1: at most 30 hazards with unique client ids; each earlier hazard linked at most once;
     * a status note unless OPEN; the owner's report date, receiver and relay basis only, and date and receiver always,
     * with OWNER_REPORTED_FIXED. The report date range needs the chain and is checked at save step 9 (spec A-7).
     */
    private static List<Hazard> hazards(BodyChecker check, String path, List<Hazard> hazards) {
        if (check.list(path, hazards, MAX_HAZARDS, "hazards") == null) {
            return null;
        }
        Set<String> ids = new HashSet<>();
        Set<HazardLink> links = new HashSet<>();
        List<Hazard> normalised = new ArrayList<>();
        for (int i = 0; i < hazards.size(); i++) {
            Hazard hazard = hazards.get(i);
            String at = path + "." + i;
            String id = TemplateV1Rules.uniqueId(check, at + ".hazardId", hazard.hazardId(), ids);
            String spaceId = hazard.spaceId() == null ? null : check.ulid(at + ".spaceId", hazard.spaceId());
            HazardLink previous = link(check, at + ".previous", hazard.previous(), links);
            String description = check.text(at + ".description", hazard.description(), 500);
            check.present(at + ".severity", hazard.severity());
            HazardStatus status = check.present(at + ".status", hazard.status());
            String statusNote = status == HazardStatus.OPEN || status == null
                    ? check.optionalText(at + ".statusNote", hazard.statusNote(), 500)
                    : check.text(at + ".statusNote", hazard.statusNote(), 500);
            if (status == HazardStatus.OWNER_REPORTED_FIXED) {
                BodyChecker.Statement report = check.statement(at, "ownerReportedOn", hazard.ownerReportedOn(),
                        hazard.receivedByUserId(), hazard.relayBasis(), false);
                normalised.add(new Hazard(id, spaceId, previous, description, hazard.severity(), status, statusNote,
                        report.confirmedOn(), report.receivedByUserId(), report.relayBasis()));
            } else {
                if (hazard.ownerReportedOn() != null) {
                    check.mustBeEmpty(at + ".ownerReportedOn", "unless the owner reported a fix");
                }
                check.noStatement(at, null, hazard.receivedByUserId(), hazard.relayBasis(),
                        "unless the owner reported a fix");
                normalised.add(new Hazard(id, spaceId, previous, description, hazard.severity(), status, statusNote,
                        null, null, null));
            }
        }
        return normalised;
    }

    private static HazardLink link(BodyChecker check, String path, HazardLink link, Set<HazardLink> seen) {
        if (link == null) {
            return null;
        }
        String checklistId = check.ulid(path + ".checklistId", link.checklistId());
        String hazardId = check.ulid(path + ".hazardId", link.hazardId());
        if (checklistId == null || hazardId == null) {
            return null;
        }
        HazardLink normalised = new HazardLink(checklistId, hazardId);
        if (!seen.add(normalised)) {
            check.add(path, path + " links an earlier hazard another hazard of this checklist already links.");
        }
        return normalised;
    }
}
