package vn.sunvibe.auth.core.infrastructure.persistence.converter.auto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;

@Mapper(
        componentModel = "spring",
        uses = UserVOMapper.class
)
public interface UserMapper {

    /*────────────── ENTITY → DOMAIN ──────────────*/
    @Mapping(target = "id", source = "id", qualifiedByName = "toUserId")
    @Mapping(target = "username", source = "username", qualifiedByName = "toUserName")
    @Mapping(target = "email", source = "email", qualifiedByName = "toEmail")
    @Mapping(target = "password", source = "password", qualifiedByName = "toPassword")
    @Mapping(target = "profileImage", source = "profileImage", qualifiedByName = "toProfileImage")
    @Mapping(target = "createdDate", source = "createdDate")
    @Mapping(target = "lastUpdate", source = "lastUpdate")
    @Mapping(target = "lastLogin", source = "lastLogin")
    @Mapping(target = "userStatus", source = "userStatus")
    @Mapping(target = "userRoles", source = "userRoles", qualifiedByName = "mapUserRolesToStrings")
    @Mapping(target = "idCardNumber", source = "idCardNumber", qualifiedByName = "toIdCardNumber")
    @Mapping(target = "idCardFrontUrl", source = "idCardFrontUrl")
    @Mapping(target = "idCardBackUrl", source = "idCardBackUrl")
    @Mapping(target = "kycStatus", source = "kycStatus")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "failedLoginAttempts", source = "failedLoginAttempts")
    @Mapping(target = "lockedUntil", source = "lockedUntil")
    AppUser toDomain(UserEntity userEntity);

    /*────────────── DOMAIN → ENTITY ──────────────*/
    @Mapping(target = "id", source = "id", qualifiedByName = "mapUserId")
    @Mapping(target = "username", source = "username", qualifiedByName = "mapUsername")
    @Mapping(target = "email", source = "email", qualifiedByName = "mapEmail")
    @Mapping(target = "password", source = "password", qualifiedByName = "mapPassword")
    @Mapping(target = "profileImage", source = "profileImage", qualifiedByName = "mapProfileImage")
    @Mapping(target = "createdDate", source = "createdDate")
    @Mapping(target = "lastUpdate", source = "lastUpdate")
    @Mapping(target = "lastLogin", source = "lastLogin")
    @Mapping(target = "userStatus", source = "userStatus")
    @Mapping(target = "userRoles", source = "userRoles", qualifiedByName = "mapStringsToUserRoles")
    @Mapping(target = "idCardNumber", source = "idCardNumber", qualifiedByName = "mapIdCardNumber")
    @Mapping(target = "idCardFrontUrl", source = "idCardFrontUrl")
    @Mapping(target = "idCardBackUrl", source = "idCardBackUrl")
    @Mapping(target = "kycStatus", source = "kycStatus")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "failedLoginAttempts", source = "failedLoginAttempts")
    @Mapping(target = "lockedUntil", source = "lockedUntil")
    @Mapping(target = "idCardNumberHash", ignore = true)
    UserEntity toEntity(AppUser appUser);

}