package vn.sunvibe.auth.core.application.usecase.role.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.query.role.UserRolesResult;
import vn.sunvibe.auth.core.application.usecase.role.UserRoleQueryUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/**
 * Reads the roles of one account for the role admin API.
 */
@Service
@RequiredArgsConstructor
public class UserRoleQueryService implements UserRoleQueryUseCase {

    private final UserDomainRepository userRepository;

    @Transactional(readOnly = true)
    @Override
    public UserRolesResult execute(String userId) {
        AppUser user = RoleChangeRules.target(userRepository, userId);
        return new UserRolesResult(user.getId().getValue(), user.getUserRoles().stream().sorted().toList());
    }
}
