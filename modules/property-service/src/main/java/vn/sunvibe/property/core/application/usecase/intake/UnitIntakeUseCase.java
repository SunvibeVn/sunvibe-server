package vn.sunvibe.property.core.application.usecase.intake;

import java.util.Collection;
import java.util.List;

/**
 * Use Case - the intakes, readiness and availability of a set of units (brokerage-agreement US-4 AC-1, AC-2; BR-3, BR-12,
 * BR-18): for each unit, the latest COMPLETED intake of each lead that maps it, with the state, source revision and unit
 * reasons that {@link InventoryIntakeReadinessUseCase#readiness} computes for that lead. Nothing is written.
 * <p>
 * Viewer-less. Called only by an operation that has already checked that its caller may see what it asks about, or by an
 * anonymous read that shows none of these values, through an adapter in {@code vn.sunvibe.integration} that never
 * exposes it over HTTP (ADR-009; ADR-012 decision 2; ViewerlessReadArchTest). Read-only; no lock; no value of it reaches
 * a public answer except as ADR-012 decision 1 allows.
 */
public interface UnitIntakeUseCase {

    int MAX_UNITS = 250;

    /**
     * Read 1 (ADR-012 decision 2, row 1), in unit id order, at a fixed number of statements plus one call of each set read
     * of survey and auth per 250 leads or users. Null elements and repeats are dropped first; an unknown or malformed id is
     * left out; an input with no id left answers an empty list without a statement.
     *
     * @throws IllegalArgumentException for more than {@link #MAX_UNITS} distinct ids, before any statement
     * @throws IllegalStateException    when an intake on a property read names a lead that does not exist
     * @throws NullPointerException     for a null collection
     */
    List<UnitIntakeView> forUnits(Collection<String> unitIds);
}
