package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Hand-written fake of the staff directory: eligible ids and full names; counts the name lookups. */
public class InMemorySurveyorDirectory implements SurveyorDirectoryPort {

    private final Set<String> eligible = new HashSet<>();
    private final Map<String, String> names = new HashMap<>();
    public int nameLookups;

    public InMemorySurveyorDirectory withEligible(String userId, String name) {
        eligible.add(userId);
        names.put(userId, name);
        return this;
    }

    public InMemorySurveyorDirectory withName(String userId, String name) {
        names.put(userId, name);
        return this;
    }

    public void makeIneligible(String userId) {
        eligible.remove(userId);
    }

    @Override
    public boolean isEligible(String userId) {
        return eligible.contains(userId);
    }

    @Override
    public Set<String> eligibleAmong(Set<String> userIds) {
        return userIds.stream().filter(eligible::contains).collect(Collectors.toSet());
    }

    @Override
    public Map<String, String> displayNames(Set<String> userIds) {
        nameLookups++;
        return userIds.stream().filter(names::containsKey).collect(Collectors.toMap(id -> id, names::get));
    }
}
