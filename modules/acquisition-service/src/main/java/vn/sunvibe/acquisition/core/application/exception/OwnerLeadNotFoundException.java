package vn.sunvibe.acquisition.core.application.exception;

import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.io.Serial;

/**
 * The lead does not exist, its id is not a lead id, or the staff member may not see it. A lead the staff member may
 * not see answers exactly like a missing one (acquisition-owner-lead-queue BR-2): all cases give the same exception
 * with the same message, so the answer never tells whether a hidden lead exists. The message names no id.
 */
public class OwnerLeadNotFoundException extends ResourceNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OwnerLeadNotFoundException() {
        super("Owner lead not found.");
    }
}
