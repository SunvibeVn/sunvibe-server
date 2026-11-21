package vn.sunvibe.property;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.sunvibe.outbox.OutboxConsumer;
import vn.sunvibe.outbox.OutboxRetryPolicy;
import vn.sunvibe.outbox.OutboxSubscriptions;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The test subscription of the stop tests (brokerage-agreement E05-S8 plan E05S8-P8), a top-level copy of
 * {@link AgreementActivationOutboxPostgresTest}'s: one consumer of the unit-advertising event in place of the production
 * list, which subscribes none, so a stop's event is stored as a row. The worker is off in the test profile, so the rows
 * stay to be counted.
 */
@TestConfiguration
class AgreementStopTestSubscription {

	static final String CONSUMER = "test-unit-advertising";

	@Bean
	@Primary
	OutboxSubscriptions testOutboxSubscriptions() {
		return OutboxSubscriptions.of(Map.of(UnitAdvertisingChangedEvent.TYPE, new OutboxSubscriptions.Subscription(
				Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of(CONSUMER))));
	}

	/** Records nothing: the worker is off in the test profile, so nothing is delivered. */
	@Bean
	OutboxConsumer testUnitAdvertisingConsumer() {
		return new OutboxConsumer() {

			@Override
			public String name() {
				return CONSUMER;
			}

			@Override
			public OutboxRetryPolicy retryPolicy() {
				return OutboxRetryPolicy.DEFAULT;
			}

			@Override
			public void deliver(Delivery delivery) {
				// never called here
			}
		};
	}
}
