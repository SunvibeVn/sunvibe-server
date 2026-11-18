package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One event of an agreement's history (brokerage-agreement US-4 AC-4, BR-17; E05-S7 spec S7-D7, S7-D8): what happened,
 * the version, who acted and when. No reason, note, term, value or user id; the owner is never named.
 */
@Schema(name = "BrokerageAgreementHistoryEntry")
public record BrokerageAgreementHistoryEntryResponse(
        @Schema(description = "The event; OTHER for one this version of the server does not know", allowableValues = {
                "CREATED", "DRAFT_EDITED", "VERSION_SENT", "OWNER_CONFIRMED", "OWNER_REJECTED", "VERSION_LAPSED",
                "DRAFT_OPENED", "ACTIVATED", "ACTIVATION_REFUSED", "VERSION_STARTED", "OTHER"})
        String type,
        @Schema(nullable = true, description = "The version the event is about; null for none")
        Integer versionNo,
        @Schema(description = "Who acted: a staff member, the owner, or Sunvibe's own job",
                allowableValues = {"STAFF", "OWNER", "SYSTEM"})
        String actorType,
        @Schema(nullable = true, description = "The staff member's full name; null for the owner, for Sunvibe's job and"
                + " for an account the server does not know")
        String actorName,
        Instant occurredAt) {
}
