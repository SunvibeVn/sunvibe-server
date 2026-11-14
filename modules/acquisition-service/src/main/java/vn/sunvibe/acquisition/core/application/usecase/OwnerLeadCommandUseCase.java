package vn.sunvibe.acquisition.core.application.usecase;

import vn.sunvibe.acquisition.core.application.command.AddLeadNoteCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadsCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadIntentCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadStatusCommand;
import vn.sunvibe.acquisition.core.application.query.AssignLeadsResult;
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

    /**
     * Give several leads to one staff member in one action (acquisition-owner-lead-bulk-assign spec 3.1). Checks of the
     * whole action, in this order: the note (400), then the assignee, once, also when every lead is already theirs
     * (422). Then the leads are locked in ascending id order (acquisition-owner-lead-bulk-assign D-2), and each gets
     * exactly one outcome, decided under its lock and checked in this order (acquisition-owner-lead-bulk-assign BR-7):
     * NOT_FOUND (unknown, not a lead id, or not visible to the viewer), CLOSED, ALREADY_ASSIGNED (nothing written),
     * STALE (another version than the one sent; nothing written), else ASSIGNED with the effects of {@link #assign}.
     * One lead's outcome never stops the others; an exception rolls the whole action back. Returns one outcome per lead,
     * in request order. Writes no access-log row.
     */
    AssignLeadsResult assignLeads(LeadViewer viewer, AssignLeadsCommand command);

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
