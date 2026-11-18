package vn.sunvibe.auth.core.application.usecase.role;

import java.util.Collection;
import java.util.Map;

/**
 * The owner standing of a set of accounts (auth-roles BR-8, property-create intake D5-10), each answered exactly as
 * {@link OwnerAccountUseCase#ownerStanding} answers it alone.
 * <p>
 * Viewer-less. Called only by an operation that has already checked that its caller may see what it asks about, or by an
 * anonymous read that shows none of these values, through an adapter in {@code vn.sunvibe.integration} that never
 * exposes it over HTTP (ADR-009; ADR-012 decision 2; ViewerlessReadArchTest). Read-only; no lock; no value of it reaches
 * a public answer except as ADR-012 decision 1 allows.
 */
public interface OwnerStandingUseCase {

    /**
     * Read 4 (ADR-012 decision 2, row 4). Any number of ids, read in chunks of 250 at a fixed number of statements each;
     * a repeated id counts once. Every id is answered: one with no account is not eligible with -1 sign-in losses.
     *
     * @throws NullPointerException for a null collection or a null id
     */
    Map<String, OwnerStanding> ownerStandings(Collection<String> userIds);
}
