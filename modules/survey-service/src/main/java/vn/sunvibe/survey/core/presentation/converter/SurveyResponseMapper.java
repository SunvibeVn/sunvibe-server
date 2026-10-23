package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.query.AppointmentView;
import vn.sunvibe.survey.core.application.query.LeadState;
import vn.sunvibe.survey.core.application.query.OverlapView;
import vn.sunvibe.survey.core.application.query.ResumptionView;
import vn.sunvibe.survey.core.application.query.SurveyBlock;
import vn.sunvibe.survey.core.application.query.SurveyCommandResult;
import vn.sunvibe.survey.core.application.query.SurveyListItem;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.Cancellation;
import vn.sunvibe.survey.core.domain.model.appointment.Confirmation;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.repository.PageResult;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyAppointmentResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyBasisResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyBlockResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyBookingResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyCancellationResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyCommandResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyConfirmationResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyEventResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyHistoryEntryResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyHolderResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyLeadStateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyListItemResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyLocationResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyOverlapResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyOverlapsResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyPageResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyProxyResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyResumptionResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyRevisionResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyVisitResponse;

import java.util.List;

/**
 * Converter - SurveyResponseMapper
 * <p>
 * Turns the survey read models into the response bodies of survey-schedule spec 7.2. Staff names come from the
 * block's one name lookup; an unknown name is null.
 */
@Component
public class SurveyResponseMapper {

    public SurveyCommandResponse toCommand(SurveyCommandResult result) {
        LeadState lead = result.lead();
        return new SurveyCommandResponse(toBlock(result.survey()),
                new SurveyLeadStateResponse(lead.id(), lead.status(), lead.version(), lead.lastActivityAt()),
                toEntry(result.historyEntry()), result.overlaps().stream().map(SurveyResponseMapper::toOverlap)
                .toList());
    }

    public SurveyBlockResponse toBlock(SurveyBlock block) {
        return new SurveyBlockResponse(block.leadId(), block.intentCycle(),
                new SurveyBookingResponse(block.booking().allowed(), block.booking().kind(),
                        block.booking().previousVisitId(), block.booking().blockedBy()),
                block.currentVisitId(), block.resumeAllowed(),
                block.appointments().stream().map(view -> toAppointment(view, block)).toList(),
                block.resumptions().stream().map(resumption -> toResumption(resumption, block)).toList());
    }

    public SurveyOverlapsResponse toOverlaps(List<OverlapView> overlaps) {
        return new SurveyOverlapsResponse(overlaps.stream().map(SurveyResponseMapper::toOverlap).toList());
    }

    public SurveyPageResponse toPage(PageResult<SurveyListItem> page) {
        return new SurveyPageResponse(page.items().stream().map(SurveyResponseMapper::toItem).toList(), page.page(),
                page.size(), page.totalElements());
    }

    private static SurveyAppointmentResponse toAppointment(AppointmentView view, SurveyBlock block) {
        AppointmentDetails details = view.details();
        return new SurveyAppointmentResponse(view.id(), view.leadId(), view.appointmentNo(), view.kind(),
                view.previousVisitId(), view.resurveyReason(), view.intent(), view.intentCycle(),
                view.inCurrentCycle(), view.status(), details.startAt(), details.durationMinutes(),
                toLocation(details.meetingLocation()), toLocation(view.leadLocation()), view.locationReason(),
                details.accessNote(), details.onSiteContactName(), details.onSiteContactRole(), view.surveyorUserId(),
                block.nameOf(view.surveyorUserId()), view.cannotBePerformed(),
                view.holders().stream().map(holder -> new SurveyHolderResponse(holder.userId(),
                        block.nameOf(holder.userId()), holder.from(), holder.to())).toList(),
                toConfirmation(view.confirmation(), block), toVisit(view.visit(), block),
                toCancellation(view.cancellation(), block),
                view.revisions().stream().map(revision -> toRevision(revision, block)).toList(),
                view.events().stream().map(event -> toEvent(event, block)).toList(), view.createdByUserId(),
                block.nameOf(view.createdByUserId()), view.createdAt(), view.version());
    }

    private static SurveyConfirmationResponse toConfirmation(Confirmation confirmation, SurveyBlock block) {
        return new SurveyConfirmationResponse(confirmation.state(), confirmation.cause(),
                confirmation.everConfirmed(), confirmation.changedBy(), block.nameOf(confirmation.changedBy()),
                confirmation.changedAt(), confirmation.note());
    }

