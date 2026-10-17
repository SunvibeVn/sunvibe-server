package vn.sunvibe.auth.core.presentation.controller.role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.usecase.role.RoleGrantUseCase;
import vn.sunvibe.auth.core.application.usecase.role.RoleRevokeUseCase;
import vn.sunvibe.auth.core.application.usecase.role.UserRoleQueryUseCase;
import vn.sunvibe.auth.core.presentation.converter.role.RoleConverter;
import vn.sunvibe.auth.core.presentation.dto.response.role.UserRolesResponse;
import vn.sunvibe.shared.dto.response.error.BaseErrorModel;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * Role admin API (auth-roles US-4, BR-9..BR-11). ADMIN (and SUPER_ADMIN through the role hierarchy)
 * reads, grants and revokes OWNER, CUSTOMER, SALE, SALE_LEADER, SALES_MANAGER and LISTING_REVIEWER; only
 * SUPER_ADMIN grants or revokes ADMIN. Nobody changes their own roles, and only SUPER_ADMIN changes an account
 * that holds ADMIN or SUPER_ADMIN (owner clarification Q-8).
 */
@RestController
@RequestMapping("/api/v1/staff/users/{userId}/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StaffUserRoleController {

    private static final String GRANTABLE = "OWNER, CUSTOMER, SALE, SALE_LEADER, SALES_MANAGER, LISTING_REVIEWER; "
            + "ADMIN only for SUPER_ADMIN";

    private final UserRoleQueryUseCase userRoleQueryUseCase;
    private final RoleGrantUseCase roleGrantUseCase;
    private final RoleRevokeUseCase roleRevokeUseCase;
    private final RoleConverter roleConverter;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read an account's roles (ADMIN, SUPER_ADMIN)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The account's roles",
                    content = @Content(schema = @Schema(implementation = UserRolesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "Caller is not ADMIN or SUPER_ADMIN",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "404", description = "No such account",
                    content = @Content(schema = @Schema(implementation = BaseErrorModel.class)))
    })
    public ResponseEntity<UserRolesResponse> getRoles(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(roleConverter.toResponse(userRoleQueryUseCase.execute(userId)));
    }

    @PutMapping(value = "/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Grant a role (idempotent)",
            description = "Grantable: " + GRANTABLE + ". Only an account that may sign in gets a role. "
                    + "Granting a role already held changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The account holds the role"),
            @ApiResponse(responseCode = "400", description = "Role not grantable (SUPER_ADMIN, an old role or an unknown name); field role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "Caller may not change this role (ADMIN changing ADMIN), "
                    + "changes their own roles, or is ADMIN and the account holds ADMIN or SUPER_ADMIN",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "404", description = "No such account",
                    content = @Content(schema = @Schema(implementation = BaseErrorModel.class))),
            @ApiResponse(responseCode = "422", description = "The account is locked or terminated; field userId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> grantRole(@PathVariable("userId") String userId,
            @Parameter(description = GRANTABLE) @PathVariable("role") String role) {
        roleGrantUseCase.execute(roleConverter.toCommand(userId, role));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(value = "/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Revoke a role (idempotent)",
            description = "Revocable: " + GRANTABLE + ". Revoking a held role ends every session of the account; "
                    + "revoking a role that is not held changes nothing. An account may end with no roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The account does not hold the role"),
            @ApiResponse(responseCode = "400", description = "Role not revocable (SUPER_ADMIN, an old role or an unknown name); field role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = "Caller may not change this role (ADMIN changing ADMIN), "
                    + "changes their own roles, or is ADMIN and the account holds ADMIN or SUPER_ADMIN",
                    content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "404", description = "No such account",
                    content = @Content(schema = @Schema(implementation = BaseErrorModel.class)))
    })
    public ResponseEntity<Void> revokeRole(@PathVariable("userId") String userId,
            @Parameter(description = GRANTABLE) @PathVariable("role") String role) {
        roleRevokeUseCase.execute(roleConverter.toCommand(userId, role));
        return ResponseEntity.noContent().build();
    }
}
