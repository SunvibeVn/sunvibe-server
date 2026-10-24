package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.query.ChecklistStartResult;
import vn.sunvibe.survey.core.application.query.ChecklistSummaryView;
import vn.sunvibe.survey.core.application.query.ChecklistTakeoverResult;
import vn.sunvibe.survey.core.application.query.ChecklistView;
import vn.sunvibe.survey.core.application.query.CheckpointView;
import vn.sunvibe.survey.core.application.query.LeadState;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.model.checklist.ActualAccess;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Deposit;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.FitAssessment;
import vn.sunvibe.survey.core.domain.model.checklist.Furnishing;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.Observation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Pets;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.ChargeOrigin;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStart;
import vn.sunvibe.survey.core.domain.policy.checklist.EarlierHazard;
import vn.sunvibe.survey.core.domain.policy.checklist.EffectiveCharge;
import vn.sunvibe.survey.core.domain.policy.checklist.InputForm;
import vn.sunvibe.survey.core.domain.policy.checklist.LocationDifference;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.SpaceTerms;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyLeadStateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistActualAccess;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistActualAccessField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAddressDifference;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAddressedBy;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAmenitiesContent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAmenitiesGroup;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAmenitiesSpace;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAmenitySource;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAmenityItem;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAreaCheck;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAreaCheckField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAreaField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAreaSource;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistBasis;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCharge;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistChargeOverrides;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistChargeTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCheckpointResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistConditionContent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistConditionGroup;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistConditionSpace;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCorrectedUnit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCorrection;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCountField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCountSource;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDateTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDeposit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDepositTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDimensionsContent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDimensionsFieldMeta;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDimensionsGroup;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDimensionsSpace;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistEarlierHazard;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistEditor;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistEffectiveCharge;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistEffectiveFees;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFee;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFeeListTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFitField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFurnishingField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistGroups;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHazard;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHazardLink;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHazardListField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHistoryEntryResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistIdentificationContent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistIdentificationFieldMeta;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistIdentificationGroup;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistLeadSummary;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistLocationField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistLocationSource;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistMissingItem;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistMoney;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistMonthsTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistObservation;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistObservationField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistObservedLocation;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistObservedSpace;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistOccupancyField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistOtherItem;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistOwnerForm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPartsContent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPartsGroup;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPartsSpace;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPets;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPetsTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPropertyTypeField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReadiness;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRentMoney;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRentTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSafetyContent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSafetyGroup;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSnapshotResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSpaceTerms;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStaffMember;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStartStateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStartedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSummaryResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTakenOverResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTermsCommon;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTermsContent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTermsGroup;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTermsSource;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTermsSpace;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTextField;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTextTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistUnconfirmedTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistUnitMeta;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistVisit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistWarning;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistYesNoCountField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Converter - ChecklistResponseMapper
 * <p>
 * Turns the checklist read models into the response bodies of survey-checklist spec 7.2. Each group's content is read
 * through {@code InputForm.ofStored} (plan PD-5), so every per-space list holds one entry per G1 space in G1 order with
 * UNKNOWN where nothing is stored, and each unit's metadata (or its tombstone, spec A-19) goes back onto its wrapper,
 * item, space or hazard, plain fields onto {@code fieldMeta} (plan PD-1). Source fields carry the family enums of spec
 * 7.2. Staff names come from the view's one name lookup; an unknown name is null.
 */
@Component
public class ChecklistResponseMapper {

    private static final String CURRENCY = Money.VND;
    private static final String MONTH = RentMoney.MONTH;

