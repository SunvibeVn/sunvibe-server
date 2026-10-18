package vn.sunvibe.acquisition.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadAssigneeResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadAssigneeResponse.AssigneeRole;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assignee records -> the response (acquisition-owner-lead-queue-ui spec 4.1): order kept, roles as the enum, and a
 * role the API does not know never fails the call.
 */
class LeadAssigneeResponseMapperTest {

    private final LeadAssigneeResponseMapper mapper = new LeadAssigneeResponseMapper();

    @Test
    void toResponses_keepsOrderAndMapsRoles() {
        List<LeadAssigneeResponse> responses = mapper.toResponses(List.of(
                new EligibleAssignee("u2", "Ánh", Set.of("SALE", "SALE_LEADER")),
                new EligibleAssignee("u1", "An", Set.of("SALE"))));

        assertEquals(List.of(
                new LeadAssigneeResponse("u2", "Ánh", List.of(AssigneeRole.SALE, AssigneeRole.SALE_LEADER)),
                new LeadAssigneeResponse("u1", "An", List.of(AssigneeRole.SALE))), responses);
    }

    @Test
    void toResponses_rolesComeOutSaleFirstWhateverTheInputOrder() {
        Set<String> leaderFirst = new LinkedHashSet<>(List.of("SALE_LEADER", "SALE"));

        List<LeadAssigneeResponse> responses =
                mapper.toResponses(List.of(new EligibleAssignee("u", "An", leaderFirst)));

        assertEquals(List.of(AssigneeRole.SALE, AssigneeRole.SALE_LEADER), responses.get(0).roles());
    }

    @Test
    void toResponses_unknownRole_isDroppedAndTheRestKept() {
        List<LeadAssigneeResponse> responses = mapper.toResponses(List.of(
                new EligibleAssignee("u1", "An", Set.of("SALE", "TRAINEE"))));

        assertEquals(List.of(AssigneeRole.SALE), responses.get(0).roles());
    }

    @Test
    void toResponses_onlyUnknownRoles_leavesThePersonOutWithoutFailing() {
        List<LeadAssigneeResponse> responses = mapper.toResponses(List.of(
                new EligibleAssignee("u1", "An", Set.of("TRAINEE")),
                new EligibleAssignee("u2", "Bình", Set.of()),
                new EligibleAssignee("u3", "Dũng", Set.of("SALE_LEADER"))));

        assertEquals(List.of(new LeadAssigneeResponse("u3", "Dũng", List.of(AssigneeRole.SALE_LEADER))), responses);
    }

    @Test
    void toResponses_emptyList_isEmpty() {
        assertTrue(mapper.toResponses(List.of()).isEmpty());
    }
}
