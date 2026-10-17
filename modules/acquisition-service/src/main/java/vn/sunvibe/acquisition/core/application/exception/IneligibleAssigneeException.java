package vn.sunvibe.acquisition.core.application.exception;

import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/**
 * The person cannot be given owner leads or a ward: the account is unknown, cannot sign in or holds neither SALE nor
 * SALE_LEADER (acquisition-owner-lead-queue E-4). Carries the request field that named the person
 * ({@code assigneeUserId} or {@code userId}); the message never contains the id.
 */
public class IneligibleAssigneeException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public IneligibleAssigneeException(String field) {
        super("The user cannot be given owner leads.");
        this.field = field;
    }

    /** Name of the request field that named the person, as the client sent it. */
    public String getField() {
        return field;
    }
}
