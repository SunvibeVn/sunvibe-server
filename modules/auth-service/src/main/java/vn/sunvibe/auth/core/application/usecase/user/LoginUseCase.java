package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

public interface LoginUseCase {

    AuthResult execute(LoginCommand loginCommand, String rawFingerprint);

    AuthResult loginInternal(AppUser appUser, String rawFingerprint);

}
