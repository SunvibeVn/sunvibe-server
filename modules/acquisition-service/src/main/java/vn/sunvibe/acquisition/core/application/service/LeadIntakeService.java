package vn.sunvibe.acquisition.core.application.service;

import com.github.f4b6a3.ulid.Ulid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.shared.LeadAccessPolicy;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeActivity;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeFacts;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Use Case Implementation - LeadIntakeService
 * <p>
 * The lead side of the inventory intake (property-create intake spec 5.1). It joins the caller's transaction. The intake
 * never moves a lead (BR-16): it reads, holds the row for a command and adds History entries as activity rows, so the
 * lead's version and status stay as they are. It never calls property-service back.
 */
@Service
@Transactional
public class LeadIntakeService implements LeadIntakeUseCase {

    private final OwnerLeadDomainRepository leads;
    private final OwnerLeadActivityDomainRepository activities;
    private final LeadVisibility visibility;
    private final LeadAccessPolicy accessPolicy;
    private final IdGenerator<Long> idGenerator;

    public LeadIntakeService(OwnerLeadDomainRepository leads, OwnerLeadActivityDomainRepository activities,
                             LeadVisibility visibility, LeadAccessPolicy accessPolicy,
                             @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this.leads = leads;
        this.activities = activities;
        this.visibility = visibility;
        this.accessPolicy = accessPolicy;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyLeadViewer viewerFor(String userId, Set<String> expandedAuthorities) {
        LeadAccess access = accessPolicy.accessFor(expandedAuthorities)
                .orElseThrow(() -> new IllegalStateException("Caller has no role that may read owner leads."));
        return new SurveyLeadViewer(userId, access == LeadAccess.ALL ? SurveyLeadAccess.ALL : SurveyLeadAccess.OWN);
    }

    @Override
    public Optional<LeadIntakeFacts> findVisible(String leadId, SurveyLeadViewer viewer) {
        return visibility.findVisible(leadViewer(viewer), leadId).map(this::facts);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LeadIntakeFacts> findVisibleForUpdate(String leadId, SurveyLeadViewer viewer) {
        return visibility.findVisibleForUpdate(leadViewer(viewer), leadId).map(this::facts);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LeadIntakeFacts> lockForShare(String leadId) {
        if (leadId == null || !Ulid.isValid(leadId)) {
            return Optional.empty();
        }
        return leads.findByIdForShare(OwnerLeadId.of(leadId)).map(this::facts);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, LeadIntakeFacts> systemFacts(Collection<String> leadIds) {
        List<OwnerLeadId> ids = leadIds.stream().filter(id -> id != null && Ulid.isValid(id)).distinct()
                .map(OwnerLeadId::of).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> assignees = activities.findAssigneeIds(ids.stream().map(OwnerLeadId::getValue).toList());
        Map<String, LeadIntakeFacts> facts = new LinkedHashMap<>();
        for (OwnerLead lead : leads.findAllById(ids)) {
            String id = lead.getId().getValue();
            facts.put(id, facts(lead, assignees.getOrDefault(id, Set.of())));
        }
        return facts;
    }

    @Override
    public List<String> otherOpenLeadIdsOfPhone(String leadId, SurveyLeadViewer viewer) {
        LeadViewer leadViewer = leadViewer(viewer);
        OwnerLead lead = visibility.requireVisible(leadViewer, leadId);
        List<OwnerLeadId> others = leads.findOpenIdsByPhone(lead.getPhone().getValue()).stream()
                .filter(id -> !id.equals(lead.getId())).toList();
        return leads.findAllById(others).stream().filter(leadViewer::canSee).map(other -> other.getId().getValue())
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordIntakeActivity(String leadId, LeadIntakeActivity activity) {
        activities.save(OwnerLeadActivity.inventoryEvent(idGenerator.generateId(), OwnerLeadId.of(leadId),
                OwnerLeadActivityType.valueOf(activity.event().name()), activity.actorUserIdOrNull(),
                activity.intakeIdOrNull(), activity.intakeNoOrNull(), activity.occurredAt()));
    }

    private LeadIntakeFacts facts(OwnerLead lead) {
        String id = lead.getId().getValue();
        return facts(lead, activities.findAssigneeIds(List.of(id)).getOrDefault(id, Set.of()));
    }

    private static LeadIntakeFacts facts(OwnerLead lead, Set<String> namedAssignees) {
        Set<String> assignees = new HashSet<>(namedAssignees);
        if (lead.getAssignedUserId() != null) {
            assignees.add(lead.getAssignedUserId());
        }
        return new LeadIntakeFacts(lead.getId().getValue(), lead.getRequestCode().getValue(), lead.getStatus().name(),
                lead.getIntent().name(), lead.getIntentCycle(), lead.getAssignedUserId(), assignees, lead.getWardName(),
                lead.getStatus().isClosed());
    }

    private static LeadViewer leadViewer(SurveyLeadViewer viewer) {
        return new LeadViewer(viewer.userId(),
                viewer.access() == SurveyLeadAccess.ALL ? LeadAccess.ALL : LeadAccess.OWN);
    }
}
