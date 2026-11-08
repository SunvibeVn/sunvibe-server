package vn.sunvibe.survey.core.application.service.media;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks.LockedSets;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ListedObject;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.MediaRef;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaVariantDomainRepository;

/**
 * The reconciliation after a database restore (survey-media spec 5.1; the restore runbook, scripts/ops/README.md): for
 * each READY photo not marked yet it lists {@code media/<mediaId>/}; a photo with a variant missing from the store is
 * marked CONTENT_MISSING under the set and photo locks (not servable, 409) and the set version is bumped. It deletes
 * nothing (orphans go by the sweep, I-4) and logs counts only. It runs once at start-up only while the operator sets
 * {@code app.survey.media.reconcile-after-restore=true} for that one start. It guards against a wrong or empty bucket
 * (see {@link #run}): the marks cannot be cleared by the application, so a mistaken run must mark nothing.
 */
@Service
public class MediaRestoreReconciliation {

    private static final int PAGE = 500;
    /** Below this many photos with variants the share guard does not apply (a small table after a restore). */
    static final int MIN_SAMPLE = 20;

    private static final Logger log = LoggerFactory.getLogger(MediaRestoreReconciliation.class);

    private final SurveyMediaDomainRepository media;
    private final MediaSetLocks setLocks;
    private final SurveyMediaVariantDomainRepository variants;
    private final MediaObjectStore store;
    private final MediaClock clock;
    private final TransactionTemplate transaction;
    private final boolean enabledAtStartup;
    private final boolean force;

    public MediaRestoreReconciliation(SurveyMediaDomainRepository media, MediaSetLocks setLocks,
                                      SurveyMediaVariantDomainRepository variants, MediaObjectStore store,
                                      MediaClock clock, PlatformTransactionManager transactionManager,
                                      @Value("${app.survey.media.reconcile-after-restore:false}")
                                      boolean enabledAtStartup,
                                      @Value("${app.survey.media.reconcile-after-restore-force:false}")
                                      boolean force) {
        this.media = media;
        this.setLocks = setLocks;
        this.variants = variants;
        this.store = store;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabledAtStartup = enabledAtStartup;
        this.force = force;
    }

    /** Whether this start runs the reconciliation (the operator's one-start property). */
    public boolean enabledAtStartup() {
        return enabledAtStartup;
    }

    /** The start-up run when enabled; failures are logged. */
    public void runAtStartupIfEnabled() {
        if (!enabledAtStartup) {
            return;
        }
        try {
            run();
        } catch (RuntimeException e) {
            log.error("The survey media restore reconciliation stopped", e);
        }
    }

    /**
     * Checks every READY photo not marked yet and marks those with a missing variant; returns how many it marked. It
     * marks nothing (an error log, counts only) when the store does not answer its probe, when the media bucket lists
     * nothing while READY photos exist (a wrong or empty bucket), or when more than half of at least 20 photos with
     * variants miss every one of them, unless the operator set {@code app.survey.media.reconcile-after-restore-force}.
     */
    public int run() {
        long started = System.nanoTime();
        try {
            store.probe(Bucket.MEDIA);
        } catch (StoreUnavailableException e) {
            log.error("Survey media restore reconciliation stopped: the media store did not answer its probe ({});"
                    + " nothing marked", e.code());
            return 0;
        }
        if (!force && store.list(Bucket.MEDIA, "media/", null, 1).objects().isEmpty()
                && !media.findReadyWithoutContentMissing(null, 1).isEmpty()) {
            log.error("Survey media restore reconciliation stopped: the media bucket lists nothing while READY photos"
                    + " exist (a wrong or empty bucket?); nothing marked");
            return 0;
        }
        List<MediaRef> missing = new ArrayList<>();
        int checked = 0;
        int withVariants = 0;
        int missingEvery = 0;
        String after = null;
        while (true) {
            List<MediaRef> page = media.findReadyWithoutContentMissing(after, PAGE);
            if (page.isEmpty()) {
                break;
            }
            Map<SurveyMediaId, List<MediaVariant>> expected = variants.findByMediaIds(page.stream()
                    .map(MediaRef::mediaId).toList());
            for (MediaRef ref : page) {
                checked++;
                List<MediaVariant> wanted = expected.getOrDefault(ref.mediaId(), List.of());
                long present = present(ref.mediaId(), wanted);
                if (!wanted.isEmpty()) {
                    withVariants++;
                    if (present == 0) {
                        missingEvery++;
                    }
                }
                if (wanted.isEmpty() || present < wanted.size()) {
                    missing.add(ref);
                }
            }
            after = page.get(page.size() - 1).mediaId().getValue();
        }
        if (!force && withVariants >= MIN_SAMPLE && missingEvery * 2 > withVariants) {
            log.error("Survey media restore reconciliation stopped: {} of {} READY photo(s) with variants miss every"
                    + " variant (a wrong or empty bucket?); nothing marked", missingEvery, withVariants);
            return 0;
        }
        int marked = 0;
        for (MediaRef ref : missing) {
            if (mark(ref)) {
                marked++;
            }
        }
        log.info("Survey media restore reconciliation: {} READY photo(s) checked, {} marked content missing, {} ms",
                checked, marked, java.time.Duration.ofNanos(System.nanoTime() - started).toMillis());
        return marked;
    }

    /** How many of the photo's variants the store lists under {@code media/<mediaId>/}. */
    private long present(SurveyMediaId mediaId, List<MediaVariant> wanted) {
        Set<String> listed = new HashSet<>();
        for (ListedObject object : store.list(Bucket.MEDIA, "media/" + mediaId.getValue() + "/", null, 100)
                .objects()) {
            listed.add(object.key());
        }
        return wanted.stream().filter(variant -> listed.contains(variant.storageKey())).count();
    }

    private boolean mark(MediaRef ref) {
        Boolean done = transaction.execute(status -> {
            Instant now = clock.now();
            // survey-checklist submission spec 5.4: a carried photo's loss shows in every revision that selects it
            LockedSets locked = setLocks.lockFor(ref.checklistId(), ref.mediaId()).orElse(null);
            if (locked == null) {
                return false;
            }
            SurveyMedia photo = media.lockForUpdate(List.of(ref.mediaId())).stream().findFirst().orElse(null);
            if (photo == null || photo.getStatus() != MediaStatus.READY || photo.getContentMissingAt() != null) {
                return false;
            }
            photo.markContentMissing(now);
            media.update(photo);
            setLocks.bumpAll(locked, now);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }
}
