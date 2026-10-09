package vn.sunvibe.auth.core.infrastructure.time;

import lombok.RequiredArgsConstructor;

import java.time.Instant;

@RequiredArgsConstructor
public class Clock {

    public static Instant current() {
        return Instant.now();
    }

}