    public ChecklistResponse toResponse(ChecklistView view) {
        SurveyChecklist checklist = view.checklist();
        Function<String, String> names = view::nameOf;
        List<ChecklistStaffMember> leadStaff = view.leadStaff().stream()
                .map(id -> new ChecklistStaffMember(id, view.nameOf(id))).toList();
        GroupDocument terms = view.groups().get(ChecklistGroup.TERMS).document();
        return new ChecklistResponse(checklist.getId().getValue(), checklist.getLeadId(),
                new ChecklistLeadSummary(view.requestCode(), view.leadStatus()), leadStaff,
                checklist.getChecklistNo(), checklist.getStatus(), checklist.getKind(), checklist.getTemplateCode(),
                checklist.getTemplateVersion(), visit(view.visit(), names), checklist.getHolderUserId(),
                view.nameOf(checklist.getHolderUserId()), view.editability().editable(),
                view.editability().reason(), groups(view.groups(), view.difference(), names),
                readiness(view.readiness()),
                view.effectiveTerms().stream().map(space -> spaceTerms(space, new Units(terms, names))).toList(),
                view.earlierHazards().stream().map(hazard -> earlierHazard(hazard, names)).toList(),
                ownerForm(view.ownerFormOrNull()),
                view.takeovers().stream().map(takeover -> takeover(takeover, names)).toList(),
                view.corrections().stream().map(correction -> new ChecklistCorrection(correction.saveId(),
                        correction.group(), correction.units().stream()
                        .map(unit -> new ChecklistCorrectedUnit(unit.unitKey(), unit.spaceCode())).toList(),
                        correction.reason(), basis(correction.basis()), correction.actorUserId(),
                        view.nameOf(correction.actorUserId()), correction.savedAt())).toList(),
                view.editors().stream().map(editor -> new ChecklistEditor(editor.userId(),
                        view.nameOf(editor.userId()), editor.lastSavedAt())).toList(),
                checklist.getCreatedBy(), view.nameOf(checklist.getCreatedBy()), checklist.getCreatedAt(),
                checklist.getVersion() == null ? 0L : checklist.getVersion(), view.updatedAt());
    }

    public ChecklistStartedResponse toStarted(ChecklistStartResult result) {
        LeadState lead = result.lead();
        return new ChecklistStartedResponse(toResponse(result.view()),
                new SurveyLeadStateResponse(lead.id(), lead.status(), lead.version(), lead.lastActivityAt()),
                toEntry(result.historyEntry()));
    }

    public ChecklistTakenOverResponse toTakenOver(ChecklistTakeoverResult result) {
        return new ChecklistTakenOverResponse(toResponse(result.view()), toEntry(result.historyEntry()));
    }

    public ChecklistCheckpointResponse toCheckpoint(CheckpointView view) {
        Function<String, String> names = view::nameOf;
        return new ChecklistCheckpointResponse(takeover(view.takeover(), names),
                new ChecklistSnapshotResponse(groups(view.groups(), view.difference(), names)));
    }

    /** survey-checklist spec 7.2 {@code checklistStart} of the survey block. */
    public static ChecklistStartStateResponse toStartState(ChecklistStart start) {
        return new ChecklistStartStateResponse(start.allowed(), start.visitAppointmentIdOrNull(),
                start.intakeDeclarationRequired(), start.blockedByOrNull());
    }

    /** survey-checklist spec 7.2 {@code checklists[]} of a VISITED appointment in the survey block. */
    public static ChecklistSummaryResponse toSummary(ChecklistSummaryView view, Function<String, String> names) {
        return new ChecklistSummaryResponse(view.id(), view.checklistNo(), view.status(),
                view.editability().editable(), view.editability().reason(), view.missingCount(), view.holderUserId(),
                names.apply(view.holderUserId()), view.updatedAt(), view.updatedByUserIdOrNull(),
                names.apply(view.updatedByUserIdOrNull()));
    }

    // ==================== checklist parts ====================

    private static ChecklistHistoryEntryResponse toEntry(ChecklistHistoryEntry entry) {
        return new ChecklistHistoryEntryResponse(entry.type(), entry.actorUserId(), entry.actorName(), null, null,
                null, null, null, null, null, null, entry.occurredAt(), entry.referenceId(), entry.referenceNo(),
                entry.referenceDetail());
    }

    private static ChecklistVisit visit(SurveyAppointment appointment, Function<String, String> names) {
        VisitRecord visit = appointment.getVisit();
        return new ChecklistVisit(appointment.getId().getValue(), appointment.getAppointmentNo(),
                appointment.getKind(), appointment.getIntent(), appointment.getIntentCycle(), visit.arrivedAt(),
                visit.performedBy(), names.apply(visit.performedBy()), visit.kind(), visit.recordedBy(),
                names.apply(visit.recordedBy()), visit.recordedAt(),
                SurveyResponseMapper.toLocation(appointment.getDetails().meetingLocation()),
                SurveyResponseMapper.toLocation(appointment.getLeadLocation()));
    }

