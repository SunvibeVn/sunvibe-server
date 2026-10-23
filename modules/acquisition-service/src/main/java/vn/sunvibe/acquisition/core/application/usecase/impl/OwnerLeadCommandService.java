package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.command.AddLeadNoteCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadIntentCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadStatusCommand;
import vn.sunvibe.acquisition.core.application.exception.ConcurrentLeadModificationException;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.query.LeadActivityView;
import vn.sunvibe.acquisition.core.application.query.LeadNoteResult;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.shared.StaffNames;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyPort;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyStop;
import vn.sunvibe.acquisition.core.application.shared.external.survey.SurveyAppointmentRef;
import vn.sunvibe.acquisition.core.application.usecase.OwnerLeadCommandUseCase;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.ConfirmationBasis;
import vn.sunvibe.acquisition.core.domain.model.lead.IntentChange;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadChange;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntentChange;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadIntentChangeDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Use Case Implementation - OwnerLeadCommandService
 * <p>
 * Assigns leads, changes their status and records notes. Each command checks, in this order
 * (acquisition-owner-lead-queue spec 5.3): the request values (400), that the viewer can see the lead (404, the same
 * answer as an unknown lead), the version the caller saw (409), the lead's status (409), the assignee (422). The
 * lead and its history entry are saved in the same transaction, and every answer carries the version the saved lead
 * now has, and names the lead's assignee; a note answer also names the people of its history entry
 * (acquisition-owner-lead-queue-ui C-18, one lookup per answer). An optimistic locking failure on save is left to
 * the exception handler, except for a note, whose request has no version to name.
 * <p>
 * A reassignment, a hold or a close of a SURVEY_SCHEDULED lead and a requirement change also move or cancel the
 * lead's scheduled survey appointment through {@link LeadSurveyPort}, in the same transaction and after the lead row
 * was saved and flushed (survey-schedule spec 4.6, 5.5); their one History entry names that appointment.
 */
@Service
@Transactional
public class OwnerLeadCommandService implements OwnerLeadCommandUseCase {

    private final OwnerLeadDomainRepository leads;
    private final OwnerLeadActivityDomainRepository activities;
    private final DuplicateCandidateDomainRepository duplicateCandidates;
    private final LeadVisibility visibility;
    private final LeadAssigneeDirectoryPort assigneeDirectory;
    private final LeadSurveyPort surveys;
    private final OwnerLeadIntentChangeDomainRepository intentChanges;
    private final StaffNames staffNames;
    private final IdGenerator<Long> idGenerator;

    public OwnerLeadCommandService(OwnerLeadDomainRepository leads, OwnerLeadActivityDomainRepository activities,
                                   DuplicateCandidateDomainRepository duplicateCandidates,
                                   LeadVisibility visibility, LeadAssigneeDirectoryPort assigneeDirectory,
                                   LeadSurveyPort surveys, OwnerLeadIntentChangeDomainRepository intentChanges,
                                   @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this.leads = leads;
        this.activities = activities;
        this.duplicateCandidates = duplicateCandidates;
        this.visibility = visibility;
        this.assigneeDirectory = assigneeDirectory;
        this.surveys = surveys;
        this.intentChanges = intentChanges;
        this.staffNames = new StaffNames(assigneeDirectory);
        this.idGenerator = idGenerator;
    }

    @Override
    public OwnerLeadSummary assign(LeadViewer viewer, AssignLeadCommand command) {
        OwnerLead.checkAssignValues(command.assigneeUserId(), command.note());
        OwnerLead lead = visibility.requireVisible(viewer, command.leadId());
        requireVersion(lead, command.expectedVersion());
        if (!lead.getStatus().isOpen()) {
            throw new InvalidLeadTransitionException(lead.getStatus(), null, List.of());
        }
        // Giving the lead to the person who holds it changes nothing, so their eligibility is not asked again
        if (command.assigneeUserId().equals(lead.getAssignedUserId())) {
            return summary(viewer, lead, assigneeNames(lead));
        }
        if (!assigneeDirectory.isEligibleAssignee(command.assigneeUserId())) {
            throw new IneligibleAssigneeException("assigneeUserId");
        }
        LeadChange change = lead.assignTo(command.assigneeUserId(), command.note()).orElseThrow();
        Instant now = AppClock.currentUTC();
        leads.save(lead);
        // survey-schedule US-2 AC-3: the scheduled appointment follows the lead to its new assignee
        if (change.type() == OwnerLeadActivityType.REASSIGNED) {
            change = withReference(change, surveys.onReassigned(lead.getId().getValue(), command.assigneeUserId(),
                    viewer.userId(), now));
        }
        activities.save(activity(viewer, lead, change, now));
        return summary(viewer, lead, assigneeNames(lead));
    }

