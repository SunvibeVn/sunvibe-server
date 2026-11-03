package vn.sunvibe.survey.core.presentation.dto.request.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;

/** An ADMIN's security hold of a photo (survey-media spec A-23, 7.2); strict keys, no free text. */
@Schema(description = "Hold a survey photo for a security reason")
public record MediaHoldRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "SECURITY_INCIDENT: a malicious or exploit-suspected file, or one "
                + "produced under a decoder CVE; CREDENTIAL_EXPOSURE: a Sunvibe system secret in the photo or caption")
        HoldReason reason) {
}
