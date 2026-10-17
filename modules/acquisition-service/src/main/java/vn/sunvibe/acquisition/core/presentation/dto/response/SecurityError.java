package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - SecurityError (Record)
 * <p>
 * Documents the 401 and 403 body that Spring Security writes for every endpoint (SecurityConfig in app; ADR-010
 * keeps it). Only used in the OpenAPI annotations: the acquisition code never builds it.
 */
@Schema(description = "401/403 body written by Spring Security")
public record SecurityError(
        @Schema(example = "Forbidden") String error,
        @Schema(example = "You do not have permission to access this resource.") String message,
        @Schema(example = "/api/v1/staff/owner-leads") String path) {
}
