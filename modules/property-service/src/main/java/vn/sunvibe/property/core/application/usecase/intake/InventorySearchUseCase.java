package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Use Case - the confirmed owner's properties, for a link (property-create intake spec 4.7, S-10). */
public interface InventorySearchUseCase {

    /** By {@code propertyCode}, or by {@code wardCode} and {@code q}; each search writes an access log row. */
    SearchPage search(String leadId, IntakeActor actor, String propertyCodeOrNull, String wardCodeOrNull, String qOrNull, int page);

    @Schema(name = "InventorySearchPage")
    record SearchPage(List<Item> items, int page, int size, long totalElements) {
    }

    @Schema(name = "InventorySearchItem")
    record Item(String propertyId, String propertyCode, @Schema(nullable = true) String assetType, String wardName,
                List<SearchUnit> units) {
    }

    /** {@code location} is the unit's own label, which a legacy unit may not have. */
    @Schema(name = "InventorySearchUnit")
    record SearchUnit(String unitId, String unitCode, @Schema(nullable = true) String location) {
    }
}
