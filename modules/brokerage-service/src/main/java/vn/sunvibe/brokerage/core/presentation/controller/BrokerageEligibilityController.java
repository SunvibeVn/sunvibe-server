package vn.sunvibe.brokerage.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.brokerage.core.application.usecase.EligibilityQueryUseCase;
import vn.sunvibe.brokerage.core.presentation.converter.EligibilityResponseConverter;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageEligibilityResponse;
import vn.sunvibe.brokerage.core.presentation.resolver.StaffViewerResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * Whether each unit of a lead's latest completed intake can get a brokerage agreement (brokerage-agreement US-1 AC-1,
 * AC-2; E05-S1 spec 4). {@code /api/v1/staff/**} has no path rule, so this class carries the staff rule; the lead's
 * visibility is checked in the use case. The read writes nothing and runs without open-in-view (ADR-012 decision 8).
 */
@RestController
@RequestMapping(BrokerageEligibilityController.PATH)
@RequiredArgsConstructor
@Tag(name = "Brokerage agreement")
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class BrokerageEligibilityController {

    public static final String PATH = "/api/v1/staff/owner-leads/{leadId}/brokerage-eligibility";

    private final EligibilityQueryUseCase eligibility;
    private final StaffViewerResolver viewers;
    private final EligibilityResponseConverter converter;

    @GetMapping
    @Operation(operationId = "getBrokerageEligibility", summary = "Whether each unit of the lead's latest completed intake"
            + " can get a brokerage agreement, and the reason groups when not")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The eligibility of every unit"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not staff",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BrokerageEligibilityResponse eligibility(@PathVariable("leadId") String leadId) {
        return converter.toResponse(eligibility.forLead(leadId, viewers.current()));
    }
}
