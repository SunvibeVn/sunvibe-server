package vn.sunvibe.survey.core.domain.policy.media;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Java check of every variant before it is stored (survey-media spec 4.5 last row, A-28): only SOI, APP0/JFIF with
 * a zero-size thumbnail, DQT, exactly one SOF, DHT, DRI, SOS, RST0-RST7 and EOI as the last bytes; the SOF dimensions
 * equal the processor's report and stay within the variant's bounds.
 */
class VariantJpegCheckTest {

    @Test
    void cleanBaseline_passes() {
        assertEquals(Optional.empty(), VariantJpegCheck.check(VariantKind.FULL, jpeg(new Jpeg()), 640, 480));
    }

    @Test
    void restartMarkersAndDri_pass() {
        Jpeg jpeg = new Jpeg();
        jpeg.dri = true;
        jpeg.restarts = true;

        assertEquals(Optional.empty(), VariantJpegCheck.check(VariantKind.FULL, jpeg(jpeg), 640, 480));
    }

    @Test
    void metadataSegments_areRefused() {
        for (int marker : new int[] {0xE1, 0xE2, 0xED, 0xEE, 0xFE, 0xE5}) {
            Jpeg jpeg = new Jpeg();
            jpeg.extraSegment = marker;
            assertEquals(Optional.of("MARKER"), VariantJpegCheck.check(VariantKind.FULL, jpeg(jpeg), 640, 480),
                    Integer.toHexString(marker));
        }
    }

    @Test
    void jfifThumbnailOrJfxx_isRefused() {
        Jpeg withThumbnail = new Jpeg();
        withThumbnail.jfifThumbnail = true;
        Jpeg jfxx = new Jpeg();
        jfxx.jfxx = true;

        assertEquals(Optional.of("JFIF"), VariantJpegCheck.check(VariantKind.FULL, jpeg(withThumbnail), 640, 480));
        assertEquals(Optional.of("JFIF"), VariantJpegCheck.check(VariantKind.FULL, jpeg(jfxx), 640, 480));
    }

    @Test
    void twoSofs_areRefused() {
        Jpeg jpeg = new Jpeg();
        jpeg.secondSof = true;

        assertEquals(Optional.of("SOF_COUNT"), VariantJpegCheck.check(VariantKind.FULL, jpeg(jpeg), 640, 480));
    }

    @Test
    void eoiNotLast_isRefused() {
        Jpeg jpeg = new Jpeg();
        jpeg.trailing = "trailing".getBytes(StandardCharsets.US_ASCII);

        assertEquals(Optional.of("EOI"), VariantJpegCheck.check(VariantKind.FULL, jpeg(jpeg), 640, 480));
    }

    @Test
    void missingSoiOrEoi_isRefused() {
        byte[] bytes = jpeg(new Jpeg());

        assertEquals(Optional.of("SOI"), VariantJpegCheck.check(VariantKind.FULL,
                java.util.Arrays.copyOfRange(bytes, 2, bytes.length), 640, 480));
        assertEquals(Optional.of("EOI"), VariantJpegCheck.check(VariantKind.FULL,
                java.util.Arrays.copyOf(bytes, bytes.length - 2), 640, 480));
    }

    @Test
    void dimensionMismatch_isRefused() {
        assertEquals(Optional.of("DIMENSIONS"), VariantJpegCheck.check(VariantKind.FULL, jpeg(new Jpeg()), 641, 480));
    }

    @Test
    void dimensionsOverTheVariantBounds_areRefused() {
        Jpeg wide = new Jpeg();
        wide.width = 5000;
        Jpeg thumbnail = new Jpeg();
        thumbnail.width = 401;
        thumbnail.height = 300;

        assertEquals(Optional.of("BOUNDS"), VariantJpegCheck.check(VariantKind.FULL, jpeg(wide), 5000, 480));
        assertEquals(Optional.of("BOUNDS"), VariantJpegCheck.check(VariantKind.THUMBNAIL, jpeg(thumbnail), 401,
                300));
        assertEquals(Optional.empty(), VariantJpegCheck.check(VariantKind.PREVIEW, jpeg(new Jpeg()), 640, 480));
    }

    /** A minimal synthetic JPEG, segment by segment. */
    private static final class Jpeg {
        int width = 640;
        int height = 480;
        boolean jfifThumbnail;
        boolean jfxx;
        boolean dri;
        boolean restarts;
        boolean secondSof;
        int extraSegment;
        byte[] trailing = new byte[0];
    }

    private static byte[] jpeg(Jpeg spec) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        byte[] jfif = {'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, (byte) (spec.jfifThumbnail ? 1 : 0),
                (byte) (spec.jfifThumbnail ? 1 : 0)};
        segment(out, 0xE0, spec.jfifThumbnail ? concat(jfif, new byte[3]) : jfif);
        if (spec.jfxx) {
            segment(out, 0xE0, new byte[] {'J', 'F', 'X', 'X', 0, 0x10});
        }
        if (spec.extraSegment != 0) {
            segment(out, spec.extraSegment, "Exif\0\0data".getBytes(StandardCharsets.US_ASCII));
        }
        segment(out, 0xDB, new byte[65]);
        byte[] sof = {8, (byte) (spec.height >> 8), (byte) spec.height, (byte) (spec.width >> 8), (byte) spec.width, 1,
                1, 0x11, 0};
        segment(out, 0xC0, sof);
        if (spec.secondSof) {
            segment(out, 0xC0, sof);
        }
        segment(out, 0xC4, new byte[] {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        if (spec.dri) {
            segment(out, 0xDD, new byte[] {0, 4});
        }
        segment(out, 0xDA, new byte[] {1, 1, 0, 0, 0x3F, 0});
        out.writeBytes(new byte[] {0x12, 0x34, (byte) 0xFF, 0x00, 0x56});
        if (spec.restarts) {
            out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD0, 0x11, (byte) 0xFF, (byte) 0xD7, 0x22});
        }
        out.write(0xFF);
        out.write(0xD9);
        out.writeBytes(spec.trailing);
        return out.toByteArray();
    }

    private static void segment(ByteArrayOutputStream out, int marker, byte[] payload) {
        out.write(0xFF);
        out.write(marker);
        int length = payload.length + 2;
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(payload);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] joined = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, joined, a.length, b.length);
        return joined;
    }
}