    private static ChecklistReadiness readiness(Readiness readiness) {
        return new ChecklistReadiness(readiness.submissionAvailable(), readiness.submissionUnavailableReason(),
                readiness.missing().stream().map(item -> new ChecklistMissingItem(item.group(), item.spaceIdOrNull(),
                        item.fieldOrNull(), item.problem())).toList(),
                readiness.unconfirmedTerms().stream().map(term -> new ChecklistUnconfirmedTerm(term.group(),
                        term.spaceIdOrNull(), term.field())).toList(),
                readiness.warnings().stream().map(warning -> new ChecklistWarning(warning.type(), warning.hazardId(),
                        warning.checklistId(), warning.checklistNo(), warning.spaceId())).toList());
    }

    private static ChecklistSpaceTerms spaceTerms(SpaceTerms space, Units units) {
        String id = space.spaceId();
        return new ChecklistSpaceTerms(id, effective(space.electricity(), id, "electricity", units),
                effective(space.water(), id, "water", units), effective(space.internet(), id, "internet", units),
                effective(space.parking(), id, "parking", units),
                effective(space.serviceFee(), id, "serviceFee", units),
                new ChecklistEffectiveFees(units.fees(space.commonFees(), UnitKey.commonTerm("otherFees")),
                        space.spaceFees() == null ? null
                                : units.fees(space.spaceFees(),
                                UnitKey.spaceField(ChecklistGroup.TERMS, id, "spaceFees"))));
    }

    private static ChecklistEffectiveCharge effective(EffectiveCharge charge, String spaceId, String name,
                                                      Units units) {
        UnitKey key = charge.origin() == ChargeOrigin.SPACE ? UnitKey.override(spaceId, name)
                : UnitKey.commonTerm(name);
        return new ChecklistEffectiveCharge(units.charge(charge.term(), key), charge.origin());
    }

    /** survey-checklist spec 7.2: an earlier hazard as recorded; its metadata stays on its own checklist. */
    private static ChecklistEarlierHazard earlierHazard(EarlierHazard earlier, Function<String, String> names) {
        return new ChecklistEarlierHazard(earlier.checklistId(), earlier.checklistNo(),
                hazard(earlier.hazard(), null, names),
                earlier.addressedBy().stream().map(by -> new ChecklistAddressedBy(by.checklistId(), by.checklistNo(),
                        by.hazardId(), by.kind())).toList());
    }

    private static ChecklistOwnerForm ownerForm(OwnerFormFacts form) {
        if (form == null) {
            return null;
        }
        return new ChecklistOwnerForm(SurveyTimeRules.vietnamDate(form.submittedAt()), form.addressDetail(),
                form.provinceCode(), form.wardCode(), form.latitude(), form.longitude(),
                form.monthlyAskingPriceOrNull() == null ? null
                        : new ChecklistRentMoney(form.monthlyAskingPriceOrNull(), CURRENCY, MONTH),
                form.availableFromOrNull());
    }

    private static ChecklistTakeover takeover(
            vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover takeover, Function<String, String> names) {
        return new ChecklistTakeover(takeover.takeoverNo(), takeover.kind(), takeover.fromUserId(),
                names.apply(takeover.fromUserId()), takeover.toUserId(), names.apply(takeover.toUserId()),
                takeover.reason(), basis(takeover.basis()), takeover.occurredAt(), takeover.snapshotOrNull() != null);
    }

    private static ChecklistBasis basis(Basis basis) {
        return basis == null ? null : new ChecklistBasis(basis.confirmedOn(), basis.text());
    }

    // ==================== groups ====================

    private static IdentificationContent g1(Map<ChecklistGroup, GroupState> states) {
        return (IdentificationContent) states.get(ChecklistGroup.IDENTIFICATION).document().content();
    }

