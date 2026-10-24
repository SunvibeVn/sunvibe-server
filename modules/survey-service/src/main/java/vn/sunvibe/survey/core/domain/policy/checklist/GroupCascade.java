package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What a G1 save changes in the other groups (survey-checklist spec A-12): a removed space's entries are deleted in
 * G2, G3, G4, G6 (its other items too) and G8, and its hazards are detached (kept at asset level). Leaving PARTS clears
 * the shared wet areas of G4 and the charge overrides and space fees of G8, which only separate parts are asked for.
 * Leaving WHOLE clears the per-space owner's-form rent and available-from date, which a single part cannot carry
 * (spec A-18), and nothing else; the shared wet areas are not filled (spec PD-4). Renaming a space changes nothing,
 * because entries are keyed by space id.
 */
public final class GroupCascade {

    private GroupCascade() {
    }

    public static CascadeResult of(IdentificationContent stored, IdentificationContent next,
                                   Map<ChecklistGroup, GroupDocument> others) {
        Set<String> removed = new HashSet<>(ids(stored));
        removed.removeAll(ids(next));
        boolean leavesParts = stored.rentalScope() == RentalScope.PARTS && next.rentalScope() != RentalScope.PARTS;
        boolean leavesWhole = stored.rentalScope() == RentalScope.WHOLE && next.rentalScope() != RentalScope.WHOLE;
        Map<ChecklistGroup, GroupContent> changed = new LinkedHashMap<>();
        for (ChecklistGroup group : ChecklistGroup.stored()) {
            GroupDocument document = others.get(group);
            if (group == ChecklistGroup.IDENTIFICATION || document == null) {
                continue;
            }
            GroupContent content = document.content();
            GroupContent cascaded = switch (content) {
                case PartsContent g -> new PartsContent(g.accessRoute(), g.sharedAreas(), g.totalFloors(),
                        kept(g.spaces(), PartsSpace::spaceId, removed));
                case DimensionsContent g -> new DimensionsContent(kept(g.spaces(), DimensionsSpace::spaceId, removed));
                case ConditionContent g -> new ConditionContent(leavesParts ? null : g.sharedKitchen(),
                        leavesParts ? null : g.sharedBathroom(), kept(g.spaces(), ConditionSpace::spaceId, removed)
                        .stream().map(space -> leavesWhole ? withoutFormDate(space) : space).toList());
                case SafetyContent g -> new SafetyContent(g.egress(), g.accessRestrictions(), detached(g.hazards(),
                        removed));
                case AmenitiesContent g -> new AmenitiesContent(g.sharedItems(),
                        kept(g.spaces(), AmenitiesSpace::spaceId, removed), g.otherItems().stream()
                        .filter(item -> item.spaceId() == null || !removed.contains(item.spaceId())).toList());
                case TermsContent g -> new TermsContent(g.common(), kept(g.spaces(), TermsSpace::spaceId, removed)
                        .stream().map(space -> terms(space, leavesParts, leavesWhole)).toList());
                case IdentificationContent g -> g;
            };
            if (!cascaded.equals(content)) {
                changed.put(group, cascaded);
            }
        }
        return new CascadeResult(changed);
    }

    private static List<String> ids(IdentificationContent g1) {
        return g1.spaces().stream().map(ObservedSpace::spaceId).toList();
    }

    private static <T> List<T> kept(List<T> entries, Function<T, String> idOf, Set<String> removed) {
        return entries.stream().filter(entry -> !removed.contains(idOf.apply(entry))).toList();
    }

    private static Field<List<Hazard>> detached(Field<List<Hazard>> hazards, Set<String> removed) {
        if (hazards.value() == null) {
            return hazards;
        }
        return new Field<>(hazards.state(), hazards.value().stream()
                .map(hazard -> hazard.spaceId() != null && removed.contains(hazard.spaceId())
                        ? new Hazard(hazard.hazardId(), null, hazard.previous(), hazard.description(),
                        hazard.severity(), hazard.status(), hazard.statusNote(), hazard.ownerReportedOn(),
                        hazard.receivedByUserId(), hazard.relayBasis())
                        : hazard)
                .toList(), hazards.naReason());
    }

    private static ConditionSpace withoutFormDate(ConditionSpace space) {
        return isOwnerForm(space.availableFrom())
                ? new ConditionSpace(space.spaceId(), space.occupancy(), space.expectedVacantFrom(),
                SourcedField.unknown(), space.mainSpace(), space.kitchen(), space.bathroom())
                : space;
    }

    private static TermsSpace terms(TermsSpace space, boolean leavesParts, boolean leavesWhole) {
        TermsSpace next = space;
        if (leavesParts) {
            next = new TermsSpace(next.spaceId(), next.monthlyRent(), next.deposit(), next.paymentPeriodMonths(),
                    next.minimumTermMonths(), null, null);
        }
        if (leavesWhole && isOwnerForm(next.monthlyRent())) {
            next = new TermsSpace(next.spaceId(), SourcedField.unknown(), next.deposit(), next.paymentPeriodMonths(),
                    next.minimumTermMonths(), next.chargeOverrides(), next.spaceFees());
        }
        return next;
    }

    private static boolean isOwnerForm(SourcedField<?> field) {
        return field != null && field.isValue() && field.source() == ChecklistSource.OWNER_FORM;
    }
}
