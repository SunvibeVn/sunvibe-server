package vn.sunvibe.shared.web;

import java.util.regex.Pattern;

/**
 * The two survey photo content endpoints (survey-media spec A-13): the raw content upload and the variant read. Their
 * 401, 403 and 404 answers carry {@code Connection: close} and no EntityManager stays open while their bytes move. In
 * shared-service so the app's security and web configuration use it without depending on survey-service.
 */
public final class MediaContentPaths {

    /** {@code PUT /api/v1/staff/survey-media/{mediaId}/content}. */
    public static final String UPLOAD_PATTERN = "/api/v1/staff/survey-media/*/content";
    /** {@code GET /api/v1/staff/survey-media/{mediaId}/content/{variant}}. */
    public static final String READ_PATTERN = "/api/v1/staff/survey-media/*/content/*";

    private static final Pattern CONTENT_PATH = Pattern.compile("^/api/v1/staff/survey-media/[^/]+/content(/[^/]+)?/?$");

    private MediaContentPaths() {
    }

    /** True for a request URI of one of the two content endpoints. */
    public static boolean isContentPath(String requestUri) {
        return requestUri != null && CONTENT_PATH.matcher(requestUri).matches();
    }
}
