package vn.sunvibe.brokerage.core.application.exception;

import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An agreement command refused by a rule, with its status, its stable code (the reason's name) and its field entries.
 * No message quotes a value of the request or of a row; an entry names a field or a reason group only
 * (brokerage-agreement BR-17).
 */
public final class AgreementRefusedException extends RuntimeException {

    /** Why the command was refused: the name is the error code. */
    public enum Reason {
        VALIDATION_FAILED(400, "Validation failed.", "This value is not accepted."),
        AGREEMENT_NOT_ASSIGNEE(403, "Only the lead's assigned Sale may draft an agreement.", null),
        AGREEMENT_NOT_ELIGIBLE(409, "This unit cannot get an agreement now.", "This group stops the agreement."),
        AGREEMENT_EXISTS(409, "This unit already has an agreement in progress.", null),
        AGREEMENT_NOT_DRAFT(409, "This agreement has no draft to edit.", null),
        CONCURRENT_MODIFICATION(409, "Something changed at the same time. Try again.", "This value is out of date."),
        AGREEMENT_VERSION_INCOMPLETE(409, "This version cannot be sent yet: a part is missing.",
                "This part is missing."),
        AGREEMENT_SENDING_NOT_AVAILABLE(409, "Sending to owners is not open on this server.", null),
        AGREEMENT_NOT_AWAITING_OWNER(409, "This version is not waiting for your answer.",
                "This version cannot be answered now."),
        AGREEMENT_STATEMENT_OUTDATED(409, "The confirmation text has changed. Read it again.", null),
        ACCOUNT_LOCKED(423, "Too many wrong passwords. Try again later.", null),
        AGREEMENT_NEW_DRAFT_NOT_ALLOWED(409, "A new draft can be opened only when no version is pending.", null),
        AGREEMENT_ACTIVATOR_NOT_INDEPENDENT(403, "Only a manager who did not draft this version and is not the lead's"
                + " assigned Sale may decide its activation.", null),
        AGREEMENT_NOT_AWAITING_ACTIVATION(409, "This version is not waiting for activation.", null),
        AGREEMENT_VERSION_PERIOD_ENDED(409, "This version's period has ended.", null);

        private final int status;
        private final String message;
        private final String entryMessageOrNull;

        Reason(int status, String message, String entryMessageOrNull) {
            this.status = status;
            this.message = message;
            this.entryMessageOrNull = entryMessageOrNull;
        }

        public int status() {
            return status;
        }

        public String message() {
            return message;
        }

        public String entryMessageOrNull() {
            return entryMessageOrNull;
        }
    }

    /** One entry of the error body: a field or a reason group, with the values it accepts when there is a list. */
    public record Entry(String field, List<String> allowedValuesOrNull) {

        public Entry {
            allowedValuesOrNull = allowedValuesOrNull == null ? null : List.copyOf(allowedValuesOrNull);
        }
    }

    private final Reason reason;
    private final List<Entry> entries;

    private AgreementRefusedException(Reason reason, List<Entry> entries) {
        super(reason.message());
        this.reason = reason;
        this.entries = List.copyOf(entries);
    }

    /** A request field that holds a value the command does not accept, with the accepted values. */
    public static AgreementRefusedException invalid(String field, List<String> allowedValues) {
        return new AgreementRefusedException(Reason.VALIDATION_FAILED, List.of(new Entry(field, allowedValues)));
    }

    /** brokerage-agreement BR-16: only the lead's current assignee holding SALE or SALE_LEADER drafts. */
    public static AgreementRefusedException notAssignee() {
        return new AgreementRefusedException(Reason.AGREEMENT_NOT_ASSIGNEE, List.of());
    }

    /** brokerage-agreement US-1 AC-2: the groups that stop the unit, one entry each, in the given order. */
    public static AgreementRefusedException notEligible(List<ReasonGroup> groups) {
        return new AgreementRefusedException(Reason.AGREEMENT_NOT_ELIGIBLE,
                groups.stream().map(group -> new Entry("reasonGroups." + group.name(), null)).toList());
    }

    /** brokerage-agreement BR-1: the unit already has an unfinished agreement of the type; it names none. */
    public static AgreementRefusedException exists() {
        return new AgreementRefusedException(Reason.AGREEMENT_EXISTS, List.of());
    }

    /**
     * brokerage-agreement BR-5: only the pending version of a drafting agreement, while it is a draft, changes (E05-S3
     * spec S3-D2).
     */
    public static AgreementRefusedException notDraft() {
        return new AgreementRefusedException(Reason.AGREEMENT_NOT_DRAFT, List.of());
    }

    /**
     * brokerage-agreement BR-5: a change checks the data version the caller read; this one is out of date (E05-S3 spec
     * S3-D4).
     */
    public static AgreementRefusedException stale() {
        return new AgreementRefusedException(Reason.CONCURRENT_MODIFICATION,
                List.of(new Entry("expectedDataVersion", null)));
    }

    /**
     * brokerage-agreement BR-8 and E-13: no part of a version sent to the owner is unknown; one entry per missing part,
     * in the given order (E05-S4 spec S4-D5, S4-D11).
     */
    public static AgreementRefusedException incomplete(List<String> parts) {
        return new AgreementRefusedException(Reason.AGREEMENT_VERSION_INCOMPLETE,
                parts.stream().map(part -> new Entry(part, null)).toList());
    }

    /**
     * brokerage-agreement BR-19: sending to owners is not open on this server until the owner closes SV-180 for it
     * (E05-S4 spec S4-D8, S4-D11); no entry.
     */
    public static AgreementRefusedException sendingNotAvailable() {
        return new AgreementRefusedException(Reason.AGREEMENT_SENDING_NOT_AVAILABLE, List.of());
    }

    /**
     * brokerage-agreement BR-5, BR-10 (E05-S5 spec S5-D6, S5-D7): the version is no longer the one waiting for the owner,
     * or its 7 days have passed; one {@code versions[i]} entry per failing item of a batch, none for a single version.
     */
    public static AgreementRefusedException notAwaitingOwner(List<Integer> itemIndexesOrEmpty) {
        return new AgreementRefusedException(Reason.AGREEMENT_NOT_AWAITING_OWNER, itemIndexesOrEmpty.stream().sorted()
                .map(index -> new Entry("versions[" + index + "]", null)).toList());
    }

    /** brokerage-agreement BR-10 (E05-S5 spec S5-D3): the statement the owner read is not the current one; no entry. */
    public static AgreementRefusedException statementOutdated() {
        return new AgreementRefusedException(Reason.AGREEMENT_STATEMENT_OUTDATED, List.of());
    }

    /** E05-S5 spec S5-D4: the account's re-authentication counter refuses for now; no entry. */
    public static AgreementRefusedException accountLocked() {
        return new AgreementRefusedException(Reason.ACCOUNT_LOCKED, List.of());
    }

    /**
     * brokerage-agreement BR-5 (E05-S5 spec S5-D10): a new draft opens only on a drafting agreement with no pending
     * version; no entry.
     */
    public static AgreementRefusedException newDraftNotAllowed() {
        return new AgreementRefusedException(Reason.AGREEMENT_NEW_DRAFT_NOT_ALLOWED, List.of());
    }

    /**
     * brokerage-agreement BR-11, BR-16 (E05-S6 spec S6-D2): the caller drafted the version (an author of versions 1 to
     * n) or is the basis lead's current assignee; no entry.
     */
    public static AgreementRefusedException notIndependent() {
        return new AgreementRefusedException(Reason.AGREEMENT_ACTIVATOR_NOT_INDEPENDENT, List.of());
    }

    /**
     * brokerage-agreement BR-5, BR-11 (E05-S6 spec S6-D1 step 6): the version is not the confirmed one awaiting
     * activation, or its 14 days have passed; no entry.
     */
    public static AgreementRefusedException notAwaitingActivation() {
        return new AgreementRefusedException(Reason.AGREEMENT_NOT_AWAITING_ACTIVATION, List.of());
    }

    /** brokerage-agreement US-3 AC-4 (E05-S6 spec S6-D1 step 9): the end of the version's end date has passed. */
    public static AgreementRefusedException periodEnded() {
        return new AgreementRefusedException(Reason.AGREEMENT_VERSION_PERIOD_ENDED, List.of());
    }

    /**
     * brokerage-agreement US-2 AC-5 (E05-S5 spec S5-D6 step 6): the groups that stop each item of a batch, entries
     * {@code versions[i].reasonGroups.<GROUP>}, items ascending, each item's groups in the given order.
     */
    public static AgreementRefusedException notEligibleItems(Map<Integer, List<ReasonGroup>> groupsByItemIndex) {
        return new AgreementRefusedException(Reason.AGREEMENT_NOT_ELIGIBLE, new TreeMap<>(groupsByItemIndex).entrySet()
                .stream()
                .flatMap(item -> item.getValue().stream()
                        .map(group -> new Entry("versions[" + item.getKey() + "].reasonGroups." + group.name(), null)))
                .toList());
    }

    public Reason reason() {
        return reason;
    }

    public List<Entry> entries() {
        return entries;
    }
}
