package vn.sunvibe.survey.core.application.shared.external.media;

import java.io.Serial;

/**
 * The scanner gave no answer (connection refused or reset, a timeout before any answer): infrastructure, never a
 * per-file condition or an expensive outcome (survey-media spec A-7); the job retries and a scanner probe is asked for.
 */
public class ScannerSilentException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ScannerSilentException() {
        super("SCANNER_SILENT");
    }
}
