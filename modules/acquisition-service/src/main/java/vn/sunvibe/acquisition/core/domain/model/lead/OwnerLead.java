package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.exception.IntentChangeNotAllowedException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.shared.text.PhoneNumberDetector;
import vn.sunvibe.shared.time.AppClock;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Domain Model - OwnerLead (Aggregate Root)
 * <p>
 * An owner who asked Sunvibe to take over or manage a property through the intake form
 * (acquisition-owner-intake BR-1..BR-5). Sending the form never creates a property or a listing. Contact data,
 * address and coordinates are private (acquisition module brief BR-2).
 */
public final class OwnerLead {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_NOTE_LENGTH = 2000;
    private static final List<OwnerLeadStatus> CLOSING_STATUSES =
            List.of(OwnerLeadStatus.DUPLICATE, OwnerLeadStatus.REJECTED, OwnerLeadStatus.WITHDRAWN);
    /** survey-schedule US-5, spec 4.6: the statuses in which staff may record a new requirement. */
    public static final List<OwnerLeadStatus> INTENT_CHANGE_STATUSES = List.of(OwnerLeadStatus.ASSIGNED,
            OwnerLeadStatus.CONTACTED, OwnerLeadStatus.ON_HOLD, OwnerLeadStatus.SURVEY_SCHEDULED);
    /** Dates staff type (the basis of a requirement change) are Vietnam dates (survey-schedule BR-4). */
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * The one message for every refused {@code duplicateOfLeadId}: missing, the lead itself, unknown, not visible to
     * the actor or itself a duplicate. One message for all causes means the answer never tells whether a lead exists
     * (acquisition-owner-lead-queue C-17).
     */
    public static final String INVALID_DUPLICATE_OF_MESSAGE =
            "duplicateOfLeadId must name another lead when marking a lead as a duplicate.";

    private final OwnerLeadId id;
    private final RequestCode requestCode;
    private OwnerLeadStatus status;
    private OwnerLeadIntent intent;            // Current requirement; staff may change it (survey-schedule US-5)
    private final OwnerLeadSource source;
    private final String contactName;
    private final PhoneNumber phone;
    private final String email;
    private final String provinceCode;
    private final String provinceName;
    private final String wardCode;
    private final String wardName;
    private final String addressDetail;
    private final String addressKey;
    private final Optional<GeoPoint> geoPoint;
    private final OwnerLeadPropertyType propertyType;
    private Optional<AskingPrice> askingPrice; // Current price; changes only with the requirement
    private final LocalDate availableFrom;
    private final String notes;
    private final String preferredCallbackTime;
    private final ConsentRecordId consentRecordId;
    private final String submissionSnapshot;
    private String assignedUserId;
    private Instant nextActionAt;
    private OwnerLeadId duplicateOfLeadId;
    private String closeReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;                      // Optimistic lock: null for a new lead; set when loaded or saved
    // 1 at submission, one more per requirement change (survey-schedule BR-10)
    private int intentCycle;

    /**
     * The validated pieces of an intake submission. Text fields are already trimmed and normalized by the caller;
     * email, property type, available-from date, notes and callback time are null when the owner left them out.
     */
    public record SubmitFields(
            String contactName,
            PhoneNumber phone,
            String email,
            OwnerLeadIntent intent,
            String provinceCode,
            String provinceName,
            String wardCode,
            String wardName,
            String addressDetail,
            String addressKey,
            Optional<GeoPoint> geoPoint,
            OwnerLeadPropertyType propertyType,
            Optional<AskingPrice> askingPrice,
            LocalDate availableFrom,
            String notes,
            String preferredCallbackTime) {

        /** The fields are personal data: never print them. */
        @Override
        public String toString() {
            return "SubmitFields[hidden]";
        }
    }

