package vn.sunvibe.survey.core.domain.model.appointment;

import vn.sunvibe.survey.core.domain.exception.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.exception.PerformerMismatchException;
import vn.sunvibe.survey.core.domain.policy.SurveyText;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain Model - SurveyAppointment (Aggregate Root)
 * <p>
 * One booking of a site survey for an owner lead (survey-schedule US-1..US-3, spec 4.4): SCHEDULED until a visit is
 * recorded (VISITED) or it is cancelled (CANCELLED); both are final. While scheduled its start, duration, meeting
 * place, access note, on-site contact and the owner's confirmation may change; every change keeps a full revision and
 * an event (insert-only, survey BR-5). The surveyor is always the lead's assignee (C-1) and follows a reassignment
 * (US-2 AC-3); who held the appointment when is read from its events. Lead rules (status, visibility, one scheduled
 * appointment per lead) are checked by the caller; times are passed in as {@code now}. Revisions and events added
 * since the last save are pending until the repository has stored them.
 */
public final class SurveyAppointment {

    public static final int MAX_REASON_LENGTH = 500;
    public static final int MAX_CONFIRMATION_NOTE_LENGTH = 500;
    public static final int MAX_USER_DESCRIPTION_LENGTH = 500;
    public static final int MAX_SYSTEM_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_VISIT_NOTE_LENGTH = 2000;

    private final SurveyAppointmentId id;
    private final String leadId;
    private final int appointmentNo;
    private final AppointmentKind kind;
    private final SurveyAppointmentId previousVisitId;
    private final String resurveyReason;
    private final SurveyIntent intent;
    private final int intentCycle;
    private AppointmentStatus status;
    private AppointmentDetails details;
    private int currentRevisionNo;
    private final LeadLocationSnapshot leadLocation;
    private final String locationReason;
    private String surveyorUserId;
    private Confirmation confirmation;
    private VisitRecord visit;
    private Cancellation cancellation;
    private final String createdBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;                      // Optimistic lock: null until first saved
    private final List<AppointmentRevision> revisions;
    private final List<AppointmentEvent> events;
    private final List<AppointmentRevision> pendingRevisions = new ArrayList<>();
    private final List<AppointmentEvent> pendingEvents = new ArrayList<>();

    private SurveyAppointment(SurveyAppointmentId id, String leadId, int appointmentNo, AppointmentKind kind,
                              SurveyAppointmentId previousVisitId, String resurveyReason, SurveyIntent intent,
                              int intentCycle, AppointmentStatus status, AppointmentDetails details,
                              int currentRevisionNo, LeadLocationSnapshot leadLocation, String locationReason,
                              String surveyorUserId, Confirmation confirmation, VisitRecord visit,
                              Cancellation cancellation, String createdBy, Instant createdAt, Instant updatedAt,
                              Long version, List<AppointmentRevision> revisions, List<AppointmentEvent> events) {
        this.id = required(id, "id");
        this.leadId = required(leadId, "leadId");
        if (appointmentNo < 1 || intentCycle < 1 || currentRevisionNo < 1) {
            throw new IllegalArgumentException("Numbers of an appointment start at 1.");
        }
        this.appointmentNo = appointmentNo;
        this.kind = required(kind, "kind");
        if ((kind == AppointmentKind.RESURVEY) != (previousVisitId != null && resurveyReason != null)) {
            throw new IllegalArgumentException("A re-survey, and only a re-survey, links a visit and has a reason.");
        }
        this.previousVisitId = previousVisitId;
        this.resurveyReason = resurveyReason;
        this.intent = required(intent, "intent");
        this.intentCycle = intentCycle;
        this.status = required(status, "status");
        this.details = required(details, "details");
        this.currentRevisionNo = currentRevisionNo;
        this.leadLocation = required(leadLocation, "leadLocation");
        this.locationReason = locationReason;
        this.surveyorUserId = required(surveyorUserId, "surveyorUserId");
        this.confirmation = required(confirmation, "confirmation");
        if ((status == AppointmentStatus.VISITED) != (visit != null)
                || (status == AppointmentStatus.CANCELLED) != (cancellation != null)) {
            throw new IllegalArgumentException("A visit or a cancellation is set exactly for its status.");
        }
        this.visit = visit;
        this.cancellation = cancellation;
        this.createdBy = required(createdBy, "createdBy");
        this.createdAt = required(createdAt, "createdAt");
        this.updatedAt = required(updatedAt, "updatedAt");
        this.version = version;
        this.revisions = new ArrayList<>(required(revisions, "revisions"));
        this.events = new ArrayList<>(required(events, "events"));
    }

