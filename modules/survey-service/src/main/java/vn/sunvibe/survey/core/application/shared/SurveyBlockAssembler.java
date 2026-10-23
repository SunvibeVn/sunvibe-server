package vn.sunvibe.survey.core.application.shared;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.query.AppointmentView;
import vn.sunvibe.survey.core.application.query.BookingBlock;
import vn.sunvibe.survey.core.application.query.BookingState;
import vn.sunvibe.survey.core.application.query.OverlapView;
import vn.sunvibe.survey.core.application.query.ResumptionView;
import vn.sunvibe.survey.core.application.query.SurveyBlock;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadCard;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.Holder;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.resumption.SurveyResumption;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyResumptionDomainRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the survey block of a lead (survey-schedule spec 7.2) for the reads and the command answers, and holds the
 * booking rules both share (spec 4.5, 4.6): a first survey is booked from ASSIGNED or CONTACTED, a re-survey from
 * CONTACTED or SURVEY_SCHEDULED, only for rental leads. One eligibility lookup and one name lookup per block.
 */
@Component
public class SurveyBlockAssembler {

    /** survey-schedule C-1, US-5 AC-3: only rental leads are surveyed. */
    public static final List<String> RENTAL_INTENTS = List.of("SHORT_TERM_RENT", "LONG_TERM_RENT");
    private static final List<String> FIRST_SURVEY_STATUSES = List.of("ASSIGNED", "CONTACTED");
    private static final List<String> RESURVEY_STATUSES = List.of("CONTACTED", "SURVEY_SCHEDULED");

    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyResumptionDomainRepository resumptions;
    private final SurveyorDirectoryPort directory;

    public SurveyBlockAssembler(SurveyAppointmentDomainRepository appointments,
                                SurveyResumptionDomainRepository resumptions, SurveyorDirectoryPort directory) {
        this.appointments = appointments;
        this.resumptions = resumptions;
        this.directory = directory;
    }

    /** The lead statuses a booking of this kind is made from (survey-schedule spec 4.5). */
    public static List<String> allowedStatusesFor(AppointmentKind kind) {
        return kind == AppointmentKind.RESURVEY ? RESURVEY_STATUSES : FIRST_SURVEY_STATUSES;
    }

    /** The block of the lead, read with its appointments and resumptions. */
    public SurveyBlock assemble(LeadFacts lead) {
        return assemble(lead, appointments.findByLeadId(lead.leadId()));
    }

    /** The block of the lead from its appointments (any order). */
    public SurveyBlock assemble(LeadFacts lead, List<SurveyAppointment> ofLead) {
        Set<String> toCheck = new LinkedHashSet<>();
        if (lead.assignedUserId() != null) {
            toCheck.add(lead.assignedUserId());
        }
        ofLead.stream().filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
                .forEach(appointment -> toCheck.add(appointment.getSurveyorUserId()));
        Set<String> eligible = toCheck.isEmpty() ? Set.of() : directory.eligibleAmong(toCheck);

        Optional<SurveyAppointment> currentVisit = CurrentVisitPolicy.currentVisit(ofLead, lead.intentCycle());
        BookingState booking = bookingState(lead, ofLead,
                lead.assignedUserId() != null && eligible.contains(lead.assignedUserId()));
        boolean resumeAllowed = "ON_HOLD".equals(lead.status()) && currentVisit.isPresent();

        List<AppointmentView> views = ofLead.stream()
                .sorted(Comparator.comparingInt(SurveyAppointment::getAppointmentNo).reversed())
                .map(appointment -> AppointmentView.of(appointment, lead.intentCycle(),
                        !eligible.contains(appointment.getSurveyorUserId())))
                .toList();
        Map<String, Integer> numbers = new HashMap<>();
        ofLead.forEach(appointment -> numbers.put(appointment.getId().getValue(), appointment.getAppointmentNo()));
        List<SurveyResumption> rows = new ArrayList<>(resumptions.findByLeadIdOrderByOccurredAt(lead.leadId()));
        Collections.reverse(rows);
        List<ResumptionView> resumptionViews = rows.stream()
                .map(row -> new ResumptionView(row.visitAppointmentId().getValue(),
                        numbers.getOrDefault(row.visitAppointmentId().getValue(), 0), row.reason(), row.basis(),
                        row.actorUserId(), row.occurredAt()))
                .toList();

        Set<String> staffIds = staffIds(views, resumptionViews);
        Map<String, String> names = staffIds.isEmpty() ? Map.of() : directory.displayNames(staffIds);
        return new SurveyBlock(lead.leadId(), lead.intentCycle(), booking,
                currentVisit.map(visit -> visit.getId().getValue()).orElse(null), resumeAllowed, views,
                resumptionViews, names);
    }

