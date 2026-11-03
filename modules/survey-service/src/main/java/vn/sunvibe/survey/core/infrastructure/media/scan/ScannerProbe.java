package vn.sunvibe.survey.core.infrastructure.media.scan;

import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner.ScanVerdict;
import vn.sunvibe.survey.core.application.shared.external.media.ScannerSilentException;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The scanner probe of survey-media spec A-18, shared by the adapters: PING, signatures not older than 48 hours, OK on
 * the clean sample, FOUND (not a limit) on EICAR. A scanner that could not be reached or did not answer is INCOMPLETE;
 * one that answered wrongly is WRONG_ANSWER. Nothing is logged.
 */
final class ScannerProbe {

    static final Duration MAX_SIGNATURE_AGE = Duration.ofHours(48);

    private ScannerProbe() {
    }

    static ProbeResult evaluate(BooleanSupplier ping, Supplier<Instant> signatureDate,
                                Function<InputStream, ScanVerdict> scan, Instant now) {
        try {
            if (!ping.getAsBoolean()) {
                return ProbeResult.WRONG_ANSWER;
            }
            Instant signatures = signatureDate.get();
            if (signatures == null || signatures.isBefore(now.minus(MAX_SIGNATURE_AGE))) {
                return ProbeResult.WRONG_ANSWER;
            }
            if (scan.apply(new ByteArrayInputStream(ScanSamples.cleanJpeg())).kind() != ScanVerdict.Kind.CLEAN) {
                return ProbeResult.WRONG_ANSWER;
            }
            if (scan.apply(new ByteArrayInputStream(ScanSamples.eicar())).kind() != ScanVerdict.Kind.FOUND) {
                return ProbeResult.WRONG_ANSWER;
            }
            return ProbeResult.PASSED;
        } catch (ScannerSilentException e) {
            return ProbeResult.INCOMPLETE;
        }
    }
}
