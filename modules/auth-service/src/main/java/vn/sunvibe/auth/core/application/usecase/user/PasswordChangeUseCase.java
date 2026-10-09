package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.auth.PasswordChangeCommand;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordChangeResponse;

public interface PasswordChangeUseCase {

    PasswordChangeResponse execute(PasswordChangeCommand passwordChangeCommand);

}
