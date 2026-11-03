package vn.sunvibe.survey.core.infrastructure.media.processor;

import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.ProcessorUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.policy.media.VariantJpegCheck;
import vn.sunvibe.survey.core.infrastructure.media.scan.ScanSamples;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The channel to the processing container (docker/media-processor/PROTOCOL.md; survey-media spec 4.5, A-28): one
 * request per connection, bytes only. Each variant is read within its cap (FULL 12 MiB, PREVIEW 2 MiB, THUMBNAIL
 * 256 KiB) and the reply within 15 MiB, stopping at a cap; every JPEG then passes {@link VariantJpegCheck}. A crash, a
 * timeout or a broken frame is {@link ProcessorUnavailableException}. The processor version is this deployment's.
 */
public class IsolatedProcessorClient implements ImageNormalizer {

    static final long RESPONSE_CAP = 15L * 1024 * 1024;
    private static final byte[] MAGIC_REQUEST = "SVM1".getBytes(StandardCharsets.US_ASCII);
    private static final String MAGIC_REPLY = "SVR1";
    private static final int OP_DETECT = 0x01;
    private static final int OP_NORMALIZE = 0x02;
    private static final int OK = 0;
    private static final int REJECTED = 1;
    private static final int LIMIT_HIT = 2;
    private static final int FAILED = 3;

    private final String host;
    private final int port;
    private final String processorVersion;
    private final Duration connectAndDetectTimeout;
    private final Duration normalizeTimeout;

    public IsolatedProcessorClient(String host, int port, String processorVersion, Duration connectAndDetectTimeout,
                                   Duration normalizeTimeout) {
        this.host = host;
        this.port = port;
        this.processorVersion = processorVersion;
        this.connectAndDetectTimeout = connectAndDetectTimeout;
        this.normalizeTimeout = normalizeTimeout;
    }

    @Override
    public Optional<SourceFormat> detect(InputStream source, long length) {
        try (Socket socket = open(connectAndDetectTimeout)) {
            send(socket, OP_DETECT, source, length);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            Header header = readHeader(in);
            if (header.status() == OK) {
                try {
                    return Optional.of(SourceFormat.valueOf(header.code()));
                } catch (IllegalArgumentException e) {
                    throw new ProcessorUnavailableException("PROCESSOR_BAD_FORMAT");
                }
            }
            if (header.status() == REJECTED) {
                return Optional.empty();
            }
            throw new ProcessorUnavailableException("PROCESSOR_DETECT_" + header.status());
        } catch (IOException e) {
            throw new ProcessorUnavailableException("PROCESSOR_CHANNEL");
        }
    }

    @Override
    public NormalizeResult normalize(InputStream source, long length) {
        try (Socket socket = open(normalizeTimeout)) {
            send(socket, OP_NORMALIZE, source, length);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            Header header = readHeader(in);
            return switch (header.status()) {
                case OK -> readVariants(in, 4 + 2 + header.code().length());
                case REJECTED -> rejected(header.code());
                case LIMIT_HIT -> new NormalizeResult.LimitHit();
                case FAILED -> new NormalizeResult.Failed("PROCESSOR_" + safeCode(header.code()));
                default -> throw new ProcessorUnavailableException("PROCESSOR_REFUSED");
            };
        } catch (IOException e) {
            throw new ProcessorUnavailableException("PROCESSOR_CHANNEL");
        }
    }

    @Override
    public ProbeResult probe() {
        try {
            byte[] sample = ScanSamples.cleanJpeg();
            NormalizeResult result = normalize(new ByteArrayInputStream(sample), sample.length);
            return result instanceof NormalizeResult.Normalized ? ProbeResult.PASSED : ProbeResult.WRONG_ANSWER;
        } catch (ProcessorUnavailableException e) {
            return ProbeResult.INCOMPLETE;
        }
    }

    @Override
    public String processorVersion() {
        return processorVersion;
    }

    private NormalizeResult readVariants(DataInputStream in, long alreadyRead) throws IOException {
        long total = alreadyRead + 9;
        int sourceWidth = in.readInt();
        int sourceHeight = in.readInt();
        int count = in.readUnsignedByte();
        List<NormalizedVariant> variants = new ArrayList<>();
        Set<VariantKind> kinds = EnumSet.noneOf(VariantKind.class);
        for (int v = 0; v < count; v++) {
            int code = in.readUnsignedByte();
            int width = in.readInt();
            int height = in.readInt();
            long size = Integer.toUnsignedLong(in.readInt());
            total += 13;
            if (code < 1 || code > 3) {
                return new NormalizeResult.Failed("VARIANT_KIND");
            }
            VariantKind kind = VariantKind.values()[code - 1];
            if (total + size > RESPONSE_CAP) {
                return new NormalizeResult.Failed("RESPONSE_OVER_CAP");
            }
            if (size > kind.maxBytes()) {
                return new NormalizeResult.Failed("VARIANT_OVER_CAP");
            }
            byte[] jpeg = in.readNBytes((int) size);
            if (jpeg.length != size) {
                throw new EOFException("short variant");
            }
            total += size;
            kinds.add(kind);
            variants.add(new NormalizedVariant(kind, width, height, jpeg));
        }
        if (count != 3 || kinds.size() != 3) {
            return new NormalizeResult.Failed("VARIANT_COUNT");
        }
        // the caps were applied while reading; the markers and dimensions are checked once all is read
        for (NormalizedVariant variant : variants) {
            Optional<String> problem = VariantJpegCheck.check(variant.kind(), variant.jpeg(), variant.width(),
                    variant.height());
            if (problem.isPresent()) {
                return new NormalizeResult.Failed("VARIANT_" + problem.get());
            }
        }
        variants.sort((a, b) -> a.kind().compareTo(b.kind()));
        return new NormalizeResult.Normalized(sourceWidth, sourceHeight, List.copyOf(variants));
    }

    private static NormalizeResult rejected(String code) {
        try {
            MediaStatusReason reason = MediaStatusReason.valueOf(code);
            if (reason.isRejection() && reason != MediaStatusReason.MALWARE_DETECTED
                    && reason != MediaStatusReason.SCAN_LIMIT_EXCEEDED) {
                return new NormalizeResult.Rejected(reason);
            }
        } catch (IllegalArgumentException ignored) {
            // an unknown reason fails closed below
        }
        return new NormalizeResult.Failed("PROCESSOR_BAD_REASON");
    }

    private Socket open(Duration readTimeout) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), (int) connectAndDetectTimeout.toMillis());
            socket.setSoTimeout((int) readTimeout.toMillis());
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static void send(Socket socket, int op, InputStream source, long length) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(MAGIC_REQUEST);
        out.writeByte(op);
        out.writeInt((int) length);
        long sent = source.transferTo(out);
        if (sent != length) {
            throw new IOException("source length differs");
        }
        out.flush();
    }

    private static Header readHeader(DataInputStream in) throws IOException {
        byte[] magic = in.readNBytes(4);
        if (!MAGIC_REPLY.equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new EOFException("no reply");
        }
        int status = in.readUnsignedByte();
        String code = new String(in.readNBytes(in.readUnsignedByte()), StandardCharsets.US_ASCII);
        return new Header(status, code);
    }

    /** A processor code reduced to letters, digits and underscores, at most 30 characters. */
    private static String safeCode(String code) {
        String safe = code.replaceAll("[^A-Z0-9_]", "");
        return safe.substring(0, Math.min(30, safe.length()));
    }

    private record Header(int status, String code) {
    }
}
