package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.Collection;
import java.util.List;

/**
 * The bases of a set of units and the owners of a set of properties, owned by property-service; the adapter lives in
 * the app module (ADR-009; E05-S6 spec S6-D17). Each call is one set read at a fixed number of statements, joins the
 * caller's transaction and writes nothing. Called only for agreements in effect, after the caller decided that it may
 * read them (E05-S6 spec S6-D9).
 */
public interface UnitBasisPort {

    /**
     * Read 1: per unit its property and one entry per lead's latest COMPLETED intake that maps it. An unknown unit is
     * absent. At most 250 distinct ids.
     */
    List<UnitBasisFacts> forUnits(Collection<String> unitIds);

    /** Read 1b: per property its declared owner and its ownership rows valid today. An unknown property is absent. */
    List<PropertyOwnerFacts> ownersOf(Collection<String> propertyIds);
}
