package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.service.checklist.ChecklistGroupChecks.EarlierChain;
import vn.sunvibe.survey.core.application.service.checklist.ChecklistGroupChecks.PlacedAddress;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceCheck;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceViolation;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistHazards;
import vn.sunvibe.survey.core.domain.policy.checklist.EarlierHazardChain;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmittedForm;
import vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1Rules;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks of a group save that need more than the body (survey-checklist spec 5.5, steps 1 and 9), for
 * {@link ChecklistGroupWriter}: the template rules, the catalog's pin of the observed address, the hazard chain of the
 * earlier checklists, the code of a unit's space and the owner's form as the source rules read it.
 */
@Component
public class ChecklistGroupChecks {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyLeadPort leads;

    public ChecklistGroupChecks(SurveyChecklistDomainRepository checklists, SurveyChecklistGroupDomainRepository groups,
                                SurveyLeadPort leads) {
        this.checklists = checklists;
        this.groups = groups;
        this.leads = leads;
    }

    /**
     * Step 1: the template rules; their violations join the list and the body as sent is kept so the other checks
     * can still run.
     */
    static GroupContent normalised(GroupContent content, LocalDate today, List<Violation> violations) {
        try {
            return TemplateV1Rules.checkAndNormalise(content, today);
        } catch (ChecklistValidationException e) {
            violations.addAll(e.violations());
            return content;
        }
    }

    /** The G1 content with the catalog's pin, and the names to keep (spec PD-1), when the address is a value. */
    record PlacedAddress(IdentificationContent content, ObservedPlaceNames names) {
    }

    /** Step 1: province, ward and pin by the intake location rules through the lead port (spec 4.6). */
    PlacedAddress placeAddress(IdentificationContent g1, List<Violation> violations) {
        SourcedField<ObservedLocation> address = g1.observedAddress();
        ObservedLocation value = address == null || !address.isValue() ? null : address.value();
        // The shape rules already refused missing codes or half a pin; the catalog checks the rest
        if (value == null || isBlank(value.provinceCode()) || isBlank(value.wardCode())
                || (value.latitude() == null) != (value.longitude() == null)) {
            return new PlacedAddress(g1, null);
        }
        PlaceCheck check = leads.checkPlace(value.provinceCode(), value.wardCode(), value.latitude(),
                value.longitude());
        for (PlaceViolation violation : check.violations()) {
            String path = "content.observedAddress.value." + violation.field();
            violations.add(new Violation(path, violation.message()));
        }
        if (!check.violations().isEmpty()) {
            return new PlacedAddress(g1, null);
        }
        ObservedLocation placed = new ObservedLocation(value.addressText(), value.provinceCode(), value.wardCode(),
                check.latitude(), check.longitude());
        return new PlacedAddress(new IdentificationContent(new SourcedField<>(address.state(), placed,
                address.naReason(), address.source(), address.confirmedOn(), address.receivedByUserId(),
                address.relayBasis()), g1.differenceNote(), g1.propertyType(), g1.fit(), g1.rentalScope(),
                g1.spaces(), g1.otherPartsNote(), g1.generalNote()),
                new ObservedPlaceNames(check.provinceName(), check.wardName()));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** The earlier checklists of the lead's cycle: their hazard ids, and the first sighting of a linked hazard. */
    record EarlierChain(Map<String, Set<String>> hazardIds, Map<String, Map<String, Hazard>> hazards,
                        Map<String, LocalDate> arrivals) {

        /** spec A-7: the arrival date of the visit of the first hazard of the chain; this visit's when unlinked. */
        LocalDate firstSightingArrival(Hazard hazard, LocalDate ownArrival) {
            LocalDate arrival = ownArrival;
            HazardLink link = hazard.previous();
            Set<HazardLink> seen = new LinkedHashSet<>();
            while (link != null && seen.add(link) && hazards.containsKey(link.checklistId())) {
                arrival = arrivals.get(link.checklistId());
                Hazard earlier = hazards.get(link.checklistId()).get(link.hazardId());
                link = earlier == null ? null : earlier.previous();
            }
            return arrival;
        }
    }

    EarlierChain earlierChain(SurveyChecklist checklist, CommandContext context) {
        List<SurveyChecklist> earlier = checklists.findByLeadId(checklist.getLeadId()).stream()
                .filter(other -> other.getIntentCycle() == checklist.getIntentCycle()
                        && other.getChecklistNo() < checklist.getChecklistNo())
                .toList();
        Map<String, Set<String>> ids = new HashMap<>();
        Map<String, Map<String, Hazard>> hazards = new HashMap<>();
        Map<String, LocalDate> arrivals = new HashMap<>();
        if (earlier.isEmpty()) {
            return new EarlierChain(ids, hazards, arrivals);
        }
        var earlierGroups = groups.findAllOf(earlier.stream().map(SurveyChecklist::getId).toList());
        Map<String, SurveyChecklist> byChecklistId = new HashMap<>();
        earlier.forEach(other -> byChecklistId.put(other.getId().getValue(), other));
        // survey-checklist submission spec 4.6: a revision of the same visit is the same hazard, so no link may name it
        for (ChecklistHazards other : EarlierHazardChain.linkTargets(earlier.stream()
                        .map(revision -> ChecklistViewAssembler.hazards(revision, earlierGroups.get(revision.getId())))
                        .toList(), checklist.getVisitAppointmentId().getValue())) {
            Map<String, Hazard> byId = new LinkedHashMap<>();
            other.hazards().forEach(hazard -> byId.put(hazard.hazardId(), hazard));
            hazards.put(other.checklistId(), byId);
            ids.put(other.checklistId(), byId.keySet());
            arrivals.put(other.checklistId(), ChecklistContextLoader.visitFacts(ChecklistContextLoader.visitOf(
                    byChecklistId.get(other.checklistId()), context.ofLead())).arrivalDate());
        }
        return new EarlierChain(ids, hazards, arrivals);
    }

    /** The code of the unit's space in the stored G1, so a removed space keeps its code (spec 5.2). */
    static String spaceCode(UnitKey key, IdentificationContent storedG1) {
        String spaceId = key.spaceId();
        if (spaceId == null) {
            return null;
        }
        return storedG1.spaces().stream().filter(space -> space.spaceId().equals(spaceId))
                .map(ObservedSpace::code).findFirst().orElse(null);
    }

    /** The owner's form as the source rules read it: the submission's Vietnam date (spec A-18). */
    static SubmittedForm form(OwnerFormFacts facts) {
        return new SubmittedForm(SurveyTimeRules.vietnamDate(facts.submittedAt()), facts.addressDetail(),
                facts.provinceCode(), facts.wardCode(), facts.latitude(), facts.longitude(),
                facts.monthlyAskingPriceOrNull(), facts.availableFromOrNull());
    }
}
