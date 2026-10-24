package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadCard;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadPlace;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceCheck;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceViolation;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Hand-written fake of the lead port: leads by id, the survey rows of the lead state table (survey-schedule spec 4.3),
 * a version check and a journal shared with the appointment repository fake, so a test sees the write order. The
 * catalog knows wards 20194 (Hai Chau) and 20195 (Thanh Khe) of province 48; pins must lie in 15.85..16.25.
 */
public class InMemorySurveyLeadPort implements SurveyLeadPort {

    public static final Instant CREATED_AT = Instant.parse("2026-10-01T03:00:00Z");

    private final Map<String, LeadFacts> leads = new LinkedHashMap<>();
    private final List<String> journal;
    private final List<LeadMove> moves = new ArrayList<>();
    private final List<ChecklistActivity> checklistActivities = new ArrayList<>();
    private final Map<String, Set<String>> historyStaff = new LinkedHashMap<>();
    private final Map<String, OwnerFormFacts> ownerForms = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    public RuntimeException failNextApply;
    public RuntimeException failNextChecklistActivity;

    public InMemorySurveyLeadPort(List<String> journal) {
        this.journal = journal;
    }

    /** Store a lead with the fixture place, created on 2026-10-01, never on hold. */
    public LeadFacts withLead(String leadId, String status, String intent, int cycle, String assignee, long version) {
        LeadFacts facts = new LeadFacts(leadId, "SV-" + leadId.substring(leadId.length() - 9, leadId.length() - 5)
                + "-" + leadId.substring(leadId.length() - 4), status, intent, cycle, assignee, version, CREATED_AT,
                "Owner " + leadId.substring(leadId.length() - 4), place(), CREATED_AT, null);
        leads.put(leadId, facts);
        return facts;
    }

    public void put(LeadFacts facts) {
        leads.put(facts.leadId(), facts);
    }

    public LeadFacts get(String leadId) {
        return leads.get(leadId);
    }

    public List<LeadMove> moves() {
        return List.copyOf(moves);
    }

    /** The checklist History entries written, in order. */
    public List<ChecklistActivity> checklistActivities() {
        return List.copyOf(checklistActivities);
    }

    /** Staff ids the lead's History names besides its assignee (survey-checklist spec A-25). */
    public InMemorySurveyLeadPort withHistoryStaff(String leadId, String... userIds) {
        historyStaff.computeIfAbsent(leadId, id -> new LinkedHashSet<>()).addAll(List.of(userIds));
        return this;
    }

    /** The owner's web form of the lead (survey-checklist spec A-27). */
    public InMemorySurveyLeadPort withOwnerForm(String leadId, OwnerFormFacts form) {
        ownerForms.put(leadId, form);
        return this;
    }

    /** The actor name History entries carry. */
    public InMemorySurveyLeadPort withName(String userId, String name) {
        names.put(userId, name);
        return this;
    }

    public static LeadPlace place() {
        return new LeadPlace("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau", new BigDecimal("16.054407"),
                new BigDecimal("108.202167"));
    }

    @Override
    public SurveyViewer viewer(String userId, Set<String> expandedAuthorities) {
        if (expandedAuthorities.contains("ROLE_SALE_LEADER") || expandedAuthorities.contains("ROLE_SALES_MANAGER")
                || expandedAuthorities.contains("ROLE_ADMIN")) {
            return new SurveyViewer(userId, ViewerAccess.ALL);
        }
        if (expandedAuthorities.contains("ROLE_SALE")) {
            return new SurveyViewer(userId, ViewerAccess.OWN);
        }
        throw new IllegalStateException("no staff role");
    }

    @Override
    public Optional<LeadFacts> findVisible(String leadId, SurveyViewer viewer) {
        return Optional.ofNullable(leads.get(leadId)).filter(lead -> viewer.access() == ViewerAccess.ALL
                || viewer.userId().equals(lead.assignedUserId()));
    }

    @Override
    public LeadMoveResult apply(String leadId, long expectedVersion, LeadMove move) {
        journal.add("lead:" + move.event());
        if (failNextApply != null) {
            RuntimeException failure = failNextApply;
            failNextApply = null;
            throw failure;
        }
        LeadFacts lead = leads.get(leadId);
        if (lead.version() != expectedVersion) {
            throw new LeadVersionConflictException();
        }
        String to = move.target() == LeadMoveTarget.KEEP ? null : move.target().name();
        if (!allowed(move, lead.status())) {
            throw new LeadMoveRefusedException(lead.status());
        }
        moves.add(move);
        LeadFacts moved = lead.moved(to == null ? lead.status() : to, lead.version() + 1, move.occurredAt());
        leads.put(leadId, moved);
        LeadHistoryEntry entry = new LeadHistoryEntry(move.event().name(), move.actorUserId(), null,
                to == null ? null : lead.status(), to, move.referenceId(), move.referenceNo(), move.occurredAt());
        return new LeadMoveResult(moved.status(), moved.version(), move.occurredAt(), entry);
    }

