package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.testsupport.InMemoryLeadAssigneeDirectory;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assignee list (acquisition-owner-lead-queue-ui spec 4.1): the service only forwards the directory's answer.
 */
class LeadAssigneeQueryServiceTest {

    @Test
    void listEligible_forwardsTheDirectoryAnswerInItsOrder() {
        EligibleAssignee second = new EligibleAssignee("01JBBBBBBBBBBBBBBBBBBBBBBB", "Ánh",
                Set.of("SALE", "SALE_LEADER"));
        EligibleAssignee first = new EligibleAssignee("01JAAAAAAAAAAAAAAAAAAAAAAA", "An", Set.of("SALE"));
        LeadAssigneeQueryService service = new LeadAssigneeQueryService(
                new InMemoryLeadAssigneeDirectory().withListed(second).withListed(first));

        // the service does not sort: the order is the directory's
        assertEquals(List.of(second, first), service.listEligible());
    }

    @Test
    void listEligible_nobodyEligible_isEmpty() {
        assertTrue(new LeadAssigneeQueryService(new InMemoryLeadAssigneeDirectory()).listEligible().isEmpty());
    }
}
