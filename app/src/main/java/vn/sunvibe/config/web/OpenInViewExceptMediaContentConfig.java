package vn.sunvibe.config.web;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import vn.sunvibe.shared.web.MediaContentPaths;

/**
 * Open-in-view for every endpoint except the two survey photo content streams (survey-media spec A-13) and the endpoints
 * E05 to E07 add (ADR-012 decision 8: each path pattern is listed in this class, with a test, in the change that adds
 * the endpoint). With {@code spring.jpa.open-in-view=false} this interceptor gives every other endpoint today's
 * behaviour, while no EntityManager, and so no pooled connection, is bound while a content body or variant streams, and
 * an excluded read loads everything it returns inside its own transaction. Turning open-in-view off for the whole
 * server is a separate decision.
 */
@Configuration(proxyBeanMethods = false)
public class OpenInViewExceptMediaContentConfig implements WebMvcConfigurer {

	/** The brokerage agreement eligibility read of a lead (E05-S1 spec S1-D8). */
	static final String BROKERAGE_ELIGIBILITY_PATTERN = "/api/v1/staff/owner-leads/*/brokerage-eligibility";

	/** The brokerage agreement create (E05-S2 spec S2-D13) and the staff agreement list, a GET (E05-S7 spec S7-D11). */
	static final String BROKERAGE_AGREEMENTS_PATTERN = "/api/v1/staff/brokerage-agreements";

	/** The brokerage agreement detail read (E05-S2 spec S2-D13). */
	static final String BROKERAGE_AGREEMENT_PATTERN = "/api/v1/staff/brokerage-agreements/*";

	/** The brokerage agreement draft write (E05-S3 spec S3-D16); {@code /*} above matches one segment only. */
	static final String BROKERAGE_AGREEMENT_DRAFT_PATTERN = "/api/v1/staff/brokerage-agreements/*/draft";

	/** The survey reference of an agreement's draft (E05-S3 spec S3-D16). */
	static final String BROKERAGE_AGREEMENT_SURVEY_TERMS_PATTERN = "/api/v1/staff/brokerage-agreements/*/survey-terms";

	/** The sending of an agreement's draft (E05-S4 spec S4-D12); {@code /*}{@code /draft} matches that path only. */
	static final String BROKERAGE_AGREEMENT_SUBMISSION_PATTERN = "/api/v1/staff/brokerage-agreements/*/draft/submission";

	/** The owner's read of their agreements (E05-S5 spec S5-D15). */
	static final String ME_BROKERAGE_AGREEMENTS_PATTERN = "/api/v1/me/brokerage-agreements";

	/** The owner's confirmation of sent versions (E05-S5 spec S5-D15). */
	static final String ME_BROKERAGE_AGREEMENT_CONFIRMATIONS_PATTERN = "/api/v1/me/brokerage-agreements/confirmations";

	/** The owner's rejection of a sent version (E05-S5 spec S5-D15); the staff POST .../draft is covered by the draft pattern. */
	static final String ME_BROKERAGE_AGREEMENT_REJECTION_PATTERN = "/api/v1/me/brokerage-agreements/*/rejection";

	/** The activation of a confirmed version (E05-S6 spec S6-D18). */
	static final String BROKERAGE_AGREEMENT_ACTIVATION_PATTERN = "/api/v1/staff/brokerage-agreements/*/activation";

	/** The refusal of an activation (E05-S6 spec S6-D18). */
	static final String BROKERAGE_AGREEMENT_ACTIVATION_REFUSAL_PATTERN =
			"/api/v1/staff/brokerage-agreements/*/activation-refusal";

	/** The history of an agreement (E05-S7 spec S7-D11). */
	static final String BROKERAGE_AGREEMENT_HISTORY_PATTERN = "/api/v1/staff/brokerage-agreements/*/history";

