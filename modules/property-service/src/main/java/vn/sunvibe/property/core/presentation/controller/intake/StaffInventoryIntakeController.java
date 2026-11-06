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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.property.core.application.usecase.intake.IntakeAvailabilityUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeCompletionUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakePreviewUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakePreviewView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeReadUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeRestorationUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeSelectionUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeSuspensionUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeTargetUseCase;
import vn.sunvibe.property.core.application.usecase.intake.InventorySearchUseCase;
import vn.sunvibe.property.core.application.usecase.intake.OwnerCheckContext;
import vn.sunvibe.property.core.application.usecase.intake.OwnerCheckUseCase;
import vn.sunvibe.property.core.application.usecase.intake.OwnerRequestUseCase;
import vn.sunvibe.property.core.presentation.resolver.IntakeActorResolver;
import vn.sunvibe.property.core.presentation.dto.request.intake.IntakeRequests;
import vn.sunvibe.property.core.presentation.dto.request.intake.OwnerCheckRequests;
import vn.sunvibe.property.core.presentation.dto.request.intake.OwnerConfirmationRequests;
import vn.sunvibe.property.core.presentation.dto.request.intake.SelectionRequest;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.util.List;

/**
 * The inventory intake of a lead (property-create intake spec 7.1). {@code /api/v1/staff/**} has no path rule, so this class
 * carries the staff rule; the assignee is checked in the use case, and manager paths carry a method rule of their own.
 */
