package vn.sunvibe.survey.core.application.shared;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import vn.sunvibe.survey.core.application.command.UploadContentCommand;
import vn.sunvibe.survey.core.application.exception.MediaContentTooLargeException;
import vn.sunvibe.survey.core.application.exception.MediaRequestMalformedException;
import vn.sunvibe.survey.core.domain.policy.media.MediaFileRules;

/**
 * The header checks of a content upload, decided before anything is read (survey-media spec 5.4 content (1), A-5):
 * {@code Content-Type: application/octet-stream} (else 415), a {@code Content-Length} and an RFC 9530
 * {@code Content-Digest} with a {@code sha-256} member (else 400), and at most 20 MiB (else 413).
 */
public final class MediaContentHeaders {

    private static final String OCTET_STREAM = "application/octet-stream";
    private static final int SHA256_BYTES = 32;

    private MediaContentHeaders() {
    }

    /** The declared length and the digest as lower-case hex. */
    public record ContentHeaders(long length, String sha256Hex) {

        /** The digest is not logged. */
        @Override
        public String toString() {
            return "ContentHeaders[" + length + " bytes]";
        }
    }

    public static ContentHeaders check(UploadContentCommand command) {
        if (!isOctetStream(command.contentTypeOrNull())) {
            throw new MediaRequestMalformedException("Content-Type", true);
        }
        if (command.contentLength() < 0) {
            throw new MediaRequestMalformedException("Content-Length", false);
        }
        String sha256 = sha256(command.contentDigestOrNull());
        if (sha256 == null) {
            throw new MediaRequestMalformedException("Content-Digest", false);
        }
        if (command.contentLength() > MediaFileRules.MAX_FILE_BYTES) {
            throw new MediaContentTooLargeException();
        }
        return new ContentHeaders(command.contentLength(), sha256);
    }

    private static boolean isOctetStream(String contentType) {
        if (contentType == null) {
            return false;
        }
        int parameters = contentType.indexOf(';');
        String type = (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim();
        return OCTET_STREAM.equals(type.toLowerCase(Locale.ROOT));
    }

    /** The {@code sha-256} member of the dictionary as hex, or null when missing or malformed. */
    private static String sha256(String digest) {
        if (digest == null) {
            return null;
        }
        for (String member : digest.split(",")) {
            int equals = member.indexOf('=');
            if (equals < 0 || !"sha-256".equals(member.substring(0, equals).trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            String value = member.substring(equals + 1).trim();
            if (value.length() < 2 || value.charAt(0) != ':' || value.charAt(value.length() - 1) != ':') {
                return null;
            }
            try {
                byte[] bytes = Base64.getDecoder().decode(value.substring(1, value.length() - 1));
                return bytes.length == SHA256_BYTES ? HexFormat.of().formatHex(bytes) : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
