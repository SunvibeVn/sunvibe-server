package vn.sunvibe.brokerage.core.application.shared.external.account;

/** What the check of an account's current password found (E05-S5 spec S5-D4). */
public record PasswordCheck(Outcome outcome) {

    /** The answer of a password check. */
    public enum Outcome {
        OK,
        WRONG,
        /** The account's re-authentication counter refuses for now, whatever the password. */
        LOCKED
    }
}