    private static ChecklistGroups groups(Map<ChecklistGroup, GroupState> states, LocationDifference difference,
                                          Function<String, String> names) {
        IdentificationContent g1 = g1(states);
        GroupState identification = states.get(ChecklistGroup.IDENTIFICATION);
        GroupState parts = states.get(ChecklistGroup.PARTS);
        GroupState dimensions = states.get(ChecklistGroup.DIMENSIONS);
        GroupState condition = states.get(ChecklistGroup.CONDITION);
        GroupState safety = states.get(ChecklistGroup.SAFETY);
        GroupState amenities = states.get(ChecklistGroup.AMENITIES);
        GroupState terms = states.get(ChecklistGroup.TERMS);
        return new ChecklistGroups(
                new ChecklistIdentificationGroup(identification.version(), identification.updatedAt(),
                        identification.updatedByUserId(), names.apply(identification.updatedByUserId()),
                        new Units(identification.document(), names).identification(
                                (IdentificationContent) InputForm.ofStored(identification.document(), g1),
                                difference)),
                new ChecklistPartsGroup(parts.version(), parts.updatedAt(), parts.updatedByUserId(),
                        names.apply(parts.updatedByUserId()), new Units(parts.document(), names).parts(
                        (PartsContent) InputForm.ofStored(parts.document(), g1))),
                new ChecklistDimensionsGroup(dimensions.version(), dimensions.updatedAt(),
                        dimensions.updatedByUserId(), names.apply(dimensions.updatedByUserId()),
                        new Units(dimensions.document(), names).dimensions(
                                (DimensionsContent) InputForm.ofStored(dimensions.document(), g1))),
                new ChecklistConditionGroup(condition.version(), condition.updatedAt(), condition.updatedByUserId(),
                        names.apply(condition.updatedByUserId()), new Units(condition.document(), names).condition(
                        (ConditionContent) InputForm.ofStored(condition.document(), g1))),
                new ChecklistSafetyGroup(safety.version(), safety.updatedAt(), safety.updatedByUserId(),
                        names.apply(safety.updatedByUserId()), new Units(safety.document(), names).safety(
                        (SafetyContent) InputForm.ofStored(safety.document(), g1))),
                new ChecklistAmenitiesGroup(amenities.version(), amenities.updatedAt(), amenities.updatedByUserId(),
                        names.apply(amenities.updatedByUserId()), new Units(amenities.document(), names).amenities(
                        (AmenitiesContent) InputForm.ofStored(amenities.document(), g1))),
                new ChecklistTermsGroup(terms.version(), terms.updatedAt(), terms.updatedByUserId(),
                        names.apply(terms.updatedByUserId()), new Units(terms.document(), names).terms(
                        (TermsContent) InputForm.ofStored(terms.document(), g1))));
    }

    private static ChecklistHazard hazard(Hazard hazard, ChecklistUnitMeta meta, Function<String, String> names) {
        HazardLink previous = hazard.previous();
        return new ChecklistHazard(hazard.hazardId(), hazard.spaceId(),
                previous == null ? null : new ChecklistHazardLink(previous.checklistId(), previous.hazardId()),
                hazard.description(), hazard.severity(), hazard.status(), hazard.statusNote(),
                hazard.ownerReportedOn(), hazard.receivedByUserId(), names.apply(hazard.receivedByUserId()),
                hazard.relayBasis(), meta);
    }

    /** The units of one stored group document: each wrapper with the metadata of its unit key. */
    private record Units(GroupDocument document, Function<String, String> names) {

        ChecklistUnitMeta meta(UnitKey key) {
            UnitMeta meta = document.metaOrTombstone(key);
            if (meta == null) {
                return null;
            }
            return new ChecklistUnitMeta(meta.enteredByUserId(), names.apply(meta.enteredByUserId()),
                    meta.enteredAt(), meta.observedByUserId(), names.apply(meta.observedByUserId()),
                    meta.observedOn(), meta.formField(), meta.lastSource(),
                    meta.correctionSaveId() == null ? null : String.valueOf(meta.correctionSaveId()));
        }

