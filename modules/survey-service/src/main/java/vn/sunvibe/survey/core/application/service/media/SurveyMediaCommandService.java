package vn.sunvibe.survey.core.application.service.media;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.survey.core.application.command.media.CreateMediaCommand;
import vn.sunvibe.survey.core.application.command.media.DeselectMediaCommand;
import vn.sunvibe.survey.core.application.command.media.RemoveMediaCommand;
import vn.sunvibe.survey.core.application.exception.media.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.application.query.media.MediaCreateResult;
import vn.sunvibe.survey.core.application.query.media.MediaStateResult;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.media.IntakeGate;
import vn.sunvibe.survey.core.application.shared.media.MediaContentHeaders.ContentHeaders;
import vn.sunvibe.survey.core.application.shared.media.MediaInputs;
import vn.sunvibe.survey.core.application.shared.media.MediaInputs.CreateInput;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.media.MediaDuplicateContentException;
import vn.sunvibe.survey.core.domain.exception.media.MediaUploadKeyTakenException;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;

/**
 * Survey photo commands (survey-media spec 4.4, 5.4). Each command runs in one READ COMMITTED transaction with the
 * locks of survey-media spec 5.4 in their global order; the transactions are programmatic because a refusal has work to do after
 * its rollback: a stale sweep is recorded as a pause (A-18), and a unique violation at insert is answered from a fresh
 * transaction (the replay, or the matching photo of the A-16 backstop), never a 500. This class owns every
 * transaction scope; the work inside them is in {@link MediaUploadSteps}, {@link MediaRemovalSteps} and
 * {@link MediaHoldSteps}.
 */
