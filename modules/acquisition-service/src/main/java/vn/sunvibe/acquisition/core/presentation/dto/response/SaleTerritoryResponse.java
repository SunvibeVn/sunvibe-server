package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * DTO - SaleTerritoryResponse (Record)
 * <p>
 * One ward and the staff member who looks after it (acquisition-owner-lead-queue spec 6).
 */
@Schema(description = "Ward and the staff member who looks after it")
public record SaleTerritoryResponse(
        @Schema(example = "20242") String wardCode,
        String wardName,
        @Schema(description = "As in the catalog", allowableValues = {"WARD", "COMMUNE", "SPECIAL_ZONE"})
        String wardType,
        @Schema(description = "User id of the person; null when nobody looks after the ward") String userId,
        @Schema(description = "Null when nobody looks after the ward") Instant assignedAt) {
}
