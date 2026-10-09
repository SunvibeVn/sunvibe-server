package vn.sunvibe.auth.core.domain.event;

import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
public class UserRegisteredEvent {

    private final UUID userId;
    private final String username;
    private final String email;
    private final LocalDateTime occurredAt;

}
