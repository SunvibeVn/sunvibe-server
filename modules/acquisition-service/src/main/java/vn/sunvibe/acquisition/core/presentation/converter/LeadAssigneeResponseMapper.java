package vn.sunvibe.acquisition.core.presentation.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadAssigneeResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadAssigneeResponse.AssigneeRole;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Converter - LeadAssigneeResponseMapper
 * <p>
 * Eligible assignees -> response DTOs, in the order given. A role name outside {@link AssigneeRole} (the directory
 * learned a new one) is left out instead of failing the call; a person left with no role is left out too, because
 * the list only offers people the assign call accepts. Names and ids are never logged.
 */
@Slf4j
@Component
public class LeadAssigneeResponseMapper {

    public List<LeadAssigneeResponse> toResponses(List<EligibleAssignee> assignees) {
        List<LeadAssigneeResponse> responses = new ArrayList<>(assignees.size());
        int skipped = 0;
        for (EligibleAssignee assignee : assignees) {
            Set<AssigneeRole> roles = EnumSet.noneOf(AssigneeRole.class);
            for (String role : assignee.roles()) {
                AssigneeRole known = known(role);
                if (known != null) {
                    roles.add(known);
                }
            }
            if (roles.isEmpty()) {
                skipped++;
                continue;
            }
            // EnumSet iterates in declaration order: SALE before SALE_LEADER
            responses.add(new LeadAssigneeResponse(assignee.userId(), assignee.fullName(), List.copyOf(roles)));
        }
        if (skipped > 0) {
            log.warn("Left {} staff accounts out of the assignee list: no role of SALE or SALE_LEADER", skipped);
        }
        return responses;
    }

    private static AssigneeRole known(String role) {
        for (AssigneeRole candidate : AssigneeRole.values()) {
            if (candidate.name().equals(role)) {
                return candidate;
            }
        }
        return null;
    }
}
