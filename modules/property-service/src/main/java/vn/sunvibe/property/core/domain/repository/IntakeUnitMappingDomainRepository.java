package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.intake.UnitMapping;

import java.util.Collection;
import java.util.List;

/** Which unit each surveyed space became (property-create intake spec 5.2 file 8). */
public interface IntakeUnitMappingDomainRepository {

    /** Inserts the rows and flushes. */
    void saveAll(List<UnitMapping> mappings);

    List<UnitMapping> findByIntakeId(String intakeId);

    /** The rows that name any of these units. */
    List<UnitMapping> findByUnitIds(Collection<String> unitIds);
}
