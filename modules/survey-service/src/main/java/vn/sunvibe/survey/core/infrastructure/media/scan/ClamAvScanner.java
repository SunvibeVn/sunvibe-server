package vn.sunvibe.survey.core.infrastructure.media.scan;

import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.ScannerSilentException;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ClamAV {@code clamd} over TCP or a Unix socket (survey-media spec 4.5 scan row, A-7; ADR-011 decision 4). A scan is
 * one {@code zINSTREAM} with length-prefixed chunks: {@code stream: OK} is clean; FOUND is malware unless the name is
 * {@code Heuristics.Limits.Exceeded.*} (matched by prefix), which with every other error line about the stream is a
 * per-file condition; a refused, reset or timed-out connection before any answer is silence. The signature name is
 * dropped here: never returned, logged or stored.
 */
public class ClamAvScanner implements MalwareScanner {

    private static final int CHUNK = 64 * 1024;
    private static final int MAX_REPLY = 4096;
    private static final String LIMIT_PREFIX = "Heuristics.Limits.Exceeded";
    /** The date of VERSION without its weekday, which is not needed and is never checked. */
    private static final DateTimeFormatter VERSION_DATE = DateTimeFormatter.ofPattern("MMM d HH:mm:ss yyyy",
            Locale.ENGLISH);

    private final String host;
    private final int port;
    private final String socketPathOrNull;
    private final Duration timeout;

    public ClamAvScanner(String host, int port, String socketPathOrNull, Duration timeout) {
        this.host = host;
        this.port = port;
        this.socketPathOrNull = socketPathOrNull;
        this.timeout = timeout;
    }

    @Override
    public ScanVerdict scan(InputStream content) {
        return verdict(exchange("zINSTREAM", out -> {
            byte[] buffer = new byte[CHUNK];
            int read;
            while ((read = content.read(buffer)) > 0) {
                out.writeInt(read);
                out.write(buffer, 0, read);
            }
            out.writeInt(0);
        }));
    }

    @Override
    public ProbeResult probe(Instant now) {
        return ScannerProbe.evaluate(this::ping, this::signatureDate, this::scan, now);
    }

    /** True on PONG; false on another answer. */
    public boolean ping() {
        return "PONG".equals(exchange("zPING", out -> { }));
    }

    /** The signature date of {@code VERSION} ({@code ClamAV 1.4.1/27450/Mon Oct 30 08:24:01 2026}); null if unreadable. */
    public Instant signatureDate() {
        String version = exchange("zVERSION", out -> { });
        int slash = version.lastIndexOf('/');
        if (slash < 0) {
            return null;
        }
        try {
            String date = version.substring(slash + 1).trim().replaceAll("\\s+", " ");
            String withoutWeekday = date.substring(date.indexOf(' ') + 1);
            return LocalDateTime.parse(withoutWeekday, VERSION_DATE).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static ScanVerdict verdict(String reply) {
        if ("stream: OK".equals(reply)) {
            return ScanVerdict.CLEAN;
        }
        if (reply.startsWith("stream: ") && reply.endsWith(" FOUND")) {
            String name = reply.substring("stream: ".length(), reply.length() - " FOUND".length());
            return name.startsWith(LIMIT_PREFIX) ? ScanVerdict.perFileCondition("SCAN_LIMIT") : ScanVerdict.FOUND;
        }
        return ScanVerdict.perFileCondition("SCAN_ERROR");
    }

    /** Sends one z-command (and its body) and reads one NUL-terminated reply; silence when none comes. */
    private String exchange(String command, Body body) {
        try (Connection connection = connect()) {
            DataOutputStream out = new DataOutputStream(connection.out());
            out.write((command + "\0").getBytes(StandardCharsets.US_ASCII));
            try {
                body.write(out);
                out.flush();
            } catch (IOException e) {
                // clamd may answer (a size limit) and close while the body is still being sent: read that answer
            }
            String reply = readReply(connection.in());
            if (reply.isEmpty()) {
                throw new ScannerSilentException();
            }
            return reply;
        } catch (IOException e) {
            throw new ScannerSilentException();
        }
    }

    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0 && reply.size() < MAX_REPLY) {
            reply.write(b);
        }
        return reply.toString(StandardCharsets.US_ASCII).trim();
    }

    private Connection connect() throws IOException {
        if (socketPathOrNull != null) {
            SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socketPathOrNull));
            CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // already closed
                }
            });
            return new Connection(Channels.newInputStream(channel), Channels.newOutputStream(channel), channel);
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
        socket.setSoTimeout((int) timeout.toMillis());
        return new Connection(socket.getInputStream(), socket.getOutputStream(), socket);
    }

    @FunctionalInterface
    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private record Connection(InputStream in, OutputStream out, AutoCloseable resource) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            try {
                resource.close();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
