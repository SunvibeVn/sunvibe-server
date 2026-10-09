package vn.sunvibe.auth.core.infrastructure.persistence.converter.manual;

import org.springframework.stereotype.Component;

@Component
public class UserRoleConverter {

//    public Set<RoleName> mapToDomainRoles(Set<UserRoleEntity> roleEntities) {
//        if (roleEntities == null) return Set.of();
//        return roleEntities.stream()
//                .map(UserRoleEntity::getRoleName)
//                .collect(Collectors.toSet());
//    }
//
//    public Set<UserRoleEntity> mapToEntities(AppUser appUser) {
//        if (appUser.getUserRoles() == null) return Set.of();
//        return appUser.getUserRoles().stream()
//                .map(role -> new UserRoleEntity(UUID.randomUUID(), appUser.getUsername().getValue(), role))
//                .collect(Collectors.toSet());
//    }

}
