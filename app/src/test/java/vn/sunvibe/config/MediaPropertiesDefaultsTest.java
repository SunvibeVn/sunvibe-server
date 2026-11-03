package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.ProcessorKind;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.ScannerKind;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.StoreKind;

/** The defaults of {@code app.survey.media.*} (survey-media spec 5.1, A-1, A-3, A-25, A-26; plan PD-12). */
class MediaPropertiesDefaultsTest {

	@EnableConfigurationProperties(MediaProperties.class)
	static class Bound {
	}

	@Test
	void everyDefault_matchesTheSpec() {
		new ApplicationContextRunner().withUserConfiguration(Bound.class).run(context -> {
			MediaProperties media = context.getBean(MediaProperties.class);

			assertThat(media.isUploadEnabled()).isFalse();
			assertThat(media.isProcessingEnabled()).isTrue();
			assertThat(media.isServingEnabled()).isTrue();
			assertThat(media.getStore()).isEqualTo(StoreKind.NONE);
			assertThat(media.getScanner()).isEqualTo(ScannerKind.NONE);
			assertThat(media.getProcessor().getKind()).isEqualTo(ProcessorKind.NONE);
			assertThat(media.getS3().getRegion()).isEqualTo("auto");
			assertThat(media.getWorker().getConcurrency()).isEqualTo(2);
			assertThat(media.getReads().getUserConcurrency()).isEqualTo(6);
			assertThat(media.getReads().getInstanceConcurrency()).isEqualTo(32);
			assertThat(media.getReads().getNoProgressTimeout()).isEqualTo(Duration.ofSeconds(30));
			assertThat(media.getReads().getFullDeadline()).isEqualTo(Duration.ofMinutes(5));
			assertThat(media.getReads().getPreviewDeadline()).isEqualTo(Duration.ofMinutes(1));
			assertThat(media.getReads().getThumbnailDeadline()).isEqualTo(Duration.ofMinutes(1));
			assertThat(media.getReads().getAcquisitionTimeout()).isEqualTo(Duration.ofSeconds(2));
			assertThat(media.getBudgets().getFull()).isEqualTo(600);
			assertThat(media.getBudgets().getPreview()).isEqualTo(1200);
			assertThat(media.getBudgets().getThumbnail()).isEqualTo(6000);
			assertThat(media.getBudgets().getList()).isEqualTo(1200);
			assertThat(media.getBodies().getPerUser()).isEqualTo(3);
			assertThat(media.getBodies().getPerInstance()).isEqualTo(16);
			assertThat(media.getInstanceId()).hasSize(26);
		});
	}

	@Test
	void applicationYml_holdsTheSafeDefaults() throws IOException {
		List<PropertySource<?>> base = new YamlPropertySourceLoader().load("application.yml",
				new ClassPathResource("application.yml"));

		new ApplicationContextRunner().withUserConfiguration(Bound.class)
				.withInitializer(context -> base.forEach(source ->
						context.getEnvironment().getPropertySources().addLast(source)))
				.run(context -> {
					MediaProperties media = context.getBean(MediaProperties.class);
					assertThat(media.isUploadEnabled()).isFalse();
					assertThat(media.getStore()).isEqualTo(StoreKind.NONE);
					assertThat(media.getScanner()).isEqualTo(ScannerKind.NONE);
					assertThat(media.getProcessor().getKind()).isEqualTo(ProcessorKind.NONE);
				});
	}

	@Test
	void instanceId_differsPerInstanceUnlessConfigured() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Bound.class);
		String[] ids = new String[2];
		runner.run(context -> ids[0] = context.getBean(MediaProperties.class).getInstanceId());
		runner.run(context -> ids[1] = context.getBean(MediaProperties.class).getInstanceId());
		runner.withPropertyValues("app.survey.media.instance-id=api-1")
				.run(context -> assertThat(context.getBean(MediaProperties.class).getInstanceId())
						.isEqualTo("api-1"));

		assertThat(ids[0]).isNotEqualTo(ids[1]);
	}
}
