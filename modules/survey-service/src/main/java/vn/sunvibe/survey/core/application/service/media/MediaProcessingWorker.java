package vn.sunvibe.survey.core.application.service.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaWorkCounters;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer.NormalizeResult;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer.NormalizedVariant;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner.ScanVerdict;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;
import vn.sunvibe.survey.core.application.shared.external.media.ProcessorUnavailableException;
import vn.sunvibe.survey.core.application.shared.external.media.ScannerSilentException;
import vn.sunvibe.survey.core.application.shared.external.media.StoreObjectNotFoundException;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobClaim;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaVariantDomainRepository;

/**
 * The processing worker of survey photos (survey-media spec 4.4 step 3, 4.5, A-7, A-27, A-28). One run claims one due
 * PROCESS job in its own short transaction (fair by the uploader's processing time of the hour, at most 2 in flight per
 * uploader on this instance), then outside any transaction re-hashes the source, detects its format, scans it,
 * normalizes it in the isolated processor and writes the three variants under {@code media/<id>/<attempt>/<variant>}
 * with create-only writes. The outcome commits only while the job still carries this run's lease token and the photo
 * is PROCESSING, under the set, photo and job locks; a removed photo ends CANCELLED and a source older than 50 minutes
 * never becomes READY. Infrastructure errors retry with the A-7 backoff; content errors are final; a fenced-out run
 * deletes its own objects. Logs carry ids and codes only.
 */
@Service
public class MediaProcessingWorker {

