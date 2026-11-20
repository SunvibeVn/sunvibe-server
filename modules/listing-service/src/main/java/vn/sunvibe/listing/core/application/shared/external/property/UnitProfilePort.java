package vn.sunvibe.listing.core.application.shared.external.property;

import java.util.Optional;

/**
 * Port - property's viewer-less unit profile read, as a revision's submission uses it (listing-publish E06-S2 spec
 * E06S2-D12; ADR-009). The caller has checked first that its own caller is the listing's assigned Sale: the read
 * itself checks nothing. A plain read in the caller's transaction.
 */
public interface UnitProfilePort {

    /** The unit's profile; empty for a unit property does not know. */
    Optional<UnitProfileFacts> profile(String unitId);
}