    private OwnerLead(OwnerLeadId id, RequestCode requestCode, OwnerLeadStatus status, OwnerLeadIntent intent,
                      OwnerLeadSource source, String contactName, PhoneNumber phone, String email,
                      String provinceCode, String provinceName, String wardCode, String wardName,
                      String addressDetail, String addressKey, Optional<GeoPoint> geoPoint,
                      OwnerLeadPropertyType propertyType, Optional<AskingPrice> askingPrice,
                      LocalDate availableFrom, String notes, String preferredCallbackTime,
                      ConsentRecordId consentRecordId, String submissionSnapshot, String assignedUserId,
                      Instant nextActionAt, OwnerLeadId duplicateOfLeadId, String closeReason, Instant createdAt,
                      Instant updatedAt, Long version, int intentCycle) {
        this.id = required(id, "id");
        this.requestCode = required(requestCode, "requestCode");
        this.status = required(status, "status");
        this.intent = required(intent, "intent");
        this.source = required(source, "source");
        this.contactName = required(contactName, "contactName");
        this.phone = required(phone, "phone");
        this.email = email;
        this.provinceCode = required(provinceCode, "provinceCode");
        this.provinceName = required(provinceName, "provinceName");
        this.wardCode = required(wardCode, "wardCode");
        this.wardName = required(wardName, "wardName");
        this.addressDetail = required(addressDetail, "addressDetail");
        this.addressKey = required(addressKey, "addressKey");
        this.geoPoint = required(geoPoint, "geoPoint");
        this.propertyType = propertyType;
        this.askingPrice = required(askingPrice, "askingPrice");
        // acquisition-owner-intake BR-4: the price unit is the one the intent needs (night, month or total)
        if (askingPrice.isPresent() && askingPrice.get().getUnit() != intent.requiredPriceUnit()) {
            throw new IllegalArgumentException("askingPrice unit must match the intent.");
        }
        this.availableFrom = availableFrom;
        this.notes = notes;
        this.preferredCallbackTime = preferredCallbackTime;
        this.consentRecordId = required(consentRecordId, "consentRecordId");
        this.submissionSnapshot = required(submissionSnapshot, "submissionSnapshot");
        this.assignedUserId = assignedUserId;
        this.nextActionAt = nextActionAt;
        this.duplicateOfLeadId = duplicateOfLeadId;
        this.closeReason = closeReason;
        this.createdAt = required(createdAt, "createdAt");
        this.updatedAt = required(updatedAt, "updatedAt");
        this.version = version;
        if (intentCycle < 1) {
            throw new IllegalArgumentException("intentCycle must be at least 1.");
        }
        this.intentCycle = intentCycle;
    }

    /**
     * A new lead from the public form: source WEB, status NEW, or ASSIGNED to the given person when the ward has an
     * eligible one. The caller generates the request code and has checked that it is free.
     */
    public static OwnerLead submit(SubmitFields fields, ConsentRecordId consentId,
                                   Optional<String> autoAssigneeUserId, String submissionSnapshot,
                                   RequestCode requestCode) {
        Instant now = AppClock.currentUTC();
        OwnerLeadStatus status = autoAssigneeUserId.isPresent() ? OwnerLeadStatus.ASSIGNED : OwnerLeadStatus.NEW;
        return new OwnerLead(OwnerLeadId.generate(), requestCode, status, fields.intent(), OwnerLeadSource.WEB,
                fields.contactName(), fields.phone(), fields.email(), fields.provinceCode(), fields.provinceName(),
                fields.wardCode(), fields.wardName(), fields.addressDetail(), fields.addressKey(),
                fields.geoPoint(), fields.propertyType(), fields.askingPrice(), fields.availableFrom(),
                fields.notes(), fields.preferredCallbackTime(), consentId, submissionSnapshot,
                autoAssigneeUserId.orElse(null), null, null, null, now, now, null, 1);
    }

