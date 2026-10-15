package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;

/**
 * Cross-module composition: answers property-service's owner questions with auth-service's published
 * {@link OwnerAccountUseCase}, so neither module depends on the other. Lives in app, which depends on every module.
 */
@Component
@RequiredArgsConstructor
public class AuthUserDirectoryAdapter implements UserDirectoryPort {

    private final OwnerAccountUseCase ownerAccountUseCase;

    @Override
    public boolean isEligibleOwner(String userId) {
        return ownerAccountUseCase.isEligibleOwner(userId);
    }

    @Override
    public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
        return ownerAccountUseCase.ensureOwnerRole(ownerUserId, actorUserId);
    }
}
