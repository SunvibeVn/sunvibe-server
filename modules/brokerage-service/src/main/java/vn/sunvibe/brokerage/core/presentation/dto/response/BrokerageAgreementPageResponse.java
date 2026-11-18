package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** One page of the staff agreement list (E05-S7 spec S7-D1, section 4). */
@Schema(name = "BrokerageAgreementPage")
public record BrokerageAgreementPageResponse(
        List<BrokerageAgreementListItemResponse> items,
        @Schema(description = "The zero-based page answered")
        int page,
        @Schema(description = "The size asked for")
        int size,
        @Schema(description = "Every agreement in the caller's scope that matches")
        long totalElements) {
}
