package vn.sunvibe.brokerage.core.application.shared.external.account;

/**
 * The owner's account, owned by auth-service; the adapter lives in the app module (ADR-009) and joins the caller's
 * transaction.
 */
public interface OwnerAccountPort {

    /** The account can sign in and holds no staff role (brokerage-agreement BR-4). */
    boolean eligibleOwner(String userId);
}