    /**
     * Book an appointment already agreed with the owner, or not yet confirmed (survey-schedule US-1, US-3 AC-2). The
     * caller has checked the lead: no scheduled appointment, a status and a rental intent that allow booking, the
     * kind (first survey or re-survey of the current visit) and that the assignee, who becomes the surveyor, may take
     * leads. Checked here, in this order: the re-survey reason, a reason when the meeting place differs from the lead's
     * place (survey-schedule BR-7), a start from the current minute to 90 days ahead (survey-schedule BR-4). The
     * confirmation is UNCONFIRMED with cause NEW unless {@code ownerConfirmed} (survey-schedule spec A-28); its note
     * may only come with a confirmation.
     *
     * @throws InvalidSurveyValueException field {@code confirmationNote}, {@code resurveyReason},
     *                                     {@code locationReason} or {@code startAt}
     */
    public static SurveyAppointment book(SurveyAppointmentId id, String leadId, int appointmentNo, AppointmentKind kind,
                                         SurveyAppointmentId previousVisitIdOrNull, String resurveyReasonOrNull,
                                         SurveyIntent intent, int intentCycle, LeadLocationSnapshot leadLocation,
                                         AppointmentDetails details, String locationReasonOrNull,
                                         String surveyorUserId, boolean ownerConfirmed,
                                         String confirmationNoteOrNull, String actorUserId, Instant now) {
        String note = SurveyText.optional("confirmationNote", confirmationNoteOrNull, MAX_CONFIRMATION_NOTE_LENGTH);
        if (note != null && !ownerConfirmed) {
            throw new InvalidSurveyValueException("confirmationNote",
                    "confirmationNote can only be given when the owner confirmed.");
        }
        String resurveyReason = null;
        if (kind == AppointmentKind.RESURVEY) {
            required(previousVisitIdOrNull, "previousVisitId");
            resurveyReason = SurveyText.require("resurveyReason", resurveyReasonOrNull, MAX_REASON_LENGTH);
        } else if (previousVisitIdOrNull != null) {
            throw new IllegalArgumentException("A first survey links no visit.");
        }
        String locationReason = details.meetingLocation().differsFrom(leadLocation)
                ? SurveyText.require("locationReason", locationReasonOrNull, MAX_REASON_LENGTH)
                : null;
        SurveyTimeRules.requireStartWindow(details.startAt(), now);

        Confirmation confirmation = ownerConfirmed
                ? new Confirmation(ConfirmationState.CONFIRMED, null, true, actorUserId, now, note)
                : new Confirmation(ConfirmationState.UNCONFIRMED, ConfirmationCause.NEW, false, actorUserId, now,
                        null);
        SurveyAppointment appointment = new SurveyAppointment(id, leadId, appointmentNo, kind,
                previousVisitIdOrNull, resurveyReason, intent, intentCycle, AppointmentStatus.SCHEDULED, details, 1,
                leadLocation, locationReason, surveyorUserId, confirmation, null, null, actorUserId, now, now, null,
                List.of(), List.of());
        appointment.addRevision(new AppointmentRevision(1, now, details, locationReason, actorUserId));
        appointment.addEvent(new AppointmentEvent(AppointmentEventType.BOOKED, actorUserId, now, 1, null,
                surveyorUserId, ownerConfirmed ? null : ConfirmationCause.NEW, null));
        if (ownerConfirmed) {
            appointment.addEvent(new AppointmentEvent(AppointmentEventType.CONFIRMED, actorUserId, now, 1, null, null,
                    null, note));
        }
        return appointment;
    }