    /** survey-media spec A-7: the lease of a claimed job, renewed every minute while working. */
    public static final Duration LEASE = Duration.ofMinutes(5);
    public static final Duration RENEW_EVERY = Duration.ofMinutes(1);
    /** survey-media spec A-7: at most 6 attempts, the backoff before attempts 2 to 6. */
    public static final int MAX_ATTEMPTS = 6;
    static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(30), Duration.ofMinutes(1),
            Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(10));
    /** survey-media spec A-7, BR-18: a source 50 minutes after receipt is never processed into READY. */
    public static final Duration SOURCE_MAX_AGE = Duration.ofMinutes(50);
    /** survey-media spec A-27: at most 2 runs in flight per uploader on this instance. */
    public static final int IN_FLIGHT_PER_UPLOADER = 2;
    /** survey-media spec A-27: a decode over 30 s of wall clock is an expensive outcome. */
    static final Duration SLOW_DECODE = Duration.ofSeconds(30);
    /** survey-media spec A-18: while scanning is blocked a job waits this long without using an attempt. */
    static final Duration SCAN_BLOCKED_WAIT = Duration.ofMinutes(1);
    /** survey-media spec A-7: a per-file scan condition is retried at most twice; a processor limit hit, once. */
    static final int SCAN_CONDITION_RETRIES = 2;

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingWorker.class);

    private final SurveyMediaJobDomainRepository jobs;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSetDomainRepository sets;
    private final SurveyMediaVariantDomainRepository variants;
    private final SurveyChecklistDomainRepository checklists;
    private final MediaObjectStore store;
    private final MalwareScanner scanner;
    private final ImageNormalizer normalizer;
    private final IntakeHealthRepository health;
    private final MediaProbeRequests probes;
    private final MediaWorkCounters workCounters;
    private final SecurityEventRecorder events;
    private final MediaClock clock;
    private final IdGenerator<String> ulids;
    private final TransactionTemplate transaction;
    private final Map<String, Integer> inFlight = new ConcurrentHashMap<>();

    public MediaProcessingWorker(SurveyMediaJobDomainRepository jobs, SurveyMediaDomainRepository media,
                                 SurveyMediaSetDomainRepository sets, SurveyMediaVariantDomainRepository variants,
                                 SurveyChecklistDomainRepository checklists, MediaObjectStore store,
                                 MalwareScanner scanner, ImageNormalizer normalizer, IntakeHealthRepository health,
                                 MediaProbeRequests probes, MediaWorkCounters workCounters,
                                 SecurityEventRecorder events, MediaClock clock,
                                 @Qualifier("Ulid") IdGenerator<String> ulids,
                                 PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.media = media;
        this.sets = sets;
        this.variants = variants;
        this.checklists = checklists;
        this.store = store;
        this.scanner = scanner;
        this.normalizer = normalizer;
        this.health = health;
        this.probes = probes;
        this.workCounters = workCounters;
        this.events = events;
        this.clock = clock;
        this.ulids = ulids;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Records the heartbeat, claims one due job and processes it; false when none was due. */
    public boolean runOnce() {
        Instant now = clock.now();
        try {
            health.beatWorker(now);
        } catch (RuntimeException e) {
            log.error("Could not record the media worker heartbeat", e);
        }
        Set<String> busy = inFlight.entrySet().stream().filter(entry -> entry.getValue() >= IN_FLIGHT_PER_UPLOADER)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        String token = ulids.generateId();
        Optional<MediaJobClaim> claimed = transaction.execute(status -> jobs.claimNextProcess(now,
                ReadBudgetArithmetic.hourStart(now), busy, token, now.plus(LEASE)));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        MediaJobClaim claim = claimed.get();
        String uploader = claim.uploadedByUserId();
        inFlight.merge(uploader, 1, Integer::sum);
        long started = System.nanoTime();
        try {
            new Run(claim, token).process();
        } catch (RuntimeException e) {
            // an unexpected failure: the lease runs out and the job is claimed again with its next attempt
            log.error("Survey media job {} of photo {} failed unexpectedly", claim.job().id(), claim.job().mediaId(),
                    e);
        } finally {
            inFlight.computeIfPresent(uploader, (key, count) -> count <= 1 ? null : count - 1);
            workCounters.addProcessingMillis(uploader, Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
        return true;
    }

    /** The outcomes a run can commit. */
    private enum Kind {
        READY, REJECT, FAIL, CANCEL
    }

    /** What a run decided; written under the locks if the run still holds its lease. */
    private record Outcome(Kind kind, MediaStatusReason reasonOrNull, String errorCodeOrNull,
                           List<MediaVariant> variantsOrNull, SourceFormat formatOrNull) {

        static Outcome reject(MediaStatusReason reason) {
            return new Outcome(Kind.REJECT, reason, reason.name(), null, null);
        }

        static Outcome fail(String code) {
            return new Outcome(Kind.FAIL, null, code, null, null);
        }
    }

    /** One claimed job, processed outside any transaction. */
    private final class Run {

        private final MediaJob job;
        private final SurveyMediaId mediaId;
        private final String uploader;
        private final String token;
        private final List<String> written = new ArrayList<>();
        private Instant lastRenewal;

        Run(MediaJobClaim claim, String token) {
            this.job = claim.job();
            this.mediaId = job.mediaId();
            this.uploader = claim.uploadedByUserId();
            this.token = token;
            this.lastRenewal = clock.now();
        }

        void process() {
            Optional<SurveyMedia> found = media.findById(mediaId);
            if (found.isEmpty()) {
                transaction.executeWithoutResult(status -> jobs.finish(job.id(), token, MediaJobState.CANCELLED,
                        "PHOTO_GONE", clock.now()));
                return;
            }
            SurveyMedia photo = found.get();
            if (photo.isRemoved()) {
                commit(new Outcome(Kind.CANCEL, MediaStatusReason.REMOVED, "REMOVED", null, null), photo);
                return;
            }
            if (photo.getStatus() != MediaStatus.PROCESSING) {
                transaction.executeWithoutResult(status -> jobs.finish(job.id(), token, MediaJobState.DONE,
                        "NOT_PROCESSING", clock.now()));
                return;
            }
            if (sourceExpired(photo)) {
                commit(Outcome.fail("SOURCE_EXPIRED"), photo);
                return;
            }
            work(photo);
        }

        private void work(SurveyMedia photo) {
            byte[] source;
            try (MediaObjectStore.StoredObject object = store.get(Bucket.QUARANTINE, photo.getSourceStorageKey())) {
                source = object.content().readAllBytes();
            } catch (StoreObjectNotFoundException e) {
                commit(Outcome.fail("SOURCE_MISSING"), photo);
                return;
            } catch (StoreUnavailableException e) {
                probes.requestStoreProbe();
                retry(e.code(), job.limitHits(), photo);
                return;
            } catch (IOException e) {
                probes.requestStoreProbe();
                retry("STORE_IO", job.limitHits(), photo);
                return;
            }
            if (!sha256(source).equals(photo.getSourceSha256())) {
                log.error("Survey photo {} source does not match its committed hash; failed closed", mediaId);
                commit(Outcome.fail("SOURCE_HASH_MISMATCH"), photo);
                return;
            }
            if (!renewIfDue()) {
                return;
            }
            Optional<SourceFormat> format;
            try {
                format = normalizer.detect(new ByteArrayInputStream(source), source.length);
            } catch (ProcessorUnavailableException e) {
                retry(e.getMessage(), job.limitHits(), photo);
                return;
            }
            if (format.isEmpty()) {
                commit(Outcome.reject(MediaStatusReason.UNSUPPORTED_FORMAT), photo);
                return;
            }
            if (health.read().scanBlockedSince() != null) {
                Boolean deferred = transaction.execute(status -> jobs.defer(job.id(), token,
                        clock.now().plus(SCAN_BLOCKED_WAIT), clock.now()));
                if (!Boolean.TRUE.equals(deferred)) {
                    log.warn("Survey media job {} of photo {} lost its lease; the defer was not applied", job.id(),
                            mediaId);
                }
                return;
            }
            ScanVerdict verdict;
            try {
                verdict = scanner.scan(new ByteArrayInputStream(source));
            } catch (ScannerSilentException e) {
                probes.requestScannerProbe();
                retry("SCANNER_SILENT", job.limitHits(), photo);
                return;
            }
            switch (verdict.kind()) {
                case FOUND -> {
                    commit(Outcome.reject(MediaStatusReason.MALWARE_DETECTED), photo);
                    return;
                }
                case PER_FILE_CONDITION -> {
                    workCounters.addExpensive(uploader);
                    if (job.limitHits() >= SCAN_CONDITION_RETRIES) {
                        commit(Outcome.reject(MediaStatusReason.SCAN_LIMIT_EXCEEDED), photo);
                    } else {
                        retry("SCAN_CONDITION", job.limitHits() + 1, photo);
                    }
                    return;
                }
                case CLEAN -> {
                    // go on to the processor
                }
            }
            if (!renewIfDue()) {
                return;
            }
            long decodeStart = System.nanoTime();
            NormalizeResult result;
            try {
                result = normalizer.normalize(new ByteArrayInputStream(source), source.length);
            } catch (ProcessorUnavailableException e) {
                retry(e.getMessage(), job.limitHits(), photo);
                return;
            }
            if (Duration.ofNanos(System.nanoTime() - decodeStart).compareTo(SLOW_DECODE) > 0) {
                workCounters.addExpensive(uploader);
            }
            switch (result) {
                case NormalizeResult.Rejected rejected -> commit(Outcome.reject(rejected.reason()), photo);
                case NormalizeResult.LimitHit hit -> {
                    workCounters.addExpensive(uploader);
                    if (job.limitHits() >= 1) {
                        commit(Outcome.reject(MediaStatusReason.UNREADABLE), photo);
                    } else {
                        retry("PROCESSOR_LIMIT", job.limitHits() + 1, photo);
                    }
                }
                case NormalizeResult.Failed failed -> {
                    log.error("Survey photo {} variants failed their checks ({}); failed closed", mediaId,
                            failed.code());
                    commit(Outcome.fail(failed.code()), photo);
                }
                case NormalizeResult.Normalized normalized -> {
                    List<MediaVariant> stored = writeVariants(normalized);
                    if (stored != null && renewIfDue()) {
                        commit(new Outcome(Kind.READY, null, null, stored, format.get()), photo);
                    }
                }
            }
        }

        /** Create-only writes of the three variants; null after a store failure (the run then retries). */
        private List<MediaVariant> writeVariants(NormalizeResult.Normalized normalized) {
            List<MediaVariant> stored = new ArrayList<>();
            Instant now = clock.now();
            try {
                for (NormalizedVariant variant : normalized.variants()) {
                    String key = "media/" + mediaId.getValue() + "/" + job.attempts() + "/" + variant.kind().name();
                    written.add(key);
                    MediaObjectStore.PutOutcome put = store.putCreateOnly(Bucket.MEDIA, key,
                            new ByteArrayInputStream(variant.jpeg()), variant.jpeg().length);
                    if (put != MediaObjectStore.PutOutcome.CREATED) {
                        throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
                    }
                    stored.add(new MediaVariant(variant.kind(), job.attempts(), key, sha256(variant.jpeg()),
                            variant.jpeg().length, variant.width(), variant.height(), normalizer.processorVersion(),
                            now));
                }
                return stored;
            } catch (StoreUnavailableException e) {
                probes.requestStoreProbe();
                deleteWritten();
                retry(e.code(), job.limitHits(), null);
                return null;
            }
        }

        /** survey-media spec A-7: the next attempt after its backoff, or FAILED after the last one. */
        private void retry(String code, int limitHits, SurveyMedia photoOrNull) {
            if (job.attempts() >= MAX_ATTEMPTS) {
                SurveyMedia photo = photoOrNull != null ? photoOrNull : media.findById(mediaId).orElse(null);
                if (photo != null) {
                    commit(Outcome.fail(code), photo);
                }
                return;
            }
            Instant now = clock.now();
            Instant due = now.plus(BACKOFF.get(job.attempts() - 1));
            Boolean retried = transaction.execute(status -> jobs.retry(job.id(), token, due, code, limitHits, now));
            if (!Boolean.TRUE.equals(retried)) {
                // the lease is gone: the job row belongs to another run, so nothing changed here
                log.warn("Survey media job {} of photo {} lost its lease; the retry was not applied ({})", job.id(),
                        mediaId, code);
                return;
            }
            log.warn("Survey media job {} of photo {} retries after attempt {} ({})", job.id(), mediaId,
                    job.attempts(), code);
        }

        /** survey-media spec A-7: the lease extended every minute; false when this run lost it (its objects are deleted). */
        private boolean renewIfDue() {
            Instant now = clock.now();
            if (Duration.between(lastRenewal, now).compareTo(RENEW_EVERY) < 0) {
                return true;
            }
            Boolean renewed = transaction.execute(status -> jobs.renewLease(job.id(), token, now.plus(LEASE), now));
            if (!Boolean.TRUE.equals(renewed)) {
                log.warn("Survey media job {} lost its lease; its work is dropped", job.id());
                deleteWritten();
                return false;
            }
            lastRenewal = now;
            return true;
        }

        /**
         * The outcome under the set, photo and job locks (survey-media spec 5.4), only while the job carries this lease and the
         * photo is PROCESSING; then the source is deleted and marked. A fenced-out run deletes only its own objects.
         */
        private void commit(Outcome outcome, SurveyMedia seen) {
            Kind applied = transaction.execute(status -> commitInTransaction(outcome));
            if (applied == null) {
                deleteWritten();
                return;
            }
            if (applied != Kind.READY) {
                deleteWritten();
            }
            deleteSource(seen);
        }

        private Kind commitInTransaction(Outcome outcome) {
            Instant now = clock.now();
            String checklistId = media.findChecklistIdById(mediaId).orElse(null);
            if (checklistId == null) {
                return null;
            }
            MediaSet set = sets.lockForUpdate(checklistId).orElseThrow(
                    () -> new IllegalStateException("A photo's checklist has its media set."));
            SurveyMedia photo = media.lockForUpdate(List.of(mediaId)).stream().findFirst().orElse(null);
            MediaJob locked = jobs.lockForUpdate(job.id()).orElse(null);
            if (photo == null || locked == null || locked.state() != MediaJobState.PENDING
                    || !token.equals(locked.leaseTokenOrNull())) {
                return null;
            }
            if (photo.getStatus() != MediaStatus.PROCESSING) {
                jobs.finish(job.id(), token, MediaJobState.DONE, "NOT_PROCESSING", now);
                return null;
            }
            Outcome effective = outcome;
            if (photo.isRemoved()) {
                effective = new Outcome(Kind.CANCEL, MediaStatusReason.REMOVED, "REMOVED", null, null);
            } else if (outcome.kind() == Kind.READY && sourceExpired(photo)) {
                effective = Outcome.fail("SOURCE_EXPIRED");
            }
            switch (effective.kind()) {
                case READY -> {
                    variants.saveAll(mediaId, effective.variantsOrNull());
                    photo.ready(effective.variantsOrNull(), effective.formatOrNull(), now);
                }
                case REJECT -> {
                    photo.reject(effective.reasonOrNull(), now);
                    if (effective.reasonOrNull() == MediaStatusReason.MALWARE_DETECTED) {
                        String leadId = checklists.findLeadIdById(SurveyChecklistId.of(checklistId)).orElseThrow();
                        // survey-media spec 4.9: the uploader, the photo and the job id; no body, hash or signature name
                        events.recordInTransaction(photo.getUploadedByUserId(), leadId, SecurityResourceType.MEDIA,
                                mediaId.getValue(), SecurityAction.SCAN, SecurityOutcome.MALWARE_DETECTED,
                                String.valueOf(job.id()));
                    }
                }
                case FAIL -> photo.fail(now);
                case CANCEL -> photo.cancel(now);
            }
            media.update(photo);
            jobs.finish(job.id(), token, effective.kind() == Kind.CANCEL ? MediaJobState.CANCELLED
                    : MediaJobState.DONE, effective.errorCodeOrNull(), now);
            set.bump(now);
            sets.update(set);
            return effective.kind();
        }

        /** After an outcome: delete the source, then mark it deleted in a short transaction (survey-media spec 4.4 step 3). */
        private void deleteSource(SurveyMedia seen) {
            String key = seen.getSourceStorageKey();
            try {
                store.delete(Bucket.QUARANTINE, key);
            } catch (RuntimeException e) {
                // the sweep deletes it later (survey-media spec 4.4 step 4)
                log.warn("Could not delete the source of survey photo {}", mediaId);
                return;
            }
            try {
                transaction.executeWithoutResult(status -> {
                    String checklistId = media.findChecklistIdById(mediaId).orElseThrow();
                    sets.lockForUpdate(checklistId);
                    media.lockForUpdate(List.of(mediaId)).stream().findFirst().ifPresent(photo -> {
                        if (photo.getSourceDeletedAt() == null && photo.getStatus() != MediaStatus.RECEIVING) {
                            photo.markSourceDeleted(clock.now());
                            media.update(photo);
                        }
                    });
                });
            } catch (RuntimeException e) {
                log.warn("Could not mark the source of survey photo {} deleted", mediaId);
            }
        }

        private void deleteWritten() {
            for (String key : written) {
                try {
                    store.delete(Bucket.MEDIA, key);
                } catch (RuntimeException e) {
                    // the orphan sweep deletes it later (survey-media spec A-17)
                    log.warn("Could not delete a variant of survey photo {}", mediaId);
                }
            }
            written.clear();
        }

        private boolean sourceExpired(SurveyMedia photo) {
            return photo.getContentReceivedAt() != null
                    && !clock.now().isBefore(photo.getContentReceivedAt().plus(SOURCE_MAX_AGE));
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
