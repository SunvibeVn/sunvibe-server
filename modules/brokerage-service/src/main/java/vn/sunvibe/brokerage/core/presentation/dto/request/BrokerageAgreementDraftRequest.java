package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The whole content of an agreement's draft (E05-S3 spec 4, S3-D5; its fee policy, E05-S4 spec S4-D1): a part not
 * known yet is null, so a body without a fee policy stores none. Unknown properties
 * are ignored: no field sets a status, an owner, a basis, a drafter, an actor, a time, a version number or the
 * advertising scope. Numbers are read strictly: a fraction is a malformed body, never cut (plan E05S3-P4).
 */
@Schema(name = "BrokerageAgreementDraftRequest")
public record BrokerageAgreementDraftRequest(
        @NotNull @Min(0)
        @Schema(format = "int64", minimum = "0", description = "The dataVersion of the detail the caller read")
        Long expectedDataVersion,
        @NotNull @Size(max = 20)
        @Schema(allowableValues = {"LONG_TERM_RENT"}, maxLength = 20, description = "Only long-term rental now")
        String transactionType,
        @NotNull
        @Schema(description = "A calendar date in Vietnam time, 2000-01-01 to 2099-12-31")
        LocalDate startDate,
        @NotNull
        @Schema(description = "A calendar date in Vietnam time, not before the start date and at most 12 months after it")
        LocalDate endDate,
        @Schema(nullable = true, description = "Public photos of the interior and common areas; null = not answered yet")
        Boolean photoConsent,
        @NotNull
        @Schema(description = "Public photos of the exterior (front, entrance, street view); true only with photoConsent"
                + " true")
        Boolean exteriorPhotoConsent,
        @NotNull @Valid
        BrokerageAgreementTermsInput terms,
        @Valid
        @Schema(nullable = true, description = "The brokerage fee policy; absent or null = nothing entered yet")
        BrokerageAgreementFeePolicyInput feePolicy) {
}