    /**
     * Load from database (reconstruct). {@code intentCycle} is the current requirement cycle (1 for a lead whose
     * requirement was never changed).
     */
    public static OwnerLead load(OwnerLeadId id, RequestCode requestCode, OwnerLeadStatus status,
                                 OwnerLeadIntent intent, OwnerLeadSource source, String contactName,
                                 PhoneNumber phone, String email, String provinceCode, String provinceName,
                                 String wardCode, String wardName, String addressDetail, String addressKey,
                                 Optional<GeoPoint> geoPoint, OwnerLeadPropertyType propertyType,
                                 Optional<AskingPrice> askingPrice, LocalDate availableFrom, String notes,
                                 String preferredCallbackTime, ConsentRecordId consentRecordId,
                                 String submissionSnapshot, String assignedUserId, Instant nextActionAt,
                                 OwnerLeadId duplicateOfLeadId, String closeReason, Instant createdAt,
                                 Instant updatedAt, Long version, int intentCycle) {
        return new OwnerLead(id, requestCode, status, intent, source, contactName, phone, email, provinceCode,
                provinceName, wardCode, wardName, addressDetail, addressKey, geoPoint, propertyType, askingPrice,
                availableFrom, notes, preferredCallbackTime, consentRecordId, submissionSnapshot, assignedUserId,
                nextActionAt, duplicateOfLeadId, closeReason, createdAt, updatedAt, version, intentCycle);
    }

    /**
     * Record the version the repository gave this lead when it saved it (0 for a new lead, one more after each
     * update), so the next save of the same object is an update guarded by that version. The version never goes
     * back and is never null.
     */
    public void markPersisted(Long persistedVersion) {
        if (persistedVersion == null) {
            throw new IllegalArgumentException("persistedVersion is required.");
        }
        if (version != null && persistedVersion < version) {
            throw new IllegalArgumentException("persistedVersion cannot go back.");
        }
        this.version = persistedVersion;
    }

    /**
     * The statuses this lead may move to through {@link #changeStatus} (acquisition module brief BR-3): an assigned
     * lead goes to CONTACTED or ON_HOLD, a contacted lead to ON_HOLD, a held lead back to CONTACTED, and any open
     * lead can be closed as DUPLICATE, REJECTED or WITHDRAWN. A NEW lead only reaches ASSIGNED through
     * {@link #assignTo}, so it may only be closed here. A lead with survey work may only be put on hold or closed
     * (survey-schedule BR-3, B-2); SURVEY_SCHEDULED itself is reached only through {@link #moveForSurvey}. Closed
     * leads and the statuses not used yet allow nothing.
     */
    public List<OwnerLeadStatus> allowedNextStatuses() {
        return switch (status) {
            case NEW -> CLOSING_STATUSES;
            case ASSIGNED -> withClosing(OwnerLeadStatus.CONTACTED, OwnerLeadStatus.ON_HOLD);
            case CONTACTED -> withClosing(OwnerLeadStatus.ON_HOLD);
            case ON_HOLD -> withClosing(OwnerLeadStatus.CONTACTED);
            case SURVEY_SCHEDULED -> withClosing(OwnerLeadStatus.ON_HOLD);
            default -> List.of();
        };
    }

    /**
     * The lead move a survey command asks for (survey-schedule spec 4.3; survey BR-12 table; survey-schedule BR-3,
     * BR-11, US-2 AC-2). {@code targetOrNull} null keeps the status; the returned change then has no from/to status.
     * Allowed: SURVEY_BOOKED from ASSIGNED or CONTACTED to SURVEY_SCHEDULED, or kept in SURVEY_SCHEDULED (a re-survey);
     * SURVEY_CHANGED, SURVEY_CONFIRMED, SURVEY_UNCONFIRMED and SURVEY_VISITED kept in SURVEY_SCHEDULED;
     * SURVEY_CANCELLED kept in SURVEY_SCHEDULED or to CONTACTED; SURVEY_RESUMED from ON_HOLD to SURVEY_SCHEDULED.
     * Every allowed move touches {@code updatedAt}, so persistence bumps the version even when the status stays
     * (survey-schedule spec A-2). The change carries the appointment reference and no text (survey-schedule BR-8).
     *
     * @throws InvalidLeadTransitionException any other combination; carries no allowed status
     */
    public LeadChange moveForSurvey(OwnerLeadActivityType type, OwnerLeadStatus targetOrNull, String referenceId,
                                    Integer referenceNo) {
        if (type == null || !isSurveyMove(type, status, targetOrNull)) {
            throw new InvalidLeadTransitionException(status, targetOrNull, List.of());
        }
        if (referenceId == null || referenceNo == null) {
            throw new IllegalArgumentException("A survey move names its appointment.");
        }
        OwnerLeadStatus from = status;
        if (targetOrNull != null) {
            this.status = targetOrNull;
        }
        this.updatedAt = AppClock.currentUTC();
        return new LeadChange(type, targetOrNull == null ? null : from, targetOrNull, null, null, null, null,
                referenceId, referenceNo);
    }

