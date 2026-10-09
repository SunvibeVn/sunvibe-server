package vn.sunvibe.auth.core.presentation.dto.response.auth;

import lombok.Builder;

import java.util.Set;

@Builder
public record LoginResponse(String tokenType, String username, String userEmail, Set<String> roles, String message) {
}