package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Retry policies of ADR-014 decision 8 (E05-S5b S5b-D6). */
class OutboxRetryPolicyTest {

	@Test
	void defaultPolicy_backoffSchedule() {
		List<Integer> minutes = List.of(1, 2, 5, 10, 30, 60, 120, 240, 480);

		for (int attempt = 1; attempt <= 9; attempt++) {
			assertThat(OutboxRetryPolicy.DEFAULT.retryAfter(attempt))
					.as("attempt %d", attempt)
					.contains(Duration.ofMinutes(minutes.get(attempt - 1)));
		}
	}

	@Test
	void defaultPolicy_tenthFailure_isDead() {
		assertThat(OutboxRetryPolicy.DEFAULT.retryAfter(10)).isEqualTo(Optional.empty());
		assertThat(OutboxRetryPolicy.DEFAULT.retryAfter(11)).isEqualTo(Optional.empty());
	}

	@Test
	void removalPolicy_neverDead_everyFiveMinutes() {
		for (int attempt : new int[] { 1, 10, 1_000 }) {
			assertThat(OutboxRetryPolicy.REMOVAL.retryAfter(attempt)).as("attempt %d", attempt)
					.contains(Duration.ofMinutes(5));
		}
	}
}