	/** The suspension of an agreement (E05-S8 spec S8-D15). */
	static final String BROKERAGE_AGREEMENT_SUSPENSION_PATTERN = "/api/v1/staff/brokerage-agreements/*/suspension";

	/** The release of a suspension (E05-S8 spec S8-D15). */
	static final String BROKERAGE_AGREEMENT_SUSPENSION_RELEASE_PATTERN =
			"/api/v1/staff/brokerage-agreements/*/suspension-release";

	/** The termination of an agreement (E05-S8 spec S8-D15). */
	static final String BROKERAGE_AGREEMENT_TERMINATION_PATTERN = "/api/v1/staff/brokerage-agreements/*/termination";

	/** The cancellation of an agreement (E05-S8 spec S8-D15). */
	static final String BROKERAGE_AGREEMENT_CANCELLATION_PATTERN = "/api/v1/staff/brokerage-agreements/*/cancellation";

	/** The listing create (listing-publish E06-S1 spec E06S1-D11). */
	static final String LISTINGS_PATTERN = "/api/v1/staff/listings";

	/** The listing read (listing-publish E06-S1 spec E06S1-D11); {@code /*} matches one segment only. */
	static final String LISTING_PATTERN = "/api/v1/staff/listings/*";

	/** The listing section of an agreement's page (listing-publish E06-S1 spec E06S1-D11). */
	static final String LISTINGS_BY_AGREEMENT_PATTERN = "/api/v1/staff/listings/by-agreement/*";

	/**
	 * Every sub-resource of one listing: its revisions, their commands and the terms sources (listing-publish E06-S2 spec
	 * E06S2-D16), so a later sub-resource falls under it with a test of its own.
	 */
	static final String LISTING_SUBRESOURCES_PATTERN = "/api/v1/staff/listings/*/**";

	/** The anonymous listing read by its public code (listing-publish E06-S7 spec E06S7-D8). */
	static final String PUBLIC_LISTING_PATTERN = "/api/v1/public/listings/*";

	private final EntityManagerFactory entityManagerFactory;

	public OpenInViewExceptMediaContentConfig(EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		OpenEntityManagerInViewInterceptor openInView = new OpenEntityManagerInViewInterceptor();
		if (entityManagerFactory != null) {
			openInView.setEntityManagerFactory(entityManagerFactory);
		}
		registry.addWebRequestInterceptor(openInView)
				.excludePathPatterns(MediaContentPaths.UPLOAD_PATTERN, MediaContentPaths.READ_PATTERN,
						BROKERAGE_ELIGIBILITY_PATTERN, BROKERAGE_AGREEMENTS_PATTERN, BROKERAGE_AGREEMENT_PATTERN,
						BROKERAGE_AGREEMENT_DRAFT_PATTERN, BROKERAGE_AGREEMENT_SURVEY_TERMS_PATTERN,
						BROKERAGE_AGREEMENT_SUBMISSION_PATTERN, ME_BROKERAGE_AGREEMENTS_PATTERN,
						ME_BROKERAGE_AGREEMENT_CONFIRMATIONS_PATTERN, ME_BROKERAGE_AGREEMENT_REJECTION_PATTERN,
						BROKERAGE_AGREEMENT_ACTIVATION_PATTERN, BROKERAGE_AGREEMENT_ACTIVATION_REFUSAL_PATTERN,
						BROKERAGE_AGREEMENT_HISTORY_PATTERN, BROKERAGE_AGREEMENT_SUSPENSION_PATTERN,
						BROKERAGE_AGREEMENT_SUSPENSION_RELEASE_PATTERN, BROKERAGE_AGREEMENT_TERMINATION_PATTERN,
						BROKERAGE_AGREEMENT_CANCELLATION_PATTERN, LISTINGS_PATTERN, LISTING_PATTERN,
						LISTINGS_BY_AGREEMENT_PATTERN, LISTING_SUBRESOURCES_PATTERN, PUBLIC_LISTING_PATTERN);
	}
}
