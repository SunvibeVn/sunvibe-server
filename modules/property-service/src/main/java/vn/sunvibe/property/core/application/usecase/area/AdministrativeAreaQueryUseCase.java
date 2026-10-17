package vn.sunvibe.property.core.application.usecase.area;

import java.util.List;
import java.util.Optional;

/**
 * Reads the administrative-area catalog (acquisition-owner-intake brief BR-6). Read-only and safe for anonymous
 * callers: it exposes only public catalog data. Published for other modules, which reach it through a port and an
 * adapter in the app module (ADR-009).
 */
public interface AdministrativeAreaQueryUseCase {

    /** All provinces, sorted by name in Vietnamese order. */
    List<ProvinceView> findProvinces();

    /**
     * The wards of a province, sorted by name in Vietnamese order.
     *
     * @throws vn.sunvibe.shared.exception.ResourceNotFoundException when the province code is unknown
     */
    List<WardView> findWards(String provinceCode);

    /** One ward by its official code, or empty when the code is unknown. */
    Optional<WardView> findWard(String wardCode);
}
