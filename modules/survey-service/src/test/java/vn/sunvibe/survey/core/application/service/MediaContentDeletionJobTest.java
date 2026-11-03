package vn.sunvibe.survey.core.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.testsupport.MediaFixture;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;

/** The content deletion job (survey-media spec 4.7, A-17, A-23; plan Task A19) with in-memory fakes. */
class MediaContentDeletionJobTest {

    private final MediaServiceHarness h = new MediaServiceHarness();
    private final MediaContentDeletionJob job = new MediaContentDeletionJob(h.jobs, h.media, h.sets, h.store,
            h.health, h.clock, () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString(),
            h.transactions);

    @Test
    void due_deletesEveryObjectOfThePhotoOnly_marksDeletedBumpsTheSet() {
        SurveyMedia photo = removedReady();
        put("media/" + photo.getId() + "/1/FULL");
        put("media/" + photo.getId() + "/1/PREVIEW");
        put("media/" + photo.getId() + "/2/THUMBNAIL");
        put("media/01JOTHERPHOTO00000000000001/1/FULL");
        long before = setVersion();

        assertTrue(job.runOnce());

        assertEquals(List.of("MEDIA:media/01JOTHERPHOTO00000000000001/1/FULL"), List.copyOf(h.store.objects.keySet()));
        assertNotNull(h.media.stored(photo.getId()).orElseThrow().getContentDeletedAt());
        assertEquals(before + 1, setVersion());
        assertEquals(MediaJobState.DONE, deletionJob(photo).state());
        assertFalse(job.runOnce(), "nothing left");
    }

    /** spec A-23: held at the claim (a state the database refuses, re-checked all the same): nothing deleted. */
    @Test
    void heldAtClaim_deletesNothing() {
        SurveyMedia photo = MediaFixture.ready();
        photo.hold(MediaFixture.ADMIN, HoldReason.SECURITY_INCIDENT, MediaFixture.T0.plusSeconds(200));
        photo.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, MediaFixture.T0.plusSeconds(300));
        store(photo);
        h.jobs.upsertDeleteContent(photo.getId(), Instant.now().minusSeconds(1), Instant.now());
        put("media/" + photo.getId() + "/1/FULL");

        assertTrue(job.runOnce());

        assertEquals(1, h.store.objects.size());
        assertTrue(h.store.deleted.isEmpty());
        assertNull(h.media.stored(photo.getId()).orElseThrow().getContentDeletedAt());
        assertEquals(MediaJobState.CANCELLED, deletionJob(photo).state());
        assertEquals("HELD", deletionJob(photo).lastErrorCodeOrNull());
    }

    @Test
    void notDueYet_nothingClaimed() {
        SurveyMedia photo = removedReady();
        h.jobs.upsertDeleteContent(photo.getId(), Instant.now().plus(MediaContentDeletionJob.DELAY), Instant.now());

        assertFalse(job.runOnce());
        assertNull(h.media.stored(photo.getId()).orElseThrow().getContentDeletedAt());
    }

    @Test
    void storeUnavailable_retriesLater() {
        SurveyMedia photo = removedReady();
        put("media/" + photo.getId() + "/1/FULL");
        h.store.failLists = true;

        assertTrue(job.runOnce());

        MediaJob retried = deletionJob(photo);
        assertEquals(MediaJobState.PENDING, retried.state());
        assertEquals("STORE_UNAVAILABLE", retried.lastErrorCodeOrNull());
        assertTrue(retried.dueAt().isAfter(Instant.now()));
        assertNull(h.media.stored(photo.getId()).orElseThrow().getContentDeletedAt());
    }

    @Test
    void run_recordsTheDeletionHeartbeat() {
        job.runOnce();

        assertTrue(h.health.calls.contains("deletionBeat"));
    }

    // ==================== helpers ====================

    /** A READY photo of the harness checklist, removed by its holder, with its job due now. */
    private SurveyMedia removedReady() {
        SurveyMedia photo = MediaFixture.ready();
        photo.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, MediaFixture.T0.plusSeconds(300));
        store(photo);
        h.jobs.upsertDeleteContent(photo.getId(), Instant.now().minusSeconds(1), Instant.now());
        return photo;
    }

    private void store(SurveyMedia photo) {
        h.sets.insertIgnoringExisting(MediaFixture.CHECKLIST, Instant.now());
        h.media.put(photo);
    }

    private void put(String key) {
        h.store.putCreateOnly(Bucket.MEDIA, key, new ByteArrayInputStream(new byte[] {1, 2, 3}), 3);
    }

    private long setVersion() {
        return h.sets.findForRead(MediaFixture.CHECKLIST).orElseThrow().getSetVersion();
    }

    private MediaJob deletionJob(SurveyMedia photo) {
        return h.jobs.findByMedia(photo.getId()).get(0);
    }
}
