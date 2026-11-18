package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStandingUseCase;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerEligibilityPort;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-module composition (E05-S6 spec S6-D17, ADR-009; ADR-012 decision 2, read 4): answers brokerage-service's eligible
 * owners of a set of accounts with auth-service's published {@link OwnerStandingUseCase}. It only translates, and joins
 * the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class AuthOwnerEligibilityAdapter implements OwnerEligibilityPort {

    private final OwnerStandingUseCase standings;

    @Override
    public Set<String> eligibleOwners(Collection<String> userIds) {
        return standings.ownerStandings(userIds).entrySet().stream()
                .filter(entry -> entry.getValue().eligible())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
