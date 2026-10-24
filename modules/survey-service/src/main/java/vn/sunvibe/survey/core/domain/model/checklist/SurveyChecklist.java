package vn.sunvibe.survey.core.domain.model.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;

import java.time.Instant;

/**
 * Aggregate - SurveyChecklist (survey-checklist spec 4.1, 4.2): one checklist revision of a lead, bound to one VISITED
 * long-term appointment and its intent cycle, numbered within the lead. In this slice it is a DRAFT of template
 * LONG_TERM_RENT v1, kind FULL_ASSESSMENT. The row changes only at start and at each takeover (I-7): the holder is the
 * starter, then each person who took the draft over. Group contents live in their own rows.
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
    private final ChecklistStatus status;
    private final String createdBy;
    private final Instant createdAt;
    private Long version;

    private SurveyChecklist(SurveyChecklistId id, String leadId, int checklistNo,
                            SurveyAppointmentId visitAppointmentId, int intentCycle, String holderUserId,
                            String templateCode, int templateVersion, ChecklistKind kind, ChecklistStatus status,
                            String createdBy, Instant createdAt, Long version) {
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
        this.version = version;
    }

    /** A new DRAFT of template v1 held by its starter (survey-checklist US-1 AC-1, spec 4.8). */
    public static SurveyChecklist start(SurveyChecklistId id, String leadId, int checklistNo,
                                        SurveyAppointmentId visitAppointmentId, int intentCycle, String starterUserId,
                                        Instant now) {
        return new SurveyChecklist(id, leadId, checklistNo, visitAppointmentId, intentCycle, starterUserId,
                TemplateV1.CODE, TemplateV1.VERSION, ChecklistKind.FULL_ASSESSMENT, ChecklistStatus.DRAFT,
                starterUserId, now, null);
    }

    /** Load from database (reconstruct). */
    public static SurveyChecklist load(SurveyChecklistId id, String leadId, int checklistNo,
                                       SurveyAppointmentId visitAppointmentId, int intentCycle, String holderUserId,
                                       String templateCode, int templateVersion, ChecklistKind kind,
                                       ChecklistStatus status, String createdBy, Instant createdAt, Long version) {
        return new SurveyChecklist(id, leadId, checklistNo, visitAppointmentId, intentCycle, holderUserId,
                templateCode, templateVersion, kind, status, createdBy, createdAt, version);
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
        String previous = holderUserId;
        holderUserId = newHolderUserId;
        return previous;
    }

    /** What the editability rule reads (spec 4.4). */
    public SurveyChecklistSnapshot snapshot() {
        return new SurveyChecklistSnapshot(id.getValue(), visitAppointmentId.getValue(), intentCycle, holderUserId,
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

    public Long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "SurveyChecklist[" + id + " #" + checklistNo + "]";
    }
}
