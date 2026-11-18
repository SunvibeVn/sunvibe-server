package vn.sunvibe.brokerage.core.application.shared.external.account;

/**
 * The owner's account notices, owned by auth-service; the adapter lives in the app module (ADR-009). A call joins the
 * caller's transaction: auth writes one durable notice row there, so a rolled-back command leaves none, and auth's job
 * delivers it later; the request never waits on mail. An account without a verified address gets no row and the caller
 * goes on (E05-S4 spec S4-D7).
 */
public interface OwnerNoticePort {

    /**
     * brokerage-agreement BR-17: a neutral notice that a brokerage agreement version waits for the account's answer; it
     * only invites a sign-in and names no term, unit, address, amount or agreement.
     */
    void agreementPending(String ownerUserId);

    /**
     * brokerage-agreement US-3 AC-5, BR-17 (E05-S6 spec S6-D13): a neutral notice that a brokerage agreement the account
     * confirmed was signed and activated; it only invites a sign-in and names no term, unit, address, amount or contract
     * number.
     */
    void agreementActivated(String ownerUserId);
}