    private static boolean isSurveyMove(OwnerLeadActivityType type, OwnerLeadStatus from, OwnerLeadStatus target) {
        boolean scheduledKept = from == OwnerLeadStatus.SURVEY_SCHEDULED && target == null;
        return switch (type) {
            case SURVEY_BOOKED -> scheduledKept || target == OwnerLeadStatus.SURVEY_SCHEDULED
                    && (from == OwnerLeadStatus.ASSIGNED || from == OwnerLeadStatus.CONTACTED);
            case SURVEY_CHANGED, SURVEY_CONFIRMED, SURVEY_UNCONFIRMED, SURVEY_VISITED -> scheduledKept;
            case SURVEY_CANCELLED -> scheduledKept
                    || from == OwnerLeadStatus.SURVEY_SCHEDULED && target == OwnerLeadStatus.CONTACTED;
            case SURVEY_RESUMED -> from == OwnerLeadStatus.ON_HOLD && target == OwnerLeadStatus.SURVEY_SCHEDULED;
            default -> false;
        };
    }

    /**
     * Record a new requirement agreed with the owner (survey-schedule US-5, BR-10): a rental intent other than the
     * current one and a price in its unit, or no price until the owner confirms one. A new intent cycle starts, so
     * earlier visits stop counting (survey-schedule US-5 AC-4); an assigned or survey-scheduled lead goes back to
     * CONTACTED, a contacted or held lead keeps its status. The submission snapshot never changes. The caller checks
     * the request values first ({@link #checkIntentChangeValues}); here the status is checked before the values that
     * need the lead (survey-schedule spec 5.5): the new intent differs from the current one and the basis is not
     * before the lead's creation date (spec A-25).
     *
     * @throws IntentChangeNotAllowedException the lead's status does not allow a requirement change
     * @throws InvalidLeadCommandException     field {@code newIntent}, {@code askingPrice.unit}, {@code basis} or
     *                                         {@code basis.confirmedOn}
     */
    public IntentChange changeIntent(OwnerLeadIntent newIntent, Optional<AskingPrice> newPrice,
                                     ConfirmationBasis basis) {
        if (!INTENT_CHANGE_STATUSES.contains(status)) {
            throw new IntentChangeNotAllowedException(status, INTENT_CHANGE_STATUSES);
        }
        checkNewRequirement(newIntent, newPrice);
        if (newIntent == intent) {
            throw new InvalidLeadCommandException("newIntent", "newIntent must differ from the current requirement.");
        }
        requireBasis(basis).requireValid(LocalDate.ofInstant(createdAt, VIETNAM), vietnamToday());

        OwnerLeadIntent fromIntent = intent;
        Optional<AskingPrice> fromPrice = askingPrice;
        OwnerLeadStatus from = status;
        OwnerLeadStatus to = from == OwnerLeadStatus.ASSIGNED || from == OwnerLeadStatus.SURVEY_SCHEDULED
                ? OwnerLeadStatus.CONTACTED : null;
        this.intent = newIntent;
        this.askingPrice = newPrice;
        this.intentCycle = intentCycle + 1;
        if (to != null) {
            this.status = to;
        }
        this.updatedAt = AppClock.currentUTC();
        LeadChange change = new LeadChange(OwnerLeadActivityType.INTENT_CHANGED, to == null ? null : from, to, null,
                null, null, null);
        return new IntentChange(fromIntent, fromPrice, newIntent, newPrice, intentCycle, change);
    }

    /**
     * The value rules of {@link #changeIntent} that need no lead (survey-schedule US-5 AC-1..AC-3, spec A-14, A-17,
     * A-25): a rental intent, a price in its unit (or none), a reason of 1..500 characters and a basis whose date is
     * not after today and whose text has 1..500 characters; neither text may hold a phone number.
     *
     * @throws InvalidLeadCommandException field {@code newIntent}, {@code askingPrice.unit}, {@code reason},
     *                                     {@code basis}, {@code basis.confirmedOn} or {@code basis.text}
     */
    public static void checkIntentChangeValues(OwnerLeadIntent newIntent, Optional<AskingPrice> price, String reason,
                                               ConfirmationBasis basis) {
        checkNewRequirement(newIntent, price);
        requireReason(reason);
        requireBasis(basis).requireValid(null, vietnamToday());
    }

