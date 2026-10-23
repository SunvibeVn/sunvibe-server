package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-module composition (survey-schedule spec 4.7, 5.4; ADR-009): answers survey-service's surveyor questions with
 * auth-service's published {@link LeadAssigneeAccountUseCase}, the same eligibility as lead assignment
 * (acquisition-owner-lead-queue BR-4). The batch question asks per id, because auth's list of eligible staff is cut
 * at its limit; the ids are the few surveyors of one survey block or one list page.
 */
@Component
@RequiredArgsConstructor
public class AuthSurveyorDirectoryAdapter implements SurveyorDirectoryPort {

    private final LeadAssigneeAccountUseCase leadAssigneeAccountUseCase;

    @Override
    public boolean isEligible(String userId) {
        return userId != null && leadAssigneeAccountUseCase.isEligibleOwnerLeadAssignee(userId);
    }

    @Override
    public Set<String> eligibleAmong(Set<String> userIds) {
        return userIds.stream().filter(this::isEligible).collect(Collectors.toSet());
    }

    @Override
    public Map<String, String> displayNames(Set<String> userIds) {
        return userIds.isEmpty() ? Map.of() : leadAssigneeAccountUseCase.findStaffDisplayNames(userIds);
    }
}
