package vn.sunvibe.survey.core.application.shared;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistEditability;
import vn.sunvibe.survey.core.domain.policy.checklist.Editability;
import vn.sunvibe.survey.core.domain.policy.checklist.ReadOnlyReason;
import vn.sunvibe.survey.core.domain.policy.checklist.VisitFacts;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The checks every checklist command and read starts with, in the order of survey-checklist spec 5.5: the lead id by a
 * scalar query (an unknown or malformed id is not found, with no event), the lead visible (else not found, with a
 * NOT_VISIBLE event), then for a command the lead share lock, the caller the assignee (else 403 with a NOT_ASSIGNEE
 * event), appointments and checklist read fresh, the read-only states and the assignee's eligibility. Rules that need
 * stored data never answer before the 404.
 */
@Component
public class ChecklistContextLoader {

    private static final Set<ReadOnlyReason> READ_ONLY = EnumSet.of(ReadOnlyReason.INTENT_CHANGED,
            ReadOnlyReason.NEWER_VISIT, ReadOnlyReason.LEAD_STATUS);

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final SecurityEventRecorder securityEvents;

    public ChecklistContextLoader(SurveyChecklistDomainRepository checklists,
                                  SurveyAppointmentDomainRepository appointments, SurveyLeadPort leads,
                                  SurveyorDirectoryPort directory, SecurityEventRecorder securityEvents) {
        this.checklists = checklists;
        this.appointments = appointments;
        this.leads = leads;
        this.directory = directory;
        this.securityEvents = securityEvents;
    }

    /** What a start needs after its first checks: the lead under the share lock, the appointment and the lead's. */
    public record StartContext(LeadFacts lead, SurveyAppointment appointment, List<SurveyAppointment> ofLead) {
    }

    /** What a save or takeover needs after steps 2..5 (spec 5.5). */
    public record CommandContext(LeadFacts lead, SurveyChecklist checklist, List<SurveyAppointment> ofLead,
                                 SurveyAppointment visit, VisitFacts visitFacts) {
    }

    /** What a read needs: the visible lead, the checklist and the lead's appointments. */
    public record ReadContext(LeadFacts lead, SurveyChecklist checklist, List<SurveyAppointment> ofLead) {
    }

    /** Start: appointment exists and its lead is visible (404, event), caller is the assignee (403, event). */
    public StartContext loadForStart(SurveyViewer viewer, String appointmentId) {
        if (!SurveyAppointmentId.isValid(appointmentId)) {
            throw new SurveyLeadNotFoundException();
        }
        SurveyAppointmentId id = SurveyAppointmentId.of(appointmentId);
        String leadId = appointments.findLeadIdById(id).orElseThrow(SurveyLeadNotFoundException::new);
        LeadFacts lead = visibleForShare(viewer, leadId, SecurityResourceType.APPOINTMENT, appointmentId,
                SecurityAction.START, SurveyLeadNotFoundException::new);
        requireAssignee(viewer, lead, SecurityResourceType.APPOINTMENT, appointmentId, SecurityAction.START);
        List<SurveyAppointment> ofLead = appointments.findByLeadId(leadId);
        SurveyAppointment appointment = ofLead.stream().filter(candidate -> candidate.getId().equals(id)).findFirst()
                .orElseThrow(SurveyLeadNotFoundException::new);
        return new StartContext(lead, appointment, ofLead);
    }

