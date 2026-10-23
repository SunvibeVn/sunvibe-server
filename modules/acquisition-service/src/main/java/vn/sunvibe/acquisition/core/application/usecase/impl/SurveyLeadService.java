package vn.sunvibe.acquisition.core.application.usecase.impl;

import com.github.f4b6a3.ulid.Ulid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.application.shared.IntakeLocationRules;
import vn.sunvibe.acquisition.core.application.shared.LeadAccessPolicy;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.shared.StaffNames;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceCheck;
import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceViolation;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadCard;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadFacts;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadHistoryEntry;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMove;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveRefusedException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadPlace;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadVersionConflictException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadChange;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Use Case Implementation - SurveyLeadService
 * <p>
 * The lead side of survey commands (survey-schedule spec 5.4, 5.5). It joins the caller's transaction, so a survey
 * command that fails after the lead move rolls the move and its History entry back with the survey rows. The lead
 * row is saved and flushed here, before the caller writes any survey row: two commands on one lead meet on the lead
 * row (one wins, the other gets a version conflict) and never deadlock. It never calls survey-service back.
 */
@Service
@Transactional
public class SurveyLeadService implements SurveyLeadUseCase {

    private final OwnerLeadDomainRepository leads;
    private final OwnerLeadActivityDomainRepository activities;
    private final LeadVisibility visibility;
    private final LeadAccessPolicy accessPolicy;
    private final IntakeLocationRules locationRules;
    private final StaffNames staffNames;
    private final IdGenerator<Long> idGenerator;

    public SurveyLeadService(OwnerLeadDomainRepository leads, OwnerLeadActivityDomainRepository activities,
                             LeadVisibility visibility, LeadAccessPolicy accessPolicy,
                             IntakeLocationRules locationRules, LeadAssigneeDirectoryPort assigneeDirectory,
                             @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this.leads = leads;
        this.activities = activities;
        this.visibility = visibility;
        this.accessPolicy = accessPolicy;
        this.locationRules = locationRules;
        this.staffNames = new StaffNames(assigneeDirectory);
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
    public Optional<SurveyLeadFacts> findVisible(String leadId, SurveyLeadViewer viewer) {
        return visibility.findVisible(leadViewer(viewer), leadId).map(this::facts);
    }

    @Override
    public SurveyLeadMoveResult applySurveyMove(String leadId, long expectedVersion, SurveyLeadMove move) {
        OwnerLead lead = leads.findById(OwnerLeadId.of(leadId))
                .orElseThrow(() -> new IllegalStateException("A survey move names a lead the caller found."));
        if (lead.getVersion() == null || lead.getVersion() != expectedVersion) {
            throw new SurveyLeadVersionConflictException();
        }
        OwnerLeadStatus target = switch (move.target()) {
            case KEEP -> null;
            case SURVEY_SCHEDULED -> OwnerLeadStatus.SURVEY_SCHEDULED;
            case CONTACTED -> OwnerLeadStatus.CONTACTED;
        };
        LeadChange change;
        try {
            change = lead.moveForSurvey(OwnerLeadActivityType.valueOf(move.event().name()), target,
                    move.referenceId(), move.referenceNo());
        } catch (InvalidLeadTransitionException e) {
            throw new SurveyLeadMoveRefusedException(lead.getStatus().name());
        }
        try {
            leads.save(lead);
        } catch (OptimisticLockingFailureException e) {
            // A concurrent command saved the lead between the read and this write (survey-schedule spec 5.5)
            throw new SurveyLeadVersionConflictException();
        }
        OwnerLeadActivity activity = OwnerLeadActivity.from(idGenerator.generateId(), lead.getId(),
                move.actorUserId(), change, move.occurredAt());
        activities.save(activity);
        String id = lead.getId().getValue();
        // acquisition-owner-lead-queue-ui C-49: read back after the save, so the answer holds the stored value
        Instant lastActivityAt = activities.findLatestOccurredAt(List.of(id)).get(id);
        Map<String, String> names = staffNames.lookup(List.of(move.actorUserId()));
        SurveyLeadHistoryEntry entry = new SurveyLeadHistoryEntry(activity.getType().name(),
                activity.getActorUserId(), StaffNames.nameOf(names, activity.getActorUserId()),
                nameOf(activity.getFromStatus()), nameOf(activity.getToStatus()), activity.getReferenceId(),
                activity.getReferenceNo(), activity.getOccurredAt());
        return new SurveyLeadMoveResult(lead.getStatus().name(), lead.getVersion(), lastActivityAt, entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, SurveyLeadCard> findCards(SurveyLeadViewer viewer, Collection<String> leadIds) {
        List<OwnerLeadId> ids = leadIds.stream().filter(id -> id != null && Ulid.isValid(id)).distinct()
                .map(OwnerLeadId::of).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        LeadViewer leadViewer = leadViewer(viewer);
        Map<String, SurveyLeadCard> cards = new LinkedHashMap<>();
        for (OwnerLead lead : leads.findAllById(ids)) {
            if (leadViewer.canSee(lead)) {
                String id = lead.getId().getValue();
                cards.put(id, new SurveyLeadCard(id, lead.getRequestCode().getValue(), lead.getContactName(),
                        lead.getWardName()));
            }
        }
        return cards;
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingPlaceCheck checkMeetingPlace(String provinceCode, String wardCode, BigDecimal latitude,
                                               BigDecimal longitude) {
        try {
            AdministrativeWard ward = locationRules.resolveWard(provinceCode, wardCode);
            Optional<GeoPoint> pin = locationRules.geoPoint(latitude, longitude, ward);
            return new MeetingPlaceCheck(ward.provinceName(), ward.name(),
                    pin.map(GeoPoint::getLatitude).orElse(null), pin.map(GeoPoint::getLongitude).orElse(null),
                    List.of());
        } catch (IntakeValidationException e) {
            return new MeetingPlaceCheck(null, null, null, null,
                    List.of(new MeetingPlaceViolation(e.getFieldName(), e.getMessage())));
        }
    }

    private SurveyLeadFacts facts(OwnerLead lead) {
        String id = lead.getId().getValue();
        Instant lastActivityAt = activities.findLatestOccurredAt(List.of(id)).get(id);
        // survey-schedule spec A-25: a resumption's basis may not be older than the moment the lead went on hold
        Instant onHoldSince = lead.getStatus() == OwnerLeadStatus.ON_HOLD
                ? activities.findLatestOccurredAtOfStatus(lead.getId(), OwnerLeadStatus.ON_HOLD).orElse(null)
                : null;
        SurveyLeadPlace place = new SurveyLeadPlace(lead.getAddressDetail(), lead.getProvinceCode(),
                lead.getProvinceName(), lead.getWardCode(), lead.getWardName(),
                lead.getGeoPoint().map(GeoPoint::getLatitude).orElse(null),
                lead.getGeoPoint().map(GeoPoint::getLongitude).orElse(null));
        return new SurveyLeadFacts(id, lead.getRequestCode().getValue(), lead.getStatus().name(),
                lead.getIntent().name(), lead.getIntentCycle(), lead.getAssignedUserId(), lead.getVersion(),
                lastActivityAt, lead.getContactName(), place, lead.getCreatedAt(), onHoldSince);
    }

    private static LeadViewer leadViewer(SurveyLeadViewer viewer) {
        return new LeadViewer(viewer.userId(),
                viewer.access() == SurveyLeadAccess.ALL ? LeadAccess.ALL : LeadAccess.OWN);
    }

    private static String nameOf(OwnerLeadStatus status) {
        return status == null ? null : status.name();
    }
}