        ChecklistIdentificationContent identification(IdentificationContent g, LocationDifference difference) {
            ChecklistGroup group = ChecklistGroup.IDENTIFICATION;
            SourcedField<ObservedLocation> address = g.observedAddress();
            ObservedPlaceNames place = address.isValue() ? document.placeNamesOrNull() : null;
            return new ChecklistIdentificationContent(
                    new ChecklistLocationField(address.state(), location(address.value()), address.naReason(),
                            source(ChecklistLocationSource.class, address.source()), address.confirmedOn(),
                            address.receivedByUserId(), address.relayBasis(),
                            meta(UnitKey.field(group, "observedAddress"))),
                    g.differenceNote(),
                    new ChecklistPropertyTypeField(g.propertyType().state(), g.propertyType().value(),
                            g.propertyType().naReason(), meta(UnitKey.field(group, "propertyType"))),
                    fit(g.fit(), UnitKey.field(group, "fit")), g.rentalScope(),
                    g.spaces().stream().map(this::space).toList(), g.otherPartsNote(), g.generalNote(),
                    new ChecklistAddressDifference(difference.fromLead(), difference.fromMeetingPlace()),
                    place == null ? null : place.provinceName(), place == null ? null : place.wardName(),
                    new ChecklistIdentificationFieldMeta(meta(UnitKey.field(group, "rentalScope")),
                            meta(UnitKey.field(group, "differenceNote")),
                            meta(UnitKey.field(group, "otherPartsNote")),
                            meta(UnitKey.field(group, "generalNote"))));
        }

        ChecklistPartsContent parts(PartsContent g) {
            ChecklistGroup group = ChecklistGroup.PARTS;
            return new ChecklistPartsContent(text(g.accessRoute(), UnitKey.field(group, "accessRoute")),
                    text(g.sharedAreas(), UnitKey.field(group, "sharedAreas")),
                    count(g.totalFloors(), UnitKey.field(group, "totalFloors")),
                    g.spaces().stream().map(this::partsSpace).toList());
        }

        ChecklistDimensionsContent dimensions(DimensionsContent g) {
            return new ChecklistDimensionsContent(g.spaces().stream().map(this::dimensionsSpace).toList());
        }

        ChecklistConditionContent condition(ConditionContent g) {
            ChecklistGroup group = ChecklistGroup.CONDITION;
            return new ChecklistConditionContent(
                    g.sharedKitchen() == null ? null
                            : areaCheck(g.sharedKitchen(), UnitKey.field(group, "sharedKitchen")),
                    g.sharedBathroom() == null ? null
                            : areaCheck(g.sharedBathroom(), UnitKey.field(group, "sharedBathroom")),
                    g.spaces().stream().map(this::conditionSpace).toList());
        }

        ChecklistSafetyContent safety(SafetyContent g) {
            ChecklistGroup group = ChecklistGroup.SAFETY;
            Field<Observation> restrictions = g.accessRestrictions();
            Field<List<Hazard>> hazards = g.hazards();
            return new ChecklistSafetyContent(text(g.egress(), UnitKey.field(group, "egress")),
                    new ChecklistObservationField(restrictions.state(), observation(restrictions.value()),
                            restrictions.naReason(), meta(UnitKey.field(group, "accessRestrictions"))),
                    new ChecklistHazardListField(hazards.state(), hazards.value() == null ? null
                            : hazards.value().stream().map(hazard -> hazard(hazard,
                            meta(UnitKey.hazard(hazard.hazardId())), names)).toList(),
                            hazards.naReason(), meta(UnitKey.hazardList())));
        }

        ChecklistAmenitiesContent amenities(AmenitiesContent g) {
            return new ChecklistAmenitiesContent(
                    g.sharedItems().stream().map(item -> item(item, UnitKey.sharedItem(item.code()))).toList(),
                    g.spaces().stream().map(this::amenitiesSpace).toList(),
                    g.otherItems().stream().map(this::otherItem).toList());
        }