    @Override
    public OwnerLeadSummary changeStatus(LeadViewer viewer, ChangeLeadStatusCommand command) {
        String duplicateOfText = command.duplicateOfLeadId() == null || command.duplicateOfLeadId().isBlank()
                ? null : command.duplicateOfLeadId();
        OwnerLead.checkStatusChangeValues(command.toStatus(), command.reason(), duplicateOfText != null,
                command.note());
        OwnerLeadId duplicateOf = command.toStatus() == OwnerLeadStatus.DUPLICATE
                ? duplicateTarget(viewer, command.leadId(), duplicateOfText)
                : null;
        OwnerLead lead = visibility.requireVisible(viewer, command.leadId());
        requireVersion(lead, command.expectedVersion());
        OwnerLeadStatus from = lead.getStatus();
        LeadChange change = lead.changeStatus(command.toStatus(), command.reason(), duplicateOf, command.note(),
                command.nextActionAt());
        Instant now = AppClock.currentUTC();
        leads.save(lead);
        // survey-schedule BR-3: holding or closing a lead with survey work cancels its scheduled appointment, keeping
        // the hold note or the close reason on it
        if (from == OwnerLeadStatus.SURVEY_SCHEDULED) {
            boolean hold = command.toStatus() == OwnerLeadStatus.ON_HOLD;
            change = withReference(change, surveys.onHeldOrClosed(lead.getId().getValue(),
                    hold ? LeadSurveyStop.LEAD_ON_HOLD : LeadSurveyStop.LEAD_CLOSED, viewer.userId(),
                    hold ? change.note() : lead.getCloseReason(), now));
        }
        activities.save(activity(viewer, lead, change, now));
        return summary(viewer, lead, assigneeNames(lead));
    }

    /**
     * survey-schedule US-5, BR-10: record a new requirement agreed with the owner. Checks, in this order: the request
     * values (400), the lead is visible (404), the version (409), the lead's status (409), the values that need the
     * lead (400: same intent, basis before the lead's creation date). The lead row is saved first; then its scheduled
     * survey appointment, if any, is cancelled; then the requirement change row and the one History entry are written.
     */
    @Override
    public OwnerLeadSummary changeIntent(LeadViewer viewer, ChangeLeadIntentCommand command) {
        OwnerLead.checkIntentChangeValues(command.newIntent(), command.askingPrice(), command.reason(),
                command.basis());
        String reason = OwnerLead.requireReason(command.reason());
        ConfirmationBasis basis = command.basis().requireValid(null, OwnerLead.vietnamToday());
        OwnerLead lead = visibility.requireVisible(viewer, command.leadId());
        requireVersion(lead, command.expectedVersion());
        IntentChange change = lead.changeIntent(command.newIntent(), command.askingPrice(), basis);
        Instant now = AppClock.currentUTC();
        leads.save(lead);
        Optional<SurveyAppointmentRef> cancelled = surveys.onIntentChanged(lead.getId().getValue(),
                change.newIntentCycle(), viewer.userId(), now);
        intentChanges.save(OwnerLeadIntentChange.create(idGenerator.generateId(), lead.getId(), change, reason,
                basis, viewer.userId(), now));
        activities.save(activity(viewer, lead, withReference(change.change(), cancelled), now));
        return summary(viewer, lead, assigneeNames(lead));
    }

