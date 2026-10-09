package vn.sunvibe.auth.core.domain.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A password-reset code was e-mailed. Deliberately carries no OTP or reset
 * token: events may be logged or forwarded, secrets must not travel with them.
 */
@Data
@Builder
public class ForgotPasswordInitiatedEvent {
    private String userId;
    private String email;
    private LocalDateTime initiatedAt;
}
