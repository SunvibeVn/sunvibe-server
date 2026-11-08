package vn.sunvibe.survey.core.application.service.media;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaServingDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaStoreUnavailableException;
import vn.sunvibe.survey.core.application.query.media.MediaRead;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.media.MediaAccessLogger;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaReadBudgets;
import vn.sunvibe.survey.core.application.shared.media.MediaReadSettings;
import vn.sunvibe.survey.core.application.shared.media.MediaReadSlots;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.StoreObjectNotFoundException;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaReadUseCase;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;

/**
 * The content reads of survey photos (survey-media spec 5.4 content GET order, ADR-011 decision 2, A-6, A-23 to A-26,
 * A-30): every read re-checks, without any cache, the caller's lead visibility and that the photo is servable (an ADMIN
 * may read a held photo whose content is present, as MEDIA_HELD_READ); the read slots come after the 404 and 409
 * checks and before the budget and the store call; the access row is written once the store has answered and before
 * the first byte. Refusals write no access row and make no store call. Logs carry ids only.
 */
@Service
public class SurveyMediaReadService implements SurveyMediaReadUseCase {

    private static final Logger log = LoggerFactory.getLogger(SurveyMediaReadService.class);

    private final ChecklistContextLoader loader;
    private final SurveyMediaDomainRepository media;
    private final MediaReadSlots slots;
    private final MediaReadBudgets budgets;
    private final MediaObjectStore store;
    private final MediaAccessLogger accessLogger;
    private final MediaSwitches switches;
    private final MediaReadSettings settings;
    private final SecurityEventRecorder events;
    private final MediaClock clock;
    private final TransactionTemplate readTransaction;