        ChecklistTermsContent terms(TermsContent g) {
            TermsCommon common = g.common();
            return new ChecklistTermsContent(new ChecklistTermsCommon(
                    charge(common.electricity(), UnitKey.commonTerm("electricity")),
                    charge(common.water(), UnitKey.commonTerm("water")),
                    charge(common.internet(), UnitKey.commonTerm("internet")),
                    charge(common.parking(), UnitKey.commonTerm("parking")),
                    charge(common.serviceFee(), UnitKey.commonTerm("serviceFee")),
                    fees(common.otherFees(), UnitKey.commonTerm("otherFees")),
                    textTerm(common.houseRules(), UnitKey.commonTerm("houseRules")),
                    pets(common.pets(), UnitKey.commonTerm("pets")),
                    textTerm(common.handoverMaintenance(), UnitKey.commonTerm("handoverMaintenance"))),
                    g.spaces().stream().map(this::termsSpace).toList());
        }

        // ---------- spaces and items ----------

        private ChecklistObservedSpace space(ObservedSpace space) {
            return new ChecklistObservedSpace(space.spaceId(), space.code(), space.location(), space.description(),
                    meta(UnitKey.space(space.spaceId())));
        }

        private ChecklistPartsSpace partsSpace(PartsSpace space) {
            ChecklistGroup group = ChecklistGroup.PARTS;
            String id = space.spaceId();
            Field<ActualAccess> access = space.actualAccess();
            return new ChecklistPartsSpace(id, text(space.floor(), UnitKey.spaceField(group, id, "floor")),
                    text(space.privateAreas(), UnitKey.spaceField(group, id, "privateAreas")),
                    new ChecklistActualAccessField(access.state(), actualAccess(access.value()), access.naReason(),
                            meta(UnitKey.spaceField(group, id, "actualAccess"))));
        }

        private ChecklistDimensionsSpace dimensionsSpace(DimensionsSpace space) {
            ChecklistGroup group = ChecklistGroup.DIMENSIONS;
            String id = space.spaceId();
            SourcedField<BigDecimal> area = space.area();
            SourcedField<YesNo> shared = space.sharedBathroomUse();
            return new ChecklistDimensionsSpace(id,
                    new ChecklistAreaField(area.state(), area.value(), area.naReason(),
                            source(ChecklistAreaSource.class, area.source()), area.confirmedOn(),
                            area.receivedByUserId(), area.relayBasis(), meta(UnitKey.spaceField(group, id, "area"))),
                    count(space.bedrooms(), UnitKey.spaceField(group, id, "bedrooms")),
                    count(space.privateBathrooms(), UnitKey.spaceField(group, id, "privateBathrooms")),
                    new ChecklistYesNoCountField(shared.state(), shared.value(), shared.naReason(),
                            source(ChecklistCountSource.class, shared.source()), shared.confirmedOn(),
                            shared.receivedByUserId(), shared.relayBasis(),
                            meta(UnitKey.spaceField(group, id, "sharedBathroomUse"))),
                    space.layoutNote(),
                    new ChecklistDimensionsFieldMeta(meta(UnitKey.spaceField(group, id, "layoutNote"))));
        }

        private ChecklistConditionSpace conditionSpace(ConditionSpace space) {
            ChecklistGroup group = ChecklistGroup.CONDITION;
            String id = space.spaceId();
            return new ChecklistConditionSpace(id,
                    new ChecklistOccupancyField(space.occupancy().state(), space.occupancy().value(),
                            space.occupancy().naReason(), meta(UnitKey.spaceField(group, id, "occupancy"))),
                    date(space.expectedVacantFrom(), UnitKey.spaceField(group, id, "expectedVacantFrom")),
                    date(space.availableFrom(), UnitKey.spaceField(group, id, "availableFrom")),
                    areaCheck(space.mainSpace(), UnitKey.spaceField(group, id, "mainSpace")),
                    areaCheck(space.kitchen(), UnitKey.spaceField(group, id, "kitchen")),
                    areaCheck(space.bathroom(), UnitKey.spaceField(group, id, "bathroom")));
        }

