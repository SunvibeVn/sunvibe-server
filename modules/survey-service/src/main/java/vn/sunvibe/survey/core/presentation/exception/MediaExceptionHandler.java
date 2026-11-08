package vn.sunvibe.survey.core.presentation.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.web.MediaContentPaths;
import vn.sunvibe.survey.core.application.exception.media.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentTooLargeException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaReferencedException;
import vn.sunvibe.survey.core.application.exception.media.MediaMethodNotAllowedException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaServingDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaRequestMalformedException;
import vn.sunvibe.survey.core.application.exception.media.MediaServerBusyException;
import vn.sunvibe.survey.core.application.exception.media.MediaStoreUnavailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.exception.media.MediaLimitReachedException;
import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadKeyReusedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadStateException;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;

import java.util.List;

import static vn.sunvibe.survey.core.presentation.exception.CommonExceptionHandler.conflict;
import static vn.sunvibe.survey.core.presentation.exception.CommonExceptionHandler.payloadTooLarge;

/**
 * The bodies of the survey-media refusals (survey-media spec 7.3). Not an advice: {@link SurveyExceptionHandler} is the
 * one survey advice and keeps every {@code @ExceptionHandler} method in its order, so Spring picks the same handler as
 * before (clean-up plan amendment 2026-11-08, SV-272); each method here answers for the handler of the same name.
 */
final class MediaExceptionHandler {

    private MediaExceptionHandler() {
    }

    /** survey-media spec A-11: a JSON body above 16 KiB, stopped while it was read. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaBodyTooLarge(MediaBodyTooLargeException e) {
        return payloadTooLarge();
    }

    /** survey-media spec A-1: uploads are off. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadDisabled(MediaUploadDisabledException e) {
        return conflict(ErrorCode.MEDIA_UPLOAD_DISABLED, e.getMessage());
    }

    /** survey-media spec A-16: the matching photo's id in {@code allowedValues} of {@code contentSha256}. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaContentIdentity(
            MediaContentIdentityException e) {
        String code = e.getKind() == IdentityVerdict.Kind.FROM_OTHER_VISIT ? ErrorCode.MEDIA_FROM_OTHER_VISIT
                : ErrorCode.MEDIA_DUPLICATE_CONTENT;
        List<String> matching = e.getMatchingMediaIdOrNull() == null ? List.of()
                : List.of(e.getMatchingMediaIdOrNull());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(code, e.getMessage(),
                List.of(ValidationError.withAllowed("contentSha256", e.getMessage(), matching))));
    }

    /** survey-media BR-6, A-3: field {@code selected} or {@code pending}. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaLimitReached(MediaLimitReachedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.MEDIA_LIMIT_REACHED,
                e.getMessage(), List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 4.4 step 1: the upload key names another body. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadKeyReused(
            MediaUploadKeyReusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                e.getMessage(), List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 7.3: 429 on the field, {@code Retry-After} always. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaRateLimited(MediaRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(ErrorResponse.of(ErrorCode.RATE_LIMITED, e.getMessage(),
                        List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec A-18: retry the same request later; no {@code Retry-After} (spec 7.3). */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaIntakePaused(MediaIntakePausedException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(ErrorCode.MEDIA_INTAKE_PAUSED, e.getMessage(), List.of()));
    }

    /** survey-media spec 5.4 content (1), (12), 4.4 step 2: 415 on Content-Type, else 400 on the field. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaRequestMalformed(
            MediaRequestMalformedException e) {
        return ResponseEntity.status(e.isUnsupportedMediaType() ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                : HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, e.getMessage(),
                List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 4.4 step 2: the body ended early, failed or passed its deadline; nothing is stored. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaBodyIncomplete(MediaBodyIncompleteException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST,
                e.getMessage(), List.of(ValidationError.of("body", e.getMessage()))));
    }

    /** survey-media spec A-2: a declared length above 20 MiB. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaContentTooLarge(MediaContentTooLargeException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE, e.getMessage(), List.of()));
    }

    /**
     * survey-media spec 4.3: one 404 body for a missing photo and one of a hidden lead; on a content path it closes the
     * connection (A-13), since the body was not read.
     */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaNotFound(MediaNotFoundException e,
                                                                             HttpServletRequest request) {
        ResponseEntity.BodyBuilder answer = ResponseEntity.status(HttpStatus.NOT_FOUND);
        if (MediaContentPaths.isContentPath(request.getRequestURI())) {
            answer.header(HttpHeaders.CONNECTION, "close");
        }
        return answer.body(ErrorResponse.of(ErrorCode.NOT_FOUND, e.getMessage(), List.of()));
    }

    /** survey-media spec 5.4 content (8): field {@code uploadedBy} or {@code status}. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaNotAvailable(MediaNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.MEDIA_NOT_AVAILABLE,
                e.getMessage(), List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 4.7, 5.4 removal (8). */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaReferenced(MediaReferencedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.MEDIA_REFERENCED,
                e.getMessage(), List.of()));
    }

    /** survey-media spec 5.4 content (9). */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadState(MediaUploadStateException e) {
        String code = switch (e.getKind()) {
            case CONTENT_ALREADY_RECEIVED -> ErrorCode.MEDIA_CONTENT_ALREADY_RECEIVED;
            case UPLOAD_IN_PROGRESS -> ErrorCode.MEDIA_UPLOAD_IN_PROGRESS;
            case UPLOAD_EXPIRED -> ErrorCode.MEDIA_UPLOAD_EXPIRED;
        };
        return conflict(code, e.getMessage());
    }

    /** survey-media spec A-3, A-25: 503 with {@code Retry-After}. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaServerBusy(MediaServerBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(ErrorResponse.of(ErrorCode.MEDIA_SERVER_BUSY, e.getMessage(), List.of()));
    }

    /** survey-media spec A-24: serving is off; {@code Retry-After}. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaServingDisabled(MediaServingDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(MediaServingDisabledException.RETRY_AFTER_SECONDS))
                .body(ErrorResponse.of(ErrorCode.MEDIA_SERVING_DISABLED, e.getMessage(), List.of()));
    }

    /** survey-media spec 7.1, 7.3: the content read is GET only; HEAD 405 with {@code Allow: GET}. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaMethodNotAllowed(
            MediaMethodNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "GET")
                .body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage(), List.of()));
    }

    /** survey-media spec 4.4 step 2, A-18: the same request may be sent again; no {@code Retry-After} (7.3). */
    static ResponseEntity<ErrorResponse<ValidationError>> handleMediaStoreUnavailable(
            MediaStoreUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(ErrorCode.MEDIA_STORE_UNAVAILABLE, e.getMessage(), List.of()));
    }
}