    @Override
    public LeadNoteResult addNote(LeadViewer viewer, AddLeadNoteCommand command) {
        OwnerLead.checkNoteValues(command.note());
        OwnerLead lead = visibility.requireVisible(viewer, command.leadId());
        LeadChange change = lead.addNote(command.note(), command.nextActionAt());
        // Only a next action time changes the lead row; a plain note leaves it, and its version, as it is
        if (command.nextActionAt() != null) {
            saveForNote(lead);
        }
        OwnerLeadActivity activity = activity(viewer, lead, change, AppClock.currentUTC());
        activities.save(activity);
        List<String> staffIds = new ArrayList<>(StaffNames.idsOf(activity));
        staffIds.add(lead.getAssignedUserId());
        Map<String, String> names = staffNames.lookup(staffIds);
        return new LeadNoteResult(LeadActivityView.of(activity, names), summary(viewer, lead, names));
    }

    /**
     * acquisition-owner-lead-queue C-17: a lead can only be closed as a duplicate of another lead the actor can see
     * and that is not a duplicate itself (no chains). Every failure has the same message as an unknown id, so the
     * answer never tells whether a hidden lead exists. Checked with the request values, before the lead itself.
     */
    private OwnerLeadId duplicateTarget(LeadViewer viewer, String leadId, String duplicateOfLeadId) {
        if (duplicateOfLeadId.equals(leadId)) {
            throw invalidDuplicateOf();
        }
        return visibility.findVisible(viewer, duplicateOfLeadId)
                .filter(target -> target.getStatus() != OwnerLeadStatus.DUPLICATE)
                .map(OwnerLead::getId)
                .orElseThrow(OwnerLeadCommandService::invalidDuplicateOf);
    }

    private static InvalidLeadCommandException invalidDuplicateOf() {
        return new InvalidLeadCommandException("duplicateOfLeadId", OwnerLead.INVALID_DUPLICATE_OF_MESSAGE);
    }

    /** acquisition-owner-lead-queue BR-9: a change made on an outdated copy of the lead is refused. */
    private static void requireVersion(OwnerLead lead, long expectedVersion) {
        if (lead.getVersion() == null || lead.getVersion() != expectedVersion) {
            throw new ConcurrentLeadModificationException();
        }
    }

    /**
     * acquisition-owner-lead-queue BR-9: the lead row changed by a note can still lose to a change saved at the same
     * time. The note request carries no expectedVersion, so the conflict is reported without naming a field.
     */
    private void saveForNote(OwnerLead lead) {
        try {
            leads.save(lead);
        } catch (OptimisticLockingFailureException e) {
            throw ConcurrentLeadModificationException.withoutField();
        }
    }

    private OwnerLeadActivity activity(LeadViewer viewer, OwnerLead lead, LeadChange change, Instant occurredAt) {
        return OwnerLeadActivity.from(idGenerator.generateId(), lead.getId(), viewer.userId(), change, occurredAt);
    }

    /** The change naming the survey appointment a hook changed, if any (survey-schedule spec 4.6, A-12). */
    private static LeadChange withReference(LeadChange change, Optional<SurveyAppointmentRef> appointment) {
        return appointment.map(ref -> change.withReference(ref.appointmentId(), ref.appointmentNo())).orElse(change);
    }

    /** One lookup for the name of the lead's assignee (none when nobody holds the lead). */
    private Map<String, String> assigneeNames(OwnerLead lead) {
        return staffNames.lookup(Collections.singletonList(lead.getAssignedUserId()));
    }

    /**
     * acquisition-owner-lead-queue-ui C-49: the latest activity time is read back from the store after the new
     * activity is saved (the query flushes it first), so the answer holds the stored value the next list call returns.
     */
    private OwnerLeadSummary summary(LeadViewer viewer, OwnerLead lead, Map<String, String> names) {
        String id = lead.getId().getValue();
        boolean possibleDuplicate = duplicateCandidates
                .leadIdsWithVisibleCandidate(List.of(id), LeadVisibility.candidateViewer(viewer))
                .contains(id);
        Instant lastActivityAt = activities.findLatestOccurredAt(List.of(id)).get(id);
        return OwnerLeadSummary.of(lead, possibleDuplicate, names, lastActivityAt);
    }
}
