package vn.sunvibe.auth.core.domain.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ForgotPasswordInitiatedEvent {
    private String userId;
    private String email;
    private String otpCode;
    private String resetToken;
    private LocalDateTime initiatedAt;
}