    /**
     * Load from database (reconstruct), with every revision and event in the order they happened.
     */
    public static SurveyAppointment load(SurveyAppointmentId id, String leadId, int appointmentNo,
                                         AppointmentKind kind, SurveyAppointmentId previousVisitId,
                                         String resurveyReason, SurveyIntent intent, int intentCycle,
                                         AppointmentStatus status, AppointmentDetails details, int currentRevisionNo,
                                         LeadLocationSnapshot leadLocation, String locationReason,
                                         String surveyorUserId, Confirmation confirmation, VisitRecord visit,
                                         Cancellation cancellation, String createdBy, Instant createdAt,
                                         Instant updatedAt, Long version, List<AppointmentRevision> revisions,
                                         List<AppointmentEvent> events) {
        return new SurveyAppointment(id, leadId, appointmentNo, kind, previousVisitId, resurveyReason, intent,
                intentCycle, status, details, currentRevisionNo, leadLocation, locationReason, surveyorUserId,
                confirmation, visit, cancellation, createdBy, createdAt, updatedAt, version, revisions, events);
    }

    /**
     * Change the editable values with a reason (survey-schedule US-2 AC-1, spec A-4): a new revision keeps the full
     * new values and the earlier ones stay. A new start lies from the current minute to 90 days ahead; a kept start is
     * not checked again, so an overdue appointment can still change its note or place (survey-schedule BR-4). A change
     * of start or duration makes the appointment UNCONFIRMED with cause TIME_CHANGED, a change of place only with cause
     * LOCATION_CHANGED (survey-schedule spec A-28); other changes keep the confirmation.
     *
     * @throws InvalidSurveyValueException field {@code reason} or {@code startAt}; no field for "Nothing changed."
     * @throws AppointmentStateException   the appointment is not scheduled
     */
    public void change(AppointmentDetails newDetails, String reason, String actorUserId, Instant now) {
        String cleanReason = SurveyText.require("reason", reason, MAX_REASON_LENGTH);
        required(newDetails, "newDetails");
        requireScheduled();
        if (!newDetails.startAt().equals(details.startAt())) {
            SurveyTimeRules.requireStartWindow(newDetails.startAt(), now);
        }
        if (newDetails.equals(details)) {
            throw new InvalidSurveyValueException(null, "Nothing changed.");
        }
        ConfirmationCause cause = newDetails.timeDiffersFrom(details) ? ConfirmationCause.TIME_CHANGED
                : !newDetails.meetingLocation().equals(details.meetingLocation()) ? ConfirmationCause.LOCATION_CHANGED
                : null;
        currentRevisionNo++;
        details = newDetails;
        addRevision(new AppointmentRevision(currentRevisionNo, now, newDetails, cleanReason, actorUserId));
        if (cause != null) {
            confirmation = new Confirmation(ConfirmationState.UNCONFIRMED, cause, confirmation.everConfirmed(),
                    actorUserId, now, null);
        }
        addEvent(new AppointmentEvent(AppointmentEventType.CHANGED, actorUserId, now, currentRevisionNo, null, null,
                cause, null));
        updatedAt = now;
    }

    /**
     * Record that the owner confirmed the current time and place (survey-schedule US-1 AC-5), with an optional note.
     * Returns false, and changes nothing, when it is already confirmed (spec A-6).
     *
     * @throws InvalidSurveyValueException field {@code note}
     * @throws AppointmentStateException   the appointment is not scheduled
     */
    public boolean confirm(String actorUserId, String noteOrNull, Instant now) {
        String note = SurveyText.optional("note", noteOrNull, MAX_CONFIRMATION_NOTE_LENGTH);
        requireScheduled();
        if (confirmation.state() == ConfirmationState.CONFIRMED) {
            return false;
        }
        confirmation = new Confirmation(ConfirmationState.CONFIRMED, null, true, actorUserId, now, note);
        addEvent(new AppointmentEvent(AppointmentEventType.CONFIRMED, actorUserId, now, currentRevisionNo, null, null,
                null, note));
        updatedAt = now;
        return true;
    }

