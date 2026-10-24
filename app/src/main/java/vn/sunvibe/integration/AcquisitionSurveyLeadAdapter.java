package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceCheck;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistActivity;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistActivityResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistHistoryEntry;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadFacts;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadHistoryEntry;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMove;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveRefusedException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadTarget;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadVersionConflictException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadCard;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadPlace;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceCheck;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceViolation;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cross-module composition (survey-schedule spec 5.4, ADR-009): answers survey-service's lead questions with
 * acquisition-service's published {@link SurveyLeadUseCase}, so neither module depends on the other. It maps records
 * and the two published exceptions and forwards; every lead rule stays in acquisition. Calls join the caller's
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class AcquisitionSurveyLeadAdapter implements SurveyLeadPort {

    private final SurveyLeadUseCase surveyLeadUseCase;

    @Override
    public SurveyViewer viewer(String userId, Set<String> expandedAuthorities) {
        SurveyLeadViewer viewer = surveyLeadUseCase.viewerFor(userId, expandedAuthorities);
        return new SurveyViewer(viewer.userId(),
                viewer.access() == SurveyLeadAccess.ALL ? ViewerAccess.ALL : ViewerAccess.OWN);
    }

    @Override
    public Optional<LeadFacts> findVisible(String leadId, SurveyViewer viewer) {
        return surveyLeadUseCase.findVisible(leadId, toProvider(viewer)).map(AcquisitionSurveyLeadAdapter::facts);
    }

    @Override
    public LeadMoveResult apply(String leadId, long expectedVersion, LeadMove move) {
        SurveyLeadMoveResult result;
        try {
            result = surveyLeadUseCase.applySurveyMove(leadId, expectedVersion, new SurveyLeadMove(
                    SurveyLeadEvent.valueOf(move.event().name()), SurveyLeadTarget.valueOf(move.target().name()),
                    move.actorUserId(), move.referenceId(), move.referenceNo(), move.occurredAt()));
        } catch (SurveyLeadVersionConflictException e) {
            throw new LeadVersionConflictException();
        } catch (SurveyLeadMoveRefusedException e) {
            throw new LeadMoveRefusedException(e.getStatus());
        }
        SurveyLeadHistoryEntry entry = result.entry();
        return new LeadMoveResult(result.status(), result.version(), result.lastActivityAt(),
                new LeadHistoryEntry(entry.type(), entry.actorUserId(), entry.actorName(), entry.fromStatus(),
                        entry.toStatus(), entry.referenceId(), entry.referenceNo(), entry.occurredAt()));
    }

    @Override
    public Map<String, LeadCard> cards(SurveyViewer viewer, Collection<String> leadIds) {
        Map<String, LeadCard> cards = new LinkedHashMap<>();
        surveyLeadUseCase.findCards(toProvider(viewer), leadIds).forEach((id, card) -> cards.put(id,
                new LeadCard(card.leadId(), card.requestCode(), card.contactName(), card.wardName())));
        return cards;
    }

    @Override
    public PlaceCheck checkPlace(String provinceCode, String wardCode, BigDecimal latitude, BigDecimal longitude) {
        MeetingPlaceCheck check = surveyLeadUseCase.checkMeetingPlace(provinceCode, wardCode, latitude, longitude);
        return new PlaceCheck(check.provinceName(), check.wardName(), check.latitude(), check.longitude(),
                check.violations().stream()
                        .map(violation -> new PlaceViolation(violation.field(), violation.message()))
                        .toList());
    }

    @Override
    public Optional<LeadFacts> findVisibleForShare(String leadId, SurveyViewer viewer) {
        return surveyLeadUseCase.findVisibleForShare(leadId, toProvider(viewer))
                .map(AcquisitionSurveyLeadAdapter::facts);
    }

    @Override
    public ChecklistActivityResult recordChecklistActivity(String leadId, ChecklistActivity activity) {
        SurveyChecklistActivityResult result = surveyLeadUseCase.recordChecklistActivity(leadId,
                new SurveyChecklistActivity(SurveyChecklistEvent.valueOf(activity.type().name()),
                        activity.actorUserId(), activity.checklistId(), activity.checklistNo(),
                        activity.detailOrNull(), activity.occurredAt()));
        SurveyChecklistHistoryEntry entry = result.entry();
        return new ChecklistActivityResult(result.lastActivityAt(), new ChecklistHistoryEntry(entry.type(),
                entry.actorUserId(), entry.actorName(), entry.referenceId(), entry.referenceNo(),
                entry.referenceDetail(), entry.occurredAt()));
    }

    @Override
    public boolean leadExists(String leadId) {
        return surveyLeadUseCase.leadExists(leadId);
    }

    @Override
    public Optional<OwnerFormFacts> ownerForm(String leadId) {
        return surveyLeadUseCase.ownerForm(leadId).map(form -> new OwnerFormFacts(form.submittedAt(),
                form.addressDetail(), form.provinceCode(), form.wardCode(), form.latitude(), form.longitude(),
                form.monthlyAskingPriceOrNull(), form.availableFromOrNull()));
    }

    @Override
    public Set<String> leadStaffIds(String leadId) {
        return surveyLeadUseCase.leadStaffIds(leadId);
    }

    private static SurveyLeadViewer toProvider(SurveyViewer viewer) {
        return new SurveyLeadViewer(viewer.userId(),
                viewer.access() == ViewerAccess.ALL ? SurveyLeadAccess.ALL : SurveyLeadAccess.OWN);
    }

    private static LeadFacts facts(SurveyLeadFacts facts) {
        return new LeadFacts(facts.leadId(), facts.requestCode(), facts.status(), facts.intent(),
                facts.intentCycle(), facts.assignedUserId(), facts.version(), facts.lastActivityAt(),
                facts.contactName(), new LeadPlace(facts.place().addressDetail(), facts.place().provinceCode(),
                        facts.place().provinceName(), facts.place().wardCode(), facts.place().wardName(),
                        facts.place().latitude(), facts.place().longitude()),
                facts.createdAt(), facts.onHoldSince());
    }
}
