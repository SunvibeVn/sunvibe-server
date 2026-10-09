package vn.sunvibe.auth.core.application.query.auth;

import lombok.Builder;
import org.springframework.http.ResponseCookie;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;

@Builder
public record AuthResult(ResponseCookie accessCookie, ResponseCookie refreshCookie, LoginResponse loginResponse) {
}