    /**
     * Remove the owner's confirmation (cause USER), with an optional note. Returns false, and changes nothing, when it
     * is already unconfirmed (survey-schedule spec A-6).
     *
     * @throws InvalidSurveyValueException field {@code note}
     * @throws AppointmentStateException   the appointment is not scheduled
     */
    public boolean unconfirm(String actorUserId, String noteOrNull, Instant now) {
        String note = SurveyText.optional("note", noteOrNull, MAX_CONFIRMATION_NOTE_LENGTH);
        requireScheduled();
        if (confirmation.state() == ConfirmationState.UNCONFIRMED) {
            return false;
        }
        confirmation = new Confirmation(ConfirmationState.UNCONFIRMED, ConfirmationCause.USER,
                confirmation.everConfirmed(), actorUserId, now, note);
        addEvent(new AppointmentEvent(AppointmentEventType.UNCONFIRMED, actorUserId, now, currentRevisionNo, null,
                null, ConfirmationCause.USER, note));
        updatedAt = now;
        return true;
    }

    /**
     * Cancel by staff with one of the five user reasons (survey-schedule US-2 AC-2); a description of 1..500
     * characters is required for OTHER and optional otherwise. An overdue appointment can be cancelled (BR-4).
     *
     * @throws InvalidSurveyValueException field {@code cancelReason} or {@code description}
     * @throws AppointmentStateException   the appointment is not scheduled
     */
    public void cancel(CancelReason reason, String descriptionOrNull, String actorUserId, Instant now) {
        if (reason == null || !reason.isUserChoice()) {
            throw new InvalidSurveyValueException("cancelReason",
                    "cancelReason must be one of the reasons staff may pick.");
        }
        String description = SurveyText.optional("description", descriptionOrNull, MAX_USER_DESCRIPTION_LENGTH);
        if (reason == CancelReason.OTHER && description == null) {
            throw new InvalidSurveyValueException("description", "description is required for the reason OTHER.");
        }
        requireScheduled();
        cancelWith(reason, description, actorUserId, now);
    }

    /**
     * Cancel because a lead command stopped the survey work (survey-schedule BR-3, spec 4.6): LEAD_ON_HOLD with the
     * hold note, LEAD_CLOSED with the close reason, INTENT_CHANGED without text. The text was checked by the lead
     * command; it is kept as typed (NFC, trimmed), up to 2000 characters.
     *
     * @throws AppointmentStateException the appointment is not scheduled
     */
    public void cancelBySystem(CancelReason reason, String textOrNull, String actorUserId, Instant now) {
        if (reason == null || reason.isUserChoice()) {
            throw new IllegalArgumentException("A system cancellation uses a system reason.");
        }
        String description = SurveyText.system(textOrNull, MAX_SYSTEM_DESCRIPTION_LENGTH);
        requireScheduled();
        cancelWith(reason, description, actorUserId, now);
    }

    /**
     * The lead was given to someone else: the appointment follows with the new surveyor and needs the owner's
     * confirmation again (survey-schedule US-2 AC-3, cause REASSIGNED). Nothing changes when the surveyor is the same.
     *
     * @throws AppointmentStateException the appointment is not scheduled
     */
    public void reassign(String newSurveyorUserId, String actorUserId, Instant now) {
        required(newSurveyorUserId, "newSurveyorUserId");
        requireScheduled();
        if (newSurveyorUserId.equals(surveyorUserId)) {
            return;
        }
        String previous = surveyorUserId;
        surveyorUserId = newSurveyorUserId;
        confirmation = new Confirmation(ConfirmationState.UNCONFIRMED, ConfirmationCause.REASSIGNED,
                confirmation.everConfirmed(), actorUserId, now, null);
        addEvent(new AppointmentEvent(AppointmentEventType.SURVEYOR_CHANGED, actorUserId, now, currentRevisionNo,
                previous, newSurveyorUserId, ConfirmationCause.REASSIGNED, null));
        updatedAt = now;
    }

