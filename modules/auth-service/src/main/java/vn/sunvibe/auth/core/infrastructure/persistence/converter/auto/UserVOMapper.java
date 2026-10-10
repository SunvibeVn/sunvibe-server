package vn.sunvibe.auth.core.infrastructure.persistence.converter.auto;

import org.mapstruct.Mapper;
import org.mapstruct.Named;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Value-object conversions used by {@link UserMapper}. The domain side is
 * rebuilt with the VOs' {@code from()} factories (no re-validation of stored data).
 */
@Mapper(componentModel = "spring")
public interface UserVOMapper {

    /* ===== DOMAIN → ENTITY (VO → String) ===== */
    @Named("mapUserId")
    default String mapUserId(UserId userId) {
        return userId.getValue();
    }

    @Named("mapUsername")
    default String mapUsername(Username username) {
        return username.getValue();
    }

    @Named("mapEmail")
    default String mapEmail(Email email) {
        return email != null ? email.getValue() : null;
    }

    @Named("mapPassword")
    default String mapPassword(Password password) {
        return password.getHashedValue();
    }

    @Named("mapProfileImage")
    default String mapProfileImage(ProfileImage profileImg) {
        return profileImg != null ? profileImg.getUrl() : null;
    }

    @Named("mapIdCardNumber")
    default String mapIdCardNumber(IdCardNumber idCardNumber) {
        return idCardNumber != null ? idCardNumber.getValue() : null;
    }

    @Named("mapStringsToUserRoles")
    default Set<UserRoleEntity> mapStringsToUserRoles(Set<String> roles) {
        if (roles == null) return null;
        return roles.stream().map(roleName -> {
            UserRoleEntity role = new UserRoleEntity();
            role.setName(roleName);
            return role;
        }).collect(Collectors.toSet());
    }

    /* ===== ENTITY → DOMAIN (String → VO) ===== */
    @Named("toUserId")
    default UserId toUserId(String id) {
        return UserId.from(id);
    }

    @Named("toUserName")
    default Username toUserName(String username) {
        return Username.from(username);
    }

    @Named("toEmail")
    default Email toEmail(String email) {
        return email != null ? Email.from(email) : null;
    }

    @Named("toPassword")
    default Password toPassword(String passwordHash) {
        return Password.from(passwordHash);
    }

    @Named("toProfileImage")
    default ProfileImage toProfileImage(String url) {
        return url != null ? ProfileImage.from(url) : null;
    }

    @Named("toIdCardNumber")
    default IdCardNumber toIdCardNumber(String value) {
        return value != null ? IdCardNumber.from(value) : null;
    }

    @Named("mapUserRolesToStrings")
    default Set<String> mapUserRolesToStrings(Set<UserRoleEntity> roles) {
        if (roles == null) return null;
        return roles.stream().map(UserRoleEntity::getName).collect(Collectors.toSet());
    }
}
