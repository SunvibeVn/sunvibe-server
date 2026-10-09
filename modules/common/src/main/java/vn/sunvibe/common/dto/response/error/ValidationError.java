package vn.sunvibe.common.dto.response.error;

import lombok.Builder;

@Builder
public record ValidationError(String field, String message) {
}
