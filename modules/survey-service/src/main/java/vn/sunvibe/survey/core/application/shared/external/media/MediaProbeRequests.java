package vn.sunvibe.survey.core.application.shared.external.media;

/**
 * Asks for an immediate health probe (survey-media spec A-18): after a store-side failure (5xx, timeout, connection
 * error; never a 404, a 412 or a client abort) and after a scanner gave no answer (A-7). Non-blocking; at most one
 * request per kind is pending, and the probe runner takes it.
 */
public interface MediaProbeRequests {

    void requestStoreProbe();

    void requestScannerProbe();
}