    /**
     * Record that someone reached the property (survey-schedule US-3 AC-1, AC-1a, E-4, E-7; spec 4.8, A-8). The
     * arrival is a whole minute, not in the future, not before the minute of booking and at most 60 minutes before the
     * start of the revision in force at the arrival; the performer must have held the appointment then. An own record
     * is entered by the performer; a proxy record by someone else, with a reason and a basis dated from the arrival
     * date to today. The recording time is {@code now}; the lead keeps its status (the caller moves nothing).
     *
     * @throws InvalidSurveyValueException field {@code arrivedAt}, {@code note}, {@code proxy.reason} or
     *                                     {@code proxy.basis.confirmedOn}
     * @throws AppointmentStateException   the appointment is not scheduled
     * @throws PerformerMismatchException  the performer did not hold the appointment at the arrival
     */
    public void recordVisit(Instant arrivedAt, String note, String performedBy, String recordedBy,
                            VisitRecordKind recordKind, String proxyReasonOrNull, Basis basisOrNull, Instant now) {
        SurveyTimeRules.requireWholeMinute("arrivedAt", arrivedAt);
        String cleanNote = SurveyText.require("note", note, MAX_VISIT_NOTE_LENGTH);
        required(performedBy, "performedBy");
        required(recordedBy, "recordedBy");
        String proxyReason = null;
        if (recordKind == VisitRecordKind.OWN) {
            if (!performedBy.equals(recordedBy) || proxyReasonOrNull != null || basisOrNull != null) {
                throw new IllegalArgumentException("An own record is entered by the performer, without a basis.");
            }
        } else if (recordKind == VisitRecordKind.PROXY) {
            if (proxyReasonOrNull == null || basisOrNull == null) {
                throw new IllegalArgumentException("A proxy record has a reason and a basis.");
            }
            proxyReason = SurveyText.require("proxy.reason", proxyReasonOrNull, MAX_REASON_LENGTH);
        } else {
            throw new IllegalArgumentException("recordKind is required.");
        }
        requireScheduled();
        SurveyTimeRules.requireArrivalWindow(arrivedAt, now, createdAt);
        AppointmentRevision inForce = revisionInForceAt(arrivedAt);
        SurveyTimeRules.requireArrivalNotTooEarly(arrivedAt, inForce.details().startAt());
        if (basisOrNull != null) {
            basisOrNull.requireWithin("proxy.basis", SurveyTimeRules.vietnamDate(arrivedAt),
                    SurveyTimeRules.vietnamDate(now));
        }
        if (!performedBy.equals(holderAt(arrivedAt))) {
            throw new PerformerMismatchException();
        }
        visit = new VisitRecord(recordKind, performedBy, arrivedAt, inForce.revisionNo(), cleanNote, recordedBy, now,
                proxyReason, basisOrNull);
        status = AppointmentStatus.VISITED;
        addEvent(new AppointmentEvent(AppointmentEventType.VISITED, recordedBy, now, inForce.revisionNo(), null, null,
                null, null));
        updatedAt = now;
    }

    /**
     * The revision in force at a minute: the latest one whose own time, cut to the minute, is not after it (spec 4.8),
     * so an arrival entered in the booking minute finds revision 1.
     *
     * @throws IllegalStateException the minute is before the appointment was booked
     */
    public AppointmentRevision revisionInForceAt(Instant minute) {
        AppointmentRevision found = null;
        for (AppointmentRevision revision : revisions) {
            if (!SurveyTimeRules.minuteOf(revision.validFrom()).isAfter(minute)
                    && (found == null || revision.revisionNo() > found.revisionNo())) {
                found = revision;
            }
        }
        if (found == null) {
            throw new IllegalStateException("No revision was in force at that minute.");
        }
        return found;
    }