        private ChecklistAmenitiesSpace amenitiesSpace(AmenitiesSpace space) {
            String id = space.spaceId();
            SourcedField<Furnishing> furnishing = space.furnishing();
            return new ChecklistAmenitiesSpace(id,
                    new ChecklistFurnishingField(furnishing.state(), furnishing.value(), furnishing.naReason(),
                            source(ChecklistAmenitySource.class, furnishing.source()), furnishing.confirmedOn(),
                            furnishing.receivedByUserId(), furnishing.relayBasis(),
                            meta(UnitKey.spaceField(ChecklistGroup.AMENITIES, id, "furnishing"))),
                    space.items().stream().map(item -> item(item, UnitKey.spaceItem(id, item.code()))).toList());
        }

        private ChecklistAmenityItem item(AmenityItem item, UnitKey key) {
            return new ChecklistAmenityItem(item.code(), item.presence(),
                    source(ChecklistAmenitySource.class, item.source()), item.confirmedOn(), item.receivedByUserId(),
                    item.relayBasis(), meta(key));
        }

        private ChecklistOtherItem otherItem(OtherItem item) {
            return new ChecklistOtherItem(item.itemId(), item.label(), item.spaceId(), item.presence(),
                    source(ChecklistAmenitySource.class, item.source()), item.confirmedOn(), item.receivedByUserId(),
                    item.relayBasis(), meta(UnitKey.otherItem(item.itemId())));
        }

        private ChecklistTermsSpace termsSpace(TermsSpace space) {
            ChecklistGroup group = ChecklistGroup.TERMS;
            String id = space.spaceId();
            SourcedField<RentMoney> rent = space.monthlyRent();
            SourcedField<Deposit> deposit = space.deposit();
            ChargeOverrides overrides = space.chargeOverrides();
            return new ChecklistTermsSpace(id,
                    new ChecklistRentTerm(rent.state(), rentMoney(rent.value()), rent.naReason(),
                            source(ChecklistTermsSource.class, rent.source()), rent.confirmedOn(),
                            rent.receivedByUserId(), rent.relayBasis(),
                            meta(UnitKey.spaceField(group, id, "monthlyRent"))),
                    new ChecklistDepositTerm(deposit.state(), deposit(deposit.value()), deposit.naReason(),
                            source(ChecklistTermsSource.class, deposit.source()), deposit.confirmedOn(),
                            deposit.receivedByUserId(), deposit.relayBasis(),
                            meta(UnitKey.spaceField(group, id, "deposit"))),
                    months(space.paymentPeriodMonths(), UnitKey.spaceField(group, id, "paymentPeriodMonths")),
                    months(space.minimumTermMonths(), UnitKey.spaceField(group, id, "minimumTermMonths")),
                    overrides == null ? null : new ChecklistChargeOverrides(
                            override(overrides.electricity(), id, "electricity"),
                            override(overrides.water(), id, "water"),
                            override(overrides.internet(), id, "internet"),
                            override(overrides.parking(), id, "parking"),
                            override(overrides.serviceFee(), id, "serviceFee")),
                    space.spaceFees() == null ? null
                            : fees(space.spaceFees(), UnitKey.spaceField(group, id, "spaceFees")));
        }

        private ChecklistChargeTerm override(SourcedField<Charge> charge, String spaceId, String name) {
            return charge == null ? null : charge(charge, UnitKey.override(spaceId, name));
        }

        // ---------- wrappers ----------

        private ChecklistTextField text(Field<String> field, UnitKey key) {
            return new ChecklistTextField(field.state(), field.value(), field.naReason(), meta(key));
        }

        private ChecklistFitField fit(Field<FitAssessment> field, UnitKey key) {
            FitAssessment value = field.value();
            return new ChecklistFitField(field.state(),
                    value == null ? null : new ChecklistFit(value.assessment(), value.reason()), field.naReason(),
                    meta(key));
        }

        private ChecklistAreaCheckField areaCheck(Field<AreaCheck> field, UnitKey key) {
            AreaCheck value = field.value();
            return new ChecklistAreaCheckField(field.state(), value == null ? null
                    : new ChecklistAreaCheck(value.result(), value.noneObserved(), value.note(),
                    value.notEnteredReason()), field.naReason(), meta(key));
        }

        private ChecklistCountField count(SourcedField<Integer> field, UnitKey key) {
            return new ChecklistCountField(field.state(), field.value(), field.naReason(),
                    source(ChecklistCountSource.class, field.source()), field.confirmedOn(),
                    field.receivedByUserId(), field.relayBasis(), meta(key));
        }

