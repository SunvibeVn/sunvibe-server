package vn.sunvibe.acquisition.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.query.LeadActivityView;
import vn.sunvibe.acquisition.core.application.query.LeadNoteResult;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadDetail;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.application.query.SaleTerritoryView;
import vn.sunvibe.acquisition.core.domain.repository.PageResult;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadActivityResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadNoteResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadPageResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerLeadDetailResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerLeadDetailResponse.AskingPriceResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerLeadDetailResponse.ConsentResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerLeadDetailResponse.DuplicateCandidateResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerLeadSummaryResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.SaleTerritoryResponse;

/**
 * Converter - OwnerLeadResponseMapper
 * <p>
 * Lead and territory views -> response DTOs, field by field. The views already leave out what staff may not see
 * (hashes, the submission snapshot, leads the caller cannot see); nothing is added here. The staff names are
 * already in the views (acquisition-owner-lead-queue-ui C-18): the mapper only copies them.
 */
@Component
public class OwnerLeadResponseMapper {

    public LeadPageResponse toPage(PageResult<OwnerLeadSummary> page) {
        return new LeadPageResponse(page.items().stream().map(this::toSummary).toList(), page.page(), page.size(),
                page.totalElements());
    }

    public OwnerLeadSummaryResponse toSummary(OwnerLeadSummary lead) {
        return new OwnerLeadSummaryResponse(lead.id(), lead.requestCode(), lead.status(), lead.intent(),
                lead.contactName(), lead.provinceCode(), lead.wardCode(), lead.wardName(), lead.propertyType(),
                lead.assignedUserId(), lead.assignedUserName(), lead.nextActionAt(), lead.possibleDuplicate(),
                lead.createdAt(), lead.lastActivityAt(), lead.version());
    }

    public OwnerLeadDetailResponse toDetail(OwnerLeadDetail lead) {
        OwnerLeadDetail.AskingPriceView price = lead.askingPrice();
        return new OwnerLeadDetailResponse(lead.id(), lead.requestCode(), lead.status(), lead.intent(),
                lead.contactName(), lead.provinceCode(), lead.wardCode(), lead.wardName(), lead.propertyType(),
                lead.assignedUserId(), lead.assignedUserName(), lead.nextActionAt(), lead.possibleDuplicate(),
                lead.createdAt(), lead.version(), lead.phone(), lead.email(), lead.addressDetail(), lead.latitude(),
                lead.longitude(),
                price == null ? null : new AskingPriceResponse(price.amount(), price.currency(), price.unit()),
                lead.availableFrom(), lead.notes(), lead.preferredCallbackTime(), lead.source(),
                lead.duplicateOfLeadId(), lead.closeReason(),
                lead.consent() == null ? null
                        : new ConsentResponse(lead.consent().noticeVersion(), lead.consent().acceptedAt()),
                lead.duplicateCandidates().stream()
                        .map(candidate -> new DuplicateCandidateResponse(candidate.id(), candidate.requestCode(),
                                candidate.status(), candidate.createdAt()))
                        .toList(),
                lead.activities().stream().map(this::toActivity).toList());
    }

    public LeadActivityResponse toActivity(LeadActivityView activity) {
        return new LeadActivityResponse(activity.type(), activity.actorUserId(), activity.actorName(),
                activity.fromStatus(), activity.toStatus(), activity.fromAssigneeId(), activity.fromAssigneeName(),
                activity.toAssigneeId(), activity.toAssigneeName(), activity.note(), activity.nextActionAt(),
                activity.occurredAt());
    }

    public LeadNoteResponse toNote(LeadNoteResult result) {
        return new LeadNoteResponse(toActivity(result.activity()), toSummary(result.lead()));
    }

    public SaleTerritoryResponse toTerritory(SaleTerritoryView territory) {
        return new SaleTerritoryResponse(territory.wardCode(), territory.wardName(), territory.wardType(),
                territory.userId(), territory.userName(), territory.assignedAt());
    }
}
