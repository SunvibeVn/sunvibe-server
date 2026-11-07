package vn.sunvibe.survey;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.sunvibe.survey.core.application.shared.checklist.ChecklistSwitches;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;

/**
 * The test-only runtime toggle of the survey-media switches (plan Global Constraints): the media API tests share one
 * Spring context, so a test turns uploads off and back on here instead of starting a context with other properties.
 * Uploads are on unless a test turns them off; {@link #reset()} after each such test.
 */
public final class MediaTestSwitches implements MediaSwitches, ChecklistSwitches {

	private volatile boolean uploads = true;
	private volatile boolean serving = true;
	private volatile boolean processing = true;
	private volatile boolean submission;

	@Override
	public boolean uploadsOn() {
		return uploads;
	}

	@Override
	public boolean servingOn() {
		return serving;
	}

	@Override
	public boolean submissionEnabled() {
		return submission;
	}

	/** The checklist submission switch (checklist submission spec A-3): off, as configured, unless a test turns it on. */
	public void submission(boolean on) {
		submission = on;
	}

	public void uploads(boolean on) {
		uploads = on;
	}

	public void serving(boolean on) {
		serving = on;
	}

	/** Processing effectively on for an explicit sweep (the media test context has no scanner or processor). */
	public void processing(boolean on) {
		processing = on;
	}

	@Override
	public boolean processingOn() {
		return processing;
	}

	public void reset() {
		processing = true;
		uploads = true;
		serving = true;
		submission = false;
	}

	/** Imported by {@link MediaApiTestBase}: the toggle wins over the configured switches. */
	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		MediaTestSwitches mediaTestSwitches() {
			return new MediaTestSwitches();
		}

		/** The probe answers of the scanner and processor the shared media context lacks (spec A-18). */
		@Bean
		MediaTestProbes mediaTestProbes() {
			return new MediaTestProbes();
		}

		@Bean
		@Primary
		vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner mediaTestScanner(
				MediaTestProbes probes) {
			return probes.scanner();
		}

		@Bean
		@Primary
		vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer mediaTestNormalizer(
				MediaTestProbes probes) {
			return probes.normalizer();
		}

		/** The set repository behind the read hook of the evidence tests (plan Task A18). */
		@Bean
		@Primary
		MediaTestSetReads mediaTestSetReads(
				@Qualifier("surveyMediaSetRepositoryAdapter") SurveyMediaSetDomainRepository adapter) {
			return new MediaTestSetReads(adapter);
		}

		/** The configured store behind the test spy. */
		@Bean
		@Primary
		MediaTestStore mediaTestStore(@Qualifier("mediaObjectStore") MediaObjectStore configured) {
			return new MediaTestStore(configured);
		}
	}
}
