package vn.sunvibe.property.core.presentation.controller.intake;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.property.core.application.usecase.intake.InventoryCaseUseCase;
import vn.sunvibe.property.core.application.usecase.intake.InventoryCaseView;
import vn.sunvibe.property.core.presentation.resolver.IntakeActorResolver;
import vn.sunvibe.property.core.presentation.dto.request.intake.CaseDecisionRequest;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * Duplicate cases for managers (property-create intake spec 7.2). The chain ends in {@code authenticated()}, so this class carries
 * the role rule; who worked either lead is refused in the use case.
 */
@RestController
@RequestMapping(StaffInventoryCaseController.PATH)
@RequiredArgsConstructor
@Tag(name = "Inventory cases")
@PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')")
public class StaffInventoryCaseController {

    public static final String PATH = "/api/v1/staff/inventory-cases";

    private final InventoryCaseUseCase cases;
    private final IntakeActorResolver actors;

    @GetMapping
    @Operation(summary = "The cases a manager may act on, oldest first; whoever worked a side does not see them")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of cases"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "The caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the intake switch is off",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InventoryCaseUseCase.CasePage list(@RequestParam(name = "status", required = false) String status,
                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                              @RequestParam(name = "size", defaultValue = "20") int size) {
        return cases.list(actors.current(), status, page, size);
    }

    @GetMapping("/{caseId}")
    @Operation(summary = "One case; writes an access log row")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The case"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CASE_NOT_ALLOWED, or the caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The case does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InventoryCaseView read(@PathVariable("caseId") String caseId) {
        return cases.read(caseId, actors.current());
    }

    @PostMapping("/{caseId}/decision")
    @Operation(summary = "Rule on a case: DIFFERENT_ASSET or SAME_ASSET")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The case"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CASE_NOT_ALLOWED, or the caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The case does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INTAKE_STATE or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InventoryCaseView decide(@PathVariable("caseId") String caseId, @Valid @RequestBody CaseDecisionRequest request) {
        return cases.decide(caseId, actors.current(), request.outcome(), request.proceedingIntakeId(), request.basis(),
                request.expectedVersion());
    }
}
