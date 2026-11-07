package vn.sunvibe.survey.core.infrastructure.media;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.service.media.MediaFullListingJob;
import vn.sunvibe.survey.core.application.service.media.MediaProbeRunner;
import vn.sunvibe.survey.core.application.service.media.MediaRestoreReconciliation;
import vn.sunvibe.survey.core.application.service.media.MediaSweeper;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaScheduling;

/**
 * Puts the sweep and the probe poll on the media scheduler (survey-media spec 4.4 step 4, A-18; plan PD-4): unless
 * every adapter is {@code none}, whatever processing says. The sweep runs at start-up (after the configured start
 * delay, none by default) and then every 5 minutes, so intake opens only after the first complete sweep; the poll
 * runs requested probes and the re-runs of incomplete ones every 10 seconds.
 */
@Component
public class MediaHousekeepingRegistration {

    private final MediaScheduling scheduling;
    private final MediaSweeper sweeper;
    private final MediaProbeRunner probes;
    private final MediaFullListingJob listing;
    private final MediaRestoreReconciliation reconciliation;
    private final MediaProperties.Housekeeping settings;

    public MediaHousekeepingRegistration(MediaScheduling scheduling, MediaSweeper sweeper, MediaProbeRunner probes,
                                         MediaFullListingJob listing, MediaRestoreReconciliation reconciliation,
                                         MediaProperties properties) {
        this.scheduling = scheduling;
        this.sweeper = sweeper;
        this.probes = probes;
        this.listing = listing;
        this.reconciliation = reconciliation;
        this.settings = properties.getHousekeeping();
    }

    @PostConstruct
    void register() {
        scheduling.scheduleUnlessEveryAdapterNone("media-sweeper", sweeper::sweepQuietly,
                settings.getSweepStartDelay(), settings.getSweepInterval());
        scheduling.scheduleUnlessEveryAdapterNone("media-probes", probes::poll, settings.getProbePoll(),
                settings.getProbePoll());
        scheduling.scheduleUnlessEveryAdapterNone("media-full-listing", listing::runQuietly, settings.getListingPoll(),
                settings.getListingPoll());
        if (reconciliation.enabledAtStartup()) {
            scheduling.runOnceUnlessEveryAdapterNone("media-restore-reconciliation",
                    reconciliation::runAtStartupIfEnabled);
        }
    }
}
