package vn.sunvibe.property.core.application.usecase.intake;

import java.util.Collection;
import java.util.List;

/**
 * Use Case - the declared owner and the ownership rows valid today of a set of properties, as
 * {@link CompletedIntakeUseCase#latest} reads them for one result property (brokerage-agreement BR-4, E05-S1 S1-D4;
 * delivery plan PLAN5-D3). Nothing is written.
 * <p>
 * Viewer-less. Called only by an operation that has already checked that its caller may see what it asks about, or by an
 * anonymous read that shows none of these values, through an adapter in {@code vn.sunvibe.integration} that never
 * exposes it over HTTP (ADR-009; ADR-012 decision 2; ViewerlessReadArchTest). Read-only; no lock; no value of it reaches
 * a public answer except as ADR-012 decision 1 allows.
 */
public interface PropertyOwnershipUseCase {

    int MAX_PROPERTIES = 250;

    /**
     * Read 1b (delivery plan PLAN5-D3): the owner data of E05-S1's BR-4 check (S1-D4) for a set of properties, in property
     * id order, at a fixed number of statements. Null elements and repeats are dropped first; an unknown or malformed id is
     * left out; an input with no id left answers an empty list without a statement.
     *
     * @throws IllegalArgumentException for more than {@link #MAX_PROPERTIES} distinct ids, before any statement
     * @throws NullPointerException     for a null collection
     */
    List<PropertyOwnershipView> ownershipOf(Collection<String> propertyIds);
}
