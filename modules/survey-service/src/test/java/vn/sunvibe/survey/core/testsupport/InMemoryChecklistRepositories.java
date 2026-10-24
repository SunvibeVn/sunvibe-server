package vn.sunvibe.survey.core.testsupport;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveySecurityEventDomainRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written fakes of the checklist repositories. They write the calls that matter for the order of spec 5.5 to a
 * shared journal ("checklist:insert", "group:share:IDENTIFICATION", "group:read", ...), so a test can assert that no
 * group row is read before its lock and that the History entry comes last.
 */
public final class InMemoryChecklistRepositories {

    private InMemoryChecklistRepositories() {
    }

    /** Checklists by id; both unique indexes of spec I-2 refuse like the database. */
    public static class Checklists implements SurveyChecklistDomainRepository {

        private final Map<SurveyChecklistId, SurveyChecklist> stored = new LinkedHashMap<>();
        private final List<String> journal;
        public RuntimeException failNextUpdate;

        public Checklists(List<String> journal) {
            this.journal = journal;
        }

        public List<SurveyChecklist> all() {
            return stored.values().stream().map(Checklists::copy).toList();
        }

        @Override
        public void insert(SurveyChecklist checklist) {
            journal.add("checklist:insert");
            boolean taken = stored.values().stream().anyMatch(other -> other.getLeadId().equals(checklist.getLeadId())
                    && other.getChecklistNo() == checklist.getChecklistNo()
                    || other.getStatus() == ChecklistStatus.DRAFT
                    && other.getVisitAppointmentId().equals(checklist.getVisitAppointmentId()));
            if (taken) {
                throw new ChecklistAlreadyStartedException(null);
            }
            checklist.markPersisted(0L);
            stored.put(checklist.getId(), copy(checklist));
        }

        @Override
        public void update(SurveyChecklist checklist) {
            journal.add("checklist:update");
            if (failNextUpdate != null) {
                RuntimeException failure = failNextUpdate;
                failNextUpdate = null;
                throw failure;
            }
            SurveyChecklist current = stored.get(checklist.getId());
            if (!current.getVersion().equals(checklist.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(SurveyChecklist.class, checklist.getId().getValue());
            }
            checklist.markPersisted(current.getVersion() + 1);
            stored.put(checklist.getId(), copy(checklist));
        }

        @Override
        public Optional<SurveyChecklist> findById(SurveyChecklistId id) {
            journal.add("checklist:read");
            return Optional.ofNullable(stored.get(id)).map(Checklists::copy);
        }

        @Override
        public Optional<String> findLeadIdById(SurveyChecklistId id) {
            journal.add("checklist:leadId");
            return Optional.ofNullable(stored.get(id)).map(SurveyChecklist::getLeadId);
        }

        @Override
        public List<SurveyChecklist> findByLeadId(String leadId) {
            journal.add("checklist:byLead");
            return stored.values().stream().filter(checklist -> checklist.getLeadId().equals(leadId))
                    .sorted(Comparator.comparingInt(SurveyChecklist::getChecklistNo)).map(Checklists::copy).toList();
        }

        @Override
        public int nextChecklistNo(String leadId) {
            return stored.values().stream().filter(checklist -> checklist.getLeadId().equals(leadId))
                    .mapToInt(SurveyChecklist::getChecklistNo).max().orElse(0) + 1;
        }

        private static SurveyChecklist copy(SurveyChecklist c) {
            return SurveyChecklist.load(c.getId(), c.getLeadId(), c.getChecklistNo(), c.getVisitAppointmentId(),
                    c.getIntentCycle(), c.getHolderUserId(), c.getTemplateCode(), c.getTemplateVersion(), c.getKind(),
                    c.getStatus(), c.getCreatedBy(), c.getCreatedAt(), c.getVersion());
        }
    }

    /** Group rows; each lock and read is journaled; an update checks the version like the database. */
    public static class Groups implements SurveyChecklistGroupDomainRepository {

        private final Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> stored = new LinkedHashMap<>();
        private final List<String> journal;
        public RuntimeException failNextUpdate;

        public Groups(List<String> journal) {
            this.journal = journal;
        }

        /** Test helper: the stored state without a journal entry. */
        public GroupState get(SurveyChecklistId checklistId, ChecklistGroup group) {
            return stored.get(checklistId).get(group);
        }

        /** Test helper: store a state as if a save wrote it. */
        public void put(SurveyChecklistId checklistId, ChecklistGroup group, GroupState state) {
            stored.computeIfAbsent(checklistId, id -> new EnumMap<>(ChecklistGroup.class)).put(group, state);
        }

        @Override
        public void insertAll(SurveyChecklistId checklistId, Map<ChecklistGroup, GroupState> groups) {
            journal.add("group:insertAll");
            stored.put(checklistId, new EnumMap<>(groups));
        }

