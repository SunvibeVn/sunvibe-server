package vn.sunvibe.auth.core.application.usecase.user;


import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;

public interface UserRegisterUseCase {

    void execute(UserRegisterCommand userRegisterRequest, String rawFingerprint);

}
