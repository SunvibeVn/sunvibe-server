package vn.sunvibe.survey.core.infrastructure.media;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.service.media.MediaContentDeletionJob;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaScheduling;

/**
 * Puts the content deletion job on the media scheduler (survey-media plan PD-4, survey-media spec 4.7): unless every adapter is
 * {@code none}, whatever processing says, so deletions go on while processing is off.
 */
@Component
public class MediaDeletionRegistration {

    private final MediaScheduling scheduling;
    private final MediaContentDeletionJob deletion;
    private final Duration poll;

    public MediaDeletionRegistration(MediaScheduling scheduling, MediaContentDeletionJob deletion,
                                     MediaProperties properties) {
        this.scheduling = scheduling;
        this.deletion = deletion;
        this.poll = properties.getHousekeeping().getDeletionPoll();
    }

    @PostConstruct
    void register() {
        scheduling.scheduleUnlessEveryAdapterNone("media-deletion", deletion::drain, poll, poll);
    }
}
