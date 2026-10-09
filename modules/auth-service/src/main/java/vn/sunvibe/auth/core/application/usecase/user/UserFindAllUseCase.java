package vn.sunvibe.auth.core.application.usecase.user;


import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

public interface UserFindAllUseCase {

    AppUser getUserInfo(String username);

}
