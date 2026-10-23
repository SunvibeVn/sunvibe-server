package vn.sunvibe.acquisition.core.application.usecase;

import vn.sunvibe.acquisition.core.application.command.AddLeadNoteCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadIntentCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadStatusCommand;
import vn.sunvibe.acquisition.core.application.query.LeadNoteResult;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;

/**
 * Use Case - OwnerLeadCommandUseCase
 * <p>
 * Staff work a lead: assign it, move its status, add a note. Every change writes one history entry in the same
 * transaction (acquisition module brief BR-4). Checks run in this order (acquisition-owner-lead-queue spec 5.3):
 * request values, the lead is visible to the viewer, the version the caller saw, the lead's status, the assignee.
 */
public interface OwnerLeadCommandUseCase {

    /** Give the lead to a staff member; the current assignee again changes nothing. Returns the lead after it. */
    OwnerLeadSummary assign(LeadViewer viewer, AssignLeadCommand command);

    /** Move the lead to another status. Returns the lead after it. */
    OwnerLeadSummary changeStatus(LeadViewer viewer, ChangeLeadStatusCommand command);

    /** Record a note, optionally with the next action time. Returns the history entry and the lead after it. */
    LeadNoteResult addNote(LeadViewer viewer, AddLeadNoteCommand command);

    /**
     * Record a new requirement (rental intent and price) agreed with the owner (survey-schedule US-5); cancels the
     * scheduled survey appointment, if any. Returns the lead after it.
     */
    OwnerLeadSummary changeIntent(LeadViewer viewer, ChangeLeadIntentCommand command);
}
