package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - OwnerIntakeResponse (Record)
 * <p>
 * The public code of the request (acquisition-owner-intake BR-11), shown to the owner; nothing else is returned.
 */
public record OwnerIntakeResponse(
        @Schema(description = "Public code of the request: SV-XXXX-XXXX", example = "SV-7K3M-Q9TX")
        String requestCode) {
}
