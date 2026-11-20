package vn.sunvibe.property.core.application.usecase.intake;

import java.util.Collection;
import java.util.List;

/**
 * Use Case - the profile of units: the property's asset type, its ward and province from the catalog, and the unit's
 * rooms and area (listing-publish E06-S2 spec E06S2-D12: a listing revision snapshots them at its submission,
 * listing-publish BR-4). Published for listing-service (ADR-009), read-only, in the caller's transaction.
 * <p>
 * It has no viewer of its own: its one allowed caller is listing's submission, after listing checked that its caller is
 * the listing's assigned Sale, and its adapter never exposes it over HTTP. Only an adapter in vn.sunvibe.integration
 * calls it (ViewerlessReadArchTest).
 */
public interface UnitProfileUseCase {

    /** The most unit ids one call takes. */
    int MAX_UNITS = 250;

    /**
     * The profile of each known unit, in one statement whatever the size of the set; an unknown id is left out, an empty
     * set reads nothing. The ward and its province come from the catalog only: a property whose ward code the catalog
     * lacks, or a legacy property with a free-text ward, has neither.
     *
     * @throws IllegalArgumentException more than {@link #MAX_UNITS} ids, before any statement
     */
    List<UnitProfileView> profiles(Collection<String> unitIds);
}