@Service
public class SurveyMediaCommandService implements SurveyMediaCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(SurveyMediaCommandService.class);

    private final MediaUploadSteps uploads;
    private final MediaRemovalSteps removals;
    private final MediaHoldSteps holds;
    private final SecurityEventRecorder events;
    private final IntakeGate intakeGate;
    private final TransactionTemplate commandTransaction;
    private final TransactionTemplate answerTransaction;

    public SurveyMediaCommandService(MediaUploadSteps uploads, MediaRemovalSteps removals, MediaHoldSteps holds,
                                     SecurityEventRecorder events, IntakeGate intakeGate,
                                     PlatformTransactionManager transactionManager) {
        this.uploads = uploads;
        this.removals = removals;
        this.holds = holds;
        this.events = events;
        this.intakeGate = intakeGate;
        this.commandTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction.setReadOnly(true);
    }

    /**
     * Create order (spec 5.4): (1) body, before any lookup → (2) 404 checklist or lead not visible → (3) 403 not the
     * assignee → (4) 409 read-only → (5) 422 ineligible → (6) 409 takeover required → (7) replay → (8) 409 uploads off
     * → (9) 503 intake paused → (10) 400 placement against the current G1 → (11) 429 hourly records, bytes and
     * expensive outcomes → (12) 409 content identity (A-16) → (13) 409 selected or pending limit. Locks: lead and
     * checklist shared, the G1 row shared, the user's advisory lock, the set row; the replay is checked again after
     * the advisory and set locks, since a twin may have committed meanwhile.
     */
    @Override
    public MediaCreateResult create(SurveyViewer viewer, boolean viewerCanHold, CreateMediaCommand command) {
        CreateInput input = MediaInputs.create(command);
        try {
            return inCommand(() -> uploads.createInTransaction(viewer, viewerCanHold, input));
        } catch (MediaIntakePausedException e) {
            intakeGate.recordRefusal(e);
            throw e;
        } catch (MediaUploadKeyTakenException e) {
            // spec 4.4 step 1: a twin with this key committed between the re-check and the insert: its replay
            return answerTransaction.execute(status -> uploads.replay(viewer, viewerCanHold, input, null)
                    .orElseThrow(() -> new IllegalStateException("The photo of the upload key is gone.", e)));
        } catch (MediaDuplicateContentException e) {
            // spec A-16 (a): the partial unique index refused a second selected photo with the hash
            String matching = answerTransaction.execute(status -> uploads.selectedWithHash(input));
            throw new MediaContentIdentityException(IdentityVerdict.Kind.DUPLICATE_CONTENT, matching);
        }
    }

    // ==================== the content upload (spec 4.4 step 2) ====================

    /** The photo of a content upload and its checklist, after the authority checks. */
    public record ContentTarget(SurveyMediaId mediaId, String checklistId) {
    }

    /** The claim of one upload attempt, or the replay answer when these bytes were already received. */
    public record ContentClaim(String token, String receiveKey, Instant claimedAt, MediaContentResult replayOrNull) {

        public boolean isReplay() {
            return replayOrNull != null;
        }

        /** The token and the key are not logged. */
        @Override
        public String toString() {
            return "ContentClaim[" + (isReplay() ? "replay" : claimedAt) + "]";
        }
    }

    /**
     * Content order (2) to (3b) (spec 5.4), answered before the body slots: an unknown or malformed photo 404 without
     * an event; its lead hidden 404 and the caller not the assignee 403, each with a throttled event of resource MEDIA;
     * the uploader at the A-27 cap of expensive outcomes 429. One short transaction.
     */
    public ContentTarget authorizeContent(SurveyViewer viewer, String mediaId) {
        if (!SurveyMediaId.isValid(mediaId)) {
            throw new MediaNotFoundException();
        }
        SurveyMediaId id = SurveyMediaId.of(mediaId);
        return inCommand(() -> uploads.authorizeInTransaction(viewer, id, mediaId));
    }

    /**
     * Content order (5) to (12) and the claim (spec 5.4, 4.4 step 2): read-only 409, ineligible 422, takeover required
     * 409, not the creator or removed 409 MEDIA_NOT_AVAILABLE, the upload state (a replay of the same bytes, other
     * bytes received, in progress, expired), uploads off 409, intake paused 503, then the headers against the
     * declaration 400; the claim is a conditional update under the set lock that also enforces the 60-minute window.
     */
    public ContentClaim claimContent(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
                                     ContentHeaders headers) {
        try {
            return inCommand(() -> uploads.claimInTransaction(viewer, viewerCanHold, target, headers));
        } catch (MediaIntakePausedException e) {
            intakeGate.recordRefusal(e);
            throw e;
        }
    }

    /**
     * An aborted or refused upload gives its claim back (spec 4.4 step 2): AWAITING_CONTENT again when it still holds
     * this token, the set version bumped. A failure is logged with ids only; the sweeper releases a stale claim later.
     */
    public void releaseContentClaim(ContentTarget target, String token) {
        try {
            commandTransaction.executeWithoutResult(status -> uploads.releaseInTransaction(target, token));
        } catch (RuntimeException e) {
            log.error("Could not release the upload claim of survey photo {}", target.mediaId(), e);
        }
    }

    /**
     * The commit, the completion point of BR-17 (spec 4.4 step 2): a fresh transaction re-checks (2) to (11), the
     * creator, the claim token, the placement in the current G1 and the content identity of A-16, in the lock order
     * of 5.4 (lead and checklist shared, G1 shared, set, photo, job, activity); then PROCESSING with the source key,
     * the PROCESS job, the History entry CHECKLIST_MEDIA_ADDED with the category and a set version bump.
     */
    public MediaContentResult commitContent(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
                                            ContentClaim claim, long sizeBytes) {
        try {
            return inCommand(() -> uploads.commitInTransaction(viewer, viewerCanHold, target, claim, sizeBytes));
        } catch (MediaIntakePausedException e) {
            intakeGate.recordRefusal(e);
            throw e;
        }
    }

    // ==================== removal (spec 4.7) ====================

    /**
     * Removal order (spec 5.4): (1) the correction's shape, before any lookup → (2) 404 → (3) 403 not the assignee →
     * (4) 409 read-only → (5) 422 ineligible → (6) 409 takeover required; then under the set and photo locks (7) an
     * already removed photo answers as it is → (8) 409 referenced → (9) 400 correction missing where BR-12 needs it,
     * or its date out of range; then the removal: the deletion request and its job (due 10 minutes later, A-17) only
     * for a READY photo that is not held (A-23), the set version bumped, and the History row of a PROCESSING or READY
     * photo (A-10).
     */
    @Override
    public MediaContentResult remove(SurveyViewer viewer, boolean viewerCanHold, RemoveMediaCommand command) {
        List<Violation> violations = new ArrayList<>();
        ReasonBasis correction = command.correctionOrNull() == null ? null : ChecklistInputs.reasonAndBasis(
                "correction.", command.correctionOrNull().reason(), command.correctionOrNull().basis(), violations);
        ChecklistInputs.throwIfAny(violations);
        if (!SurveyMediaId.isValid(command.mediaId())) {
            throw new MediaNotFoundException();
        }
        SurveyMediaId id = SurveyMediaId.of(command.mediaId());
        return inCommand(() -> removals.removeInTransaction(viewer, viewerCanHold, id, correction));
    }

    // ==================== deselection (survey-checklist submission spec 4.6, 5.4, A-12) ====================

    /**
     * Deselection order (survey-checklist submission spec 5.5, as a removal, on the revision of the path): (1) the correction's shape,
     * before any lookup → (2) 404 the revision or its lead not visible → (3) 403 not the assignee → 404 a photo not
     * carried into the revision, from a scalar read before any lock, so another lead's rows are never touched and its
     * photos never told apart → (4) 409 read-only → (5) 422 ineligible → (6) 409 takeover required; then under the set
     * and photo locks an already deselected photo changes nothing → (9) 400 correction missing where survey-media BR-12
     * needs it, or its date out of range. The photo row is only read: it is never removed and no deletion is asked for.
     * The History row is a removal's.
     *
     * @return whether the photo left the revision now; false for a repeat
     */
    @Override
    public boolean deselect(SurveyViewer viewer, DeselectMediaCommand command) {
        List<Violation> violations = new ArrayList<>();
        ReasonBasis correction = command.correctionOrNull() == null ? null : ChecklistInputs.reasonAndBasis(
                "correction.", command.correctionOrNull().reason(), command.correctionOrNull().basis(), violations);
        ChecklistInputs.throwIfAny(violations);
        if (!SurveyMediaId.isValid(command.mediaId())) {
            throw new MediaNotFoundException();
        }
        SurveyMediaId id = SurveyMediaId.of(command.mediaId());
        return inCommand(() -> removals.deselectInTransaction(viewer, command.checklistId(), id, correction));
    }

    // ==================== security hold (spec A-23) ====================

    /**
     * Hold order (spec 5.4): (1) the reason, before any lookup (the role is checked before this call) → (3) 404 →
     * under the lead and checklist share locks and the set and photo locks: (4) already held answers the photo as it
     * is, nothing bumped and no event → (5) 409 MEDIA_NOT_AVAILABLE field status for a photo that is removed or not
     * PROCESSING or READY; then the hold, the set version bumped and one HOLD/HELD event in the transaction.
     */
    @Override
    public MediaStateResult hold(SurveyViewer viewer, String mediaId, HoldReason reasonOrNull) {
        if (reasonOrNull == null) {
            throw new ChecklistValidationException(List.of(new Violation("reason", "reason is required.")));
        }
        SurveyMediaId id = adminTarget(mediaId);
        return inCommand(() -> holds.holdInTransaction(viewer, id, reasonOrNull));
    }

    /**
     * Release order (spec 5.4): (3) 404 → under the locks (4) not held answers the photo as it is → the release
     * ({@code hold_released_at} not before {@code held_at}), for a removed READY photo its deletion request and job in
     * the same transaction, the set version bumped and one RELEASE/RELEASED event.
     */
    @Override
    public MediaStateResult release(SurveyViewer viewer, String mediaId) {
        SurveyMediaId id = adminTarget(mediaId);
        return inCommand(() -> holds.releaseInTransaction(viewer, id));
    }

    private static SurveyMediaId adminTarget(String mediaId) {
        if (!SurveyMediaId.isValid(mediaId)) {
            throw new MediaNotFoundException();
        }
        return SurveyMediaId.of(mediaId);
    }

    /** One command transaction; its deferred refusal events are written once it released its connection. */
    private <T> T inCommand(java.util.function.Supplier<T> work) {
        try {
            return commandTransaction.execute(status -> work.get());
        } finally {
            events.flushDeferred();
        }
    }
}