        @Override
        public Map<ChecklistGroup, GroupState> findAll(SurveyChecklistId checklistId) {
            journal.add("group:read");
            return new EnumMap<>(stored.get(checklistId));
        }

        @Override
        public Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> findAllOf(
                Collection<SurveyChecklistId> checklistIds) {
            journal.add("group:readMany");
            Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> groups = new LinkedHashMap<>();
            for (SurveyChecklistId id : checklistIds) {
                if (stored.containsKey(id)) {
                    groups.put(id, new EnumMap<>(stored.get(id)));
                }
            }
            return groups;
        }

        @Override
        public GroupState lockForShare(SurveyChecklistId checklistId, ChecklistGroup group) {
            journal.add("group:share:" + group);
            return stored.get(checklistId).get(group);
        }

        @Override
        public GroupState lockForUpdate(SurveyChecklistId checklistId, ChecklistGroup group) {
            journal.add("group:update-lock:" + group);
            return stored.get(checklistId).get(group);
        }

        @Override
        public Map<ChecklistGroup, GroupState> findOthers(SurveyChecklistId checklistId, ChecklistGroup except) {
            journal.add("group:others");
            Map<ChecklistGroup, GroupState> others = new EnumMap<>(stored.get(checklistId));
            others.remove(except);
            return others;
        }

        @Override
        public GroupState update(SurveyChecklistId checklistId, ChecklistGroup group, long expectedVersion,
                                 GroupState next) {
            journal.add("group:write:" + group);
            if (failNextUpdate != null) {
                RuntimeException failure = failNextUpdate;
                failNextUpdate = null;
                throw failure;
            }
            GroupState current = stored.get(checklistId).get(group);
            if (current.version() != expectedVersion) {
                throw new ObjectOptimisticLockingFailureException(GroupState.class, checklistId.getValue());
            }
            GroupState written = new GroupState(next.document(), expectedVersion + 1, next.updatedAt(),
                    next.updatedByUserId());
            stored.get(checklistId).put(group, written);
            return written;
        }
    }

    /** Insert-only takeover rows. */
    public static class Takeovers implements SurveyChecklistTakeoverDomainRepository {

        private final List<ChecklistTakeover> stored = new ArrayList<>();
        private final List<String> journal;

        public Takeovers(List<String> journal) {
            this.journal = journal;
        }

        public List<ChecklistTakeover> all() {
            return List.copyOf(stored);
        }

        @Override
        public void save(ChecklistTakeover takeover) {
            journal.add("takeover:" + takeover.kind());
            stored.add(takeover);
        }

        @Override
        public List<ChecklistTakeover> findByChecklistId(SurveyChecklistId checklistId) {
            return stored.stream().filter(takeover -> takeover.checklistId().equals(checklistId))
                    .sorted(Comparator.comparingInt(ChecklistTakeover::takeoverNo)).toList();
        }

        @Override
        public Optional<ChecklistTakeover> find(SurveyChecklistId checklistId, int takeoverNo) {
            return findByChecklistId(checklistId).stream().filter(takeover -> takeover.takeoverNo() == takeoverNo)
                    .findFirst();
        }

        @Override
        public int findNextTakeoverNo(SurveyChecklistId checklistId) {
            return findByChecklistId(checklistId).stream().mapToInt(ChecklistTakeover::takeoverNo).max().orElse(0)
                    + 1;
        }
    }

    /** The insert-only save log. */
    public static class Saves implements SurveyChecklistSaveDomainRepository {

        private final List<ChecklistSave> stored = new ArrayList<>();
        private final List<String> journal;

        public Saves(List<String> journal) {
            this.journal = journal;
        }

        public List<ChecklistSave> all() {
            return List.copyOf(stored);
        }

        @Override
        public void save(ChecklistSave save) {
            journal.add("save-log:" + save.group());
            stored.add(save);
        }

        @Override
        public List<ChecklistSave> findByChecklistId(SurveyChecklistId checklistId) {
            return stored.stream().filter(save -> save.checklistId().equals(checklistId)).toList();
        }
    }

    /** Insert-only security events; able to fail like a broken table. */
    public static class SecurityEvents implements SurveySecurityEventDomainRepository {

        private final List<SurveySecurityEvent> stored = new ArrayList<>();
        public RuntimeException failure;

        public List<SurveySecurityEvent> all() {
            return List.copyOf(stored);
        }

        @Override
        public void save(SurveySecurityEvent event) {
            if (failure != null) {
                throw failure;
            }
            stored.add(event);
        }

        @Override
        public List<SurveySecurityEvent> findByLeadId(String leadId) {
            return stored.stream().filter(event -> event.leadId().equals(leadId)).toList();
        }
    }
}
