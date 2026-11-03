package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import vn.sunvibe.survey.core.application.shared.MediaInstanceSettings;
import vn.sunvibe.survey.core.application.shared.MediaReadBudgetLimits;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.infrastructure.config.MediaInstanceConfig;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaReadConfig;

/**
 * survey-media spec A-3, A-26 and 9 (configuration), code review M2 (SV-069): the read budgets and the bodies per user
 * reach the settings the limits use; no bound property does nothing.
 */
class MediaLimitPropertiesWiringTest {

	@EnableConfigurationProperties(MediaProperties.class)
	static class Bound {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(Bound.class, MediaReadConfig.class, MediaInstanceConfig.class);

	@Test
	void configuredBudgetsAndBodiesPerUser_reachTheSettings() {
		runner.withPropertyValues("app.survey.media.budgets.full=7", "app.survey.media.budgets.preview=8",
				"app.survey.media.budgets.thumbnail=9", "app.survey.media.budgets.list=10",
				"app.survey.media.bodies.per-user=5", "app.survey.media.bodies.per-instance=11").run(context -> {
					MediaReadBudgetLimits budgets = context.getBean(MediaReadBudgetLimits.class);
					assertThat(budgets.of(MediaCounterKind.FULL)).isEqualTo(7);
					assertThat(budgets.of(MediaCounterKind.PREVIEW)).isEqualTo(8);
					assertThat(budgets.of(MediaCounterKind.THUMBNAIL)).isEqualTo(9);
					assertThat(budgets.of(MediaCounterKind.LIST)).isEqualTo(10);
					MediaInstanceSettings instance = context.getBean(MediaInstanceSettings.class);
					assertThat(instance.bodiesPerUser()).isEqualTo(5);
					assertThat(instance.bodiesPerInstance()).isEqualTo(11);
				});
	}

	@Test
	void defaults_areTheSpecValues() {
		runner.run(context -> {
			assertThat(context.getBean(MediaReadBudgetLimits.class)).isEqualTo(MediaReadBudgetLimits.SPEC);
			assertThat(context.getBean(MediaInstanceSettings.class).bodiesPerUser()).isEqualTo(3);
		});
	}

	@Test
	void zeroBudget_failsAtStartUp() {
		runner.withPropertyValues("app.survey.media.budgets.list=0").run(context ->
				assertThat(context).hasFailed());
	}
}