@RestController
@RequestMapping(StaffInventoryIntakeController.PATH)
@RequiredArgsConstructor
@Tag(name = "Inventory intake")
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class StaffInventoryIntakeController {

    public static final String PATH = "/api/v1/staff/owner-leads/{leadId}/inventory-intake";
    private static final String MANAGERS = "hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')";

    private final IntakeReadUseCase reads;
    private final IntakeSelectionUseCase selections;
    private final OwnerRequestUseCase ownerRequests;
    private final OwnerCheckUseCase ownerChecks;
    private final IntakeSuspensionUseCase suspensions;
    private final InventorySearchUseCase searches;
    private final IntakeTargetUseCase targets;
    private final IntakeAvailabilityUseCase availabilities;
    private final IntakePreviewUseCase previews;
    private final IntakeCompletionUseCase completions;
    private final IntakeRestorationUseCase restorations;
    private final IntakeActorResolver actors;

    @GetMapping
    @Operation(summary = "The intake block of a lead, for any staff who can see it")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "Caller is not staff",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible, or the intake is off",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView block(@PathVariable("leadId") String leadId) {
        return reads.block(leadId, actors.current());
    }

    @PutMapping("/selection")
    @Operation(summary = "Choose the parts an intake takes; a change voids the request, proof, target and availability")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or INTAKE_SELECTION_INVALID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "INTAKE_GATE_FAILED, INTAKE_STATE or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView select(@PathVariable("leadId") String leadId, @Valid @RequestBody SelectionRequest request) {
        return selections.select(leadId, actors.current(), request.spaceIds(), request.expectedVersion());
    }

    @PostMapping("/owner-confirmation")
    @Operation(summary = "Ask an account to confirm; the answer is the same whether the address matched or not")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The request and its code, which shows only here"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on ownerEmail",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "INTAKE_STATE, INTAKE_GATE_FAILED or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "OWNER_REQUEST_LIMIT, or too many requests from this address",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OwnerRequestUseCase.IssuedRequest requestOwner(@PathVariable("leadId") String leadId,
                                                          @Valid @RequestBody OwnerConfirmationRequests.Ask request) {
        return ownerRequests.request(leadId, actors.current(), request.ownerEmail(), request.expectedVersion());
    }

    @GetMapping("/inventory-search")
    @Operation(summary = "The confirmed owner's properties, by code, or by ward and text; writes an access log row")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of properties"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "OWNER_PROOF_REQUIRED, INTAKE_STATE or INTAKE_GATE_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public InventorySearchUseCase.SearchPage search(@PathVariable("leadId") String leadId,
                                                    @RequestParam(name = "propertyCode", required = false) String propertyCode,
                                                    @RequestParam(name = "wardCode", required = false) String wardCode,
                                                    @RequestParam(name = "q", required = false) String q,
                                                    @RequestParam(name = "page", defaultValue = "0") int page) {
        return searches.search(leadId, actors.current(), propertyCode, wardCode, q, page);
    }

    @PutMapping("/target")
    @Operation(summary = "Choose a new property, or a link to one the confirmed owner holds")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or LINK_MAPPING_INVALID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "OWNER_PROOF_REQUIRED, CANONICAL_UNRECONCILED, "
                    + "INTAKE_CREATE_NOT_ALLOWED, INTAKE_DISPUTED, INTAKE_STATE, INTAKE_GATE_FAILED or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView target(@PathVariable("leadId") String leadId, @Valid @RequestBody IntakeRequests.Target request) {
        List<IntakeTargetUseCase.TargetCommand.Mapping> mappings = request.mappings() == null ? List.of()
                : request.mappings().stream()
                .map(item -> new IntakeTargetUseCase.TargetCommand.Mapping(item.spaceId(), item.unitId())).toList();
        return targets.target(leadId, actors.current(), new IntakeTargetUseCase.TargetCommand(request.mode(),
                request.propertyId(), request.canonicalSpaceId(), mappings), request.expectedVersion());
    }

    @PutMapping("/availability")
    @Operation(summary = "What the owner said about each selected space, on the day of the contact")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "OWNER_PROOF_REQUIRED, INTAKE_STATE, INTAKE_GATE_FAILED or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView availability(@PathVariable("leadId") String leadId,
                                        @Valid @RequestBody IntakeRequests.Availability request) {
        List<IntakeAvailabilityUseCase.Item> items = request.items().stream()
                .map(item -> new IntakeAvailabilityUseCase.Item(item.spaceId(), item.state(), item.contactedOn(), item.note(),
                        item.expectedDate())).toList();
        return availabilities.availability(leadId, actors.current(), items, request.expectedVersion());
    }

    @GetMapping("/preview")
    @Operation(summary = "What a completion would write, and the hash it must name; writes an access log row")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The preview"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "OWNER_PROOF_REQUIRED, INTAKE_STATE, INTAKE_GATE_FAILED or INTAKE_SOURCE_INVALID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakePreviewView preview(@PathVariable("leadId") String leadId) {
        return previews.preview(leadId, actors.current());
    }

    @PostMapping("/completion")
    @Operation(summary = "Write the result, all or nothing; a repeat with the same hash answers the stored result")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The result"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or LINK_MAPPING_INVALID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INTAKE_STATE, INTAKE_GATE_FAILED, OWNER_PROOF_REQUIRED, "
                    + "AVAILABILITY_NOT_CONFIRMED, INTAKE_PREVIEW_STALE, INTAKE_DUPLICATE_CHECK, INTAKE_ALREADY_COMPLETED, "
                    + "INTAKE_CREATE_NOT_ALLOWED, INTAKE_DISPUTED, CANONICAL_UNRECONCILED, INTAKE_SOURCE_INVALID or "
                    + "CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeCompletionUseCase.CompletionResult complete(@PathVariable("leadId") String leadId,
                                                             @Valid @RequestBody IntakeRequests.Completion request) {
        return completions.complete(leadId, actors.current(), request.expectedVersion(), request.previewHash());
    }

    @PostMapping("/restoration")
    @Operation(summary = "Let a new checked proof of the owner take over a result")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "INTAKE_NOT_ASSIGNEE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INTAKE_STATE, INTAKE_GATE_FAILED, OWNER_PROOF_REQUIRED, "
                    + "INTAKE_DISPUTED or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView restore(@PathVariable("leadId") String leadId, @RequestBody IntakeRequests.Restoration request) {
        return restorations.restore(leadId, actors.current(), request.expectedVersion());
    }

    @GetMapping("/owner-check-context")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "What a manager reads before calling the lead's phone; writes an access log row")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The context"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "OWNER_CHECK_NOT_ALLOWED, or the caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INTAKE_STATE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OwnerCheckContext ownerCheckContext(@PathVariable("leadId") String leadId) {
        return ownerChecks.context(leadId, actors.current());
    }

    @PostMapping("/owner-check/start")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Open a phrase for 10 minutes; a new start replaces it")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The phrase, which shows only here"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "OWNER_CHECK_NOT_ALLOWED, or the caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INTAKE_STATE or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public OwnerCheckUseCase.StartedCheck startOwnerCheck(@PathVariable("leadId") String leadId,
                                                          @Valid @RequestBody OwnerCheckRequests.Check request) {
        return ownerChecks.start(leadId, actors.current(), request.proofId(), request.basis(), request.expectedVersion());
    }

    @PostMapping("/owner-check/refusal")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Refuse the proof after reaching a stranger; an open row goes back to DRAFT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "OWNER_CHECK_NOT_ALLOWED, or the caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INTAKE_STATE or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView refuseOwner(@PathVariable("leadId") String leadId,
                                       @Valid @RequestBody OwnerCheckRequests.Check request) {
        return ownerChecks.refuse(leadId, actors.current(), request.proofId(), request.basis(), request.expectedVersion());
    }

    @PostMapping("/suspensions")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Restrict the whole lead, or one unit of a result of the lead")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "OWNER_CHECK_NOT_ALLOWED, or the caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead or the unit does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView suspend(@PathVariable("leadId") String leadId,
                                   @Valid @RequestBody OwnerCheckRequests.Suspension request) {
        return suspensions.suspend(leadId, actors.current(), request.unitId(), request.reason(), request.expectedVersion());
    }

    @PostMapping("/suspensions/{id}/release")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Release a suspension of this lead")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The block"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "OWNER_CHECK_NOT_ALLOWED, or the caller holds no manager role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The lead or the suspension does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INTAKE_STATE or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public IntakeBlockView release(@PathVariable("leadId") String leadId, @PathVariable("id") String id,
                                   @Valid @RequestBody OwnerCheckRequests.Release request) {
        return suspensions.release(leadId, actors.current(), id, request.releaseReason(), request.expectedVersion());
    }
}
