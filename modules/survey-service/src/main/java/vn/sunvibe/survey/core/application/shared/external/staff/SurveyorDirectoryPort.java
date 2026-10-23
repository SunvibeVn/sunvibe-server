package vn.sunvibe.survey.core.application.shared.external.staff;

import java.util.Map;
import java.util.Set;

/**
 * Port to staff accounts, owned by auth-service (survey-schedule spec 4.7, 5.4; ADR-009). Eligible = the account
 * exists, can sign in and holds SALE or SALE_LEADER, asked when the command runs or the data is read
 * (acquisition-owner-lead-queue BR-4). The adapter lives in app.
 */
public interface SurveyorDirectoryPort {

    boolean isEligible(String userId);

    /** The ids among these that are eligible now; one lookup. */
    Set<String> eligibleAmong(Set<String> userIds);

    /** The current full name of each id that has one; one lookup. Names are never logged. */
    Map<String, String> displayNames(Set<String> userIds);
}
