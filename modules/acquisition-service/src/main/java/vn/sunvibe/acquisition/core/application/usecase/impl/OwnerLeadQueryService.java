package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.query.ConsentView;
import vn.sunvibe.acquisition.core.application.query.DuplicateCandidateView;
import vn.sunvibe.acquisition.core.application.query.LeadActivityView;
import vn.sunvibe.acquisition.core.application.query.LeadListQuery;
import vn.sunvibe.acquisition.core.application.query.LeadScope;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadDetail;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.usecase.OwnerLeadQueryUseCase;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.log.OwnerLeadAccessLog;
import vn.sunvibe.acquisition.core.domain.repository.ConsentRecordDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.LeadSearchCriteria;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadAccessLogDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.PageResult;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Use Case Implementation - OwnerLeadQueryService
 * <p>
 * Lists and opens owner leads for staff. Every lookup goes through {@link LeadVisibility}; duplicate candidates are
 * counted and shown only when the viewer can see them (acquisition-owner-lead-queue C-17). Opening a lead writes one
 * access-log row in the same transaction (acquisition-owner-lead-queue BR-7); listing writes nothing.
 */
@Service
@Transactional
public class OwnerLeadQueryService implements OwnerLeadQueryUseCase {

    private final OwnerLeadDomainRepository leads;
    private final OwnerLeadActivityDomainRepository activities;
    private final OwnerLeadAccessLogDomainRepository accessLogs;
    private final DuplicateCandidateDomainRepository duplicateCandidates;
    private final ConsentRecordDomainRepository consents;
    private final LeadVisibility visibility;
    private final IdGenerator<Long> idGenerator;

    public OwnerLeadQueryService(OwnerLeadDomainRepository leads, OwnerLeadActivityDomainRepository activities,
                                 OwnerLeadAccessLogDomainRepository accessLogs,
                                 DuplicateCandidateDomainRepository duplicateCandidates,
                                 ConsentRecordDomainRepository consents, LeadVisibility visibility,
                                 @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this.leads = leads;
        this.activities = activities;
        this.accessLogs = accessLogs;
        this.duplicateCandidates = duplicateCandidates;
        this.consents = consents;
        this.visibility = visibility;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OwnerLeadSummary> list(LeadViewer viewer, LeadListQuery query) {
        // acquisition-owner-lead-queue BR-1: a SALE sees only the leads assigned to them, whatever scope was asked for
        Optional<String> assignee = viewer.access() == LeadAccess.OWN || query.scope() == LeadScope.MINE
                ? Optional.of(viewer.userId())
                : Optional.empty();
        Set<OwnerLeadStatus> statuses = query.statuses();
        if (query.scope() == LeadScope.UNASSIGNED) {
            // Unassigned means status NEW; a status filter without NEW leaves nothing to show
            if (!statuses.isEmpty() && !statuses.contains(OwnerLeadStatus.NEW)) {
                return new PageResult<>(List.of(), query.page(), query.size(), 0);
            }
            statuses = Set.of(OwnerLeadStatus.NEW);
        }
        PageResult<OwnerLead> page = leads.search(
                new LeadSearchCriteria(assignee, statuses, Optional.ofNullable(query.wardCodeOrNull())),
                query.page(), query.size());

        List<String> ids = page.items().stream().map(lead -> lead.getId().getValue()).toList();
        Set<String> withCandidate = ids.isEmpty() ? Set.of()
                : duplicateCandidates.leadIdsWithVisibleCandidate(ids, LeadVisibility.candidateViewer(viewer));
        List<OwnerLeadSummary> items = page.items().stream()
                .map(lead -> OwnerLeadSummary.of(lead, withCandidate.contains(lead.getId().getValue())))
                .toList();
        return new PageResult<>(items, page.page(), page.size(), page.totalElements());
    }

    @Override
    public OwnerLeadDetail get(LeadViewer viewer, String leadId) {
        OwnerLead lead = visibility.requireVisible(viewer, leadId);
        accessLogs.save(OwnerLeadAccessLog.create(idGenerator.generateId(), lead.getId(), viewer.userId(),
                AppClock.currentUTC()));

        List<DuplicateCandidateView> candidates = duplicateCandidates
                .findVisibleCandidates(lead.getId(), LeadVisibility.candidateViewer(viewer)).stream()
                .map(DuplicateCandidateView::of)
                .toList();
        // acquisition-owner-lead-queue C-17: the lead this one repeats is named only when the viewer can see it
        String duplicateOf = lead.getDuplicateOfLeadId() == null ? null
                : visibility.findVisible(viewer, lead.getDuplicateOfLeadId().getValue())
                        .map(original -> original.getId().getValue())
                        .orElse(null);
        ConsentRecord consent = consents.findById(lead.getConsentRecordId())
                .orElseThrow(() -> new IllegalStateException("The consent record of a lead is missing."));
        List<LeadActivityView> history = activities.findByLeadIdOrderByOccurredAt(lead.getId()).stream()
                .map(LeadActivityView::of)
                .toList();
        return detail(lead, candidates, duplicateOf, consent, history);
    }

    private static OwnerLeadDetail detail(OwnerLead lead, List<DuplicateCandidateView> candidates,
                                          String duplicateOf, ConsentRecord consent,
                                          List<LeadActivityView> history) {
        OwnerLeadSummary summary = OwnerLeadSummary.of(lead, !candidates.isEmpty());
        OwnerLeadDetail.AskingPriceView price = lead.getAskingPrice()
                .map(p -> new OwnerLeadDetail.AskingPriceView(p.getAmount(), p.getCurrency(), p.getUnit()))
                .orElse(null);
        return new OwnerLeadDetail(summary.id(), summary.requestCode(), summary.status(), summary.intent(),
                summary.contactName(), summary.provinceCode(), summary.wardCode(), summary.wardName(),
                summary.propertyType(), summary.assignedUserId(), summary.nextActionAt(), summary.possibleDuplicate(),
                summary.createdAt(), summary.version(), lead.getPhone().getValue(), lead.getEmail(),
                lead.getAddressDetail(), lead.getGeoPoint().map(GeoPoint::getLatitude).orElse(null),
                lead.getGeoPoint().map(GeoPoint::getLongitude).orElse(null), price, lead.getAvailableFrom(),
                lead.getNotes(), lead.getPreferredCallbackTime(), lead.getSource(), duplicateOf,
                lead.getCloseReason(), new ConsentView(consent.getNoticeVersion(), consent.getAcceptedAt()),
                candidates, history);
    }
}
