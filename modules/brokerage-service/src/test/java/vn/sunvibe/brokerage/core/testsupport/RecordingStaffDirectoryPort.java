package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.account.StaffDirectoryPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Staff names in a map; an unknown id is absent. Every call records the ids it asked for. */
public final class RecordingStaffDirectoryPort implements StaffDirectoryPort {

    private final Map<String, String> names = new HashMap<>();
    private final List<Set<String>> calls = new ArrayList<>();

    /** The full name answered for the account from now on. */
    public void name(String userId, String fullName) {
        names.put(userId, fullName);
    }

    @Override
    public Map<String, String> displayNames(Collection<String> userIds) {
        calls.add(Set.copyOf(new LinkedHashSet<>(userIds)));
        Map<String, String> found = new HashMap<>();
        for (String userId : userIds) {
            if (names.containsKey(userId)) {
                found.put(userId, names.get(userId));
            }
        }
        return Map.copyOf(found);
    }

    /** The ids of each call so far, in call order. */
    public List<Set<String>> calls() {
        return List.copyOf(calls);
    }
}
