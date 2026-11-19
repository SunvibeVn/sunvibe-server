package vn.sunvibe.listing.core.application.exception;

import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;

import java.util.List;

/**
 * A listing command refused by a rule, with its status, its stable code (the reason's name) and, for a refusal by E05's
 * answer, the reason groups in E05's order (listing-publish E06-S1 spec section 4, E06S1-P3). No message quotes a value
 * of the request or of a row, and nothing names a lead or a listing.
 */
public final class ListingRefusedException extends RuntimeException {

    /** Why the command was refused: the name is the error code. */
    public enum Reason {
        LISTING_NOT_ALLOWED(409, "A listing cannot be created for this unit now."),
        LISTING_EXISTS(409, "This agreement already has a listing.");

        private final int status;
        private final String message;

        Reason(int status, String message) {
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

    private final Reason reason;
    private final List<AgreementReasonGroup> groups;

    private ListingRefusedException(Reason reason, List<AgreementReasonGroup> groups) {
        super(reason.message());
        this.reason = reason;
        this.groups = List.copyOf(groups);
    }

    /** listing-publish BR-1: refused with E05's reason groups, in E05's order. */
    public static ListingRefusedException notAllowed(List<AgreementReasonGroup> groups) {
        return new ListingRefusedException(Reason.LISTING_NOT_ALLOWED, groups);
    }

    /** listing BR-1: the agreement already has a listing not archived; the answer carries no id. */
    public static ListingRefusedException exists() {
        return new ListingRefusedException(Reason.LISTING_EXISTS, List.of());
    }

    public Reason reason() {
        return reason;
    }

    public List<AgreementReasonGroup> groups() {
        return groups;
    }
}
