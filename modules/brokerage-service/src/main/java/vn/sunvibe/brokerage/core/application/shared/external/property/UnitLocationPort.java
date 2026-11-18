package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.Collection;
import java.util.List;

/**
 * Port - the real location of the agreements' units, from property-service (E05-S5 spec S5-D2, ADR-009). Called only by
 * the owner's read, after it found that the caller owns these agreements; it joins the caller's transaction.
 */
public interface UnitLocationPort {

    /** The location of each known unit, at most 250 ids; an unknown unit is left out. */
    List<UnitLocationFacts> locate(Collection<String> unitIds);
}
