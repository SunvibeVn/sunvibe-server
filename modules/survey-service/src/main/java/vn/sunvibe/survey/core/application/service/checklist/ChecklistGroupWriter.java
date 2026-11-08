package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.command.checklist.SaveChecklistGroupCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.service.checklist.ChecklistGroupChecks.EarlierChain;
import vn.sunvibe.survey.core.application.service.checklist.ChecklistGroupChecks.PlacedAddress;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistMediaCascade;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistMediaCascade.Ended;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.checklist.StoredDataRules;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.Correction;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitDiff;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.policy.checklist.CascadeResult;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectedUnit;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectionPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.GroupCascade;
import vn.sunvibe.survey.core.domain.policy.checklist.GroupDocuments;
import vn.sunvibe.survey.core.domain.policy.checklist.InputForm;
import vn.sunvibe.survey.core.domain.policy.checklist.MetaRules;
import vn.sunvibe.survey.core.domain.policy.checklist.SourceRules;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmittedForm;
import vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1Rules;
import vn.sunvibe.survey.core.domain.policy.checklist.VisitFacts;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSaveDomainRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Saves one group of a checklist draft for {@link SurveyChecklistCommandService} (survey-checklist spec 5.5): the
 * checks in their order, the locks, the group rows with their save-log rows and the photos a G1 save ends, in the
 * service's transaction. The service writes the access-log row and the History entry after it.
 */
@Component
public class ChecklistGroupWriter {

    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistSaveDomainRepository saves;
    private final SurveyLeadPort leads;
    private final ChecklistContextLoader loader;
    private final ChecklistViewAssembler views;
    private final ChecklistMediaCascade mediaCascade;
    private final ChecklistGroupChecks checks;
    private final IdGenerator<Long> snowflakes;

    public ChecklistGroupWriter(SurveyChecklistGroupDomainRepository groups, SurveyChecklistSaveDomainRepository saves,
                                SurveyLeadPort leads, ChecklistContextLoader loader, ChecklistViewAssembler views,
                                ChecklistMediaCascade mediaCascade, ChecklistGroupChecks checks,
                                @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.groups = groups;
        this.saves = saves;
        this.leads = leads;
        this.loader = loader;
        this.views = views;
        this.mediaCascade = mediaCascade;
        this.checks = checks;
        this.snowflakes = snowflakes;
    }

    /** The checklist context the save ran on, and whether it wrote a group: false for a no-op (spec A-17). */
    public record Saved(CommandContext context, boolean written) {
    }

