package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.Cancellation;
import vn.sunvibe.survey.core.domain.model.appointment.Confirmation;
import vn.sunvibe.survey.core.domain.model.appointment.Holder;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;

import java.time.Instant;
import java.util.List;

/**
 * One appointment as staff who may see its lead read it (survey-schedule spec 7.2): every current value, the lead's
 * place, the surveyor with the "cannot be performed" flag, who held it when, the confirmation, the visit or the
 * cancellation, every revision and event. Staff names come from the block's name map. Private: never logged.
 */
public record AppointmentView(String id, String leadId, int appointmentNo, AppointmentKind kind,
                              String previousVisitId, String resurveyReason, SurveyIntent intent, int intentCycle,
                              boolean inCurrentCycle, AppointmentStatus status, AppointmentDetails details,
                              LeadLocationSnapshot leadLocation, String locationReason, String surveyorUserId,
                              boolean cannotBePerformed, List<Holder> holders, Confirmation confirmation,
                              VisitRecord visit, Cancellation cancellation, List<AppointmentRevision> revisions,
                              List<AppointmentEvent> events, String createdByUserId, Instant createdAt,
                              long version, List<ChecklistSummaryView> checklists) {

    public AppointmentView {
        holders = List.copyOf(holders);
        revisions = List.copyOf(revisions);
        events = List.copyOf(events);
        checklists = List.copyOf(checklists);
    }

    /**
     * The view of an appointment of a lead whose current intent cycle is given; {@code surveyorIneligible} is whether
     * its surveyor can no longer take leads (it only flags a scheduled appointment, survey-schedule BR-6);
     * {@code checklists} are those of a VISITED appointment, newest first (survey-checklist spec 7.2).
     */
    public static AppointmentView of(SurveyAppointment appointment, int currentIntentCycle,
                                     boolean surveyorIneligible, List<ChecklistSummaryView> checklists) {
        return new AppointmentView(appointment.getId().getValue(), appointment.getLeadId(),
                appointment.getAppointmentNo(), appointment.getKind(),
                appointment.getPreviousVisitId() == null ? null : appointment.getPreviousVisitId().getValue(),
                appointment.getResurveyReason(), appointment.getIntent(), appointment.getIntentCycle(),
                appointment.getIntentCycle() == currentIntentCycle, appointment.getStatus(), appointment.getDetails(),
                appointment.getLeadLocation(), appointment.getLocationReason(), appointment.getSurveyorUserId(),
                appointment.getStatus() == AppointmentStatus.SCHEDULED && surveyorIneligible,
                appointment.holders(), appointment.getConfirmation(), appointment.getVisit(),
                appointment.getCancellation(), appointment.getRevisions(), appointment.getEvents(),
                appointment.getCreatedBy(), appointment.getCreatedAt(),
                appointment.getVersion() == null ? 0L : appointment.getVersion(), checklists);
    }

    @Override
    public String toString() {
        return "AppointmentView[" + id + "]";
    }
}
