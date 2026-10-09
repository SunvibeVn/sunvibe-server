package vn.sunvibe.common.time;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Infrastructure implementation of DomainClockProvider.
 * Provides the actual time source for the domain layer.
 */
public class AppClock {

    // Get current time type Instant
    public static Instant currentUTC() {
        return Instant.now();
    }

    // Get current time type LocalDateTime (legacy method for backward compatibility)
    public static LocalDateTime currentLocalTime() {
        return LocalDateTime.now();
    }

}
