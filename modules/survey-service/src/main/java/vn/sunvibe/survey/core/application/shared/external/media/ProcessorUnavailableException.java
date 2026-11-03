package vn.sunvibe.survey.core.application.shared.external.media;

import java.io.Serial;

/**
 * The processor could not be reached, broke the channel or answered nothing usable: infrastructure, retried by the
 * job's attempts (survey-media spec A-7). The message is a code only.
 */
public class ProcessorUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProcessorUnavailableException(String code) {
        super(code);
    }
}
