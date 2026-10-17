package vn.sunvibe.acquisition.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.command.AddLeadNoteCommand;
import vn.sunvibe.acquisition.core.application.command.AssignLeadCommand;
import vn.sunvibe.acquisition.core.application.command.ChangeLeadStatusCommand;
import vn.sunvibe.acquisition.core.presentation.dto.request.AddLeadNoteRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.AssignLeadRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.ChangeLeadStatusRequest;

/**
 * Converter - OwnerLeadRequestMapper
 * <p>
 * Staff lead request DTOs -> commands. The version was validated as present, so it is unboxed here.
 */
@Component
public class OwnerLeadRequestMapper {

    public AssignLeadCommand toCommand(String leadId, AssignLeadRequest request) {
        return new AssignLeadCommand(leadId, request.assigneeUserId(), request.expectedVersion(), request.note());
    }

    public ChangeLeadStatusCommand toCommand(String leadId, ChangeLeadStatusRequest request) {
        return new ChangeLeadStatusCommand(leadId, request.toStatus(), request.expectedVersion(), request.reason(),
                request.duplicateOfLeadId(), request.note(), request.nextActionAt());
    }

    public AddLeadNoteCommand toCommand(String leadId, AddLeadNoteRequest request) {
        return new AddLeadNoteCommand(leadId, request.note(), request.nextActionAt());
    }
}