    /**
     * The reason of a requirement change, cleaned (NFC, trimmed): 1..500 characters without a phone number.
     *
     * @throws InvalidLeadCommandException field {@code reason}
     */
    public static String requireReason(String reason) {
        String text = reason == null ? "" : cleanNote(reason);
        if (text.isEmpty() || text.length() > MAX_REASON_LENGTH) {
            throw new InvalidLeadCommandException("reason",
                    "reason must be between 1 and " + MAX_REASON_LENGTH + " characters.");
        }
        if (PhoneNumberDetector.containsVietnamesePhoneNumber(text)) {
            throw new InvalidLeadCommandException("reason", "reason must not contain a phone number.");
        }
        return text;
    }

    /** survey-schedule US-5 AC-1, AC-2: only a rental intent, and a price in that intent's unit or none. */
    private static void checkNewRequirement(OwnerLeadIntent newIntent, Optional<AskingPrice> price) {
        if (newIntent == null) {
            throw new InvalidLeadCommandException("newIntent", "newIntent is required.");
        }
        if (newIntent == OwnerLeadIntent.SALE) {
            throw new InvalidLeadCommandException("newIntent", "newIntent must be a short-term or long-term rental.");
        }
        if (price == null) {
            throw new IllegalArgumentException("price is required (empty for no price).");
        }
        if (price.isPresent() && price.get().getUnit() != newIntent.requiredPriceUnit()) {
            throw new InvalidLeadCommandException("askingPrice.unit",
                    "Asking price unit does not match the new requirement.");
        }
    }

    private static ConfirmationBasis requireBasis(ConfirmationBasis basis) {
        if (basis == null) {
            throw new InvalidLeadCommandException("basis", "basis is required.");
        }
        return basis;
    }

    /** Today's date in Vietnam. */
    public static LocalDate vietnamToday() {
        return LocalDate.ofInstant(AppClock.currentUTC(), VIETNAM);
    }

    /**
     * Move the lead to another status. Closing needs a reason (1..500 characters) and, for DUPLICATE, the lead it
     * repeats; closing clears the next action. Whether that other lead exists, is visible to the actor and is not
     * itself a duplicate is checked by the caller. Value errors are reported before the transition check, as the
     * lead queue orders its checks (body first, state after). The version is left alone: persistence bumps it.
     *
     * @throws InvalidLeadCommandException    a value breaks a rule (field {@code toStatus}, {@code note},
     *                                        {@code reason} or {@code duplicateOfLeadId})
     * @throws InvalidLeadTransitionException the move is not in the table; carries the statuses allowed now
     */
    public LeadChange changeStatus(OwnerLeadStatus to, String reason, OwnerLeadId duplicateOfOrNull,
                                   String noteOrNull, Instant nextActionAtOrNull) {
        checkStatusChangeValues(to, reason, duplicateOfOrNull != null, noteOrNull);
        if (to == OwnerLeadStatus.DUPLICATE && duplicateOfOrNull.equals(id)) {
            throw new InvalidLeadCommandException("duplicateOfLeadId", INVALID_DUPLICATE_OF_MESSAGE);
        }
        String note = noteOrNull == null ? null : requireNote(noteOrNull);
        String closeText = to.isClosed() ? normalizeText(reason) : null;
        List<OwnerLeadStatus> allowed = allowedNextStatuses();
        if (!allowed.contains(to)) {
            throw new InvalidLeadTransitionException(status, to, allowed);
        }

        OwnerLeadStatus from = status;
        Instant recordedNextActionAt = nextActionAtOrNull;
        this.status = to;
        if (to.isClosed()) {
            this.closeReason = closeText;
            this.duplicateOfLeadId = to == OwnerLeadStatus.DUPLICATE ? duplicateOfOrNull : null;
            this.nextActionAt = null;
            recordedNextActionAt = null;
        } else if (nextActionAtOrNull != null) {
            this.nextActionAt = nextActionAtOrNull;
        }
        this.updatedAt = AppClock.currentUTC();
        return new LeadChange(OwnerLeadActivityType.STATUS_CHANGED, from, to, null, null, note,
                recordedNextActionAt);
    }

