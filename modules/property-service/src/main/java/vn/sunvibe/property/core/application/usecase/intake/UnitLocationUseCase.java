package vn.sunvibe.property.core.application.usecase.intake;

import java.util.Collection;
import java.util.List;

/**
 * Use Case - the real location of units (brokerage-agreement E05-S5 spec S5-D2: US-2 AC-1 shows the owner "mã căn và vị
 * trí thực"). Published for brokerage-service's owner read and listing-service's review (ADR-009), read-only, in the
 * caller's transaction.
 * <p>
 * It has no viewer of its own. Its two allowed callers: brokerage's owner read, after it checked that the caller is the
 * agreement's owner; and listing's opening of the real location of a revision's unit by a listing reviewer
 * (listing-publish E06-S4 spec E06S4-D12), after listing checked the reviewer role, the reviewer's independence from the
 * revision and that the revision may be decided now. The values are private to those two readers; its adapters never
 * expose it over HTTP and never log a value. Only an adapter in vn.sunvibe.integration calls it (ViewerlessReadArchTest).
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
