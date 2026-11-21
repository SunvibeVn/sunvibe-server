package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Whether the viewer may create a listing under the agreement now; a hint, the command decides. */
@Schema(name = "ListingCreation")
public record ListingCreationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean viewerMayCreate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "E05's reason groups are exactly"
                + " NOT_STARTED: the listing can be prepared before the start")
        boolean advertisingNotStarted,
        @ArraySchema(schema = @Schema(allowableValues = {"NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED", "SUSPENDED",
                "ENDED", "BASIS", "PROPERTY", "UNIT", "OWNER"}), arraySchema = @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED, description = "The groups that stop a create, in this"
                + " order; empty when E05's answer allows one (NOT_STARTED alone included); ENDED when the agreement"
                + " has ended; NO_AGREEMENT when E05 gives no answer"))
        List<String> reasonGroups) {
}
