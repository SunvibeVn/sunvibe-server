package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Staff terminate an agreement that has not ended (brokerage-agreement BR-14, BR-16, US-5 AC-2; E05-S8 spec 3.3, 4,
 * S8-D5). Each type takes its own fields and none of another: OWNER_WITHDREW reportedOn and channel, UNIT_LET reportedOn
 * and introducedBySunvibe, SUNVIBE_TERMINATED a note; a note is optional for the first two. No data version: a stop
 * decides on the state read under its locks.
 */
@Schema(name = "BrokerageAgreementTerminationRequest", example = "{\"type\":\"UNIT_LET\",\"reportedOn\":\"2026-12-02\","
        + "\"introducedBySunvibe\":\"UNKNOWN\",\"note\":null}")
public record BrokerageAgreementTerminationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"OWNER_WITHDREW", "UNIT_LET", "SUNVIBE_TERMINATED"},
                description = "OWNER_WITHDREW: the owner withdrew; UNIT_LET: the unit is let; SUNVIBE_TERMINATED:"
                        + " Sunvibe's decision (a manager only)")
        String type,
        @Schema(nullable = true, description = "OWNER_WITHDREW and UNIT_LET only, required for them: the date the owner"
                + " said it or reported it, a calendar date in Vietnam from 2000-01-01 to today")
        LocalDate reportedOn,
        @Schema(nullable = true, allowableValues = {"PHONE_CALL", "IN_PERSON", "MESSAGE", "EMAIL", "OTHER"},
                description = "OWNER_WITHDREW only, required for it: how the owner said it")
        String channel,
        @Schema(nullable = true, allowableValues = {"YES", "NO", "UNKNOWN"},
                description = "UNIT_LET only, required for it: whether Sunvibe introduced the tenant")
        String introducedBySunvibe,
        @Size(max = 500)
        @Schema(nullable = true, description = "Private; at most 500 characters as sent, trimmed; required (1 to 500) for"
                + " SUNVIBE_TERMINATED, optional and stored only when not blank otherwise; no control or bidirectional"
                + " formatting character")
        String note) {

    /** brokerage-agreement BR-17: the date, the channel, the answer on the tenant and the note stay out of every log. */
    @Override
    public String toString() {
        return "BrokerageAgreementTerminationRequest[" + type + "]";
    }
}
