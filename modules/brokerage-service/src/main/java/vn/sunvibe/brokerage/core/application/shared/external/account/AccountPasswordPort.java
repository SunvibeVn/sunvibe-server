package vn.sunvibe.brokerage.core.application.shared.external.account;

/**
 * Port - the account's current password, checked by auth-service (brokerage-agreement BR-10: the owner re-enters it to
 * confirm; E05-S5 spec S5-D4, ADR-009). A wrong password counts on the account's re-authentication counter, which every
 * re-authentication of the account shares.
 */
public interface AccountPasswordPort {

    /**
     * Runs auth's own short transaction: call it outside any transaction of the caller, so a refused command keeps the
     * count and no row stays locked while the hash is computed. Never throws for a wrong password.
     */
    PasswordCheck check(String userId, String password);
}