    /**
     * Save check order (spec 5.5): (1) body, correction shape and the G1 catalog and pin, before any lookup → (2)..(5)
     * checklist, visible lead, assignee, read-only, eligible → (6) holder → locks, no group row read before its lock:
     * a G2..G8 save shares the G1 row then locks its own; a G1 save locks G1, reads the others, cascades and locks each
     * group it changes in group order → (7) the canonical content equal to the stored one and no cascade: a no-op →
     * (8) versions → (9) rules that need stored data, on changed units → (10) a correction where the rule asks for one.
     * Then one save-log row per written group and the correction on the named group's row; the access log and the
     * History entry, the last writes, are the service's.
     */
    public Saved save(SurveyViewer viewer, SaveChecklistGroupCommand command, Instant now) {
        LocalDate today = SurveyTimeRules.vietnamDate(now);
        List<Violation> violations = new ArrayList<>();
        if (command.expectedVersion() == null) {
            violations.add(new Violation("expectedVersion", "expectedVersion is required."));
        }
        if (command.content() == null || command.content().group() != command.group()) {
            violations.add(new Violation("content", "content is required."));
            ChecklistInputs.throwIfAny(violations);
        }
        GroupContent body = ChecklistGroupChecks.normalised(command.content(), today, violations);
        ReasonBasis correction = command.correctionOrNull() == null ? null : ChecklistInputs.reasonAndBasis(
                "correction.", command.correctionOrNull().reason(), command.correctionOrNull().basis(), violations);
        ObservedPlaceNames placeNames = null;
        if (body instanceof IdentificationContent g1) {
            PlacedAddress placed = checks.placeAddress(g1, violations);
            body = placed.content();
            placeNames = placed.names();
        }
        ChecklistInputs.throwIfAny(violations);

        CommandContext context = loader.loadForCommand(viewer, command.checklistId(), SecurityAction.SAVE);
        SurveyChecklist checklist = context.checklist();
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        SurveyChecklistId id = checklist.getId();
        ChecklistGroup group = command.group();
        GroupState stored;
        IdentificationContent storedG1;
        IdentificationContent nextG1;
        Map<ChecklistGroup, GroupState> cascaded = new LinkedHashMap<>();
        Map<ChecklistGroup, GroupContent> cascadedContent = new LinkedHashMap<>();
        if (group == ChecklistGroup.IDENTIFICATION) {
            stored = groups.lockForUpdate(id, ChecklistGroup.IDENTIFICATION);
            storedG1 = (IdentificationContent) stored.document().content();
            nextG1 = (IdentificationContent) body;
            Map<ChecklistGroup, GroupState> others = groups.findOthers(id, ChecklistGroup.IDENTIFICATION);
            Map<ChecklistGroup, GroupDocument> documents =
                    new EnumMap<>(ChecklistGroup.class);
            others.forEach((other, state) -> documents.put(other, state.document()));
            CascadeResult cascade = GroupCascade.of(storedG1, nextG1, documents);
            for (Map.Entry<ChecklistGroup, GroupContent> entry : cascade.changed().entrySet()) {
                cascaded.put(entry.getKey(), groups.lockForUpdate(id, entry.getKey()));
                cascadedContent.put(entry.getKey(), entry.getValue());
            }
        } else {
            GroupState g1State = groups.lockForShare(id, ChecklistGroup.IDENTIFICATION);
            storedG1 = (IdentificationContent) g1State.document().content();
            nextG1 = storedG1;
            stored = groups.lockForUpdate(id, group);
        }
        GroupContent next = TemplateV1Rules.canonical(body, nextG1);

        // (7) spec A-17: nothing to write, also with a stale version; only the access-log row of the answer
        if (cascaded.isEmpty() && next.equals(InputForm.ofStored(stored.document(), storedG1))) {
            return new Saved(context, false);
        }
        // (8)
        if (command.expectedVersion() != stored.version()) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        for (Map.Entry<ChecklistGroup, GroupState> entry : cascaded.entrySet()) {
            Long sent = command.otherGroupVersions().get(entry.getKey());
            if (sent == null || sent != entry.getValue().version()) {
                throw new SurveyConcurrentModificationException("otherGroupVersions." + entry.getKey().pathName());
            }
        }
        // (9)
        List<UnitChange> changes = UnitDiff.between(stored.document(), storedG1, body, nextG1);
        Map<ChecklistGroup, List<UnitChange>> cascadeChanges = new LinkedHashMap<>();
        cascadedContent.forEach((other, content) -> cascadeChanges.put(other, UnitDiff.between(
                cascaded.get(other).document(), storedG1, content, nextG1)));
        Optional<SubmittedForm> form = leads.ownerForm(context.lead().leadId()).map(ChecklistGroupChecks::form);
        StoredDataRules.unknownSpaces(body, storedG1, violations);
        StoredDataRules.scopeNulls(body, nextG1.rentalScope(), violations);
        if (group == ChecklistGroup.SAFETY) {
            EarlierChain chain = checks.earlierChain(checklist, context);
            StoredDataRules.previousLinks(changes, chain.hazardIds(), violations);
            StoredDataRules.reportDates(changes, hazard -> chain.firstSightingArrival(hazard,
                    context.visitFacts().arrivalDate()), today, violations);
        }
        Set<String> leadStaff = views.leadStaff(context.lead(), context.ofLead());
        violations.addAll(SourceRules.check(changes, viewer.userId(), leadStaff, form, nextG1.rentalScope()));
        if (correction != null) {
            ChecklistDateRules.requireBasisDate("correction.basis.confirmedOn", correction.basis().confirmedOn(),
                    context.visitFacts().arrivalDate(), today).ifPresent(violations::add);
        }
        ChecklistInputs.throwIfAny(violations);
        // (10)
        Set<UnitKey> required = new LinkedHashSet<>(CorrectionPolicy.requiring(stored.document(), changes,
                viewer.userId(), context.visitFacts()));
        cascadeChanges.forEach((other, otherChanges) -> required.addAll(CorrectionPolicy.requiring(
                cascaded.get(other).document(), otherChanges, viewer.userId(), context.visitFacts())));
        // survey-media spec A-9: after the group rows, the set and the photos on the placements this save ends
        Ended endedPhotos = group == ChecklistGroup.IDENTIFICATION
                ? mediaCascade.lockEnded(id.getValue(), storedG1, nextG1) : null;
        List<SurveyMedia> mediaRequired = endedPhotos == null ? List.of()
                : endedPhotos.needingCorrection(viewer.userId());
        if (!required.isEmpty() && correction == null) {
            throw new ChecklistValidationException(List.of(new Violation("correction",
                    "Changing values someone else recorded needs a correction: reason, date and basis.")));
        }
        if (!mediaRequired.isEmpty() && correction == null) {
            throw new ChecklistValidationException(List.of(new Violation("correction",
                    "Removing photos someone else uploaded needs a correction: reason, date and basis.")));
        }

        long savedId = snowflakes.generateId();
        Set<UnitKey> covered = new LinkedHashSet<>();
        if (correction != null) {
            if (required.isEmpty() && mediaRequired.isEmpty()) {
                changes.forEach(change -> covered.add(change.key()));
                cascadeChanges.values().forEach(list -> list.forEach(change -> covered.add(change.key())));
            } else {
                covered.addAll(required);
            }
        }
        List<CorrectedUnit> correctedUnits = new ArrayList<>(covered.stream()
                .map(key -> new CorrectedUnit(key.value(), ChecklistGroupChecks.spaceCode(key, storedG1))).toList());
        // survey-media spec A-9: one unit per photo of another uploader the save removes
        mediaRequired.forEach(photo -> correctedUnits.add(new CorrectedUnit("media/" + photo.getId().getValue(),
                photo.getPlacement().spaceCodeAtUploadOrNull())));
        Correction recorded = correction == null ? null : new Correction(correction.reason(), correction.basis(),
                List.copyOf(correctedUnits));
        write(id, group, stored, next, changes, covered, savedId, true, recorded, viewer, now, context, form,
                group == ChecklistGroup.IDENTIFICATION ? placeNames : null);
        for (Map.Entry<ChecklistGroup, GroupContent> entry : cascadedContent.entrySet()) {
            ChecklistGroup other = entry.getKey();
            write(id, other, cascaded.get(other), TemplateV1Rules.canonical(entry.getValue(), nextG1),
                    cascadeChanges.get(other), covered, savedId, false, null, viewer, now, context, form, null);
        }
        if (endedPhotos != null) {
            mediaCascade.remove(endedPhotos, viewer.userId(), savedId, now);
        }
        return new Saved(context, true);
    }

