package vn.sunvibe.survey.media;

import java.nio.file.Path;
import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.github.dockerjava.api.model.Capability;

/**
 * The media processing image built from {@code docker/media-processor/} (survey-media ADR-011 decision 4 form (a),
 * plan Task A9) with a fixed tag and kept after the run, so the build reuses Docker's layer cache. {@link #hardened}
 * applies the production limits of {@code compose.yaml}: read-only root, a tmpfs work area, the non-root user,
 * {@code no-new-privileges}, every capability dropped, process and memory limits.
 */
public final class ProcessorImage {

	public static final String TAG = "sunvibe/media-processor:test";
	public static final int PORT = 7300;
	/** At least the sum of the concurrent run limits plus the probe's (spec A-27): 3 x 1.5 GiB. */
	public static final long MEMORY_BYTES = 5L * 1024 * 1024 * 1024;

	private ProcessorImage() {
	}

	/** The image, built once per Docker cache. */
	public static ImageFromDockerfile image() {
		Path context = Path.of(System.getProperty("user.dir")).resolveSibling("docker").resolve("media-processor");
		if (!context.toFile().isDirectory()) {
			context = Path.of(System.getProperty("user.dir")).resolve("docker").resolve("media-processor");
		}
		return new ImageFromDockerfile(TAG, false).withFileFromPath(".", context);
	}

	/** A container of the image with the production hardening of compose.yaml and the given supervisor arguments. */
	public static GenericContainer<?> hardened(List<String> command) {
		GenericContainer<?> container = new GenericContainer<>(image())
				.withCommand(command.toArray(String[]::new))
				.withTmpFs(java.util.Map.of("/work", "rw,size=1g,mode=0700,uid=10001,gid=10001"));
		container.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
				.withReadonlyRootfs(true)
				.withSecurityOpts(List.of("no-new-privileges:true"))
				.withCapDrop(Capability.ALL)
				.withPidsLimit(64L)
				.withMemory(MEMORY_BYTES)
				.withMemorySwap(MEMORY_BYTES));
		return container;
	}
}