    /**
     * Whether a booking is possible now, in the order of the booking checks (survey-schedule spec 4.6, 7.2): a
     * scheduled appointment, a NEW lead (no assignee), the status for the booking kind, the intent, the assignee's
     * eligibility.
     */
    public static BookingState bookingState(LeadFacts lead, List<SurveyAppointment> ofLead,
                                            boolean assigneeEligible) {
        if (ofLead.stream().anyMatch(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)) {
            return blocked(BookingBlock.ALREADY_SCHEDULED);
        }
        if (lead.assignedUserId() == null || "NEW".equals(lead.status())) {
            return blocked(BookingBlock.NO_ASSIGNEE);
        }
        CurrentVisitPolicy.BookingKind kind = CurrentVisitPolicy.bookingKind(ofLead, lead.intentCycle());
        if (!allowedStatusesFor(kind.kind()).contains(lead.status())) {
            return blocked(BookingBlock.LEAD_STATUS);
        }
        if (!RENTAL_INTENTS.contains(lead.intent())) {
            return blocked(BookingBlock.INTENT);
        }
        if (!assigneeEligible) {
            return blocked(BookingBlock.SURVEYOR_INELIGIBLE);
        }
        return new BookingState(true, kind.kind(),
                kind.previousVisitId() == null ? null : kind.previousVisitId().getValue(), null);
    }

    /** The overlap views of these appointments; an appointment whose lead card is missing is left out. */
    public static List<OverlapView> overlapViews(List<SurveyAppointment> overlapping, Map<String, LeadCard> cards) {
        return overlapping.stream()
                .filter(appointment -> cards.containsKey(appointment.getLeadId()))
                .map(appointment -> new OverlapView(appointment.getId().getValue(), appointment.getLeadId(),
                        cards.get(appointment.getLeadId()).requestCode(), appointment.getDetails().startAt(),
                        appointment.getDetails().durationMinutes()))
                .toList();
    }

    private static BookingState blocked(BookingBlock block) {
        return new BookingState(false, null, null, block);
    }

    private static Set<String> staffIds(List<AppointmentView> views, List<ResumptionView> resumptions) {
        Set<String> ids = new LinkedHashSet<>();
        for (AppointmentView view : views) {
            ids.add(view.surveyorUserId());
            ids.add(view.createdByUserId());
            ids.add(view.confirmation().changedBy());
            view.holders().stream().map(Holder::userId).forEach(ids::add);
            if (view.visit() != null) {
                ids.add(view.visit().performedBy());
                ids.add(view.visit().recordedBy());
            }
            if (view.cancellation() != null) {
                ids.add(view.cancellation().cancelledBy());
            }
            view.revisions().stream().map(AppointmentRevision::actorUserId).forEach(ids::add);
            for (AppointmentEvent event : view.events()) {
                ids.add(event.actorUserId());
                ids.add(event.fromSurveyorUserId());
                ids.add(event.toSurveyorUserId());
            }
        }
        resumptions.forEach(resumption -> ids.add(resumption.actorUserId()));
        ids.remove(null);
        return ids;
    }
}
