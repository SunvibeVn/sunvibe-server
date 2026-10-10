package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.query.user.UserProfileResult;

/**
 * Reads the signed-in user's own profile.
 */
public interface UserGetInfoUseCase {

    UserProfileResult execute();

}
