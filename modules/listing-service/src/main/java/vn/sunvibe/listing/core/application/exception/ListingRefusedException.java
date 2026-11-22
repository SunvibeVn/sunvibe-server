package vn.sunvibe.listing.core.application.exception;

import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;

import java.util.List;

/**
 * A listing command refused by a rule, with its status, its stable code (the reason's name) and, for a refusal by E05's
 * answer, the reason groups in E05's order (listing-publish E06-S1 spec section 4, E06S1-P3); a put live refused by the
 * display conditions also carries the display reasons in their fixed order, E05's groups riding with
 * {@code ADVERTISING_RIGHT} (E06-S6 spec E06S6-D19). No message quotes a value of the request or of a row, and nothing
 * names a lead or a listing.
 */
public final class ListingRefusedException extends RuntimeException {

    /** Why the command was refused: the name is the error code. */
    public enum Reason {
        LISTING_NOT_ALLOWED(409, "A listing cannot be created for this unit now."),
        LISTING_EXISTS(409, "This agreement already has a listing."),
        /** listing-publish US-9 AC-3: an archived listing takes no command (E06-S2 spec E06S2-D3). */
        LISTING_ARCHIVED(409, "The listing is archived."),
        /** listing-publish BR-4: E05 has no such version to copy now (E06-S2 spec E06S2-D6). */
        TERMS_SOURCE_ABSENT(409, "These terms are not available."),
        /**
         * listing-publish BR-4, US-4 AC-5, E-5 (E06-S4 spec E06S4-D7): an approval of a revision whose terms equal neither
         * the agreement's version in effect nor its version waiting to start.
         */
        REVISION_TERMS_MISMATCH(409, "The revision's terms equal no version of the agreement."),
        /**
         * listing BR-4, listing-publish BR-11 (E06-S6 spec E06S6-D5, D19): a display condition does not hold, so the
         * Approved revision does not go live and nothing is written.
         */
        PUT_LIVE_REFUSED(409, "The revision cannot go live now.");

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
    private final List<DisplayReason> displayReasons;

    private ListingRefusedException(Reason reason, List<AgreementReasonGroup> groups,
                                    List<DisplayReason> displayReasons) {
        super(reason.message());
        this.reason = reason;
        this.groups = List.copyOf(groups);
        this.displayReasons = List.copyOf(displayReasons);
    }

    /** listing-publish BR-1: refused with E05's reason groups, in E05's order. */
    public static ListingRefusedException notAllowed(List<AgreementReasonGroup> groups) {
        return new ListingRefusedException(Reason.LISTING_NOT_ALLOWED, groups, List.of());
    }

    /** listing BR-1: the agreement already has a listing not archived; the answer carries no id. */
    public static ListingRefusedException exists() {
        return new ListingRefusedException(Reason.LISTING_EXISTS, List.of(), List.of());
    }

    /** listing-publish US-9 AC-3: the listing is archived, stored or because its agreement ended. */
    public static ListingRefusedException archived() {
        return new ListingRefusedException(Reason.LISTING_ARCHIVED, List.of(), List.of());
    }

    /** listing-publish BR-4: E05 answers no version of the asked source, or its agreement ended. */
    public static ListingRefusedException termsSourceAbsent() {
        return new ListingRefusedException(Reason.TERMS_SOURCE_ABSENT, List.of(), List.of());
    }

    /** listing-publish BR-4, US-4 AC-5: the revision's terms equal no version of the agreement, so it is not approved. */
    public static ListingRefusedException termsMismatch() {
        return new ListingRefusedException(Reason.REVISION_TERMS_MISMATCH, List.of(), List.of());
    }

    /**
     * listing-publish US-5 AC-3, BR-11 (E06-S6 spec E06S6-D19): the display reasons that stop the put live, in their
     * fixed order, and E05's groups that ride with {@code ADVERTISING_RIGHT}, in E05's order.
     *
     * @throws IllegalArgumentException for no reason: a refusal names at least one
     */
    public static ListingRefusedException putLiveRefused(List<DisplayReason> reasons,
                                                         List<AgreementReasonGroup> groups) {
        if (reasons == null || reasons.isEmpty()) {
            throw new IllegalArgumentException("a put live is refused for at least one reason");
        }
        return new ListingRefusedException(Reason.PUT_LIVE_REFUSED, groups, reasons);
    }

    public Reason reason() {
        return reason;
    }

    public List<AgreementReasonGroup> groups() {
        return groups;
    }

    /** The display reasons of a refused put live; empty for every other refusal. */
    public List<DisplayReason> displayReasons() {
        return displayReasons;
    }
}
