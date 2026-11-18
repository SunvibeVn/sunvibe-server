package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** One page of an agreement's history, newest first (E05-S7 spec S7-D7, section 4). */
@Schema(name = "BrokerageAgreementHistory")
public record BrokerageAgreementHistoryResponse(
        List<BrokerageAgreementHistoryEntryResponse> entries,
        @Schema(description = "The zero-based page answered")
        int page,
        @Schema(description = "The size asked for")
        int size,
        @Schema(description = "Every stored event of the agreement")
        long totalElements) {
}