    /**
     * Who held the appointment at a minute, matched like {@link #revisionInForceAt}; null before the booking minute.
     */
    public String holderAt(Instant minute) {
        String found = null;
        for (Holder holder : holders()) {
            if (!SurveyTimeRules.minuteOf(holder.from()).isAfter(minute)) {
                found = holder.userId();
            }
        }
        return found;
    }

    /** Who held the appointment from when to when, oldest first: the booking and every reassignment. */
    public List<Holder> holders() {
        List<Holder> holders = new ArrayList<>();
        for (AppointmentEvent event : events) {
            if (event.type() == AppointmentEventType.BOOKED || event.type() == AppointmentEventType.SURVEYOR_CHANGED) {
                if (!holders.isEmpty()) {
                    Holder last = holders.remove(holders.size() - 1);
                    holders.add(new Holder(last.userId(), last.from(), event.occurredAt()));
                }
                holders.add(new Holder(event.toSurveyorUserId(), event.occurredAt(), null));
            }
        }
        return holders;
    }

    /**
     * Record the version the repository gave the appointment and forget the pending revisions and events it stored.
     */
    public void markPersisted(long persistedVersion) {
        if (version != null && persistedVersion < version) {
            throw new IllegalArgumentException("persistedVersion cannot go back.");
        }
        version = persistedVersion;
        pendingRevisions.clear();
        pendingEvents.clear();
    }

    private void cancelWith(CancelReason reason, String description, String actorUserId, Instant now) {
        cancellation = new Cancellation(reason, description, actorUserId, now);
        status = AppointmentStatus.CANCELLED;
        addEvent(new AppointmentEvent(AppointmentEventType.CANCELLED, actorUserId, now, currentRevisionNo, null, null,
                null, null));
        updatedAt = now;
    }

    private void requireScheduled() {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new AppointmentStateException(status.name());
        }
    }

    private void addRevision(AppointmentRevision revision) {
        revisions.add(revision);
        pendingRevisions.add(revision);
    }

    private void addEvent(AppointmentEvent event) {
        events.add(event);
        pendingEvents.add(event);
    }

    // ==================== Getters ====================

    public SurveyAppointmentId getId() {
        return id;
    }

    public String getLeadId() {
        return leadId;
    }

    public int getAppointmentNo() {
        return appointmentNo;
    }

    public AppointmentKind getKind() {
        return kind;
    }

    public SurveyAppointmentId getPreviousVisitId() {
        return previousVisitId;
    }

    public String getResurveyReason() {
        return resurveyReason;
    }

    public SurveyIntent getIntent() {
        return intent;
    }

    public int getIntentCycle() {
        return intentCycle;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public AppointmentDetails getDetails() {
        return details;
    }

    public int getCurrentRevisionNo() {
        return currentRevisionNo;
    }

    public LeadLocationSnapshot getLeadLocation() {
        return leadLocation;
    }

    public String getLocationReason() {
        return locationReason;
    }

    public String getSurveyorUserId() {
        return surveyorUserId;
    }

    public Confirmation getConfirmation() {
        return confirmation;
    }

    public VisitRecord getVisit() {
        return visit;
    }

    public Cancellation getCancellation() {
        return cancellation;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    /** Every revision, oldest first. */
    public List<AppointmentRevision> getRevisions() {
        return Collections.unmodifiableList(revisions);
    }

    /** Every event, in the order they happened. */
    public List<AppointmentEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /** The revisions added since the last save. */
    public List<AppointmentRevision> getPendingRevisions() {
        return List.copyOf(pendingRevisions);
    }

    /** The events added since the last save. */
    public List<AppointmentEvent> getPendingEvents() {
        return List.copyOf(pendingEvents);
    }

    /** Lead place, notes and contacts are private: never print them. */
    @Override
    public String toString() {
        return "SurveyAppointment[" + id + ", " + status + "]";
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
