package vn.sunvibe.auth.core.presentation.controller.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.auth.core.application.usecase.user.UserGetInfoUseCase;
import vn.sunvibe.common.response.ApiResponse;
import vn.sunvibe.auth.core.presentation.dto.response.user.UserProfileInfo;

@RestController
@RequestMapping("/api/user/query")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserGetInfoUseCase userGetInfoUseCase;

    @GetMapping("/my-info")
    public ResponseEntity<ApiResponse<UserProfileInfo>> showMe() {
        return ResponseEntity.ok(ApiResponse.<UserProfileInfo>builder()
                .code(HttpStatus.OK.value())
                .result(userGetInfoUseCase.execute())
                .build());
    }

}
