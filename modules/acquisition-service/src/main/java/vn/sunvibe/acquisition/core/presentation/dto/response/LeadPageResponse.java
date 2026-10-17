package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO - LeadPageResponse (Record)
 * <p>
 * One page of the staff lead queue, newest first (acquisition-owner-lead-queue spec 6).
 */
@Schema(description = "One page of owner leads, newest first")
public record LeadPageResponse(
        List<OwnerLeadSummaryResponse> items,
        @Schema(description = "Zero-based page number") int page,
        @Schema(description = "Page size asked for") int size,
        @Schema(description = "Leads over all pages that the caller can see") long totalElements) {

    public LeadPageResponse {
        items = List.copyOf(items);
    }
}
