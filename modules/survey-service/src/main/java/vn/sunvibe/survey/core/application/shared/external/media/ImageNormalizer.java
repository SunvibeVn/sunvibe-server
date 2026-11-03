package vn.sunvibe.survey.core.application.shared.external.media;

import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * The isolated processor of ADR-011 decision 4 (survey-media spec 4.5, A-4, A-28): the API never runs a decoder. The
 * client reads each variant within its byte cap and the whole reply within 15 MiB, checks every JPEG's markers and SOF
 * dimensions itself, and takes the processor version from its own configuration, never from the reply.
 */
public interface ImageNormalizer {

    /**
     * The format from the source's bytes, run before the scan (spec 4.5 row 2); empty when unsupported.
     *
     * @throws ProcessorUnavailableException when the processor cannot be reached or answers nothing usable
     */
    Optional<SourceFormat> detect(InputStream source, long length);

    /**
     * Structure, bounds, decode and the three variants (spec 4.5 rows 4-8).
     *
     * @throws ProcessorUnavailableException when the processor cannot be reached or the channel breaks
     */
    NormalizeResult normalize(InputStream source, long length);

    /**
     * The processor probe of spec A-18: a fixed tiny JPEG normalized within a time limit, its output passing the caps
     * and checks. INCOMPLETE when the processor could not be reached or did not answer in time.
     */
    ProbeResult probe();

    /** The deployment's processor version stored on every variant (spec A-28). */
    String processorVersion();

    /** The outcome of a normalize run. */
    sealed interface NormalizeResult {

        /** Three checked variants and the source's composed dimensions. */
        record Normalized(int sourceWidth, int sourceHeight, List<NormalizedVariant> variants)
                implements NormalizeResult {
        }

        /** A content reason: the photo ends REJECTED. */
        record Rejected(MediaStatusReason reason) implements NormalizeResult {
        }

        /** The child exceeded a limit, crashed or was killed; twice is REJECTED UNREADABLE (spec A-7). */
        record LimitHit() implements NormalizeResult {
        }

        /** The output failed a cap or a check: fail closed, the photo ends FAILED (spec 4.5 last row, A-28). */
        record Failed(String code) implements NormalizeResult {
        }
    }

    /** One checked JPEG variant. The bytes are never printed. */
    record NormalizedVariant(VariantKind kind, int width, int height, byte[] jpeg) {

        @Override
        public boolean equals(Object other) {
            return other instanceof NormalizedVariant variant && kind == variant.kind && width == variant.width
                    && height == variant.height && java.util.Arrays.equals(jpeg, variant.jpeg);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(kind, width, height, java.util.Arrays.hashCode(jpeg));
        }

        @Override
        public String toString() {
            return "NormalizedVariant[" + kind + " " + width + "x" + height + ", " + jpeg.length + " bytes]";
        }
    }
}
