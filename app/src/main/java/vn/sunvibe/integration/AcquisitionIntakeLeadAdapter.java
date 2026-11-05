package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeActivity;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeEvent;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeFacts;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeHistoryEntry;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeViewer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cross-module composition (property-create intake spec 5.1, ADR-009): answers property-service's lead questions with
 * acquisition-service's published {@link LeadIntakeUseCase}. It only translates records, and joins the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class AcquisitionIntakeLeadAdapter implements IntakeLeadPort {

    private final LeadIntakeUseCase leads;

    @Override
    public IntakeViewer viewerFor(String userId, Set<String> expandedAuthorities) {
        SurveyLeadViewer viewer = leads.viewerFor(userId, expandedAuthorities);
        return new IntakeViewer(viewer.userId(), viewer.access() == SurveyLeadAccess.ALL);
    }

    @Override
    public Optional<IntakeLead> findVisible(String leadId, IntakeViewer viewer) {
        return leads.findVisible(leadId, viewer(viewer)).map(AcquisitionIntakeLeadAdapter::lead);
    }

    @Override
    public Optional<IntakeLead> findVisibleForUpdate(String leadId, IntakeViewer viewer) {
        return leads.findVisibleForUpdate(leadId, viewer(viewer)).map(AcquisitionIntakeLeadAdapter::lead);
    }

    @Override
    public Optional<IntakeLead> lockForShare(String leadId) {
        return leads.lockForShare(leadId).map(AcquisitionIntakeLeadAdapter::lead);
    }

    @Override
    public Map<String, IntakeLead> systemFacts(Collection<String> leadIds) {
        Map<String, IntakeLead> facts = new LinkedHashMap<>();
        leads.systemFacts(leadIds).forEach((id, lead) -> facts.put(id, lead(lead)));
        return facts;
    }

    @Override
    public List<String> otherOpenLeadIdsOfPhone(String leadId, IntakeViewer viewer) {
        return leads.otherOpenLeadIdsOfPhone(leadId, viewer(viewer));
    }

    @Override
    public void recordActivity(String leadId, IntakeHistoryEntry entry) {
        leads.recordIntakeActivity(leadId, new LeadIntakeActivity(LeadIntakeEvent.valueOf(entry.type().name()),
                entry.actorUserIdOrNull(), entry.intakeIdOrNull(), entry.intakeNoOrNull(), entry.occurredAt()));
    }

    private static SurveyLeadViewer viewer(IntakeViewer viewer) {
        return new SurveyLeadViewer(viewer.userId(), viewer.seesAllLeads() ? SurveyLeadAccess.ALL : SurveyLeadAccess.OWN);
    }

    private static IntakeLead lead(LeadIntakeFacts facts) {
        return new IntakeLead(facts.leadId(), facts.requestCode(), facts.status(), facts.intent(), facts.intentCycle(),
                facts.assignedUserIdOrNull(), facts.earlierAssignees(), facts.wardName(), facts.closed());
    }
}
