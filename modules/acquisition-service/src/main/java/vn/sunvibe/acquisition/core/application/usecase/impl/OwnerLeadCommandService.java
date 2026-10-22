package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.command.AddLeadNoteCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadStatusCommand;
import vn.sunvibe.acquisition.core.application.exception.ConcurrentLeadModificationException;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.query.LeadActivityView;
import vn.sunvibe.acquisition.core.application.query.LeadNoteResult;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.shared.StaffNames;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.application.usecase.OwnerLeadCommandUseCase;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadChange;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
 */
@Service
@Transactional
public class OwnerLeadCommandService implements OwnerLeadCommandUseCase {

    private final OwnerLeadDomainRepository leads;
    private final OwnerLeadActivityDomainRepository activities;
    private final DuplicateCandidateDomainRepository duplicateCandidates;
    private final LeadVisibility visibility;
    private final LeadAssigneeDirectoryPort assigneeDirectory;
    private final StaffNames staffNames;
    private final IdGenerator<Long> idGenerator;

    public OwnerLeadCommandService(OwnerLeadDomainRepository leads, OwnerLeadActivityDomainRepository activities,
                                   DuplicateCandidateDomainRepository duplicateCandidates,
                                   LeadVisibility visibility, LeadAssigneeDirectoryPort assigneeDirectory,
                                   @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this.leads = leads;
        this.activities = activities;
        this.duplicateCandidates = duplicateCandidates;
        this.visibility = visibility;
        this.assigneeDirectory = assigneeDirectory;
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
        save(viewer, lead, change);
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
        LeadChange change = lead.changeStatus(command.toStatus(), command.reason(), duplicateOf, command.note(),
                command.nextActionAt());
        save(viewer, lead, change);
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
        OwnerLeadActivity activity = activity(viewer, lead, change);
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

    /** Save the changed lead (it then carries its new version) and its history entry. */
    private void save(LeadViewer viewer, OwnerLead lead, LeadChange change) {
        leads.save(lead);
        activities.save(activity(viewer, lead, change));
    }

    private OwnerLeadActivity activity(LeadViewer viewer, OwnerLead lead, LeadChange change) {
        return OwnerLeadActivity.from(idGenerator.generateId(), lead.getId(), viewer.userId(), change,
                AppClock.currentUTC());
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
