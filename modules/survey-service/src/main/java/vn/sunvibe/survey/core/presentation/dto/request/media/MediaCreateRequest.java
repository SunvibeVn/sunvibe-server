package vn.sunvibe.survey.core.presentation.dto.request.media;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/**
 * DTO - MediaCreateRequest (Record)
 * <p>
 * Create a survey photo record before its bytes (survey-media spec 4.4 step 1, 7.2): every key present, unknown keys
 * refused (W16 A-4); at most 16 KiB (A-11).
 */
@Schema(description = "Create a survey photo record before sending its bytes")
public record MediaCreateRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "A ULID the client generates once per file: the same key and body "
                + "again answers the same photo (200)")
        String uploadKey,

        @JsonProperty(required = true)
        @Schema(nullable = true, description = "A space of the checklist's current G1, or null for the whole asset")
        String spaceId,

        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The evidence category; allowed at the placement's level and, for "
                + "SHARED_*, only while the rental scope is PARTS")
        EvidenceCategory category,

        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Optional, 1..200 characters, no phone number")
        String caption,

        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The file size in bytes, 1..20971520 (20 MiB)")
        Long sizeBytes,

        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The SHA-256 of the file, 64 hexadecimal characters")
        String contentSha256) {

    /** Staff text and the file hash: never print them. */
    @Override
    public String toString() {
        return "MediaCreateRequest[" + uploadKey + "]";
    }
}
