package vn.sunvibe.brokerage.core.application.exception;

import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;

import java.util.List;

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
        AGREEMENT_EXISTS(409, "This unit already has an agreement in progress.", null);

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

    public Reason reason() {
        return reason;
    }

    public List<Entry> entries() {
        return entries;
    }
}
