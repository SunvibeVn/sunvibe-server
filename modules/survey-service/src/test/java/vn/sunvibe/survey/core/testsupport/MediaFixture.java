package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Survey photos in each state of survey-media spec 4.2, built through the aggregate's own moves. */
public final class MediaFixture {

    public static final Instant T0 = Instant.parse("2026-11-01T02:00:00Z");
    public static final String CHECKLIST = "01JCHECK1157000000000000A1";
    public static final String SALE = "01JSA1E0000000000000000001";
    public static final String OTHER_SALE = "01JSA1E0000000000000000002";
    public static final String ADMIN = "01JADM1N000000000000000001";
    public static final String TOKEN = "01JT0KEN000000000000000001";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private MediaFixture() {
    }

    /** A 64-hex hash, different per n. */
    public static String sha(int n) {
        return String.format("%064x", n);
    }

    public static SurveyMediaId newId() {
        return SurveyMediaId.of(String.format("01JMED1A%018d", SEQUENCE.incrementAndGet()));
    }

    /** A photo of the asset's exterior by SALE, created at T0. */
    public static SurveyMedia awaiting() {
        return awaiting(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset(), SALE, sha(1));
    }

    public static SurveyMedia awaiting(EvidenceCategory category, MediaPlacement placement, String uploader,
                                       String sha256) {
        return awaitingOn(CHECKLIST, category, placement, uploader, sha256);
    }

    /** A photo of another checklist (the checklist service tests start their own). */
    public static SurveyMedia awaitingOn(String checklistId, EvidenceCategory category, MediaPlacement placement,
                                         String uploader, String sha256) {
        int n = SEQUENCE.incrementAndGet();
        return SurveyMedia.create(newId(), checklistId, String.format("01JUPK%020d", n), n, placement, category,
                null, uploader, 1000L, sha256, T0);
    }

    public static SurveyMedia receiving() {
        SurveyMedia media = awaiting();
        media.claim(TOKEN, "quarantine/" + media.getId() + "/1", T0.plusSeconds(60));
        return media;
    }

    public static SurveyMedia processing() {
        return processing(awaiting());
    }

    public static SurveyMedia processing(SurveyMedia awaiting) {
        awaiting.claim(TOKEN, "quarantine/" + awaiting.getId() + "/1", T0.plusSeconds(60));
        awaiting.commit("quarantine/" + awaiting.getId() + "/1", awaiting.getDeclaredSizeBytes(), T0.plusSeconds(90));
        return awaiting;
    }

    public static SurveyMedia ready() {
        return ready(awaiting());
    }

    public static SurveyMedia ready(SurveyMedia awaiting) {
        SurveyMedia media = processing(awaiting);
        media.ready(variants(1), SourceFormat.JPEG, T0.plusSeconds(120));
        return media;
    }

    public static SurveyMedia rejected() {
        SurveyMedia media = processing();
        media.reject(MediaStatusReason.UNREADABLE, T0.plusSeconds(120));
        return media;
    }

    public static SurveyMedia failed() {
        SurveyMedia media = processing();
        media.fail(T0.plusSeconds(120));
        return media;
    }

    public static SurveyMedia cancelled() {
        SurveyMedia media = processing();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(100));
        media.cancel(T0.plusSeconds(120));
        return media;
    }

    public static SurveyMedia expired() {
        SurveyMedia media = awaiting();
        media.expire(T0.plusSeconds(3600));
        return media;
    }

    public static SurveyMedia held(SurveyMedia media, HoldReason reason) {
        media.hold(ADMIN, reason, T0.plusSeconds(300));
        return media;
    }

    public static MediaRemovalCorrection correction() {
        return new MediaRemovalCorrection("Ảnh sai vị trí", new Basis(LocalDate.parse("2026-10-31"),
                "Chủ nhà xác nhận qua điện thoại"));
    }

    public static List<MediaVariant> variants(int attempt) {
        return List.of(variant(VariantKind.FULL, attempt, 4000, 3000),
                variant(VariantKind.PREVIEW, attempt, 1600, 1200),
                variant(VariantKind.THUMBNAIL, attempt, 400, 300));
    }

    public static MediaVariant variant(VariantKind kind, int attempt, int width, int height) {
        return new MediaVariant(kind, attempt, "media/x/" + attempt + "/" + kind, sha(kind.ordinal() + 100), 1000L,
                width, height, "dev-8.15.1", T0.plusSeconds(110));
    }
}
