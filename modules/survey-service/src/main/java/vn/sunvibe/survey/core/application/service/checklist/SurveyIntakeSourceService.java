package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.survey.core.application.service.common.SurveyStateService;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyIntakeSourceUseCase;
import vn.sunvibe.survey.core.domain.model.checklist.ActualAccess;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.Usability;
import vn.sunvibe.survey.core.domain.repository.appointment.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistRequalificationDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSubmissionDomainRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - the frozen source of an inventory intake (property-create intake spec 5.1). Built on
 * {@link SurveyRecordReader}, as {@link SurveyStateService} is, and never on the checklist context loader, which writes
 * security events through REQUIRES_NEW and asks acquisition about a viewer.
 */
@Service
public class SurveyIntakeSourceService implements SurveyIntakeSourceUseCase {

    private final SurveyRecordReader records;
    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistSubmissionDomainRepository submissions;
    private final SurveyChecklistRequalificationDomainRepository requalifications;

    public SurveyIntakeSourceService(SurveyRecordReader records, SurveyAppointmentDomainRepository appointments,
                                     SurveyChecklistDomainRepository checklists,
                                     SurveyChecklistGroupDomainRepository groups,
                                     SurveyChecklistSubmissionDomainRepository submissions,
                                     SurveyChecklistRequalificationDomainRepository requalifications) {
        this.records = records;
        this.appointments = appointments;
        this.checklists = checklists;
        this.groups = groups;
        this.submissions = submissions;
        this.requalifications = requalifications;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IntakeSourceView> currentSource(String leadId, int intentCycle) {
        SurveyRecordReader.CycleRecord record = records.read(leadId, intentCycle, appointments.findByLeadId(leadId));
        return record.passed().map(passed -> view(passed, record, intentCycle, leadId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<IntakeSourceView.MarkedRevision> markedRevisions(String leadId) {
        return checklists.findByLeadId(leadId).stream().filter(SurveyChecklist::isRequalified)
                .map(checklist -> new IntakeSourceView.MarkedRevision(checklist.getId().getValue(),
                        requalifications.findByChecklistId(checklist.getId())
                                .map(ChecklistRequalification::kind).map(Enum::name).orElse(null),
                        checklist.getRequalifiedAt()))
                .toList();
    }

    private IntakeSourceView view(SurveyChecklist passed, SurveyRecordReader.CycleRecord record, int intentCycle,
                                  String leadId) {
        ChecklistSubmission submission = submissions.findByChecklistId(passed.getId())
                .orElseThrow(() -> new IllegalStateException("A PASSED revision has no submission."));
        ChecklistDecision decision = record.decisions().get(passed.getId());
        Map<ChecklistGroup, GroupState> stored = groups.findAll(passed.getId());
        GroupDocument g1Document = stored.get(ChecklistGroup.IDENTIFICATION).document();
        IdentificationContent g1 = (IdentificationContent) g1Document.content();
        ObservedLocation place = g1.observedAddress().value();
        ObservedPlaceNames names = g1Document.placeNamesOrNull();
        LocalDate sourceDate = SurveyTimeRules.vietnamDate(submission.visitArrivedAt());
        Usability usability = records.usability(passed, intentCycle, false);
        TermsContent terms = content(stored, ChecklistGroup.TERMS);
        return new IntakeSourceView(passed.getId().getValue(), decision == null ? null : decision.decidedAt(),
                submission.visitPerformedBy(), submission.visitRecordedBy(), submission.contentSha256(),
                submission.rentalScope().name(),
                submission.propertyTypeOrNull() == null ? null : submission.propertyTypeOrNull().name(),
                place == null ? null : place.addressText(), place == null ? null : place.provinceCode(),
                names == null ? null : names.provinceName(), place == null ? null : place.wardCode(),
                names == null ? null : names.wardName(), place == null ? null : place.latitude(),
                place == null ? null : place.longitude(), sourceDate,
                spaces(g1, stored, sourceDate), terms(g1, terms), usability.usable(),
                usability.reasonOrNull() == null ? null : usability.reasonOrNull().name(), markedRevisions(leadId));
    }

    private static List<IntakeSourceView.Space> spaces(IdentificationContent g1, Map<ChecklistGroup, GroupState> stored,
                                                       LocalDate sourceDate) {
        PartsContent parts = content(stored, ChecklistGroup.PARTS);
        DimensionsContent dimensions = content(stored, ChecklistGroup.DIMENSIONS);
        ConditionContent condition = content(stored, ChecklistGroup.CONDITION);
        AmenitiesContent amenities = content(stored, ChecklistGroup.AMENITIES);
        Map<String, PartsSpace> partsById = parts.spaces().stream()
                .collect(Collectors.toMap(PartsSpace::spaceId, Function.identity()));
        Map<String, DimensionsSpace> dimensionsById = dimensions.spaces().stream()
                .collect(Collectors.toMap(DimensionsSpace::spaceId, Function.identity()));
        Map<String, ConditionSpace> conditionById = condition.spaces().stream()
                .collect(Collectors.toMap(ConditionSpace::spaceId, Function.identity()));
        Map<String, AmenitiesSpace> amenitiesById = amenities.spaces().stream()
                .collect(Collectors.toMap(AmenitiesSpace::spaceId, Function.identity()));
        return g1.spaces().stream().map(space -> space(space, partsById.get(space.spaceId()),
                dimensionsById.get(space.spaceId()), conditionById.get(space.spaceId()),
                amenitiesById.get(space.spaceId()), amenities.otherItems(), sourceDate)).toList();
    }

    private static IntakeSourceView.Space space(ObservedSpace space, PartsSpace parts, DimensionsSpace dimensions,
                                                ConditionSpace condition, AmenitiesSpace amenities,
                                                List<OtherItem> otherItems, LocalDate sourceDate) {
        List<IntakeSourceView.FieldSource> sources = new ArrayList<>();
        BigDecimal area = dimensions == null ? null : valueOf(dimensions.area(), "area", sources, sourceDate);
        Integer bedrooms = dimensions == null ? null : valueOf(dimensions.bedrooms(), "bedrooms", sources, sourceDate);
        Integer bathrooms = dimensions == null ? null
                : valueOf(dimensions.privateBathrooms(), "bathrooms", sources, sourceDate);
        return new IntakeSourceView.Space(space.spaceId(), space.code(), space.location(), accessLimits(parts), sourceDate,
                area, bedrooms, bathrooms, condition == null ? null : occupancy(condition), amenities(space.spaceId(),
                amenities, otherItems), List.copyOf(sources));
    }

    private static <V> V valueOf(SourcedField<V> field, String name, List<IntakeSourceView.FieldSource> sources,
                                 LocalDate sourceDate) {
        if (field == null || !field.isValue()) {
            return null;
        }
        sources.add(new IntakeSourceView.FieldSource(name, field.source() == null ? null : field.source().name(),
                field.confirmedOn() == null ? sourceDate : field.confirmedOn()));
        return field.value();
    }

    /** The extent of the visit to this space; a part entered carries the note of what was not reached. */
    private static String accessLimits(PartsSpace parts) {
        if (parts == null || parts.actualAccess() == null || !parts.actualAccess().isValue()) {
            return null;
        }
        ActualAccess access = parts.actualAccess().value();
        return access.note() == null ? access.extent().name() : access.extent().name() + ": " + access.note();
    }

    private static String occupancy(ConditionSpace condition) {
        Field<?> occupancy = condition.occupancy();
        return occupancy != null && occupancy.isValue() ? occupancy.value().toString() : null;
    }

    /** What was seen on this space alone, so a balcony of one room never reads on another (D5-20). */
    private static List<String> amenities(String spaceId, AmenitiesSpace space, List<OtherItem> otherItems) {
        List<String> seen = new ArrayList<>();
        if (space != null && space.items() != null) {
            space.items().stream().filter(item -> item.presence() == YesNo.YES).map(AmenityItem::code).map(Enum::name)
                    .forEach(seen::add);
        }
        if (otherItems != null) {
            otherItems.stream().filter(item -> spaceId.equals(item.spaceId()) && item.presence() == YesNo.YES)
                    .map(OtherItem::label).forEach(seen::add);
        }
        return List.copyOf(seen);
    }

    private static List<IntakeSourceView.Term> terms(IdentificationContent g1, TermsContent content) {
        List<IntakeSourceView.Term> terms = new ArrayList<>();
        IntakeTerms common = new IntakeTerms(terms, null);
        common.charge("electricity", content.common().electricity());
        common.charge("water", content.common().water());
        common.charge("internet", content.common().internet());
        common.charge("parking", content.common().parking());
        common.charge("serviceFee", content.common().serviceFee());
        common.fees("otherFees", content.common().otherFees());
        common.text("houseRules", content.common().houseRules(), Function.identity());
        common.text("pets", content.common().pets(), pets -> pets.note() == null ? pets.policy().name()
                : pets.policy().name() + ": " + pets.note());
        common.text("handoverMaintenance", content.common().handoverMaintenance(), Function.identity());
        Map<String, TermsSpace> bySpace = content.spaces().stream()
                .collect(Collectors.toMap(TermsSpace::spaceId, Function.identity()));
        for (ObservedSpace space : g1.spaces()) {
            TermsSpace entry = bySpace.get(space.spaceId());
            if (entry != null) {
                spaceTerms(new IntakeTerms(terms, space.code()), entry);
            }
        }
        return List.copyOf(terms);
    }

    private static void spaceTerms(IntakeTerms terms, TermsSpace space) {
        terms.rent("monthlyRent", space.monthlyRent());
        terms.deposit("deposit", space.deposit());
        terms.text("paymentPeriodMonths", space.paymentPeriodMonths(), String::valueOf);
        terms.text("minimumTermMonths", space.minimumTermMonths(), String::valueOf);
        ChargeOverrides overrides = space.chargeOverrides();
        if (overrides != null) {
            terms.overridden("electricity", overrides.electricity());
            terms.overridden("water", overrides.water());
            terms.overridden("internet", overrides.internet());
            terms.overridden("parking", overrides.parking());
            terms.overridden("serviceFee", overrides.serviceFee());
        }
        if (space.spaceFees() != null) {
            terms.fees("spaceFees", space.spaceFees());
        }
    }

    @SuppressWarnings("unchecked") // the stored document of a group holds that group's content, by its constructor
    private static <C extends GroupContent> C content(Map<ChecklistGroup, GroupState> stored, ChecklistGroup group) {
        return (C) stored.get(group).document().content();
    }
}