    /**
     * Save and takeover steps 2..5: checklist exists and its lead is visible (404, event), caller is the assignee
     * (403, event), the draft is not read-only (409), the assignee is eligible (422).
     */
    public CommandContext loadForCommand(SurveyViewer viewer, String checklistId, SecurityAction action) {
        SurveyChecklistId id = checklistId(checklistId);
        String leadId = checklists.findLeadIdById(id).orElseThrow(ChecklistNotFoundException::new);
        LeadFacts lead = visibleForShare(viewer, leadId, SecurityResourceType.CHECKLIST, checklistId, action,
                ChecklistNotFoundException::new);
        requireAssignee(viewer, lead, SecurityResourceType.CHECKLIST, checklistId, action);
        List<SurveyAppointment> ofLead = appointments.findByLeadId(leadId);
        SurveyChecklist checklist = checklists.findById(id).orElseThrow(ChecklistNotFoundException::new);
        Optional<String> currentVisit = CurrentVisitPolicy.currentVisit(ofLead, lead.intentCycle())
                .map(visit -> visit.getId().getValue());
        Editability editability = ChecklistEditability.of(checklist.snapshot(), lead.status(), lead.intentCycle(),
                lead.assignedUserId(), true, currentVisit, viewer.userId());
        if (READ_ONLY.contains(editability.reason())) {
            throw new ChecklistReadOnlyException();
        }
        if (!directory.isEligible(lead.assignedUserId())) {
            throw new SurveyorIneligibleException(null);
        }
        SurveyAppointment visit = visitOf(checklist, ofLead);
        return new CommandContext(lead, checklist, ofLead, visit, visitFacts(visit));
    }

    /** A read: checklist exists (no event when not) and its lead is visible (404 with an event of the resource). */
    public ReadContext loadForRead(SurveyViewer viewer, String checklistId, SecurityResourceType resourceType,
                                   String resourceId) {
        SurveyChecklistId id = checklistId(checklistId);
        String leadId = checklists.findLeadIdById(id).orElseThrow(ChecklistNotFoundException::new);
        LeadFacts lead = leads.findVisible(leadId, viewer).orElseThrow(() -> {
            securityEvents.refused(viewer.userId(), leadId, resourceType, resourceId, SecurityAction.READ,
                    SecurityOutcome.NOT_VISIBLE);
            return new ChecklistNotFoundException();
        });
        SurveyChecklist checklist = checklists.findById(id).orElseThrow(ChecklistNotFoundException::new);
        return new ReadContext(lead, checklist, appointments.findByLeadId(leadId));
    }

    /** The visit a checklist belongs to. */
    public static SurveyAppointment visitOf(SurveyChecklist checklist, List<SurveyAppointment> ofLead) {
        return ofLead.stream().filter(appointment -> appointment.getId().equals(checklist.getVisitAppointmentId()))
                .findFirst().orElseThrow(() -> new IllegalStateException("A checklist names a visit of its lead."));
    }

    /** spec 4.5: the performer, the Vietnam date of the arrival and the appointment's number. */
    public static VisitFacts visitFacts(SurveyAppointment visit) {
        return new VisitFacts(visit.getVisit().performedBy(), SurveyTimeRules.vietnamDate(visit.getVisit()
                .arrivedAt()), visit.getAppointmentNo());
    }

    private static SurveyChecklistId checklistId(String checklistId) {
        if (!SurveyChecklistId.isValid(checklistId)) {
            throw new ChecklistNotFoundException();
        }
        return SurveyChecklistId.of(checklistId);
    }

    private LeadFacts visibleForShare(SurveyViewer viewer, String leadId, SecurityResourceType resourceType,
                                      String resourceId, SecurityAction action,
                                      Supplier<RuntimeException> notFound) {
        return leads.findVisibleForShare(leadId, viewer).orElseThrow(() -> {
            securityEvents.refused(viewer.userId(), leadId, resourceType, resourceId, action,
                    SecurityOutcome.NOT_VISIBLE);
            return notFound.get();
        });
    }

    private void requireAssignee(SurveyViewer viewer, LeadFacts lead, SecurityResourceType resourceType,
                                 String resourceId, SecurityAction action) {
        if (!viewer.userId().equals(lead.assignedUserId())) {
            securityEvents.refused(viewer.userId(), lead.leadId(), resourceType, resourceId, action,
                    SecurityOutcome.NOT_ASSIGNEE);
            throw new AccessDeniedException("Only the lead's assignee edits the checklist.");
        }
    }
}