    /**
     * One group's next document, row and save-log row (spec 5.2): meta for every changed unit that stays and the
     * removal stamp of every removed one (survey-checklist BR-29, spec A-29: the caller as last editor), pointing to
     * the named row when a correction covered it; the correction itself only on the named group's row, which always
     * has the save's id (a G1 cascade of photos names it, survey-media spec A-9).
     */
    private void write(SurveyChecklistId id, ChecklistGroup group, GroupState stored, GroupContent next,
                       List<UnitChange> changes, Set<UnitKey> covered, long namedSaveId, boolean named,
                       Correction correction,
                       SurveyViewer viewer, Instant now, CommandContext context, Optional<SubmittedForm> form,
                       ObservedPlaceNames placeNames) {
        VisitFacts visit = context.visitFacts();
        Map<UnitKey, UnitMeta> changedMeta = new HashMap<>();
        Map<UnitKey, Tombstone> removalStamps = new HashMap<>();
        for (UnitChange change : changes) {
            if (change.kind() == ChangeKind.REMOVED) {
                Tombstone stamp = MetaRules.removed(change, viewer.userId(), now);
                removalStamps.put(change.key(), covered.contains(change.key()) ? stamp.withCorrection(namedSaveId)
                        : stamp);
            } else {
                UnitMeta meta = MetaRules.next(change, viewer.userId(), now, visit, form);
                changedMeta.put(change.key(), covered.contains(change.key()) ? meta.withCorrection(namedSaveId)
                        : meta);
            }
        }
        GroupDocument document = GroupDocuments.apply(stored.document(), next, changedMeta, removalStamps);
        if (group == ChecklistGroup.IDENTIFICATION) {
            document = document.withPlaceNames(placeNames);
        }
        GroupState written;
        try {
            written = groups.update(id, group, stored.version(), new GroupState(document, stored.version(), now,
                    viewer.userId()));
        } catch (OptimisticLockingFailureException e) {
            // spec 5.5: the row changed after its lock was read; answered on the version the caller sent
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        long saveId = named ? namedSaveId : snowflakes.generateId();
        saves.save(new ChecklistSave(saveId, id, group, written.version(), viewer.userId(), now, correction));
    }
}
