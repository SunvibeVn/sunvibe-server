package vn.sunvibe.survey.core.application.service.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import vn.sunvibe.survey.core.application.command.media.ContentBody;
import vn.sunvibe.survey.core.application.command.media.CreateMediaCommand;
import vn.sunvibe.survey.core.application.command.media.UploadContentCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.media.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentTooLargeException;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.exception.media.MediaRequestMalformedException;
import vn.sunvibe.survey.core.application.exception.media.MediaServerBusyException;
import vn.sunvibe.survey.core.application.exception.media.MediaStoreUnavailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadStateException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobKind;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.testsupport.ChecklistFixture;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;
import vn.sunvibe.survey.core.testsupport.InMemoryBodySpool;
import vn.sunvibe.survey.core.testsupport.MediaFixture;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;

/**
 * The content upload (survey-media spec 4.4 step 2, the content order of 5.4, A-3, A-5, A-13, A-27; plan Task A14):
 * one test per step, the at-once answers reading nothing of the body and every later answer draining it first, and the
 * commit re-checking everything in a fresh transaction. Hand-written fakes.
 */
class SurveyMediaContentServiceTest {

    private static final String LEAD = ChecklistServiceHarness.LEAD;
    private static final String SALE_A = MediaServiceHarness.SALE_A;
    private static final String SALE_B = MediaServiceHarness.SALE_B;
    private static final byte[] BYTES = "jpeg bytes of the photo".getBytes(StandardCharsets.UTF_8);
    private static final String SHA = InMemoryBodySpool.sha256(BYTES);

    private final MediaServiceHarness h = new MediaServiceHarness();
    private final String checklist = h.checklist(LEAD);

    // ==================== (1) headers, at once ====================

    @Test
    void notOctetStream_415OnContentType_atOnce() {
        String id = created();
        h.journal.clear();
        ContentBody body = body(BYTES);
        MediaRequestMalformedException refused = assertThrows(MediaRequestMalformedException.class,
                () -> upload(new UploadContentCommand(id, "image/jpeg", BYTES.length, digest(BYTES)), body));

        assertEquals("Content-Type", refused.getField());
        assertTrue(refused.isUnsupportedMediaType());
        assertFalse(body.touched());
        assertEquals(List.of(), h.journal);
    }

    @Test
    void missingLengthOrDigest_400_atOnce() {
        String id = created();
        assertEquals("Content-Length", assertThrows(MediaRequestMalformedException.class,
                () -> upload(new UploadContentCommand(id, "application/octet-stream", -1, digest(BYTES)),
                        body(BYTES))).getField());
        for (String digest : new String[] {null, "sha-256=abc", "sha-256=:AAAA:", "md5=:" + b64(BYTES) + ":"}) {
            MediaRequestMalformedException refused = assertThrows(MediaRequestMalformedException.class,
                    () -> upload(new UploadContentCommand(id, "application/octet-stream", BYTES.length, digest),
                            body(BYTES)));
            assertEquals("Content-Digest", refused.getField(), String.valueOf(digest));
            assertFalse(refused.isUnsupportedMediaType());
        }
    }

    @Test
    void over20MiB_413_atOnce() {
        ContentBody body = body(BYTES);
        assertThrows(MediaContentTooLargeException.class, () -> upload(new UploadContentCommand(created(),
                "application/octet-stream", 20_971_521L, digest(BYTES)), body));
        assertFalse(body.touched());
    }

    @Test
    void digestWithOtherAlgorithmsToo_isAccepted() {
        MediaContentResult result = upload(new UploadContentCommand(created(), "application/octet-stream",
                BYTES.length, "sha-512=:" + b64(BYTES) + ":, " + digest(BYTES)), body(BYTES));

        assertEquals(MediaStatus.PROCESSING, result.media().status());
    }

    // ==================== (2) 404, (3) 403, (3b) 429 expensive, at once ====================

    @Test
    void unknownOrMalformedPhoto_404WithoutEvent_atOnce() {
        for (String id : new String[] {"01JMED1A999999999999999999", "not-an-id"}) {
            ContentBody body = body(BYTES);
            assertThrows(MediaNotFoundException.class, () -> upload(command(id), body));
            assertFalse(body.touched());
        }
        assertEquals(List.of(), h.base.securityEvents.all());
    }