    /**
     * Give the lead to a staff member. A NEW lead becomes ASSIGNED; an open lead in any other state keeps its status,
     * next action and notes and only changes hands. Returns empty, and changes nothing, when the person already holds
     * the lead. The caller has checked that the person may hold leads. The optional note (cleaned like every staff
     * note, blank counts as none, at most 2000 characters) goes into the history entry only; the lead does not store
     * it.
     *
     * @throws InvalidLeadCommandException    the assignee is missing or the note is too long (field
     *                                        {@code assigneeUserId} or {@code note})
     * @throws InvalidLeadTransitionException the lead is closed (or in a status not used yet): no wanted status
     */
    public Optional<LeadChange> assignTo(String assigneeUserId, String noteOrNull) {
        checkAssignValues(assigneeUserId, noteOrNull);
        String note = cleanNote(noteOrNull);
        if (note != null && note.isEmpty()) {
            note = null;
        }
        requireOpen();
        if (assigneeUserId.equals(assignedUserId)) {
            return Optional.empty();
        }
        OwnerLeadStatus from = status;
        String previous = assignedUserId;
        this.assignedUserId = assigneeUserId;
        this.updatedAt = AppClock.currentUTC();
        if (from == OwnerLeadStatus.NEW) {
            this.status = OwnerLeadStatus.ASSIGNED;
            return Optional.of(new LeadChange(OwnerLeadActivityType.ASSIGNED, OwnerLeadStatus.NEW,
                    OwnerLeadStatus.ASSIGNED, null, assigneeUserId, note, null));
        }
        return Optional.of(new LeadChange(OwnerLeadActivityType.REASSIGNED, null, null, previous, assigneeUserId,
                note, null));
    }

    /**
     * Record a note (1..2000 characters) on an open lead, with an optional next action time. Only the next action
     * time can change the lead; a note without one changes no field, so the lead row stays as it is.
     *
     * @throws InvalidLeadCommandException    the note is empty or too long (field {@code note})
     * @throws InvalidLeadTransitionException the lead is closed (or in a status not used yet): no wanted status
     */
    public LeadChange addNote(String note, Instant nextActionAtOrNull) {
        String text = requireNote(note);
        requireOpen();
        if (nextActionAtOrNull != null) {
            this.nextActionAt = nextActionAtOrNull;
            this.updatedAt = AppClock.currentUTC();
        }
        return new LeadChange(OwnerLeadActivityType.NOTE_ADDED, null, null, null, null, text, nextActionAtOrNull);
    }

    /**
     * The value rules of {@link #changeStatus} that need no lead: a wanted status, a note of 1..2000 characters when
     * given, a reason of 1..500 characters when closing and, for DUPLICATE, a lead it repeats. The lead queue runs
     * them before it looks the lead up, because a bad request body is answered before anything about the lead.
     *
     * @throws InvalidLeadCommandException a value breaks a rule (field {@code toStatus}, {@code note},
     *                                     {@code reason} or {@code duplicateOfLeadId})
     */
    public static void checkStatusChangeValues(OwnerLeadStatus to, String reason, boolean duplicateOfGiven,
                                               String noteOrNull) {
        if (to == null) {
            throw new InvalidLeadCommandException("toStatus", "toStatus is required.");
        }
        if (noteOrNull != null) {
            requireNote(noteOrNull);
        }
        if (to.isClosed()) {
            String closeText = normalizeText(reason);
            if (closeText == null || closeText.isEmpty() || closeText.length() > MAX_REASON_LENGTH) {
                throw new InvalidLeadCommandException("reason",
                        "reason must be between 1 and " + MAX_REASON_LENGTH + " characters when closing a lead.");
            }
            if (to == OwnerLeadStatus.DUPLICATE && !duplicateOfGiven) {
                throw new InvalidLeadCommandException("duplicateOfLeadId", INVALID_DUPLICATE_OF_MESSAGE);
            }
        }
    }

