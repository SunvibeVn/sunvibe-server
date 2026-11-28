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
import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The test subscription of the stop tests (brokerage-agreement E05-S8 plan E05S8-P8), a top-level copy of
 * {@link AgreementActivationOutboxPostgresTest}'s: one consumer of the unit-advertising event in place of the production
 * list, which subscribes none, so a stop's event is stored as a row; and, for the listing API tests that share this
 * context (listing-publish E06-S6 spec E06S6-D21), one consumer of the listing event, declared with its listing id, so a
 * put live's event is stored as a row too. The worker is off in the test profile, so the rows stay to be counted.
 */
@TestConfiguration
class AgreementStopTestSubscription {

	static final String CONSUMER = "test-unit-advertising";
	static final String LISTING_CONSUMER = "test-listing-changed";
	/**
	 * Listing's real consumer of E05's event (listing-publish E06-S7 spec E06S7-D13, S-4): its adapter is a bean of every
	 * app context, and a consumer bean subscribed to nothing stops the start-up, so this list subscribes it too.
	 */
	static final String LISTING_UNIT_ADVERTISING = "listing-unit-advertising";

	@Bean
	@Primary
	OutboxSubscriptions testOutboxSubscriptions() {
		return OutboxSubscriptions.of(Map.of(UnitAdvertisingChangedEvent.TYPE, new OutboxSubscriptions.Subscription(
				Set.of(UnitAdvertisingChangedEvent.UNIT_ID), List.of(CONSUMER, LISTING_UNIT_ADVERTISING)),
				ListingChangedEvent.TYPE, new OutboxSubscriptions.Subscription(
						Set.of(ListingChangedEvent.LISTING_ID), List.of(LISTING_CONSUMER))));
	}

	/** Records nothing: the worker is off in the test profile, so nothing is delivered. */
	@Bean
	OutboxConsumer testUnitAdvertisingConsumer() {
		return idleConsumer(CONSUMER);
	}

	/** The listing event's consumer of this list; records nothing either (E06S6-D21). */
	@Bean
	OutboxConsumer testListingChangedConsumer() {
		return idleConsumer(LISTING_CONSUMER);
	}

	private static OutboxConsumer idleConsumer(String name) {
		return new OutboxConsumer() {

			@Override
			public String name() {
				return name;
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