    @Test
    void hiddenLead_404WithThrottledMediaEvent_atOnce() {
        String id = created();
        ContentBody body = body(BYTES);

        assertThrows(MediaNotFoundException.class, () -> h.contents.upload(ChecklistServiceHarness.own(SALE_B), false,
                command(id), body));

        assertFalse(body.touched());
        assertEquals(SecurityResourceType.MEDIA, h.base.securityEvents.all().get(0).resourceType());
        assertEquals(id, h.base.securityEvents.all().get(0).resourceId());
        assertEquals(SecurityOutcome.NOT_VISIBLE, h.base.securityEvents.all().get(0).outcome());
    }

    @Test
    void notAssignee_403WithEvent_atOnce() {
        String id = created();
        ContentBody body = body(BYTES);

        assertThrows(AccessDeniedException.class, () -> h.contents.upload(
                ChecklistServiceHarness.all(MediaServiceHarness.MANAGER), false, command(id), body));

        assertFalse(body.touched());
        assertEquals(SecurityOutcome.NOT_ASSIGNEE, h.base.securityEvents.all().get(0).outcome());
    }

    @Test
    void expensiveAtCap_429_beforeTheSlots_atOnce() {
        String id = created();
        h.counters.add(SALE_A, ReadBudgetArithmetic.hourStart(h.clock.now()), MediaCounterKind.EXPENSIVE, 20);
        ContentBody body = body(BYTES);

        MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class,
                () -> upload(command(id), body));

