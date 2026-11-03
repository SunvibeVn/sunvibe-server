package vn.sunvibe.survey.core.infrastructure.config;

import com.github.f4b6a3.ulid.UlidCreator;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code app.survey.media.*} (survey-media spec 5.1, A-1, A-3, A-25, A-26, A-28; plan PD-4, PD-12). The defaults are
 * the safe ones: uploads off and every adapter {@code none}, so no photo can exist. Storage credentials come only from
 * env vars (ADR-002). {@code processor.kind} and {@code processor.version} sit under one key because the version is
 * named {@code processor.version} in the spec; store and scanner keep their scalar form ({@code store=local},
 * {@code scanner=stub}) with their settings under {@code local}, {@code s3} and {@code clamav}.
 */
@ConfigurationProperties(prefix = "app.survey.media")
@Getter
@Setter
public class MediaProperties {

    /** spec A-1: the owner's go-live switch; off in every committed profile. */
    private boolean uploadEnabled = false;
    /** spec A-1: off stops the worker without stopping reads and pauses intake. */
    private boolean processingEnabled = true;
    /** spec A-24: the serving kill switch of content GETs. */
    private boolean servingEnabled = true;
    private StoreKind store = StoreKind.NONE;
    private final Local local = new Local();
    private final S3 s3 = new S3();
    private ScannerKind scanner = ScannerKind.NONE;
    private final Clamav clamav = new Clamav();
    private final Processor processor = new Processor();
    private final Worker worker = new Worker();
    /**
     * The private spool of raw bodies (survey-media spec A-13); on tmpfs or a volume outside backups (go-live check
     * 9.4). Each instance keeps its bodies in a subdirectory named by its instance id, so instances may share it.
     */
    private String spoolDir = System.getProperty("java.io.tmpdir") + "/sunvibe-media-spool";
    /** plan PD-12: this instance's id in body slots, error logs and its spool directory; by default a new ULID. */
    private String instanceId = UlidCreator.getMonotonicUlid().toString();
    private final Reads reads = new Reads();
    private final Budgets budgets = new Budgets();
    private final Bodies bodies = new Bodies();
    private final Housekeeping housekeeping = new Housekeeping();
    /** spec 5.1: the operator sets it for one start after a database restore (the restore runbook). */
    private boolean reconcileAfterRestore = false;

    /** The object store adapter. */
    public enum StoreKind {
        /** No store: no photo can exist. */
        NONE,
        /** A local directory; dev, test and lan only (spec A-1). */
        LOCAL,
        /** The S3-compatible adapter used for Cloudflare R2 (spec 4.12). */
        S3
    }

    /** The malware scanner adapter. */
    public enum ScannerKind {
        /** No scanner. */
        NONE,
        /** ClamAV {@code clamd} over TCP or a Unix socket. */
        CLAMAV,
        /** The in-memory stub; dev, test and lan only (spec A-1). */
        STUB
    }

    /** The image processor adapter. */
    public enum ProcessorKind {
        /** No processor. */
        NONE,
        /** The isolated processing container (ADR-011 decision 4, form (a)). */
        ISOLATED
    }

    @Getter
    @Setter
    public static class Local {
        /** A directory outside the repository. */
        private String root;
    }

    @Getter
    @Setter
    public static class S3 {
        /** {@code https://<account>.r2.cloudflarestorage.com}; https unless every profile is dev, test or lan. */
        private String endpoint;
        private String region = "auto";
        private String quarantineBucket;
        private String mediaBucket;
        /** From the env var only (ADR-002). */
        private String accessKeyId;
        /** From the env var only (ADR-002). */
        private String secretAccessKey;
    }

    @Getter
    @Setter
    public static class Clamav {
        private String host;
        private int port = 3310;
        /** A Unix socket path instead of host and port. */
        private String socket;
        private Duration timeout = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Processor {
        private ProcessorKind kind = ProcessorKind.NONE;
        /** spec A-28: the processing image digest, or dev-&lt;libvips version&gt;; stored on every variant. */
        private String version;
        private String host;
        private int port;
        /** A Unix socket path instead of host and port. */
        private String socket;
    }

    /** spec 4.4 step 4, A-18: the sweep and the probe poll of this instance. */
    @Getter
    @Setter
    public static class Housekeeping {
        /** The sweep runs every 5 minutes (the stale-sweep pause comes after 10). */
        private Duration sweepInterval = Duration.ofMinutes(5);
        /** The first sweep runs at start-up: intake opens only after it. */
        private Duration sweepStartDelay = Duration.ZERO;
        /** Requested probes and the re-runs of incomplete ones are looked for this often. */
        private Duration probePoll = Duration.ofSeconds(10);
        /** Due content deletions (each due 10 minutes after its request) are looked for this often. */
        private Duration deletionPoll = Duration.ofSeconds(30);
        /** The daily full listing is looked for (resumed or started when due) this often. */
        private Duration listingPoll = Duration.ofHours(1);
    }

    @Getter
    @Setter
    public static class Worker {
        private Duration pollInterval = Duration.ofSeconds(5);
        /** spec 5.1: concurrent processing runs of this instance. */
        private int concurrency = 2;
    }

    /** spec A-25: content GET concurrency and stream limits. */
    @Getter
    @Setter
    public static class Reads {
        private int userConcurrency = 6;
        private int instanceConcurrency = 32;
        private Duration noProgressTimeout = Duration.ofSeconds(30);
        private Duration fullDeadline = Duration.ofMinutes(5);
        private Duration previewDeadline = Duration.ofMinutes(1);
        private Duration thumbnailDeadline = Duration.ofMinutes(1);
        /** The GET client's connection-acquisition timeout, answered 503 MEDIA_STORE_UNAVAILABLE. */
        private Duration acquisitionTimeout = Duration.ofSeconds(2);
    }

    /** spec A-26: per-user hourly read budgets. */
    @Getter
    @Setter
    public static class Budgets {
        private int full = 600;
        private int preview = 1200;
        private int thumbnail = 6000;
        private int list = 1200;
    }

    /** spec A-3: content bodies at once. */
    @Getter
    @Setter
    public static class Bodies {
        private int perUser = 3;
        private int perInstance = 16;
    }

    /** Storage credentials are secrets: never printed. */
    @Override
    public String toString() {
        return "MediaProperties[store=" + store + ", scanner=" + scanner + ", processor=" + processor.kind + "]";
    }
}
