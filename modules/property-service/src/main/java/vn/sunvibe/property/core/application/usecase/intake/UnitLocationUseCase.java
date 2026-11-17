package vn.sunvibe.property.core.application.usecase.intake;

import java.util.Collection;
import java.util.List;

/**
 * Use Case - the real location of units (brokerage-agreement E05-S5 spec S5-D2: US-2 AC-1 shows the owner "mã căn và vị
 * trí thực"). Published for brokerage-service's owner read (ADR-009), read-only, in the caller's transaction.
 * <p>
 * It has no viewer of its own: its one allowed caller is brokerage's owner read, after it checked that the caller is the
 * agreement's owner, and its adapter never exposes it over HTTP. Only an adapter in vn.sunvibe.integration calls it
 * (ViewerlessReadArchTest).
 */
public interface UnitLocationUseCase {

    /** The most unit ids one call takes. */
    int MAX_UNITS = 250;

    /**
     * The location of each known unit, in two statements whatever the size of the set; an unknown id is left out, an
     * empty set reads nothing.
     *
     * @throws IllegalArgumentException more than {@link #MAX_UNITS} ids, before any statement
     */
    List<UnitLocationView> locate(Collection<String> unitIds);
}