    private static boolean allowed(LeadMove move, String status) {
        LeadMoveTarget target = move.target();
        return switch (move.event()) {
            case SURVEY_BOOKED -> target == LeadMoveTarget.SURVEY_SCHEDULED
                    && (status.equals("ASSIGNED") || status.equals("CONTACTED"))
                    || target == LeadMoveTarget.KEEP && status.equals("SURVEY_SCHEDULED");
            case SURVEY_CHANGED, SURVEY_CONFIRMED, SURVEY_UNCONFIRMED, SURVEY_VISITED ->
                    target == LeadMoveTarget.KEEP && status.equals("SURVEY_SCHEDULED");
            case SURVEY_CANCELLED -> target != LeadMoveTarget.SURVEY_SCHEDULED && status.equals("SURVEY_SCHEDULED");
            case SURVEY_RESUMED -> target == LeadMoveTarget.SURVEY_SCHEDULED && status.equals("ON_HOLD");
        };
    }

    @Override
    public Map<String, LeadCard> cards(SurveyViewer viewer, Collection<String> leadIds) {
        Map<String, LeadCard> cards = new LinkedHashMap<>();
        for (String id : leadIds) {
            findVisible(id, viewer).ifPresent(lead -> cards.put(id,
                    new LeadCard(id, lead.requestCode(), lead.contactName(), lead.place().wardName())));
        }
        return cards;
    }

    @Override
    public PlaceCheck checkPlace(String provinceCode, String wardCode, BigDecimal latitude, BigDecimal longitude) {
        if (!"48".equals(provinceCode)) {
            return violation("provinceCode");
        }
        if (!"20194".equals(wardCode) && !"20195".equals(wardCode)) {
            return violation("wardCode");
        }
        if ((latitude == null) != (longitude == null) || latitude != null
                && (latitude.compareTo(new BigDecimal("15.85")) < 0
                || latitude.compareTo(new BigDecimal("16.25")) > 0)) {
            return violation("latitude");
        }
        // Like the intake location rules, the pin comes back rounded to 6 decimals
        return new PlaceCheck("Da Nang", "20194".equals(wardCode) ? "Hai Chau" : "Thanh Khe",
                latitude == null ? null : latitude.setScale(6, java.math.RoundingMode.HALF_UP),
                longitude == null ? null : longitude.setScale(6, java.math.RoundingMode.HALF_UP), List.of());
    }

    @Override
    public Optional<LeadFacts> findVisibleForShare(String leadId, SurveyViewer viewer) {
        journal.add("lead:share");
        return findVisible(leadId, viewer);
    }

    @Override
    public ChecklistActivityResult recordChecklistActivity(String leadId, ChecklistActivity activity) {
        journal.add("lead:activity:" + activity.type());
        if (failNextChecklistActivity != null) {
            RuntimeException failure = failNextChecklistActivity;
            failNextChecklistActivity = null;
            throw failure;
        }
        checklistActivities.add(activity);
        LeadFacts lead = leads.get(leadId);
        // survey-checklist spec 4.9: LAST UPDATE moves, the version and status stay
        leads.put(leadId, lead.moved(lead.status(), lead.version(), activity.occurredAt()));
        return new ChecklistActivityResult(activity.occurredAt(), new ChecklistHistoryEntry(activity.type().name(),
                activity.actorUserId(), names.get(activity.actorUserId()), activity.checklistId(),
                activity.checklistNo(), activity.detailOrNull(), activity.occurredAt()));
    }

    @Override
    public boolean leadExists(String leadId) {
        return leads.containsKey(leadId);
    }

    @Override
    public Optional<OwnerFormFacts> ownerForm(String leadId) {
        return Optional.ofNullable(ownerForms.get(leadId));
    }

    @Override
    public Set<String> leadStaffIds(String leadId) {
        Set<String> staff = new LinkedHashSet<>();
        LeadFacts lead = leads.get(leadId);
        if (lead != null && lead.assignedUserId() != null) {
            staff.add(lead.assignedUserId());
        }
        staff.addAll(historyStaff.getOrDefault(leadId, Set.of()));
        return staff;
    }

    private static PlaceCheck violation(String field) {
        return new PlaceCheck(null, null, null, null, List.of(new PlaceViolation(field, field + " is not valid.")));
    }
}
