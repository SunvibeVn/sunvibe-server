package vn.sunvibe.acquisition.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.acquisition.core.application.usecase.SaleTerritoryUseCase;
import vn.sunvibe.acquisition.core.presentation.converter.LeadViewerConverter;
import vn.sunvibe.acquisition.core.presentation.converter.OwnerLeadResponseMapper;
import vn.sunvibe.acquisition.core.presentation.dto.request.AssignTerritoryRequest;
import vn.sunvibe.acquisition.core.presentation.dto.response.SaleTerritoryResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.SecurityError;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.util.List;

/**
 * Which staff member looks after each ward (acquisition-owner-lead-queue US-5): at most one person per ward, and
 * every change is recorded. A change affects only leads sent afterwards (acquisition-owner-lead-queue BR-6).
 */
@RestController
@RequestMapping(SaleTerritoryController.PATH)
@Tag(name = "Sale territories (staff)")
@RequiredArgsConstructor
// acquisition-owner-lead-queue US-5: only ADMIN manages territories; SUPER_ADMIN passes through the role hierarchy
@PreAuthorize("hasRole('ADMIN')")
public class SaleTerritoryController {

    public static final String PATH = "/api/v1/staff/sale-territories";
    /** Ward codes are at most 10 characters, the length the catalog and the territory tables store. */
    private static final String WARD_CODE_MESSAGE = "Ward code must be at most 10 characters.";

    private final SaleTerritoryUseCase territoryUseCase;
    private final LeadViewerConverter viewerConverter;
    private final OwnerLeadResponseMapper responseMapper;

    /**
     * GET /api/v1/staff/sale-territories?provinceCode=48
     * <p>
     * Every ward of the province, assigned or not, sorted by ward name.
     */
    @GetMapping
    @Operation(summary = "List the wards of a province and who looks after each (ADMIN, SUPER_ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Every ward of the province",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = SaleTerritoryResponse.class)))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on provinceCode",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the province is not in the catalog",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<SaleTerritoryResponse>> list(
            @Parameter(description = "Province code", example = "48")
            @RequestParam(name = "provinceCode")
            @NotBlank(message = "Province code is required.") String provinceCode) {
        return ResponseEntity.ok(territoryUseCase.list(provinceCode).stream()
                .map(responseMapper::toTerritory)
                .toList());
    }

    /**
     * PUT /api/v1/staff/sale-territories/{wardCode}
     * <p>
     * Give the ward to the person, replacing whoever had it; the same person again changes nothing.
     */
    @PutMapping("/{wardCode}")
    @Operation(summary = "Give a ward to a SALE or SALE_LEADER (ADMIN, SUPER_ADMIN)",
            description = "Replaces whoever had the ward; the same person again changes nothing. Existing leads "
                    + "keep their assignee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The ward after the change",
                    content = @Content(schema = @Schema(implementation = SaleTerritoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on wardCode or userId; "
                    + "MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the ward is not in the catalog",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "INELIGIBLE_ASSIGNEE (field userId): unknown user, "
                    + "cannot sign in, or neither SALE nor SALE_LEADER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SaleTerritoryResponse> assign(
            @PathVariable("wardCode") @Size(max = 10, message = WARD_CODE_MESSAGE) String wardCode,
            @Valid @RequestBody AssignTerritoryRequest request) {
        String actorId = viewerConverter.actorId(SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok(responseMapper.toTerritory(
                territoryUseCase.assign(wardCode, request.userId(), actorId)));
    }

    /**
     * DELETE /api/v1/staff/sale-territories/{wardCode}
     * <p>
     * Leave the ward without a person; 204 also when nobody had it.
     */
    @DeleteMapping("/{wardCode}")
    @Operation(summary = "Leave a ward without a person (ADMIN, SUPER_ADMIN)",
            description = "204 also when nobody looked after the ward. Existing leads keep their assignee.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Nobody looks after the ward now"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on wardCode",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the ward is not in the catalog",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> unassign(
            @PathVariable("wardCode") @Size(max = 10, message = WARD_CODE_MESSAGE) String wardCode) {
        String actorId = viewerConverter.actorId(SecurityContextHolder.getContext().getAuthentication());
        territoryUseCase.unassign(wardCode, actorId);
        return ResponseEntity.noContent().build();
    }
}
