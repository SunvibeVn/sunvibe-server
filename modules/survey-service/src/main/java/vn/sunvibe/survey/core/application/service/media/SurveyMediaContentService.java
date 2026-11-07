package vn.sunvibe.survey.core.application.service.media;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.sunvibe.survey.core.application.command.media.ContentBody;
import vn.sunvibe.survey.core.application.command.media.UploadContentCommand;
import vn.sunvibe.survey.core.application.exception.media.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.exception.media.MediaRequestMalformedException;
import vn.sunvibe.survey.core.application.exception.media.MediaStoreUnavailableException;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.application.service.media.SurveyMediaCommandService.ContentClaim;
import vn.sunvibe.survey.core.application.service.media.SurveyMediaCommandService.ContentTarget;
import vn.sunvibe.survey.core.application.shared.media.MediaBodySlots;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaContentHeaders;
import vn.sunvibe.survey.core.application.shared.media.MediaContentHeaders.ContentHeaders;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.media.MediaBodySpool;
import vn.sunvibe.survey.core.application.shared.external.media.MediaBodySpool.SpooledBody;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaContentUseCase;

/**
 * The content upload of a survey photo (survey-media spec 4.4 step 2, the content order of 5.4, A-3, A-13): the header
 * checks, the authority checks, the body slots, the claim, the body read into the private spool and checked against
 * its digest, one create-only write to the store and the commit, each transaction short and none open while bytes
 * move. Answers decided before the slots read nothing (the controller closes the connection); every later refusal
 * drains the declared body first, inside the slots, so the client reads the answer.
 */
@Service
public class SurveyMediaContentService implements SurveyMediaContentUseCase {

    /** spec A-13: a body is read within 10 minutes of its claim. */
    public static final Duration BODY_DEADLINE = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(SurveyMediaContentService.class);
    private static final int DRAIN_BUFFER = 64 * 1024;

    private final SurveyMediaCommandService commands;
    private final MediaBodySlots bodySlots;
    private final MediaObjectStore store;
    private final MediaBodySpool spool;
    private final MediaProbeRequests probes;
    private final MediaClock clock;

    public SurveyMediaContentService(SurveyMediaCommandService commands, MediaBodySlots bodySlots,
                                     MediaObjectStore store, MediaBodySpool spool, MediaProbeRequests probes,
                                     MediaClock clock) {
        this.commands = commands;
        this.bodySlots = bodySlots;
        this.store = store;
        this.spool = spool;
        this.probes = probes;
        this.clock = clock;
    }

    @Override
    public MediaContentResult upload(SurveyViewer viewer, boolean viewerCanHold, UploadContentCommand command,
                                     ContentBody body) {
        ContentHeaders headers = MediaContentHeaders.check(command);
        ContentTarget target = commands.authorizeContent(viewer, command.mediaId());
        try (MediaBodySlots.Lease slots = bodySlots.acquire(viewer.userId())) {
            ContentClaim claim;
            try {
                claim = commands.claimContent(viewer, viewerCanHold, target, headers);
            } catch (RuntimeException refused) {
                drain(body, headers.length());
                throw refused;
            }
            if (claim.isReplay()) {
                drain(body, headers.length());
                return claim.replayOrNull();
            }
            return receiveAndCommit(viewer, viewerCanHold, target, headers, claim, body);
        }
    }

    private MediaContentResult receiveAndCommit(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
                                                ContentHeaders headers, ContentClaim claim, ContentBody body) {
        SpooledBody spooled;
        try {
            spooled = spool.receive(body.open(), headers.length(), claim.claimedAt().plus(BODY_DEADLINE));
        } catch (MediaBodyIncompleteException e) {
            commands.releaseContentClaim(target, claim.token());
            throw e;
        }
        try (spooled) {
            if (!spooled.sha256Hex().equals(headers.sha256Hex())) {
                commands.releaseContentClaim(target, claim.token());
                throw new MediaRequestMalformedException("Content-Digest", false);
            }
            write(target, claim, spooled);
        }
        try {
            return commands.commitContent(viewer, viewerCanHold, target, claim, headers.length());
        } catch (RuntimeException refused) {
            deleteQuietly(target, claim.receiveKey());
            commands.releaseContentClaim(target, claim.token());
            throw refused;
        }
    }

    /** One create-only write of known length (I-3); a store failure answers 503 and asks for a store probe (A-18). */
    private void write(ContentTarget target, ContentClaim claim, SpooledBody spooled) {
        try (InputStream content = spooled.open()) {
            MediaObjectStore.PutOutcome outcome = store.putCreateOnly(MediaObjectStore.Bucket.QUARANTINE,
                    claim.receiveKey(), content, spooled.length());
            if (outcome != MediaObjectStore.PutOutcome.CREATED) {
                // a fresh attempt key never exists: the store answered wrongly
                throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
            }
        } catch (StoreUnavailableException | IOException e) {
            String code = e instanceof StoreUnavailableException unavailable ? unavailable.code() : "STORE_IO";
            log.warn("Survey photo {} could not be stored ({})", target.mediaId(), code);
            deleteQuietly(target, claim.receiveKey());
            commands.releaseContentClaim(target, claim.token());
            probes.requestStoreProbe();
            throw new MediaStoreUnavailableException();
        }
    }

    private void deleteQuietly(ContentTarget target, String key) {
        try {
            store.delete(MediaObjectStore.Bucket.QUARANTINE, key);
        } catch (RuntimeException e) {
            // the orphan sweep deletes it later (spec A-17); ids only
            log.warn("Could not delete the upload attempt of survey photo {}", target.mediaId());
        }
    }

    /**
     * spec A-13: reads and discards the rest of the declared body (at most 20 MiB, never stored) so the client reads
     * the answer; a client that stops sending ends the drain at the container's read timeout or the deadline.
     */
    private void drain(ContentBody body, long declaredLength) {
        Instant deadline = clock.now().plus(BODY_DEADLINE);
        byte[] buffer = new byte[DRAIN_BUFFER];
        long left = declaredLength;
        try {
            InputStream in = body.open();
            while (left > 0 && clock.now().isBefore(deadline)) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                if (read < 0) {
                    return;
                }
                left -= read;
            }
        } catch (IOException | RuntimeException e) {
            // the client stopped sending: the answer goes out anyway
        }
    }
}
