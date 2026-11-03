package vn.sunvibe.survey.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

/**
 * A scripted processing container speaking docker/media-processor/PROTOCOL.md on 127.0.0.1 (survey-media spec A-15,
 * A-28; plan Task A10): well-formed variants, or a variant over its cap, a response over 15 MiB, a FULL whose SOF
 * says 5000 px, a crash (the connection closes), a slow answer, or a scripted status.
 */
public final class FakeProcessor implements AutoCloseable {

	/** What the next NORMALIZE answers. */
	public enum Script {
		/** Three valid JPEG variants of 1200 x 900, 1200 x 900 and 400 x 300. */
		VALID,
		/** A FULL variant that declares 12 MiB + 1 bytes. */
		FULL_OVER_CAP,
		/** A PREVIEW variant that declares 2 MiB + 1 bytes. */
		PREVIEW_OVER_CAP,
		/** A THUMBNAIL variant that declares 256 KiB + 1 bytes. */
		THUMBNAIL_OVER_CAP,
		/** Variants under their caps that sum to more than 15 MiB. */
		RESPONSE_OVER_CAP,
		/** A FULL whose JPEG SOF and report say 5000 px wide. */
		FULL_SOF_5000,
		/** Closes the connection without an answer. */
		CRASH,
		/** Answers after 5 seconds. */
		SLOW,
		/** Answers REJECTED ANIMATED. */
		REJECTED_ANIMATED,
		/** Answers LIMIT_HIT. */
		LIMIT_HIT
	}

	private final ServerSocket server;
	private final ExecutorService threads = Executors.newCachedThreadPool();
	private volatile Script script = Script.VALID;

	public FakeProcessor() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		threads.submit(this::acceptLoop);
	}

	public int port() {
		return server.getLocalPort();
	}

	public FakeProcessor script(Script next) {
		script = next;
		return this;
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
		try (socket) {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			in.readNBytes(4);
			int op = in.readUnsignedByte();
			int length = in.readInt();
			in.readNBytes(length);
			Script current = script;
			if (current == Script.CRASH) {
				return;
			}
			if (current == Script.SLOW) {
				Thread.sleep(5000);
			}
			if (op == 0x01) {
				header(out, 0, "JPEG");
				return;
			}
			switch (current) {
				case REJECTED_ANIMATED -> header(out, 1, "ANIMATED");
				case LIMIT_HIT -> header(out, 2, "WALL_CLOCK");
				default -> normalized(out, current);
			}
			out.flush();
		} catch (IOException | InterruptedException ignored) {
			// the client stopped reading at a cap
		}
	}

	private static void normalized(DataOutputStream out, Script script) throws IOException {
		boolean overResponse = script == Script.RESPONSE_OVER_CAP;
		header(out, 0, "JPEG");
		out.writeInt(1200);
		out.writeInt(900);
		out.writeByte(overResponse ? 4 : 3);
		int fullWidth = script == Script.FULL_SOF_5000 ? 5000 : 1200;
		variant(out, 1, fullWidth, 900, script == Script.FULL_OVER_CAP ? 12 * 1024 * 1024 + 1
				: overResponse ? 12 * 1024 * 1024 : -1);
		variant(out, 2, 1200, 900, script == Script.PREVIEW_OVER_CAP ? 2 * 1024 * 1024 + 1
				: overResponse ? 2 * 1024 * 1024 : -1);
		variant(out, 3, 400, 300, script == Script.THUMBNAIL_OVER_CAP ? 256 * 1024 + 1
				: overResponse ? 256 * 1024 : -1);
		if (overResponse) {
			// each variant within its cap, the whole reply above 15 MiB
			variant(out, 2, 1200, 900, 2 * 1024 * 1024);
		}
	}

	/** A variant: a real JPEG of the size, or {@code declared} bytes of filler when declared is not -1. */
	private static void variant(DataOutputStream out, int kind, int width, int height, int declared)
			throws IOException {
		byte[] data = declared >= 0 ? new byte[declared] : jpeg(width, height);
		out.writeByte(kind);
		out.writeInt(width);
		out.writeInt(height);
		out.writeInt(data.length);
		out.write(data);
	}

	private static void header(DataOutputStream out, int status, String code) throws IOException {
		out.write("SVR1".getBytes(StandardCharsets.US_ASCII));
		out.writeByte(status);
		out.writeByte(code.length());
		out.write(code.getBytes(StandardCharsets.US_ASCII));
	}

	static byte[] jpeg(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", bytes);
		return bytes.toByteArray();
	}

	@Override
	public void close() throws IOException {
		server.close();
		threads.shutdownNow();
	}
}
