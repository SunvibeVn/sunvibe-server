package vn.sunvibe.survey.core.domain.model.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;

import java.time.Instant;

/**
 * Aggregate - SurveyChecklist (survey-checklist spec 4.1, 4.2): one checklist revision of a lead, bound to one VISITED
 * long-term appointment and its intent cycle, numbered within the lead, of template LONG_TERM_RENT v1, kind
 * FULL_ASSESSMENT. The holder is the starter, then each person who took the draft over. A revision goes from DRAFT to
 * SUBMITTED to one decided status and never back (survey-checklist submission spec 4.1); submit and decide write the row too (A-1). Group
 * contents live in their own rows. A PASSED revision can be marked as requalified, once and for good: the mark says its
 * result stopped being used and changes nothing of its content (property-create requalification spec I-2, A-3).
 */
public final class SurveyChecklist {

    private final SurveyChecklistId id;
    private final String leadId;
    private final int checklistNo;
    private final SurveyAppointmentId visitAppointmentId;
    private final int intentCycle;
    private String holderUserId;
    private final String templateCode;
    private final int templateVersion;
    private final ChecklistKind kind;
    private ChecklistStatus status;
    private final String createdBy;
    private final Instant createdAt;
    private final SurveyChecklistId previousChecklistIdOrNull;
    private Instant submittedAt;
    private String submittedByUserId;
    private Instant decidedAt;
    private Instant requalifiedAt;
    private Long version;

    private SurveyChecklist(SurveyChecklistId id, String leadId, int checklistNo,
                            SurveyAppointmentId visitAppointmentId, int intentCycle, String holderUserId,
                            String templateCode, int templateVersion, ChecklistKind kind, ChecklistStatus status,
                            String createdBy, Instant createdAt, SurveyChecklistId previousChecklistIdOrNull,
                            Instant submittedAt, String submittedByUserId, Instant decidedAt,
                            Instant requalifiedAt, Long version) {
        if (id == null || leadId == null || visitAppointmentId == null || holderUserId == null
                || templateCode == null || kind == null || status == null || createdBy == null
                || createdAt == null) {
            throw new IllegalArgumentException("A checklist needs its ids, holder, template, kind, status and "
                    + "creation.");
        }
        if (checklistNo < 1 || intentCycle < 1 || templateVersion < 1) {
            throw new IllegalArgumentException("Checklist number, intent cycle and template version start at 1.");
        }
        this.id = id;
        this.leadId = leadId;
        this.checklistNo = checklistNo;
        this.visitAppointmentId = visitAppointmentId;
        this.intentCycle = intentCycle;
        this.holderUserId = holderUserId;
        this.templateCode = templateCode;
        this.templateVersion = templateVersion;
        this.kind = kind;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.previousChecklistIdOrNull = previousChecklistIdOrNull;
        this.submittedAt = submittedAt;
        this.submittedByUserId = submittedByUserId;
        this.decidedAt = decidedAt;
        this.requalifiedAt = requalifiedAt;
        this.version = version;
    }

    /** A new DRAFT of template v1 held by its starter (survey-checklist US-1 AC-1, spec 4.8). */
    public static SurveyChecklist start(SurveyChecklistId id, String leadId, int checklistNo,
                                        SurveyAppointmentId visitAppointmentId, int intentCycle, String starterUserId,
                                        Instant now) {
        return new SurveyChecklist(id, leadId, checklistNo, visitAppointmentId, intentCycle, starterUserId,
                TemplateV1.CODE, TemplateV1.VERSION, ChecklistKind.FULL_ASSESSMENT, ChecklistStatus.DRAFT,
                starterUserId, now, null, null, null, null, null, null);
    }

    /**
     * A new DRAFT linked to the last submitted revision of the cycle (survey-checklist submission spec 4.6, A-2): the next revision of the
     * same visit after a return for documents, or the first of a later visit after a return for a re-survey.
     */
    public static SurveyChecklist startAfter(SurveyChecklistId id, String leadId, int checklistNo,
                                             SurveyAppointmentId visitAppointmentId, int intentCycle,
                                             String starterUserId, Instant now, SurveyChecklistId previousChecklistId) {
        return new SurveyChecklist(id, leadId, checklistNo, visitAppointmentId, intentCycle, starterUserId,
                TemplateV1.CODE, TemplateV1.VERSION, ChecklistKind.FULL_ASSESSMENT, ChecklistStatus.DRAFT,
                starterUserId, now, previousChecklistId, null, null, null, null, null);
    }