    public SurveyMediaReadService(ChecklistContextLoader loader, SurveyMediaDomainRepository media,
                                  MediaReadSlots slots, MediaReadBudgets budgets, MediaObjectStore store,
                                  MediaAccessLogger accessLogger, MediaSwitches switches, MediaReadSettings settings,
                                  SecurityEventRecorder events, MediaClock clock,
                                  PlatformTransactionManager transactionManager) {
        this.loader = loader;
        this.media = media;
        this.slots = slots;
        this.budgets = budgets;
        this.store = store;
        this.accessLogger = accessLogger;
        this.switches = switches;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public void requireServing() {
        if (!switches.servingOn()) {
            throw new MediaServingDisabledException();
        }
    }

    @Override
    public MediaRead open(SurveyViewer viewer, boolean viewerCanHold, String mediaId, String variant,
                          String download) {
        VariantKind kind = variant(variant);
        boolean asDownload = download(download, kind);
        if (!SurveyMediaId.isValid(mediaId)) {
            throw new MediaNotFoundException();
        }
        SurveyMediaId id = SurveyMediaId.of(mediaId);
        Target target;
        try {
            target = readTransaction.execute(status -> servable(viewer, viewerCanHold, id));
        } finally {
            events.flushDeferred();
        }
        MediaVariant stored = target.photo().getVariants().stream().filter(candidate -> candidate.kind() == kind)
                .max(Comparator.comparingInt(MediaVariant::attempt))
                .orElseThrow(() -> new MediaNotAvailableException("status"));

        MediaReadSlots.Lease lease = slots.acquire(viewer.userId());
        try {
            budgets.count(viewer.userId(), target.leadId(), counterKind(kind), SecurityResourceType.MEDIA, mediaId);
            MediaObjectStore.StoredObject object;
            try {
                object = store.getForServing(stored.storageKey());
            } catch (StoreObjectNotFoundException e) {
                log.error("Survey photo {} has no {} object in the store", mediaId, kind);
                throw new MediaNotAvailableException("status");
            } catch (StoreUnavailableException e) {
                log.warn("Survey photo {} {} could not be read from the store ({})", mediaId, kind, e.code());
                throw new MediaStoreUnavailableException();
            }
            try {
                logRead(viewer, target, kind, asDownload, mediaId);
            } catch (RuntimeException e) {
                closeQuietly(object);
                throw e;
            }
            return new MediaRead(mediaId, object.content(), object.length(), asDownload,
                    settings.noProgressTimeout(), clock.now().plus(deadline(kind)), lease, object.abort());
        } catch (RuntimeException e) {
            lease.close();
            throw e;
        }
    }

    /** The photo, visible and servable to this viewer, in one short read transaction (5.4 content GET (2), (3)). */
    private Target servable(SurveyViewer viewer, boolean viewerCanHold, SurveyMediaId id) {
        String checklistId = media.findChecklistIdById(id).orElseThrow(MediaNotFoundException::new);
        LeadFacts lead;
        try {
            lead = loader.requireMediaVisible(viewer, checklistId, SecurityResourceType.MEDIA, id.getValue(),
                    SecurityAction.READ);
        } catch (ChecklistNotFoundException e) {
            throw new MediaNotFoundException();
        }
        SurveyMedia photo = media.findById(id).orElseThrow(MediaNotFoundException::new);
        boolean heldRead = viewerCanHold && photo.isHeld() && photo.isServableToAdmin();
        if (!photo.isServable() && !heldRead) {
            throw new MediaNotAvailableException("status");
        }
        return new Target(lead.leadId(), checklistId, photo, heldRead);
    }

    /** survey-media spec A-6, A-23: the row of a delivered read; a THUMBNAIL is covered by the checklist's list row. */
    private void logRead(SurveyViewer viewer, Target target, VariantKind kind, boolean asDownload, String mediaId) {
        if (target.heldRead()) {
            accessLogger.contentRead(viewer.userId(), target.leadId(), AccessResourceType.MEDIA_HELD_READ, mediaId);
        } else if (kind == VariantKind.THUMBNAIL) {
            accessLogger.listRead(viewer.userId(), target.leadId(), target.checklistId());
        } else if (kind == VariantKind.PREVIEW) {
            accessLogger.contentRead(viewer.userId(), target.leadId(), AccessResourceType.MEDIA_PREVIEW, mediaId);
        } else {
            accessLogger.contentRead(viewer.userId(), target.leadId(), asDownload ? AccessResourceType.MEDIA_DOWNLOAD
                    : AccessResourceType.MEDIA_FULL, mediaId);
        }
    }

    /** survey-media spec 7.1, A-30: {@code thumbnail}, {@code preview} or {@code full}; anything else is 400 on {@code variant}. */
    private static VariantKind variant(String variant) {
        if (variant != null) {
            for (VariantKind kind : VariantKind.values()) {
                if (kind.name().toLowerCase(Locale.ROOT).equals(variant)) {
                    return kind;
                }
            }
        }
        throw new ChecklistValidationException(List.of(new Violation("variant",
                "variant must be thumbnail, preview or full.")));
    }

    /** survey-media spec A-30: {@code download} only as {@code true} or {@code false} and only on {@code full}. */
    private static boolean download(String download, VariantKind kind) {
        if (download == null) {
            return false;
        }
        if (kind != VariantKind.FULL || !(download.equals("true") || download.equals("false"))) {
            throw new ChecklistValidationException(List.of(new Violation("download",
                    "download is true or false, and only on the full variant.")));
        }
        return download.equals("true");
    }

    private static MediaCounterKind counterKind(VariantKind kind) {
        return switch (kind) {
            case FULL -> MediaCounterKind.FULL;
            case PREVIEW -> MediaCounterKind.PREVIEW;
            case THUMBNAIL -> MediaCounterKind.THUMBNAIL;
        };
    }

    private Duration deadline(VariantKind kind) {
        return switch (kind) {
            case FULL -> settings.fullDeadline();
            case PREVIEW -> settings.previewDeadline();
            case THUMBNAIL -> settings.thumbnailDeadline();
        };
    }

    private static void closeQuietly(MediaObjectStore.StoredObject object) {
        try {
            object.close();
        } catch (java.io.IOException e) {
            // released either way
        }
    }

    /** A servable photo of a visible lead. */
    private record Target(String leadId, String checklistId, SurveyMedia photo, boolean heldRead) {
    }
}
