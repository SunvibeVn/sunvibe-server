package vn.sunvibe.property.core.application.shared.external.user;

import java.util.Collection;
import java.util.Map;

/**
 * The owner standing of a set of accounts, owned by auth-service; the adapter lives in the app module (ADR-009). It joins
 * the caller's transaction.
 */
public interface OwnerStandingPort {

    /**
     * Every account answered as {@link UserDirectoryPort#ownerStanding} answers it alone (property-create intake D5-10), in
     * chunks of 250 at a fixed number of statements each; one with no account is not eligible with -1 sign-in losses.
     */
    Map<String, OwnerStandingFacts> ownerStandings(Collection<String> userIds);
}