    /** Load from database (reconstruct). */
    public static SurveyChecklist load(SurveyChecklistId id, String leadId, int checklistNo,
                                       SurveyAppointmentId visitAppointmentId, int intentCycle, String holderUserId,
                                       String templateCode, int templateVersion, ChecklistKind kind,
                                       ChecklistStatus status, String createdBy, Instant createdAt,
                                       SurveyChecklistId previousChecklistIdOrNull, Instant submittedAt,
                                       String submittedByUserId, Instant decidedAt, Instant requalifiedAt,
                                       Long version) {
        return new SurveyChecklist(id, leadId, checklistNo, visitAppointmentId, intentCycle, holderUserId,
                templateCode, templateVersion, kind, status, createdBy, createdAt, previousChecklistIdOrNull,
                submittedAt, submittedByUserId, decidedAt, requalifiedAt, version);
    }

    /**
     * survey-checklist BR-14: the caller becomes the holder; the previous holder is returned for the takeover row.
     *
     * @throws IllegalStateException the caller already holds the draft (checked before by the use case)
     */
    public String takeOver(String newHolderUserId) {
        if (newHolderUserId == null || newHolderUserId.equals(holderUserId)) {
            throw new IllegalStateException("The caller already holds the draft.");
        }
        requireDraft("be taken over");
        String previous = holderUserId;
        holderUserId = newHolderUserId;
        return previous;
    }

    /**
     * survey-checklist US-2 AC-3: the revision is frozen.
     *
     * @throws IllegalStateException the revision left DRAFT (checked before by the use case)
     */
    public void submit(String byUserId, Instant at) {
        requireDraft("be submitted");
        status = ChecklistStatus.SUBMITTED;
        submittedByUserId = byUserId;
        submittedAt = at;
    }

    /**
     * survey-checklist submission spec I-3: a submitted revision is decided once.
     *
     * @throws IllegalStateException the revision is not SUBMITTED (checked before by the use case)
     */
    public void decide(DecisionOutcome outcome, Instant at) {
        if (status != ChecklistStatus.SUBMITTED) {
            throw new IllegalStateException("A " + status + " checklist cannot be decided.");
        }
        status = outcome.status();
        decidedAt = at;
    }

    /**
     * property-create requalification spec I-2: the result of a PASSED revision stops being used. The revision stays
     * PASSED, and a mark is never moved or removed.
     *
     * @throws IllegalStateException the revision is not PASSED or is marked already (checked before by the use case)
     */
    public void markRequalified(Instant at) {
        if (status != ChecklistStatus.PASSED) {
            throw new IllegalStateException("A " + status + " checklist cannot be requalified.");
        }
        if (requalifiedAt != null) {
            throw new IllegalStateException("The checklist is requalified already.");
        }
        requalifiedAt = at;
    }

    private void requireDraft(String action) {
        if (status != ChecklistStatus.DRAFT) {
            throw new IllegalStateException("A " + status + " checklist cannot " + action + ".");
        }
    }

    /** What the editability rule reads (spec 4.4). */
    public ChecklistEditState snapshot() {
        return new ChecklistEditState(id.getValue(), visitAppointmentId.getValue(), intentCycle, holderUserId,
                status);
    }

    /** The version the database gave the row after a save. */
    public void markPersisted(long persistedVersion) {
        this.version = persistedVersion;
    }

    public SurveyChecklistId getId() {
        return id;
    }

    public String getLeadId() {
        return leadId;
    }

    public int getChecklistNo() {
        return checklistNo;
    }

    public SurveyAppointmentId getVisitAppointmentId() {
        return visitAppointmentId;
    }

    public int getIntentCycle() {
        return intentCycle;
    }

    public String getHolderUserId() {
        return holderUserId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public int getTemplateVersion() {
        return templateVersion;
    }

    public ChecklistKind getKind() {
        return kind;
    }

    public ChecklistStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SurveyChecklistId getPreviousChecklistIdOrNull() {
        return previousChecklistIdOrNull;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getSubmittedByUserId() {
        return submittedByUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    /** Null while the revision is not requalified. */
    public Instant getRequalifiedAt() {
        return requalifiedAt;
    }

    public boolean isRequalified() {
        return requalifiedAt != null;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "SurveyChecklist[" + id + " #" + checklistNo + "]";
    }
}