    /**
     * The value rules of {@link #assignTo} that need no lead: an assignee, and a note of at most 2000 characters.
     *
     * @throws InvalidLeadCommandException a value breaks a rule (field {@code assigneeUserId} or {@code note})
     */
    public static void checkAssignValues(String assigneeUserId, String noteOrNull) {
        if (assigneeUserId == null || assigneeUserId.isBlank()) {
            throw new InvalidLeadCommandException("assigneeUserId", "assigneeUserId is required.");
        }
        String note = cleanNote(noteOrNull);
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new InvalidLeadCommandException("note", "note must be at most " + MAX_NOTE_LENGTH + " characters.");
        }
    }

    /**
     * The value rule of {@link #addNote} that needs no lead: a note of 1..2000 characters.
     *
     * @throws InvalidLeadCommandException the note is empty or too long (field {@code note})
     */
    public static void checkNoteValues(String note) {
        requireNote(note);
    }

    private void requireOpen() {
        if (!status.isOpen()) {
            throw new InvalidLeadTransitionException(status, null, List.of());
        }
    }

    private static String requireNote(String note) {
        String text = note == null ? "" : cleanNote(note);
        if (text.isEmpty() || text.length() > MAX_NOTE_LENGTH) {
            throw new InvalidLeadCommandException("note",
                    "note must be between 1 and " + MAX_NOTE_LENGTH + " characters.");
        }
        return text;
    }

    private static List<OwnerLeadStatus> withClosing(OwnerLeadStatus... moves) {
        return Stream.concat(Stream.of(moves), CLOSING_STATUSES.stream()).toList();
    }

    /**
     * The one cleaning rule of staff notes (assignment, status change, note): Unicode NFC and trimmed. Notes may span
     * several lines, so the spaces and line breaks inside are kept. Null stays null.
     */
    private static String cleanNote(String note) {
        return note == null ? null : Normalizer.normalize(note, Normalizer.Form.NFC).strip();
    }

    /**
     * Unicode NFC, trimmed, runs of whitespace collapsed to one space. Used for stored text and the request hash so
     * the same words typed with different spacing or accent encoding are equal.
     */
    public static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC).strip()).replaceAll(" ");
    }

    /**
     * Normalized, lower-cased address used to recognise a repeated submission (acquisition-owner-intake BR-10).
     */
    public static String addressKey(String addressDetail) {
        String normalized = normalizeText(addressDetail);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    // ==================== Getters ====================

    public OwnerLeadId getId() {
        return id;
    }

    public RequestCode getRequestCode() {
        return requestCode;
    }

    public OwnerLeadStatus getStatus() {
        return status;
    }

    public OwnerLeadIntent getIntent() {
        return intent;
    }

    public OwnerLeadSource getSource() {
        return source;
    }

    public String getContactName() {
        return contactName;
    }

    public PhoneNumber getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public String getProvinceName() {
        return provinceName;
    }

    public String getWardCode() {
        return wardCode;
    }

    public String getWardName() {
        return wardName;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public String getAddressKey() {
        return addressKey;
    }

    public Optional<GeoPoint> getGeoPoint() {
        return geoPoint;
    }

    public OwnerLeadPropertyType getPropertyType() {
        return propertyType;
    }

    public Optional<AskingPrice> getAskingPrice() {
        return askingPrice;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public String getNotes() {
        return notes;
    }

    public String getPreferredCallbackTime() {
        return preferredCallbackTime;
    }

    public ConsentRecordId getConsentRecordId() {
        return consentRecordId;
    }

    public String getSubmissionSnapshot() {
        return submissionSnapshot;
    }

    public String getAssignedUserId() {
        return assignedUserId;
    }

    public Instant getNextActionAt() {
        return nextActionAt;
    }

    public OwnerLeadId getDuplicateOfLeadId() {
        return duplicateOfLeadId;
    }

    public String getCloseReason() {
        return closeReason;
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

    /** The current requirement cycle: 1 until the first requirement change (survey-schedule BR-10). */
    public int getIntentCycle() {
        return intentCycle;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
