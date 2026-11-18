package vn.sunvibe.brokerage.core.application.shared.external.account;

import java.util.Collection;
import java.util.Map;

/**
 * The full names of staff accounts, owned by auth-service, for the actors of an agreement's history (E05-S7 spec S7-D7).
 * The adapter lives in the app module (ADR-009). Only the staff actors of rows the caller may read are asked for, never
 * the owner (brokerage-agreement BR-17).
 */
public interface StaffDirectoryPort {

    /**
     * The full name of each account with one of these ids, locked, deleted and role-less accounts included, so the
     * history keeps who acted; an unknown id is absent. An empty collection asks nothing.
     */
    Map<String, String> displayNames(Collection<String> userIds);
}
