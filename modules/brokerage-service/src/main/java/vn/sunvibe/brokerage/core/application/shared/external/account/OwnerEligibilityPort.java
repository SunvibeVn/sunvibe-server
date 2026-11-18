package vn.sunvibe.brokerage.core.application.shared.external.account;

import java.util.Collection;
import java.util.Set;

/**
 * Which of a set of accounts are eligible owners (brokerage-agreement BR-4: the account may sign in and holds no staff
 * role), owned by auth-service; the adapter lives in the app module (ADR-009; E05-S6 spec S6-D17). One set read, joining
 * the caller's transaction; nothing is written.
 */
public interface OwnerEligibilityPort {

    /** The ids of those accounts that are eligible owners; an id with no account is not. */
    Set<String> eligibleOwners(Collection<String> userIds);
}
