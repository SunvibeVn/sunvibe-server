package vn.sunvibe.survey.core.infrastructure.media.scan;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The scanner probe's two samples (survey-media spec A-18): the EICAR test string, built in memory at run time (the
 * source and class files hold it reversed, so no file of the build is itself a test signature), and a fixed clean
 * JPEG encoded at start.
 */
public final class ScanSamples {

    private static final String EICAR_REVERSED =
            "*H+H$!ELIF-TSET-SURIVITNA-DRADNATS-RACIE$}7)CC7)^P(45XZP\\4[PA@%P!O5X";
    private static final byte[] CLEAN_JPEG = encodeCleanJpeg();

    private ScanSamples() {
    }

    /** The EICAR anti-malware test file, a fresh copy each time. */
    public static byte[] eicar() {
        return new StringBuilder(EICAR_REVERSED).reverse().toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** A fixed 8 x 8 grey JPEG that every scanner answers OK. */
    public static byte[] cleanJpeg() {
        return CLEAN_JPEG.clone();
    }

    private static byte[] encodeCleanJpeg() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                image.setRGB(x, y, 0x808080);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
