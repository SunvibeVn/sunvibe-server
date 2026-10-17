package vn.sunvibe.acquisition.core.application.shared.external.area;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view of the administrative-area catalog, owned by another module. The adapter lives in the app module,
 * so acquisition-service does not depend on that module (ADR-009).
 */
public interface AdministrativeAreaPort {

    /** One ward by its official code, or empty when the code is unknown. */
    Optional<AdministrativeWard> findWard(String wardCode);

    /** The wards of a province in catalog order; an empty Optional means the province code is unknown. */
    Optional<List<AdministrativeWard>> findWards(String provinceCode);
}
