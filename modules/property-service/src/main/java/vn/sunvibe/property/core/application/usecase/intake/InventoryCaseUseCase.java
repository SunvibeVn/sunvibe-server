package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Use Case - the manager's duplicate cases (property-create intake spec 4.10, 7.2). Nobody who worked either side reads one. */
public interface InventoryCaseUseCase {

    CasePage list(IntakeActor actor, String statusOrNull, int page, int size);

    InventoryCaseView read(String caseId, IntakeActor actor);

    InventoryCaseView decide(String caseId, IntakeActor actor, String outcome, String proceedingIntakeIdOrNull, String basis,
                             Long expectedVersion);

    /** {@code totalElements} counts after the filter of who worked a side (spec 4.3). */
    @Schema(name = "InventoryCasePage")
    record CasePage(List<CaseItem> items, int page, int size, long totalElements) {
    }

    @Schema(name = "InventoryCaseItem")
    record CaseItem(String caseId, String leadCode, String wardName, Instant createdAt) {
    }
}
