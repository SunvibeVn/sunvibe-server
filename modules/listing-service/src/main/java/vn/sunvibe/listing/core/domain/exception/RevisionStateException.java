package vn.sunvibe.listing.core.domain.exception;

/**
 * A revision command refused by the revision's status or by the listing's open revision (listing section 3.3;
 * listing-publish US-2 AC-5, AC-6, BR-6; E06-S2 spec E06S2-D7, E06S2-D10), or a put live of a revision that is not
 * Approved (listing-publish US-5 AC-1; E06-S6 spec E06S6-D5). Its message names no value.
 */
public class RevisionStateException extends RuntimeException {

    /** The refusal, answered as its name. */
    public enum Reason {
        /** The revision is not a Draft, so its content, terms and submission are locked. */
        REVISION_NOT_DRAFT,
        /** Only a Pending review revision is withdrawn. */
        REVISION_NOT_PENDING,
        /** listing-publish BR-6: a listing has at most one Draft or Pending review revision. */
        OPEN_REVISION_EXISTS,
        /**
         * listing-publish US-2 AC-6, BR-6 (E06-S4 spec E06S4-D14): only a Needs changes revision is copied into a new
         * Draft in this slice; a Rejected, withdrawn or other revision is not.
         */
        REVISION_NOT_COPYABLE,
        /** listing-publish US-5 AC-1 (E06-S6 spec E06S6-D5): only an Approved revision goes live. */
        REVISION_NOT_APPROVED
    }

    private final Reason reason;

    public RevisionStateException(Reason reason) {
        super("The revision's status refuses this command: " + reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