    private static SurveyVisitResponse toVisit(VisitRecord visit, SurveyBlock block) {
        if (visit == null) {
            return null;
        }
        SurveyProxyResponse proxy = visit.proxyReason() == null ? null
                : new SurveyProxyResponse(visit.proxyReason(), toBasis(visit.basis()));
        return new SurveyVisitResponse(visit.kind(), visit.performedBy(), block.nameOf(visit.performedBy()),
                visit.arrivedAt(), visit.revisionNo(), visit.note(), visit.recordedBy(),
                block.nameOf(visit.recordedBy()), visit.recordedAt(), proxy);
    }

    private static SurveyCancellationResponse toCancellation(Cancellation cancellation, SurveyBlock block) {
        return cancellation == null ? null : new SurveyCancellationResponse(cancellation.reason(),
                cancellation.description(), cancellation.cancelledBy(), block.nameOf(cancellation.cancelledBy()),
                cancellation.cancelledAt());
    }

    private static SurveyRevisionResponse toRevision(AppointmentRevision revision, SurveyBlock block) {
        AppointmentDetails details = revision.details();
        return new SurveyRevisionResponse(revision.revisionNo(), revision.validFrom(), details.startAt(),
                details.durationMinutes(), toLocation(details.meetingLocation()), details.accessNote(),
                details.onSiteContactName(), details.onSiteContactRole(), revision.reason(), revision.actorUserId(),
                block.nameOf(revision.actorUserId()));
    }

    private static SurveyEventResponse toEvent(AppointmentEvent event, SurveyBlock block) {
        return new SurveyEventResponse(event.type(), event.actorUserId(), block.nameOf(event.actorUserId()),
                event.occurredAt(), event.revisionNo(), event.fromSurveyorUserId(),
                block.nameOf(event.fromSurveyorUserId()), event.toSurveyorUserId(),
                block.nameOf(event.toSurveyorUserId()), event.confirmationCause(), event.note());
    }

    private static SurveyResumptionResponse toResumption(ResumptionView resumption, SurveyBlock block) {
        return new SurveyResumptionResponse(resumption.visitAppointmentId(), resumption.visitAppointmentNo(),
                resumption.reason(), toBasis(resumption.basis()), resumption.actorUserId(),
                block.nameOf(resumption.actorUserId()), resumption.occurredAt());
    }

    private static SurveyHistoryEntryResponse toEntry(LeadHistoryEntry entry) {
        return entry == null ? null : new SurveyHistoryEntryResponse(entry.type(), entry.actorUserId(),
                entry.actorName(), entry.fromStatus(), entry.toStatus(), null, null, null, null, null, null,
                entry.occurredAt(), entry.referenceId(), entry.referenceNo());
    }

    private static SurveyOverlapResponse toOverlap(OverlapView overlap) {
        return new SurveyOverlapResponse(overlap.appointmentId(), overlap.leadId(), overlap.requestCode(),
                overlap.startAt(), overlap.durationMinutes());
    }

    private static SurveyListItemResponse toItem(SurveyListItem item) {
        return new SurveyListItemResponse(item.id(), item.leadId(), item.requestCode(), item.ownerContactName(),
                item.wardName(), item.startAt(), item.durationMinutes(), item.surveyorUserId(), item.surveyorName(),
                new SurveyListItemResponse.Confirmation(item.confirmationState(), item.confirmationCause(),
                        item.everConfirmed()),
                item.status(), item.group(), item.kind(), item.cannotBePerformed());
    }

    private static SurveyBasisResponse toBasis(Basis basis) {
        return basis == null ? null : new SurveyBasisResponse(basis.confirmedOn(), basis.text());
    }

    private static SurveyLocationResponse toLocation(MeetingLocation location) {
        Coordinates pin = location.coordinates();
        return new SurveyLocationResponse(location.addressText(), location.provinceCode(), location.provinceName(),
                location.wardCode(), location.wardName(), pin == null ? null : pin.latitude(),
                pin == null ? null : pin.longitude());
    }

    private static SurveyLocationResponse toLocation(LeadLocationSnapshot location) {
        Coordinates pin = location.coordinates();
        return new SurveyLocationResponse(location.addressDetail(), location.provinceCode(), location.provinceName(),
                location.wardCode(), location.wardName(), pin == null ? null : pin.latitude(),
                pin == null ? null : pin.longitude());
    }
}
