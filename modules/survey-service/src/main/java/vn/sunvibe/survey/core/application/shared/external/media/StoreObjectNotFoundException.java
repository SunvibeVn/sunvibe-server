package vn.sunvibe.survey.core.application.shared.external.media;

import java.io.Serial;

/**
 * The key does not exist in the store (survey-media spec 5.1): a content GET answers 409 MEDIA_NOT_AVAILABLE with an
 * ids-only error log; the worker fails a missing source at once (A-7). Never a reason for a probe.
 */
public class StoreObjectNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StoreObjectNotFoundException() {
        super("STORE_NOT_FOUND");
    }
}