        assertEquals("expensive", refused.getField());
        assertFalse(body.touched());
        assertFalse(h.journal.contains("slot:take"), h.journal.toString());
    }

    // ==================== (4) body slots, at once ====================

    @Test
    void fourthBodyOfUser_429Bodies_atOnce() {
        String id = created();
        for (int i = 0; i < 3; i++) {
            h.slots.take(SALE_A, "01JOTHERINSTANCE0000000001", h.clock.now(), h.clock.now().plusSeconds(600));
        }
        ContentBody body = body(BYTES);

        MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class,
                () -> upload(command(id), body));

        assertEquals("bodies", refused.getField());
        assertTrue(refused.getRetryAfterSeconds() > 0);
        assertFalse(body.touched());
    }

    @Test
    void expiredSlotsOfAKilledInstance_doNotCount() {
        String id = created();
        for (int i = 0; i < 3; i++) {
            h.slots.take(SALE_A, "01JOTHERINSTANCE0000000001", h.clock.now().minus(Duration.ofMinutes(12)),
                    h.clock.now().minus(Duration.ofMinutes(1)));
        }

        assertEquals(MediaStatus.PROCESSING, upload(command(id), body(BYTES)).media().status());
    }

    @Test
    void instanceFull_503Busy_atOnce_userSlotFreed() throws Exception {
        String id = created();
        h.bodySlots.acquire(SALE_B);
        h.bodySlots.acquire(SALE_B);
        ContentBody body = body(BYTES);

        MediaServerBusyException refused = assertThrows(MediaServerBusyException.class,
                () -> upload(command(id), body));

        assertTrue(refused.getRetryAfterSeconds() > 0);
        assertFalse(body.touched());
        assertEquals(2, h.slots.countLive(SALE_B, h.clock.now()) + h.slots.countLive(SALE_A, h.clock.now()),
                "the refused upload's user slot is freed");
    }

    @Test
    void success_freesTheSlotAndDeletesTheSpool() {
        upload(command(created()), body(BYTES));

        assertEquals(0, h.slots.countLive(SALE_A, h.clock.now()));
        assertEquals(0, h.spool.open);
        assertEquals(1, h.spool.received);
    }

    // ==================== (5)..(7), drained ====================

    @Test
    void readOnly_409_drained() {
        String id = created();
        h.base.lead(LEAD, "ON_HOLD", SALE_A);

        assertDrained(ChecklistReadOnlyException.class, id);
    }

    @Test
    void ineligible_422_drained() {
        String id = created();
        h.base.directory.makeIneligible(SALE_A);

        assertDrained(SurveyorIneligibleException.class, id);
    }

    @Test
    void takeoverRequired_409_drained() {
        String id = created();
        h.base.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        ContentBody body = body(BYTES);

        assertThrows(ChecklistTakeoverRequiredException.class, () -> h.contents.upload(
                ChecklistServiceHarness.own(SALE_B), false, command(id), body));
        assertTrue(body.touched());
    }

    // ==================== (8) creator and removal ====================

    @Test
    void notCreator_409FieldUploadedBy() {
        SurveyMedia other = SurveyMedia.create(MediaFixture.newId(), checklist, MediaServiceHarness.newKey(), 9,
                MediaPlacement.asset(), EvidenceCategory.DETAIL, null, SALE_B, BYTES.length, SHA, h.clock.now());
        h.media.put(other);

        MediaNotAvailableException refused = assertDrained(MediaNotAvailableException.class,
                other.getId().getValue());
        assertEquals("uploadedBy", refused.getField());
    }

    @Test
    void removed_409FieldStatus() {
        String id = created();
        SurveyMedia photo = h.media.stored(SurveyMediaId.of(id)).orElseThrow();
        photo.remove(RemovalKind.HOLDER, SALE_A, null, null, h.clock.now());
        h.media.put(photo);

        assertEquals("status", assertDrained(MediaNotAvailableException.class, id).getField());
    }

    // ==================== (9) replay and upload state ====================

    @Test
    void sameDigestAfterSuccess_202ReplayWithoutHistory_drained() {
        String id = created();
        upload(command(id), body(BYTES));
        ContentBody again = body(BYTES);

        MediaContentResult replay = upload(command(id), again);

        assertTrue(again.touched());
        assertNull(replay.historyEntryOrNull());
        assertEquals(MediaStatus.PROCESSING, replay.media().status());
        assertEquals(1, h.spool.received, "a replay stores nothing");
        assertEquals(1, h.journal.stream().filter("lead:activity:CHECKLIST_MEDIA_ADDED"::equals).count());
    }

    @Test
    void otherDigestAfterSuccess_409AlreadyReceived() {
        String id = created();
        upload(command(id), body(BYTES));
        byte[] other = "other bytes".getBytes(StandardCharsets.UTF_8);

        MediaUploadStateException refused = assertThrows(MediaUploadStateException.class,
                () -> upload(new UploadContentCommand(id, "application/octet-stream", other.length, digest(other)),
                        body(other)));

        assertEquals(MediaUploadStateException.Kind.CONTENT_ALREADY_RECEIVED, refused.getKind());
    }

    @Test
    void receiving_409InProgress() {
        String id = created();
        SurveyMedia photo = h.media.stored(SurveyMediaId.of(id)).orElseThrow();
        photo.claim(MediaFixture.TOKEN, "quarantine/" + id + "/other", h.clock.now());
        h.media.put(photo);

        assertEquals(MediaUploadStateException.Kind.UPLOAD_IN_PROGRESS,
                assertDrained(MediaUploadStateException.class, id).getKind());
    }

    @Test
    void claimAfter60Minutes_409ExpiredBeforeTheSweep() {
        created();
        SurveyMedia old = SurveyMedia.create(MediaFixture.newId(), checklist, MediaServiceHarness.newKey(), 7,
                MediaPlacement.asset(), EvidenceCategory.DETAIL, null, SALE_A, BYTES.length, SHA,
                h.clock.now().minus(Duration.ofMinutes(61)));
        h.media.put(old);

        assertEquals(MediaUploadStateException.Kind.UPLOAD_EXPIRED,
                assertDrained(MediaUploadStateException.class, old.getId().getValue()).getKind());
        assertEquals(MediaStatus.AWAITING_CONTENT, h.media.stored(old.getId()).orElseThrow().getStatus(),
                "the sweep expires it later");
    }

    // ==================== (10) disabled, (11) paused, (12) declaration ====================

    @Test
    void uploadsOff_409_drained() {
        String id = created();
        h.switches.uploads = false;

        assertDrained(MediaUploadDisabledException.class, id);
    }

    @Test
    void paused_503_drained() {
        String id = created();
        h.health.pause(PauseReason.STORE, h.clock.now());

        assertDrained(MediaIntakePausedException.class, id);
    }

    @Test
    void lengthOtherThanSize_400_digestOtherThanDeclared_400() {
        String id = created();
        byte[] longer = "jpeg bytes of the photo, longer".getBytes(StandardCharsets.UTF_8);

        assertEquals("Content-Length", assertThrows(MediaRequestMalformedException.class,
                () -> upload(new UploadContentCommand(id, "application/octet-stream", longer.length, digest(BYTES)),
                        body(longer))).getField());
        byte[] same = "jpeg bytes of the phot0".getBytes(StandardCharsets.UTF_8);
        ContentBody body = body(same);
        assertEquals("Content-Digest", assertThrows(MediaRequestMalformedException.class,
                () -> upload(new UploadContentCommand(id, "application/octet-stream", same.length, digest(same)),
                        body)).getField());
        assertTrue(body.touched());
        assertEquals(0, h.spool.received);
    }

    // ==================== receive and store ====================

    @Test
    void bodyEndsEarly_400Body_claimReleased_nothingStored() {
        String id = created();
        UploadContentCommand command = command(id);

        assertThrows(MediaBodyIncompleteException.class, () -> upload(command, body(new byte[5])));

        assertEquals(MediaStatus.AWAITING_CONTENT, h.media.stored(SurveyMediaId.of(id)).orElseThrow().getStatus());
        assertTrue(h.store.objects.isEmpty());
        assertEquals(0, h.slots.countLive(SALE_A, h.clock.now()));
    }

    @Test
    void bodyNotMatchingDigest_400NothingStored_claimReleased() {
        String id = created();
        byte[] tampered = BYTES.clone();
        tampered[0] = 'J';

        MediaRequestMalformedException refused = assertThrows(MediaRequestMalformedException.class,
                () -> upload(command(id), body(tampered)));

        assertEquals("Content-Digest", refused.getField());
        assertTrue(h.store.objects.isEmpty());
        assertEquals(0, h.spool.open);
        assertEquals(MediaStatus.AWAITING_CONTENT, h.media.stored(SurveyMediaId.of(id)).orElseThrow().getStatus());
    }

    @Test
    void storeFailure_503StoreUnavailable_probeRequested_claimReleased() {
        String id = created();
        h.store.failPuts = true;

        assertThrows(MediaStoreUnavailableException.class, () -> upload(command(id), body(BYTES)));

        assertEquals(1, h.probes.store);
        assertEquals(MediaStatus.AWAITING_CONTENT, h.media.stored(SurveyMediaId.of(id)).orElseThrow().getStatus());
        assertEquals(0, h.spool.open);
        assertEquals(1, h.store.deleted.size(), "a partial object is deleted");
    }

    // ==================== commit ====================

    @Test
    void commit_processingJobHistoryAndSetVersion_inTheLockOrder() {
        String id = created();
        long before = h.sets.stored(checklist).orElseThrow().getSetVersion();
        h.journal.clear();

        MediaContentResult result = upload(command(id), body(BYTES));

        SurveyMedia stored = h.media.stored(SurveyMediaId.of(id)).orElseThrow();
        assertEquals(MediaStatus.PROCESSING, stored.getStatus());
        assertNotNull(stored.getContentReceivedAt());
        assertTrue(h.store.objects.containsKey("QUARANTINE:" + stored.getSourceStorageKey()));
        assertTrue(stored.getSourceStorageKey().startsWith("quarantine/" + id + "/"));
        assertEquals(MediaJobKind.PROCESS, h.jobs.findByMedia(SurveyMediaId.of(id)).get(0).kind());
        assertEquals("CHECKLIST_MEDIA_ADDED", result.historyEntryOrNull().type());
        assertEquals(before + 2, result.setVersion(), "claim and commit bump the set version");
        List<String> j = h.journal;
        int commitStart = j.lastIndexOf("lead:share");
        assertTrue(commitStart > j.indexOf("store:put"), j.toString());
        List<String> commit = j.subList(commitStart, j.size());
        int checklistLock = commit.indexOf("checklist:share");
        int g1 = commit.indexOf("group:share:" + ChecklistGroup.IDENTIFICATION);
        int set = commit.indexOf("set:lock");
        int photo = commit.indexOf("media:lock:[" + id + "]");
        int job = commit.indexOf("job:upsert:PROCESS");
        int activity = commit.indexOf("lead:activity:CHECKLIST_MEDIA_ADDED");
        assertTrue(0 < checklistLock && checklistLock < g1 && g1 < set && set < photo && photo < job
                && job < activity, commit.toString());
    }

    @Test
    void commit_reassignedDuringStream_404_objectDeletedNothingAttached() {
        String id = created();
        h.spool.afterRead = () -> h.base.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);

        // a SALE sees only their own leads: after the reassignment the lead is hidden from them (SM-7)
        assertThrows(MediaNotFoundException.class, () -> upload(command(id), body(BYTES)));
        assertEquals(SecurityOutcome.NOT_VISIBLE, h.base.securityEvents.all().get(0).outcome());

        SurveyMedia stored = h.media.stored(SurveyMediaId.of(id)).orElseThrow();
        assertEquals(MediaStatus.AWAITING_CONTENT, stored.getStatus());
        assertTrue(h.store.objects.isEmpty(), "the attempt's object is deleted");
        assertFalse(h.journal.contains("lead:activity:CHECKLIST_MEDIA_ADDED"));
    }

    @Test
    void commit_hashBecameSelectedMeanwhile_409Duplicate_objectDeleted() {
        String id = created();
        h.spool.afterRead = () -> h.media.put(SurveyMedia.create(MediaFixture.newId(), checklist,
                MediaServiceHarness.newKey(), 50, MediaPlacement.asset(), EvidenceCategory.DETAIL, null, SALE_A, 10L,
                SHA, h.clock.now()));

        MediaContentIdentityException refused = assertThrows(MediaContentIdentityException.class,
                () -> upload(command(id), body(BYTES)));

        assertEquals(IdentityVerdict.Kind.DUPLICATE_CONTENT, refused.getKind());
        assertTrue(h.store.objects.isEmpty());
        assertEquals(MediaStatus.AWAITING_CONTENT, h.media.stored(SurveyMediaId.of(id)).orElseThrow().getStatus());
    }

    @Test
    void commit_spaceRemovedFromG1Meanwhile_400SpaceId() {
        CreateMediaCommand inSpace = new CreateMediaCommand(checklist, MediaServiceHarness.newKey(),
                ChecklistFixture.SPACE_1, EvidenceCategory.MAIN_SPACE, null, (long) BYTES.length, SHA);
        String id = h.commands.create(ChecklistServiceHarness.own(SALE_A), false, inSpace).media().id();
        h.spool.afterRead = () -> h.g1(checklist, ChecklistFixture.identificationParts(
                ChecklistFixture.observedSpace(ChecklistFixture.SPACE_2, "B2")));

        assertThrows(vn.sunvibe.survey.core.domain.exception.ChecklistValidationException.class,
                () -> upload(command(id), body(BYTES)));
        assertTrue(h.store.objects.isEmpty());
    }

    // ==================== helpers ====================

    private String created() {
        return h.commands.create(ChecklistServiceHarness.own(SALE_A), false, new CreateMediaCommand(checklist,
                MediaServiceHarness.newKey(), null, EvidenceCategory.EXTERIOR_ACCESS, null, (long) BYTES.length,
                SHA)).media().id();
    }

    private MediaContentResult upload(UploadContentCommand command, ContentBody body) {
        return h.contents.upload(ChecklistServiceHarness.own(SALE_A), false, command, body);
    }

    private <T extends RuntimeException> T assertDrained(Class<T> type, String id) {
        ContentBody body = body(BYTES);
        T refused = assertThrows(type, () -> upload(command(id), body));
        assertTrue(body.touched(), "a later error drains the body first");
        assertEquals(0, h.spool.received, "a refused body is never stored");
        assertEquals(0, h.slots.countLive(SALE_A, h.clock.now()), "the slot is freed");
        return refused;
    }

    private static UploadContentCommand command(String id) {
        return new UploadContentCommand(id, "application/octet-stream", BYTES.length, digest(BYTES));
    }

    private static ContentBody body(byte[] bytes) {
        return new ContentBody(new ByteArrayInputStream(bytes));
    }

    private static String digest(byte[] bytes) {
        return "sha-256=:" + b64(bytes) + ":";
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(InMemoryBodySpool.sha256(bytes)));
    }
}
