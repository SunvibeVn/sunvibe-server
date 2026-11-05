package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Manager suspensions (property-create intake D5-16). */
public interface IntakeSuspensionDomainRepository {

    /** Inserts or writes the row and flushes. */
    void save(IntakeSuspension suspension);

    Optional<IntakeSuspension> findById(String id);

    /** The suspensions of the lead, oldest first, released ones included. */
    List<IntakeSuspension> findByLeadId(String leadId);

    /** Whether an unreleased suspension names any of these units, on any lead (spec 4.7). */
    boolean anyActiveOnUnits(Collection<String> unitIds);
}
