package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-written fake of the assignee port: only the ids added with {@link #withEligible} are eligible; names come
 * from {@link #withName}. The eligible list is the one given to {@link #withListed} (empty by default), returned as
 * given: the fake does not sort.
 */
public class InMemoryLeadAssigneeDirectory implements LeadAssigneeDirectoryPort {

    private final Set<String> eligible = new HashSet<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final List<EligibleAssignee> listed = new ArrayList<>();
    private int nameLookups;
    private Set<String> lastNameLookup = Set.of();

    public InMemoryLeadAssigneeDirectory withEligible(String userId) {
        eligible.add(userId);
        return this;
    }

    public InMemoryLeadAssigneeDirectory withName(String userId, String fullName) {
        names.put(userId, fullName);
        return this;
    }

    /** Adds one entry to {@link #listEligibleAssignees}, after the ones already added. */
    public InMemoryLeadAssigneeDirectory withListed(EligibleAssignee assignee) {
        listed.add(assignee);
        return this;
    }

    /** How many times {@link #findDisplayNames} was called. */
    public int nameLookups() {
        return nameLookups;
    }

    /** The ids of the most recent {@link #findDisplayNames} call (empty before the first call). */
    public Set<String> lastNameLookup() {
        return lastNameLookup;
    }

    @Override
    public boolean isEligibleAssignee(String userId) {
        return eligible.contains(userId);
    }

    @Override
    public List<EligibleAssignee> listEligibleAssignees() {
        return List.copyOf(listed);
    }

    @Override
    public Map<String, String> findDisplayNames(Set<String> userIds) {
        nameLookups++;
        lastNameLookup = Set.copyOf(userIds);
        Map<String, String> found = new LinkedHashMap<>();
        for (String id : userIds) {
            if (names.containsKey(id)) {
                found.put(id, names.get(id));
            }
        }
        return found;
    }
}
