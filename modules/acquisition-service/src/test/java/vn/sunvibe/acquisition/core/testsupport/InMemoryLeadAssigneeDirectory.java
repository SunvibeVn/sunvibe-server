package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;

import java.util.HashSet;
import java.util.Set;

/** Hand-written fake of the assignee port: only the ids added with {@link #withEligible} are eligible. */
public class InMemoryLeadAssigneeDirectory implements LeadAssigneeDirectoryPort {

    private final Set<String> eligible = new HashSet<>();

    public InMemoryLeadAssigneeDirectory withEligible(String userId) {
        eligible.add(userId);
        return this;
    }

    @Override
    public boolean isEligibleAssignee(String userId) {
        return eligible.contains(userId);
    }
}
