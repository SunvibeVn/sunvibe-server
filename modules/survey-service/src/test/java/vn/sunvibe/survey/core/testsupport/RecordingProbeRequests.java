package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;

/** Counts the probe requests (survey-media spec A-18). */
public final class RecordingProbeRequests implements MediaProbeRequests {

    public int store;
    public int scanner;

    @Override
    public void requestStoreProbe() {
        store++;
    }

    @Override
    public void requestScannerProbe() {
        scanner++;
    }
}
