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
        return editableContext(viewer, lead, checklist, ofLead);
    }

    /** Steps (4) read-only 409 and (5) ineligible 422 of a command (spec 5.5). */
    private CommandContext editableContext(SurveyViewer viewer, LeadFacts lead, SurveyChecklist checklist,
                                           List<SurveyAppointment> ofLead) {
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

    /**
     * A survey-media command on a checklist (survey-media plan PD-10, spec 5.4): {@link #loadForCommand}'s order, but
     * the checklist's first load is its share lock (after the lead's), and the refusal events are the throttled media
     * ones (spec A-19) of the given resource: (2) checklist or lead not visible 404, (3) not the assignee 403, (4)
     * read-only 409, (5) ineligible 422. A malformed or unknown checklist id is 404 without an event.
     */
    public CommandContext loadForMediaCommand(SurveyViewer viewer, String checklistId,
                                              SecurityResourceType resourceType, String resourceId,
                                              SecurityAction action) {
        LeadFacts lead = requireMediaAuthority(viewer, checklistId, resourceType, resourceId, action);
        SurveyChecklist checklist = checklists.findByIdForShare(SurveyChecklistId.of(checklistId))
                .orElseThrow(ChecklistNotFoundException::new);
        List<SurveyAppointment> ofLead = appointments.findByLeadId(lead.leadId());
        return editableContext(viewer, lead, checklist, ofLead);
    }

    /**
     * An ADMIN hold or release (survey-media spec A-23, 5.4): the lead and the checklist {@code FOR SHARE}; no
     * assignee, read-only, eligibility or takeover check (a hold works on every checklist state).
     */
    public LeadFacts lockForAdminMediaCommand(SurveyViewer viewer, String checklistId) {
        SurveyChecklistId id = checklistId(checklistId);
        String leadId = checklists.findLeadIdById(id).orElseThrow(ChecklistNotFoundException::new);
        LeadFacts lead = leads.findVisibleForShare(leadId, viewer).orElseThrow(ChecklistNotFoundException::new);
        checklists.findByIdForShare(id).orElseThrow(ChecklistNotFoundException::new);
        return lead;
    }

    /**
     * Step (2) of a survey-media read (spec 5.4 list order, 4.3): the checklist exists (an unknown or malformed id is
     * 404 without an event) and its lead is visible to the caller (404 with a throttled NOT_VISIBLE event of the
     * resource).
     */
    public LeadFacts requireMediaVisible(SurveyViewer viewer, String checklistId, SecurityResourceType resourceType,
                                         String resourceId, SecurityAction action) {
        SurveyChecklistId id = checklistId(checklistId);
        String leadId = checklists.findLeadIdById(id).orElseThrow(ChecklistNotFoundException::new);
        return leads.findVisible(leadId, viewer).orElseThrow(() -> {
            securityEvents.refusedThrottled(viewer.userId(), leadId, resourceType, resourceId, action,
                    SecurityOutcome.NOT_VISIBLE);
            return new ChecklistNotFoundException();
        });
    }

    /**
     * Steps (2) and (3) of a survey-media command (spec 5.4): the checklist's lead visible to the caller under its
     * share lock (404, throttled NOT_VISIBLE event) and the caller its assignee (403, throttled NOT_ASSIGNEE event).
     * The content upload answers these before it takes its body slots.
     */
    public LeadFacts requireMediaAuthority(SurveyViewer viewer, String checklistId, SecurityResourceType resourceType,
                                           String resourceId, SecurityAction action) {
        SurveyChecklistId id = checklistId(checklistId);
        String leadId = checklists.findLeadIdById(id).orElseThrow(ChecklistNotFoundException::new);
        LeadFacts lead = leads.findVisibleForShare(leadId, viewer).orElseThrow(() -> {
            securityEvents.refusedThrottled(viewer.userId(), leadId, resourceType, resourceId, action,
                    SecurityOutcome.NOT_VISIBLE);
            return new ChecklistNotFoundException();
        });
        if (!viewer.userId().equals(lead.assignedUserId())) {
            securityEvents.refusedThrottled(viewer.userId(), lead.leadId(), resourceType, resourceId, action,
                    SecurityOutcome.NOT_ASSIGNEE);
            throw new AccessDeniedException("Only the lead's assignee uploads or removes its photos.");
        }
        return lead;
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
