package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.media.MediaLimits;

import java.util.ArrayList;
import java.util.List;

/**
 * The gates of a submission after the editability checks (survey-checklist submission spec 4.4, US-2 AC-1, D3-2, D3-7, D3-11),
 * in order: S2 uploads and submission on, S3 no SCHEDULED appointment, S4 no missing item, S5 no selected photo
 * pending, S6 at most 100 selected, S7 every selected photo not held and with its content. Warnings and unconfirmed
 * terms never block a submit (US-2 AC-2). The readiness item of an unavailable photo belongs to S7, so S4 leaves it out
 * and the first failing gate names what a person can act on.
 */
public final class SubmissionGate {

    /** S2, answered by itself: 409 CHECKLIST_SUBMISSION_NOT_AVAILABLE. */
    public static final String SUBMISSION = "submission";
    private static final String APPOINTMENT = "appointment";
    private static final String MISSING = "readiness.missing";
    private static final String PENDING = "media.pending";
    private static final String LIMIT = "media.limit";
    private static final String UNAVAILABLE = "media.unavailable";

    private SubmissionGate() {
    }

    /** Every failing gate, one entry each, in the order of spec 4.4; empty when the revision can be submitted. */
    public static List<GateFailure> check(GateFacts facts) {
        List<GateFailure> failures = new ArrayList<>();
        if (!facts.uploadsOn() || !facts.submissionOn()) {
            failures.add(new GateFailure(SUBMISSION));
        }
        if (facts.scheduledAppointment()) {
            failures.add(new GateFailure(APPOINTMENT));
        }
        if (facts.missing().stream().anyMatch(item -> item.problem() != MissingProblem.EVIDENCE_UNAVAILABLE)) {
            failures.add(new GateFailure(MISSING));
        }
        List<SurveyMedia> selected = facts.photos().stream().filter(SurveyMedia::isSelected).toList();
        if (selected.stream().anyMatch(SurveyMedia::isPending)) {
            failures.add(new GateFailure(PENDING));
        }
        if (selected.size() > MediaLimits.MAX_SELECTED) {
            failures.add(new GateFailure(LIMIT));
        }
        if (selected.stream().anyMatch(SubmissionGate::unavailable)) {
            failures.add(new GateFailure(UNAVAILABLE));
        }
        return List.copyOf(failures);
    }

    /** S7, also the readiness item: a selected photo held or without its content (D3-2). */
    public static boolean unavailable(SurveyMedia photo) {
        return photo.isSelected() && (photo.isHeld() || !photo.isContentPresent());
    }

    /**
     * The read's {@code submissionUnavailableReason}, the first failing gate, null when none fails. Without photos the
     * evidence reason stands first, as it read before submission existed (spec S-19).
     *
     * @param photosAvailable the revision's photos are available (survey-media spec A-1)
     */
    public static String unavailableReason(GateFacts facts, boolean photosAvailable) {
        if (!photosAvailable) {
            return Readiness.EVIDENCE_NOT_AVAILABLE;
        }
        if (!facts.submissionOn()) {
            return Readiness.SUBMISSION_NOT_AVAILABLE;
        }
        if (!facts.uploadsOn()) {
            return Readiness.EVIDENCE_NOT_AVAILABLE;
        }
        List<GateFailure> failures = check(facts);
        if (failures.isEmpty()) {
            return null;
        }
        return switch (failures.get(0).field()) {
            case APPOINTMENT -> Readiness.APPOINTMENT_SCHEDULED;
            case MISSING -> Readiness.MISSING_ITEMS;
            case PENDING -> Readiness.PHOTOS_NOT_FINISHED;
            case LIMIT -> Readiness.PHOTO_LIMIT;
            default -> Readiness.PHOTO_UNAVAILABLE;
        };
    }
}
