package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;

/**
 * Cross-module composition: answers property-service's user lookups from auth-service, so neither module
 * depends on the other. Lives in app, which depends on every module.
 */
@Component
@RequiredArgsConstructor
public class AuthUserDirectoryAdapter implements UserDirectoryPort {

    private final UserDomainRepository userDomainRepository;

    @Override
    public boolean existsActiveUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        // Active = the same rule that lets an account sign in: not soft-deleted (findById skips those),
        // not locked, not terminated. A PENDING account counts, as it can create a property for itself.
        return userDomainRepository.findById(UserId.from(userId))
                .filter(AppUser::canAuthenticate)
                .isPresent();
    }
}
