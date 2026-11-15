package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerAccountPort;

/**
 * Cross-module composition (E05-S1 spec 5.5, ADR-009): answers brokerage-service's owner question with auth-service's
 * published owner standing (property-create intake D5-10). It only forwards the answer, and joins the caller's
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class AuthOwnerAccountAdapter implements OwnerAccountPort {

    private final OwnerAccountUseCase owners;

    @Override
    public boolean eligibleOwner(String userId) {
        return owners.ownerStanding(userId).eligible();
    }
}
