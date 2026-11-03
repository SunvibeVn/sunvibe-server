package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.media.VariantKind;

import java.util.Optional;

/**
 * The Java check of a processed variant before it is stored (survey-media spec 4.5 last row, A-28): the only markers
 * are SOI, APP0/JFIF with a zero-size thumbnail (no JFXX), DQT, exactly one SOF, DHT, DRI, SOS, RST0-RST7 and EOI as
 * the last bytes; the SOF dimensions equal the processor's report and fit the variant's bounds (A-4). Any other
 * marker (EXIF, XMP, ICC, IPTC, Adobe, comments, MPF images) fails the variant: fail closed.
 */
public final class VariantJpegCheck {

    private VariantJpegCheck() {
    }

    /** Empty when the variant passes; otherwise the problem: SOI, EOI, MARKER, JFIF, SOF_COUNT, DIMENSIONS, BOUNDS. */
    public static Optional<String> check(VariantKind kind, byte[] jpeg, int reportedWidth, int reportedHeight) {
        int n = jpeg.length;
        if (n < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return Optional.of("SOI");
        }
        if ((jpeg[n - 2] & 0xFF) != 0xFF || (jpeg[n - 1] & 0xFF) != 0xD9) {
            return Optional.of("EOI");
        }
        int sofCount = 0;
        int width = -1;
        int height = -1;
        int i = 2;
        while (i < n) {
            if ((jpeg[i] & 0xFF) != 0xFF || i + 1 >= n) {
                return Optional.of("MARKER");
            }
            int marker = jpeg[i + 1] & 0xFF;
            if (marker == 0xD9) {
                if (i + 2 != n) {
                    return Optional.of("EOI");
                }
                break;
            }
            if (marker >= 0xD0 && marker <= 0xD7) {
                i += 2;
                continue;
            }
            if (i + 4 > n) {
                return Optional.of("MARKER");
            }
            int length = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
            int segment = i + 4;
            if (length < 2 || i + 2 + length > n) {
                return Optional.of("MARKER");
            }
            switch (marker) {
                case 0xE0 -> {
                    if (!jfifWithoutThumbnail(jpeg, segment, length - 2)) {
                        return Optional.of("JFIF");
                    }
                }
                case 0xDB, 0xC4, 0xDD -> {
                    // quantization, Huffman tables, restart interval
                }
                case 0xC0, 0xC1, 0xC2 -> {
                    sofCount++;
                    if (sofCount > 1) {
                        return Optional.of("SOF_COUNT");
                    }
                    height = ((jpeg[segment + 1] & 0xFF) << 8) | (jpeg[segment + 2] & 0xFF);
                    width = ((jpeg[segment + 3] & 0xFF) << 8) | (jpeg[segment + 4] & 0xFF);
                }
                case 0xDA -> {
                    i = afterEntropyData(jpeg, i + 2 + length);
                    continue;
                }
                default -> {
                    return Optional.of("MARKER");
                }
            }
            i += 2 + length;
        }
        if (sofCount != 1) {
            return Optional.of("SOF_COUNT");
        }
        if (width != reportedWidth || height != reportedHeight) {
            return Optional.of("DIMENSIONS");
        }
        if (!kind.fits(width, height)) {
            return Optional.of("BOUNDS");
        }
        return Optional.empty();
    }

    /** APP0 is JFIF with a zero-size thumbnail; JFXX (a thumbnail extension) is refused. */
    private static boolean jfifWithoutThumbnail(byte[] jpeg, int start, int size) {
        if (size < 14 || jpeg[start] != 'J' || jpeg[start + 1] != 'F' || jpeg[start + 2] != 'I'
                || jpeg[start + 3] != 'F' || jpeg[start + 4] != 0) {
            return false;
        }
        return jpeg[start + 12] == 0 && jpeg[start + 13] == 0 && size == 14;
    }

    /** The offset of the next marker after entropy-coded data (stuffed 0xFF00 and RST markers stay inside). */
    private static int afterEntropyData(byte[] jpeg, int start) {
        int i = start;
        while (i + 1 < jpeg.length) {
            if ((jpeg[i] & 0xFF) == 0xFF) {
                int next = jpeg[i + 1] & 0xFF;
                if (next != 0x00 && !(next >= 0xD0 && next <= 0xD7)) {
                    return i;
                }
                i += 2;
                continue;
            }
            i++;
        }
        return jpeg.length;
    }
}
