package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import vn.sunvibe.survey.core.infrastructure.config.MediaProfileGuard;
import vn.sunvibe.survey.core.infrastructure.config.MediaRuntimeMode;

/**
 * The start-up guard of survey-media (spec A-1, A-28; SM-17 start-up): an environment is non-production only when its
 * active-profile set is non-empty and inside {dev, test, lan}; elsewhere the listed configuration errors refuse the
 * start. With every adapter {@code none} processing is effectively off whatever the switch says.
 */
class MediaProfileGuardTest {

	private static final List<List<String>> PRODUCTION_SETS = List.of(List.of(), List.of("prod"),
			List.of("dev", "prod"));
	private static final String S3 = "app.survey.media.store=s3";
	private static final String HTTPS = "app.survey.media.s3.endpoint=https://account.r2.cloudflarestorage.com";
	private static final String CLAMAV = "app.survey.media.scanner=clamav";
	private static final String ISOLATED = "app.survey.media.processor.kind=isolated";
	private static final String VERSION = "app.survey.media.processor.version=sha256:0123abcd";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(MediaProfileGuard.class);

	@Test
	void localStore_refusesStart() {
		refusedEverywhereInProduction("store=local", "app.survey.media.store=local",
				"app.survey.media.local.root=/srv/media");
	}

	@Test
	void stubScanner_refusesStart() {
		refusedEverywhereInProduction("scanner=stub", "app.survey.media.scanner=stub");
	}

	@Test
	void nonIsolatedProcessor_refusesStart() {
		for (List<String> profiles : PRODUCTION_SETS) {
			withProfiles(profiles).withPropertyValues("app.survey.media.processor.kind=in-process")
					.run(context -> assertThat(context).hasFailed());
		}
	}

	@Test
	void nonHttpsEndpoint_refusesStart() {
		refusedEverywhereInProduction("https", S3, "app.survey.media.s3.endpoint=http://minio:9000");
	}

	@Test
	void processorVersionBreakingPattern_refusesStart() {
		refusedEverywhereInProduction("processor.version", ISOLATED, "app.survey.media.processor.version=-dev 8");
	}

	@Test
	void processorVersionOver100_refusesStart() {
		refusedEverywhereInProduction("processor.version", ISOLATED,
				"app.survey.media.processor.version=" + "a".repeat(101));
	}

	@Test
	void uploadsOnWithNoneAdapter_refusesStart() {
		refusedEverywhereInProduction("upload-enabled", "app.survey.media.upload-enabled=true", S3, HTTPS, CLAMAV);
	}

	@Test
	void processingOnWithSomeNoneAdapters_refusesStart() {
		refusedEverywhereInProduction("processing-enabled", "app.survey.media.processing-enabled=true", S3, HTTPS);
	}

	@Test
	void prodAsShipped_everyAdapterNone_starts() throws IOException {
		List<PropertySource<?>> base = yaml("application.yml");
		List<PropertySource<?>> prod = yaml("application-prod.yml");

		runner.withInitializer(context -> {
			context.getEnvironment().setActiveProfiles("prod");
			prod.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
			base.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
		}).run(context -> {
			assertThat(context).hasNotFailed();
			MediaRuntimeMode mode = context.getBean(MediaRuntimeMode.class);
			assertThat(mode.everyAdapterNone()).isTrue();
			assertThat(mode.uploadsOn()).isFalse();
			assertThat(mode.processingOn()).isFalse();
			assertThat(mode.nonProduction()).isFalse();
		});
	}

	@Test
	void processingOnEveryAdapterNone_startsProcessingOff() {
		for (List<String> profiles : PRODUCTION_SETS) {
			withProfiles(profiles).withPropertyValues("app.survey.media.processing-enabled=true").run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(MediaRuntimeMode.class).processingOn()).isFalse();
			});
		}
	}

	@Test
	void realAdaptersInProduction_startWithProcessingOn() {
		withProfiles(List.of("prod")).withPropertyValues(S3, HTTPS, CLAMAV, ISOLATED, VERSION,
				"app.survey.media.upload-enabled=true").run(context -> {
					assertThat(context).hasNotFailed();
					MediaRuntimeMode mode = context.getBean(MediaRuntimeMode.class);
					assertThat(mode.processingOn()).isTrue();
					assertThat(mode.uploadsOn()).isTrue();
					assertThat(mode.servingOn()).isTrue();
				});
	}

	@Test
	void nonProductionSets_startWithLocalStoreAndStubScanner() {
		for (List<String> profiles : List.of(List.of("dev"), List.of("test"), List.of("lan"), List.of("dev", "test"),
				List.of("dev", "lan"))) {
			withProfiles(profiles).withPropertyValues("app.survey.media.store=local",
					"app.survey.media.local.root=/tmp/sunvibe-media", "app.survey.media.scanner=stub",
					"app.survey.media.s3.endpoint=http://localhost:9000").run(context -> {
						assertThat(context).hasNotFailed();
						assertThat(context.getBean(MediaRuntimeMode.class).nonProduction()).isTrue();
					});
		}
	}

	private void refusedEverywhereInProduction(String messagePart, String... properties) {
		for (List<String> profiles : PRODUCTION_SETS) {
			withProfiles(profiles).withPropertyValues(properties).run(failsWith(messagePart));
		}
	}

	private static ContextConsumer<AssertableApplicationContext> failsWith(String messagePart) {
		return context -> assertThat(context).hasFailed().getFailure().rootCause()
				.isInstanceOf(IllegalStateException.class).hasMessageContaining(messagePart);
	}

	private ApplicationContextRunner withProfiles(List<String> profiles) {
		return runner.withInitializer(context -> context.getEnvironment()
				.setActiveProfiles(profiles.toArray(String[]::new)));
	}

	private static List<PropertySource<?>> yaml(String name) throws IOException {
		return new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
	}
}
