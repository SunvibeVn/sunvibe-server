package vn.sunvibe.survey.media;

import java.nio.file.Path;
import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.github.dockerjava.api.model.Capability;

/**
 * The media processing image built from {@code docker/media-processor/} (survey-media ADR-011 decision 4 form (a),
 * plan Task A9) with a fixed tag and kept after the run, so the build reuses Docker's layer cache. {@link #hardened}
 * applies the production limits of {@code compose.yaml}: read-only root, a tmpfs work area, every capability dropped
 * but the four the supervisor needs to give each run slot its own uid and directory and to end a slot's processes
 * (SETUID, SETGID, CHOWN, KILL; the runs themselves have none), no shared IPC, {@code no-new-privileges}, process and
 * memory limits.
 */
public final class ProcessorImage {

	public static final String TAG = "sunvibe/media-processor:test";
	public static final int PORT = 7300;
	/** The uid and gid of run slot 1; slot n runs as {@code SLOT_UID + n - 1} (supervisor.py, SV-069). */
	public static final int SLOT_UID = 10101;
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
				.withTmpFs(java.util.Map.of("/work", "rw,size=1g,mode=0711,uid=0,gid=0"));
		container.withCreateContainerCmdModifier(cmd -> cmd.withUser("0:0").getHostConfig()
				.withInit(true)
				.withReadonlyRootfs(true)
				.withSecurityOpts(List.of("no-new-privileges:true"))
				.withCapDrop(Capability.ALL)
				.withCapAdd(Capability.SETUID, Capability.SETGID, Capability.CHOWN, Capability.KILL)
				.withIpcMode("none")
				.withSysctls(java.util.Map.of("kernel.shmmni", "0", "kernel.msgmni", "0", "kernel.sem", "0 0 0 0"))
				.withPidsLimit(96L)
				.withMemory(MEMORY_BYTES)
				.withMemorySwap(MEMORY_BYTES));
		return container;
	}
}
