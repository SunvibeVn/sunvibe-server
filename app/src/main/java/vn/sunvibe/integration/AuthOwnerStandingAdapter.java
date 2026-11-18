package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStandingUseCase;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingPort;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Cross-module composition (ADR-009; ADR-012 decision 2, read 4): answers property-service's owner standing of a set of
 * accounts with auth-service's published {@link OwnerStandingUseCase}. It only translates records.
 */
@Component
@RequiredArgsConstructor
public class AuthOwnerStandingAdapter implements OwnerStandingPort {

    private final OwnerStandingUseCase standings;

    @Override
    public Map<String, OwnerStandingFacts> ownerStandings(Collection<String> userIds) {
        Map<String, OwnerStandingFacts> facts = new HashMap<>();
        standings.ownerStandings(userIds).forEach((userId, standing) ->
                facts.put(userId, new OwnerStandingFacts(standing.eligible(), standing.signInLosses())));
        return Map.copyOf(facts);
    }
}
