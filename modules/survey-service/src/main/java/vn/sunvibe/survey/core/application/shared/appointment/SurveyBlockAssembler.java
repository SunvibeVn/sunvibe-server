package vn.sunvibe.survey.core.application.shared.appointment;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.query.appointment.AppointmentView;
import vn.sunvibe.survey.core.application.query.appointment.BookingBlock;
import vn.sunvibe.survey.core.application.query.appointment.BookingState;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSummaryView;
import vn.sunvibe.survey.core.application.query.appointment.OverlapView;
import vn.sunvibe.survey.core.application.query.appointment.ResumptionView;
import vn.sunvibe.survey.core.application.query.appointment.SurveyBlock;
import vn.sunvibe.survey.core.application.query.appointment.SurveyRecordView;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistReviewReader;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader.CycleRecord;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadCard;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.Holder;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.resumption.SurveyResumption;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStart;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistStartPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.RequalificationPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.ResumeTarget;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionStart;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionStartPolicy;
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
    private final ChecklistViewAssembler checklists;
    private final SurveyRecordReader records;
    private final ChecklistReviewReader reviews;

    public SurveyBlockAssembler(SurveyAppointmentDomainRepository appointments,
                                SurveyResumptionDomainRepository resumptions, SurveyorDirectoryPort directory,
                                ChecklistViewAssembler checklists, SurveyRecordReader records,
                                ChecklistReviewReader reviews) {
        this.appointments = appointments;
        this.resumptions = resumptions;
        this.directory = directory;
        this.checklists = checklists;
        this.records = records;
        this.reviews = reviews;
    }

    /** The lead statuses a booking of this kind is made from (survey-schedule spec 4.5). */
    public static List<String> allowedStatusesFor(AppointmentKind kind) {
        return kind == AppointmentKind.RESURVEY ? RESURVEY_STATUSES : FIRST_SURVEY_STATUSES;
    }

    /** The block of the lead as the viewer sees it, read with its appointments, resumptions and checklists. */
    public SurveyBlock assemble(LeadFacts lead, SurveyViewer viewer) {
        return assemble(lead, appointments.findByLeadId(lead.leadId()), viewer);
    }

    /**
     * The block of the lead from its appointments (any order), with the viewer's checklist start and each VISITED
     * appointment's checklists (survey-checklist spec 7.2).
     */
    public SurveyBlock assemble(LeadFacts lead, List<SurveyAppointment> ofLead, SurveyViewer viewer) {
        Set<String> toCheck = new LinkedHashSet<>();
        if (lead.assignedUserId() != null) {
            toCheck.add(lead.assignedUserId());
        }
        ofLead.stream().filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
                .forEach(appointment -> toCheck.add(appointment.getSurveyorUserId()));
        Set<String> eligible = toCheck.isEmpty() ? Set.of() : directory.eligibleAmong(toCheck);

        CycleRecord record = records.read(lead.leadId(), lead.intentCycle(), ofLead);
        Optional<SurveyAppointment> currentVisit = record.currentVisit();
        boolean assigneeEligible = lead.assignedUserId() != null && eligible.contains(lead.assignedUserId());
        BookingState booking = bookingState(lead, ofLead, assigneeEligible, record);
        Optional<ResumeTarget> resumeTarget = "ON_HOLD".equals(lead.status()) && currentVisit.isPresent()
                ? records.resumeTarget(record, lead.intentCycle()) : Optional.empty();

        Map<String, List<ChecklistSummaryView>> summaries = checklists.summaries(viewer, lead, ofLead,
                assigneeEligible);
        List<AppointmentView> views = ofLead.stream()
                .sorted(Comparator.comparingInt(SurveyAppointment::getAppointmentNo).reversed())
                .map(appointment -> AppointmentView.of(appointment, lead.intentCycle(),
                        !eligible.contains(appointment.getSurveyorUserId()),
                        summaries.getOrDefault(appointment.getId().getValue(), List.of())))
                .toList();
        boolean currentVisitHasRevision = currentVisit.isPresent() && !summaries.getOrDefault(
                currentVisit.get().getId().getValue(), List.of()).isEmpty();
        ChecklistStart checklistStart = ChecklistStartPolicy.of(currentVisit, currentVisitHasRevision, lead.status(),
                lead.assignedUserId(), assigneeEligible, viewer.userId());
        RevisionStart revisionStart = RevisionStartPolicy.of(currentVisit.isPresent(), record.latestOfCurrentVisit(),
                record.latestMark(), lead.status(), lead.assignedUserId(), assigneeEligible, viewer.userId());
        SurveyRecordView survey = new SurveyRecordView(record.hasPassed(), record.hasSubmitted(),
                record.passed().map(passed -> reviews.passed(passed, lead)).orElse(null),
                RequalificationPolicy.state(!viewer.seesOwnLeadsOnly(), record.passed(), lead.status()));
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
                currentVisit.map(visit -> visit.getId().getValue()).orElse(null), resumeTarget.isPresent(), views,
                resumptionViews, checklistStart, revisionStart, resumeTarget.orElse(null), survey, names);
    }

    /**
     * Whether a booking is possible now, in the order of the booking checks (survey-schedule spec 4.6, 7.2): a
     * scheduled appointment, a NEW lead (no assignee), the survey record (checklist submission spec 4.8), the status for
     * the booking kind, the intent, the assignee's eligibility.
     */
    public static BookingState bookingState(LeadFacts lead, List<SurveyAppointment> ofLead,
                                            boolean assigneeEligible, CycleRecord record) {
        if (ofLead.stream().anyMatch(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)) {
            return blocked(BookingBlock.ALREADY_SCHEDULED);
        }
        if (lead.assignedUserId() == null || "NEW".equals(lead.status())) {
            return blocked(BookingBlock.NO_ASSIGNEE);
        }
        if (record.latestSubmitted()) {
            return blocked(BookingBlock.CHECKLIST_SUBMITTED);
        }
        if (record.hasPassed()) {
            return blocked(BookingBlock.CHECKLIST_PASSED);
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
            for (ChecklistSummaryView summary : view.checklists()) {
                ids.add(summary.holderUserId());
                ids.add(summary.updatedByUserIdOrNull());
            }
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
