package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistDecideResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistRequalifyResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistRevisionResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistStartResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSubmitResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSummaryView;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistTakeoverResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistView;
import vn.sunvibe.survey.core.application.query.checklist.CheckpointView;
import vn.sunvibe.survey.core.application.query.common.LeadState;
import vn.sunvibe.survey.core.application.query.common.PassedView;
import vn.sunvibe.survey.core.application.query.checklist.ReviewQueueItem;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStart;
import vn.sunvibe.survey.core.domain.policy.checklist.EarlierHazard;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.RequalificationState;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionStart;
import vn.sunvibe.survey.core.domain.repository.common.PageResult;
import vn.sunvibe.survey.core.presentation.dto.response.common.SurveyLeadStateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDecidedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistMedia;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAddressedBy;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistBasis;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCheckpointResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCorrectedUnit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCorrection;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistEarlierHazard;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistEditor;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHistoryEntryResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistLeadSummary;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistMissingItem;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistOwnerForm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPassedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReadiness;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRentMoney;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRequalificationHistoryEntryResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRequalificationStateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRequalifiedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReviewHistoryEntryResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReviewQueueItemResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReviewQueueResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRevisionStartResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRevisionStartedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSnapshotResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStaffMember;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStartStateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStartedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSubmittedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSummaryResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSurveyedScope;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSurveyedSpace;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTakenOverResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistUnconfirmedTerm;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistVisit;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistWarning;

import java.util.List;
import java.util.function.Function;

/**
 * Converter - ChecklistResponseConverter
 * <p>
 * Turns the checklist read models into the response bodies of survey-checklist spec 7.2. It is the one public entry:
 * {@link ChecklistGroupsConverter} builds the groups and the effective terms, {@link ChecklistReviewConverter} the
 * review blocks. Staff names come from the view's one name lookup; an unknown name is null.
 */
@Component
public class ChecklistResponseConverter {

    private static final String CURRENCY = Money.VND;
    private static final String MONTH = RentMoney.MONTH;

    public ChecklistResponse toResponse(ChecklistView view) {
        SurveyChecklist checklist = view.checklist();
        Function<String, String> names = view::nameOf;
        List<ChecklistStaffMember> leadStaff = view.leadStaff().stream()
                .map(id -> new ChecklistStaffMember(id, view.nameOf(id))).toList();
        return new ChecklistResponse(checklist.getId().getValue(), checklist.getLeadId(),
                new ChecklistLeadSummary(view.requestCode(), view.leadStatus()), leadStaff,
                checklist.getChecklistNo(), checklist.getStatus(), checklist.getKind(), checklist.getTemplateCode(),
                checklist.getTemplateVersion(), visit(view.visit(), names), checklist.getHolderUserId(),
                view.nameOf(checklist.getHolderUserId()), view.editability().editable(),
                view.editability().reason(),
                ChecklistGroupsConverter.groups(view.groups(), view.difference(), names),
                readiness(view.readiness()), new ChecklistMedia(view.media().available(),
                        view.media().uploadEnabled(), view.media().setVersion()),
                ChecklistGroupsConverter.effectiveTerms(view.effectiveTerms(),
                        view.groups().get(ChecklistGroup.TERMS).document(), names),
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
                checklist.getVersion() == null ? 0L : checklist.getVersion(), view.updatedAt(),
                checklist.getPreviousChecklistIdOrNull() == null ? null
                        : checklist.getPreviousChecklistIdOrNull().getValue(),
                ChecklistReviewConverter.submission(view.review().submissionOrNull(), names),
                ChecklistReviewConverter.decision(view.review().decisionOrNull(), names),
                ChecklistReviewConverter.review(view.review().review()), toPassed(view.review().passedOrNull()),
                ChecklistReviewConverter.requalification(view.review().requalificationOrNull(), names));
    }

    /** survey-checklist submission spec 7.1: one page of the review queue. */
    public ChecklistReviewQueueResponse toReviewQueue(PageResult<ReviewQueueItem> page) {
        return new ChecklistReviewQueueResponse(page.items().stream().map(item ->
                new ChecklistReviewQueueItemResponse(item.checklistId(), item.checklistNo(), item.leadId(),
                        item.leadCode(), item.submittedAt(), new ChecklistStaffMember(item.submittedByUserId(),
                        item.submittedByNameOrNull()), item.canReview(), item.reasonOrNull())).toList(),
                page.page(), page.size(), page.totalElements());
    }

    public ChecklistStartedResponse toStarted(ChecklistStartResult result) {
        LeadState lead = result.lead();
        return new ChecklistStartedResponse(toResponse(result.view()),
                new SurveyLeadStateResponse(lead.id(), lead.status(), lead.version(), lead.lastActivityAt()),
                toEntry(result.historyEntry()));
    }

    public ChecklistSubmittedResponse toSubmitted(ChecklistSubmitResult result) {
        return new ChecklistSubmittedResponse(toResponse(result.view()), result.historyEntryOrNull() == null ? null
                : toEntry(result.historyEntryOrNull()));
    }

