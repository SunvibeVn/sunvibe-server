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
    UserEntity toEntity(AppUser appUser);

}