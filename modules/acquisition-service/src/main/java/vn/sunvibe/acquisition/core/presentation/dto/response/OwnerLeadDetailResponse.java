package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO - OwnerLeadDetailResponse (Record)
 * <p>
 * One lead as a staff member who may see it opens it (acquisition-owner-lead-queue spec 6): the summary fields plus
 * the contact data and the rest of the form, the consent (no hashes), the possible duplicates the caller can see and
 * the history oldest first. Never the submission snapshot, a hash, an IP address or a user agent.
 */
@Schema(description = "Owner lead with contact data, consent, possible duplicates and history")
public record OwnerLeadDetailResponse(
        String id,
        @Schema(example = "SV-7K3M-Q9TX") String requestCode,
        OwnerLeadStatus status,
        OwnerLeadIntent intent,
        String contactName,
        @Schema(example = "48") String provinceCode,
        @Schema(example = "20242") String wardCode,
        String wardName,
        @Schema(description = "Kind of property the owner describes (not the unit layout of the property create "
                + "API)", nullable = true) OwnerLeadPropertyType propertyType,
        @Schema(description = "User id of the assignee; null when nobody holds the lead") String assignedUserId,
        @Schema(description = "Full name of the assignee; null when nobody holds the lead "
                + "or the id has no user row", nullable = true)
        String assignedUserName,
        Instant nextActionAt,
        @Schema(description = "True when the lead has at least one possible duplicate the caller can see")
        boolean possibleDuplicate,
        Instant createdAt,
        @Schema(description = "Send it back as expectedVersion with the next change") long version,
        @Schema(description = "Normalized phone number", example = "0909123456") String phone,
        String email,
        String addressDetail,
        @Schema(description = "Null when the owner gave no position") BigDecimal latitude,
        @Schema(description = "Null when the owner gave no position") BigDecimal longitude,
        @Schema(description = "Null when the owner gave no price") AskingPriceResponse askingPrice,
        LocalDate availableFrom,
        String notes,
        String preferredCallbackTime,
        OwnerLeadSource source,
        @Schema(description = "Lead this one repeats; null unless the caller can see that lead")
        String duplicateOfLeadId,
        String closeReason,
        ConsentResponse consent,
        @Schema(description = "Possible duplicates the caller can see, in either direction")
        List<DuplicateCandidateResponse> duplicateCandidates,
        @Schema(description = "History, oldest first") List<LeadActivityResponse> activities) {

    /** The price the owner hopes to get, in whole VND per unit. */
    public record AskingPriceResponse(long amount, @Schema(example = "VND") String currency, PriceUnit unit) {
    }

    /** Which privacy notice the owner accepted and when. */
    public record ConsentResponse(String noticeVersion, Instant acceptedAt) {
    }

    /** Another lead that may come from the same owner: no contact data. */
    public record DuplicateCandidateResponse(String id, String requestCode, OwnerLeadStatus status,
                                             Instant createdAt) {
    }

    public OwnerLeadDetailResponse {
        duplicateCandidates = List.copyOf(duplicateCandidates);
        activities = List.copyOf(activities);
    }

    /** The detail holds contact data, address and position: never print it. */
    @Override
    public String toString() {
        return "OwnerLeadDetailResponse[" + id + "]";
    }
}
