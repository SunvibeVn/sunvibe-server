package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.application.shared.external.lead.LeadCard;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadPlace;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
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
    public RuntimeException failNextApply;

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
        return new PlaceCheck("Da Nang", "20194".equals(wardCode) ? "Hai Chau" : "Thanh Khe", latitude, longitude,
                List.of());
    }

    private static PlaceCheck violation(String field) {
        return new PlaceCheck(null, null, null, null, List.of(new PlaceViolation(field, field + " is not valid.")));
    }
}
