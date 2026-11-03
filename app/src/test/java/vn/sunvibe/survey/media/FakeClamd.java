package vn.sunvibe.survey.media;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A scripted {@code clamd} speaking the z-commands of the clamd protocol (PING, VERSION, INSTREAM) on 127.0.0.1, for
 * the ClamAV adapter's protocol tests (survey-media spec A-15). Each INSTREAM answers by the current script; the
 * received bytes are kept so a test can check what was streamed.
 */
public final class FakeClamd implements AutoCloseable {

	/** How the fake answers an INSTREAM. */
	public enum Script {
		/** {@code stream: OK}. */
		OK,
		/** {@code stream: <name> FOUND} with the configured name. */
		FOUND,
		/** {@code stream: Heuristics.Limits.Exceeded.MaxScanTime FOUND}. */
		LIMIT,
		/** {@code INSTREAM size limit exceeded. ERROR}. */
		ERROR_LINE,
		/** Reads the stream and never answers. */
		NO_ANSWER,
		/** Closes the connection before answering. */
		RESET,
		/** OK, but FOUND for a stream holding EICAR (a well-behaved clamd for the probe). */
		OK_THEN_FOUND_FOR_EICAR,
		/** OK, but a scan-limit FOUND for a stream holding EICAR. */
		OK_THEN_LIMIT_FOR_EICAR
	}

	private final ServerSocket server;
	private final ExecutorService threads = Executors.newCachedThreadPool();
	private volatile Script script = Script.OK;
	private volatile String foundName = "Eicar-Test-Signature";
	private volatile String version = "ClamAV 1.4.1/27450/Mon Oct 30 08:24:01 2026";
	private volatile boolean refusePing;
	private final ConcurrentLinkedQueue<byte[]> streams = new ConcurrentLinkedQueue<>();

	public FakeClamd() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		threads.submit(this::acceptLoop);
	}

	public int port() {
		return server.getLocalPort();
	}

	public FakeClamd script(Script next) {
		script = next;
		return this;
	}

	public FakeClamd foundName(String name) {
		foundName = name;
		return this;
	}

	public FakeClamd version(String text) {
		version = text;
		return this;
	}

	public FakeClamd refusePing(boolean refuse) {
		refusePing = refuse;
		return this;
	}

	/** The bodies received by INSTREAM, oldest first. */
	public java.util.List<byte[]> streams() {
		return java.util.List.copyOf(streams);
	}

	private void acceptLoop() {
		while (!server.isClosed()) {
			try {
				Socket socket = server.accept();
				threads.submit(() -> serve(socket));
			} catch (IOException e) {
				return;
			}
		}
	}

	private void serve(Socket socket) {
		try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
			String command = readCommand(in);
			switch (command) {
				case "zPING" -> {
					if (!refusePing) {
						reply(out, "PONG");
					}
				}
				case "zVERSION" -> reply(out, version);
				case "zINSTREAM" -> instream(new DataInputStream(in), out, socket);
				default -> reply(out, "UNKNOWN COMMAND");
			}
		} catch (IOException ignored) {
			// the client went away
		}
	}

	private void instream(DataInputStream in, OutputStream out, Socket socket) throws IOException {
		Script current = script;
		if (current == Script.RESET) {
			socket.setSoLinger(true, 0);
			return;
		}
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		while (true) {
			int length = in.readInt();
			if (length == 0) {
				break;
			}
			byte[] chunk = in.readNBytes(length);
			body.write(chunk);
		}
		byte[] received = body.toByteArray();
		streams.add(received);
		switch (current) {
			case OK -> reply(out, "stream: OK");
			case FOUND -> reply(out, "stream: " + foundName + " FOUND");
			case LIMIT -> reply(out, "stream: Heuristics.Limits.Exceeded.MaxScanTime FOUND");
			case ERROR_LINE -> reply(out, "INSTREAM size limit exceeded. ERROR");
			case NO_ANSWER -> sleepQuietly(10_000);
			case OK_THEN_FOUND_FOR_EICAR -> reply(out, new String(received, StandardCharsets.US_ASCII)
					.contains("EICAR") ? "stream: Eicar-Test-Signature FOUND" : "stream: OK");
			case OK_THEN_LIMIT_FOR_EICAR -> reply(out, new String(received, StandardCharsets.US_ASCII)
					.contains("EICAR") ? "stream: Heuristics.Limits.Exceeded.MaxScanSize FOUND" : "stream: OK");
			default -> reply(out, "stream: OK");
		}
	}

	private static String readCommand(InputStream in) throws IOException {
		ByteArrayOutputStream command = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) != -1 && b != 0) {
			command.write(b);
		}
		return command.toString(StandardCharsets.US_ASCII);
	}

	private static void reply(OutputStream out, String line) throws IOException {
		out.write((line + "\0").getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void close() throws IOException {
		server.close();
		threads.shutdownNow();
	}
}
