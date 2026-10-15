package vn.sunvibe.auth.core.presentation.dto.response.role;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Body of {@code GET /api/v1/staff/users/{userId}/roles}. */
public record UserRolesResponse(
        @Schema(description = "The account's user id")
        String userId,
        @Schema(description = "Role names, sorted, e.g. CUSTOMER, OWNER, TENANT")
        List<String> roles) {
}
