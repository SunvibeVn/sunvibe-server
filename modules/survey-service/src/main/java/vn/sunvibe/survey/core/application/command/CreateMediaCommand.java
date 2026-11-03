package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/**
 * Create a survey photo record before its bytes (survey-media spec 4.4 step 1, 7.2 {@code MediaCreateRequest}): the
 * client's upload key, the placement, the category, an optional caption, the declared size and the hex SHA-256 of the
 * file. Every value is checked by the use case, on its JSON path.
 */
public record CreateMediaCommand(String checklistId, String uploadKey, String spaceIdOrNull, EvidenceCategory category,
                                 String captionOrNull, Long sizeBytes, String contentSha256) {

    /** Staff text and the file hash: never print them. */
    @Override
    public String toString() {
        return "CreateMediaCommand[" + checklistId + "]";
    }
}
