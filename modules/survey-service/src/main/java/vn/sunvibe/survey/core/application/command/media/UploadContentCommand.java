package vn.sunvibe.survey.core.application.command.media;

/**
 * The headers of a photo content upload (survey-media spec 4.4 step 2, 7.1): the photo, {@code Content-Type},
 * {@code Content-Length} (-1 when missing) and {@code Content-Digest}. Checked by the use case before anything is read.
 */
public record UploadContentCommand(String mediaId, String contentTypeOrNull, long contentLength,
                                   String contentDigestOrNull) {
}