    public ChecklistDecidedResponse toDecided(ChecklistDecideResult result) {
        LeadState lead = result.lead();
        LeadHistoryEntry entry = result.historyEntry();
        return new ChecklistDecidedResponse(toResponse(result.view()), new SurveyLeadStateResponse(lead.id(),
                lead.status(), lead.version(), lead.lastActivityAt()), new ChecklistReviewHistoryEntryResponse(
                entry.type(), entry.actorUserId(), entry.actorName(), entry.fromStatus(), entry.toStatus(),
                entry.occurredAt(), entry.referenceId(), entry.referenceNo(), entry.referenceDetail()));
    }

    /** property-create requalification spec 7: the revision, the lead and the History entry. */
    public ChecklistRequalifiedResponse toRequalified(ChecklistRequalifyResult result) {
        LeadState lead = result.lead();
        LeadHistoryEntry entry = result.historyEntry();
        return new ChecklistRequalifiedResponse(toResponse(result.view()), new SurveyLeadStateResponse(lead.id(),
                lead.status(), lead.version(), lead.lastActivityAt()), new ChecklistRequalificationHistoryEntryResponse(
                entry.type(), entry.actorUserId(), entry.actorName(), entry.fromStatus(), entry.toStatus(),
                entry.occurredAt(), entry.referenceId(), entry.referenceNo()));
    }

    public ChecklistRevisionStartedResponse toRevisionStarted(ChecklistRevisionResult result) {
        return new ChecklistRevisionStartedResponse(toResponse(result.view()), toEntry(result.historyEntry()));
    }

    public ChecklistTakenOverResponse toTakenOver(ChecklistTakeoverResult result) {
        return new ChecklistTakenOverResponse(toResponse(result.view()), toEntry(result.historyEntry()));
    }

    public ChecklistCheckpointResponse toCheckpoint(CheckpointView view) {
        Function<String, String> names = view::nameOf;
        return new ChecklistCheckpointResponse(takeover(view.takeover(), names),
                new ChecklistSnapshotResponse(
                        ChecklistGroupsConverter.groups(view.groups(), view.difference(), names)));
    }

    /** survey-checklist spec 7.2 {@code checklistStart} of the survey block. */
    public static ChecklistStartStateResponse toStartState(ChecklistStart start) {
        return new ChecklistStartStateResponse(start.allowed(), start.visitAppointmentIdOrNull(),
                start.intakeDeclarationRequired(), start.blockedByOrNull());
    }

    /** survey-checklist submission spec 7.2 {@code revisionStart} of the survey block. */
    public static ChecklistRevisionStartResponse toRevisionStart(RevisionStart start) {
        return new ChecklistRevisionStartResponse(start.allowed(), start.checklistIdOrNull(), start.intakeRequired(),
                start.blockedByOrNull());
    }

    /** survey-checklist submission spec 4.7. */
    public static ChecklistPassedResponse toPassed(PassedView passed) {
        return passed == null ? null : new ChecklistPassedResponse(passed.usable(), passed.reasonOrNull(),
                new ChecklistSurveyedScope(passed.rentalScope(), passed.spaces().stream()
                        .map(space -> new ChecklistSurveyedSpace(space.spaceId(), space.code())).toList()));
    }

    /** property-create requalification spec 7: the action on the survey block. */
    public static ChecklistRequalificationStateResponse toRequalificationState(RequalificationState state) {
        return new ChecklistRequalificationStateResponse(state.allowed(), state.checklistIdOrNull(),
                state.expectedVersionOrNull());
    }

    /** survey-checklist spec 7.2 {@code checklists[]} of a VISITED appointment in the survey block. */
    public static ChecklistSummaryResponse toSummary(ChecklistSummaryView view, Function<String, String> names) {
        return new ChecklistSummaryResponse(view.id(), view.checklistNo(), view.status(), view.outcomeOrNull(),
                view.editability().editable(), view.editability().reason(), view.missingCount(), view.holderUserId(),
                names.apply(view.holderUserId()), view.updatedAt(), view.updatedByUserIdOrNull(),
                names.apply(view.updatedByUserIdOrNull()), view.requalified());
    }

    // ==================== checklist parts ====================

    static ChecklistHistoryEntryResponse toEntry(ChecklistHistoryEntry entry) {
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
                SurveyResponseConverter.toLocation(appointment.getDetails().meetingLocation()),
                SurveyResponseConverter.toLocation(appointment.getLeadLocation()));
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

    /** survey-checklist spec 7.2: an earlier hazard as recorded; its metadata stays on its own checklist. */
    private static ChecklistEarlierHazard earlierHazard(EarlierHazard earlier, Function<String, String> names) {
        return new ChecklistEarlierHazard(earlier.checklistId(), earlier.checklistNo(),
                ChecklistGroupsConverter.hazard(earlier.hazard(), null, names),
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
            vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeoverRow takeover,
            Function<String, String> names) {
        return new ChecklistTakeover(takeover.takeoverNo(), takeover.kind(), takeover.fromUserId(),
                names.apply(takeover.fromUserId()), takeover.toUserId(), names.apply(takeover.toUserId()),
                takeover.reason(), basis(takeover.basis()), takeover.occurredAt(), takeover.snapshotOrNull() != null);
    }

    private static ChecklistBasis basis(Basis basis) {
        return basis == null ? null : new ChecklistBasis(basis.confirmedOn(), basis.text());
    }
}
