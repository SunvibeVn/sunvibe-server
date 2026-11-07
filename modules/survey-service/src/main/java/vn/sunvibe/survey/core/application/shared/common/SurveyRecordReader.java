package vn.sunvibe.survey.core.application.shared.common;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.CompletableRecord;
import vn.sunvibe.survey.core.domain.policy.checklist.PassedUsability;
import vn.sunvibe.survey.core.domain.policy.checklist.ResumeTarget;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionMark;
import vn.sunvibe.survey.core.domain.policy.checklist.SurveyState;
import vn.sunvibe.survey.core.domain.policy.checklist.Usability;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The survey record of one intent cycle, read fresh from stored rows on every question (survey-checklist submission
 * spec 4.7, 4.8, I-6): the revisions of the cycle with their decisions and the current visit. The lead rules, the appointment
 * commands and the reads ask the same questions here, so they cannot disagree.
 */
@Component
public class SurveyRecordReader {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistDecisionDomainRepository decisions;
    private final SurveyChecklistEvidenceDomainRepository evidence;
    private final SurveyMediaDomainRepository media;

    public SurveyRecordReader(SurveyChecklistDomainRepository checklists,
                              SurveyChecklistDecisionDomainRepository decisions,
                              SurveyChecklistEvidenceDomainRepository evidence, SurveyMediaDomainRepository media) {
        this.checklists = checklists;
        this.decisions = decisions;
        this.evidence = evidence;
        this.media = media;
    }

    /** The revisions of one cycle by number, their decisions and the cycle's current visit. */
    public record CycleRecord(List<SurveyChecklist> ofCycle, Map<SurveyChecklistId, ChecklistDecision> decisions,
                              Optional<SurveyAppointment> currentVisit) {

        /**
         * The current PASSED: a cycle has at most one unmarked, by a unique index, and it refuses booking and every later
         * revision (spec 4.8). A requalified one stopped being used (property-create requalification D5-1).
         */
        public Optional<SurveyChecklist> passed() {
            return ofCycle.stream().filter(checklist -> checklist.getStatus() == ChecklistStatus.PASSED
                    && !checklist.isRequalified()).findFirst();
        }

        public boolean hasPassed() {
            return passed().isPresent();
        }

        public boolean hasSubmitted() {
            return ofCycle.stream().anyMatch(checklist -> checklist.getStatus() == ChecklistStatus.SUBMITTED);
        }

        /** The revision with the highest number on the current visit, if any (spec 4.1). */
        public Optional<SurveyChecklist> latestOfCurrentVisit() {
            return currentVisit.flatMap(visit -> ofCycle.stream()
                    .filter(checklist -> checklist.getVisitAppointmentId().equals(visit.getId()))
                    .max(Comparator.comparingInt(SurveyChecklist::getChecklistNo)));
        }

        /** Where the current visit's latest revision stands. */
        public Optional<RevisionMark> latestMark() {
            return latestOfCurrentVisit().map(this::mark);
        }

        public RevisionMark mark(SurveyChecklist checklist) {
            ChecklistDecision decision = decisions.get(checklist.getId());
            return new RevisionMark(checklist.getStatus(), decision == null ? null : decision.needsChangesKindOrNull(),
                    decision == null ? null : decision.failedKindOrNull());
        }

        /** spec 4.8: a booking is refused while the latest revision of the current visit waits for a decision. */
        public boolean latestSubmitted() {
            return latestMark().filter(mark -> mark.status() == ChecklistStatus.SUBMITTED).isPresent();
        }

        /** spec 4.8: cancel keeps SURVEY_SCHEDULED and resume returns to it only with this record. */
        public boolean completable() {
            return CompletableRecord.completable(currentVisit.isPresent(), latestMark());
        }
    }

    /**
     * @param ofLead every appointment of the lead, as read by the caller
     */
    public CycleRecord read(String leadId, int intentCycle, List<SurveyAppointment> ofLead) {
        List<SurveyChecklist> ofCycle = checklists.findByLeadId(leadId).stream()
                .filter(checklist -> checklist.getIntentCycle() == intentCycle).toList();
        return new CycleRecord(ofCycle, decisions.findByChecklistIds(ofCycle.stream().filter(checklist ->
                checklist.getStatus().isDecided()).map(SurveyChecklist::getId).toList()),
                CurrentVisitPolicy.currentVisit(ofLead, intentCycle));
    }

    /** Whether the PASSED revision can still be used, from its frozen photos as they stand (spec 4.7). */
    public Usability usability(SurveyChecklist passed, int leadIntentCycle, boolean leadClosed) {
        List<SurveyMediaId> frozen = evidence.findByChecklistId(passed.getId()).stream().map(EvidenceEntry::mediaId)
                .map(SurveyMediaId::of).toList();
        return PassedUsability.of(passed.isRequalified(), passed.getIntentCycle(), leadIntentCycle, leadClosed,
                media.findByIds(frozen));
    }

    /**
     * spec 4.8, plan PD-7: where a resume of a held lead goes; empty when the record leaves it nowhere (a PASSED that is
     * not usable, or a current visit that cannot be completed). The command and the read ask here.
     */
    public Optional<ResumeTarget> resumeTarget(CycleRecord record, int leadIntentCycle) {
        if (record.hasPassed()) {
            return state(record, leadIntentCycle, false).passedUsable() ? Optional.of(ResumeTarget.QUALIFIED)
                    : Optional.empty();
        }
        return record.completable() ? Optional.of(ResumeTarget.SURVEY_SCHEDULED) : Optional.empty();
    }

    public SurveyState state(CycleRecord record, int leadIntentCycle, boolean leadClosed) {
        boolean usable = record.passed().map(passed -> usability(passed, leadIntentCycle, leadClosed).usable())
                .orElse(false);
        return new SurveyState(record.hasPassed(), usable, record.hasSubmitted(), record.completable());
    }
}
