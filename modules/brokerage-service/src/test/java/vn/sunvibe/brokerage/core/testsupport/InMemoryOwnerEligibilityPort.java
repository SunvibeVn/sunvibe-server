package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerEligibilityPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The eligible owners in a set; every call is recorded with its ids. */
public final class InMemoryOwnerEligibilityPort implements OwnerEligibilityPort {

    private final Set<String> eligible = new HashSet<>();
    private final List<String> calls = new ArrayList<>();

    public InMemoryOwnerEligibilityPort eligible(String userId) {
        eligible.add(userId);
        return this;
    }

    public InMemoryOwnerEligibilityPort notEligible(String userId) {
        eligible.remove(userId);
        return this;
    }

    @Override
    public Set<String> eligibleOwners(Collection<String> userIds) {
        calls.add("eligibleOwners " + List.copyOf(userIds));
        return userIds.stream().filter(eligible::contains).collect(Collectors.toUnmodifiableSet());
    }

    /** The calls made so far, as "eligibleOwners [ids]", in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }
}
