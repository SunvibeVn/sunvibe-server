package vn.sunvibe.shared.dto.response.error;

import lombok.Builder;

@Builder
public record ValidationError(String field, String message) {
}