        private ChecklistDateTerm date(SourcedField<LocalDate> field, UnitKey key) {
            return new ChecklistDateTerm(field.state(), field.value(), field.naReason(),
                    source(ChecklistTermsSource.class, field.source()), field.confirmedOn(),
                    field.receivedByUserId(), field.relayBasis(), meta(key));
        }

        private ChecklistMonthsTerm months(SourcedField<Integer> field, UnitKey key) {
            return new ChecklistMonthsTerm(field.state(), field.value(), field.naReason(),
                    source(ChecklistTermsSource.class, field.source()), field.confirmedOn(),
                    field.receivedByUserId(), field.relayBasis(), meta(key));
        }

        private ChecklistChargeTerm charge(SourcedField<Charge> field, UnitKey key) {
            return new ChecklistChargeTerm(field.state(), ChecklistResponseMapper.charge(field.value()),
                    field.naReason(), source(ChecklistTermsSource.class, field.source()), field.confirmedOn(),
                    field.receivedByUserId(), field.relayBasis(), meta(key));
        }

        private ChecklistFeeListTerm fees(SourcedField<List<Fee>> field, UnitKey key) {
            return new ChecklistFeeListTerm(field.state(), field.value() == null ? null
                    : field.value().stream().map(ChecklistResponseMapper::fee).toList(), field.naReason(),
                    source(ChecklistTermsSource.class, field.source()), field.confirmedOn(),
                    field.receivedByUserId(), field.relayBasis(), meta(key));
        }

        private ChecklistTextTerm textTerm(SourcedField<String> field, UnitKey key) {
            return new ChecklistTextTerm(field.state(), field.value(), field.naReason(),
                    source(ChecklistTermsSource.class, field.source()), field.confirmedOn(),
                    field.receivedByUserId(), field.relayBasis(), meta(key));
        }

        private ChecklistPetsTerm pets(SourcedField<Pets> field, UnitKey key) {
            Pets value = field.value();
            return new ChecklistPetsTerm(field.state(), value == null ? null
                    : new ChecklistPets(value.policy(), value.note()), field.naReason(),
                    source(ChecklistTermsSource.class, field.source()), field.confirmedOn(),
                    field.receivedByUserId(), field.relayBasis(), meta(key));
        }
    }

    // ==================== values ====================

    /** The family enum of spec 7.2 for a stored source; the template rules only store a source of the family. */
    private static <E extends Enum<E>> E source(Class<E> family, ChecklistSource source) {
        return source == null ? null : Enum.valueOf(family, source.name());
    }

    private static ChecklistObservedLocation location(ObservedLocation location) {
        return location == null ? null : new ChecklistObservedLocation(location.addressText(),
                location.provinceCode(), location.wardCode(), location.latitude(), location.longitude());
    }

    private static ChecklistActualAccess actualAccess(ActualAccess access) {
        return access == null ? null : new ChecklistActualAccess(access.extent(), access.note());
    }

    private static ChecklistObservation observation(Observation observation) {
        return observation == null ? null : new ChecklistObservation(observation.noneObserved(), observation.note());
    }

    private static ChecklistMoney money(Money money) {
        return money == null ? null : new ChecklistMoney(money.amount(), money.currency());
    }

    private static ChecklistRentMoney rentMoney(RentMoney rent) {
        return rent == null ? null : new ChecklistRentMoney(rent.amount(), rent.currency(), rent.unit());
    }

    private static ChecklistDeposit deposit(Deposit deposit) {
        return deposit == null ? null : new ChecklistDeposit(deposit.basis(), deposit.months(),
                money(deposit.amount()));
    }

    private static ChecklistCharge charge(Charge charge) {
        return charge == null ? null : new ChecklistCharge(charge.basis(), money(charge.amount()),
                charge.chargeUnit(), charge.note());
    }

    private static ChecklistFee fee(Fee fee) {
        return new ChecklistFee(fee.name(), money(fee.amount()), fee.chargeUnit());
    }
}
