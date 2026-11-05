package vn.sunvibe.property.core.application.exception;

/**
 * The stable refusals of the intake API (property-create intake spec 5.4, section 7, ADR-010): the name is the {@code code}
 * of the error body. Messages are fixed: they quote no value of a request or a row.
 */
public enum IntakeError {
    NOT_FOUND(404, "The resource was not found."),
    VALIDATION_FAILED(400, "Validation failed."),
    INTAKE_NOT_ASSIGNEE(403, "Only the lead's assignee may do this."),
    OWNER_CHECK_NOT_ALLOWED(403, "You may not act on this owner check."),
    CASE_NOT_ALLOWED(403, "You may not act on this case."),
    INTAKE_STATE(409, "The intake is not in a state for this."),
    INTAKE_GATE_FAILED(409, "The intake gate is closed."),
    CONCURRENT_MODIFICATION(409, "The intake changed. Reload and try again."),
    OWNER_PROOF_REQUIRED(409, "A checked owner confirmation is required."),
    CANONICAL_UNRECONCILED(409, "The canonical unit of this property cannot take parts yet."),
    INTAKE_CREATE_NOT_ALLOWED(409, "This intake may only link to its property."),
    INTAKE_DISPUTED(409, "The property is in dispute."),
    INTAKE_SOURCE_INVALID(409, "The source holds a value that cannot be used."),
    AVAILABILITY_NOT_CONFIRMED(409, "Availability must be confirmed with the owner today."),
    INTAKE_PREVIEW_STALE(409, "The preview is out of date. Reload it."),
    INTAKE_DUPLICATE_CHECK(409, "This intake needs a manager check."),
    INTAKE_ALREADY_COMPLETED(409, "The intake is already completed."),
    INTAKE_SELECTION_INVALID(400, "The selection does not fit the source."),
    LINK_MAPPING_INVALID(400, "The link is not valid."),
    CONFIRMATION_INVALID(400, "The confirmation is not valid."),
    ACCOUNT_LOCKED(423, "The account is locked for a while."),
    OWNER_REQUEST_LIMIT(429, "Too many requests for this intake today.");

    private final int status;
    private final String message;

    IntakeError(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }
}
