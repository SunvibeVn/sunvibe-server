package vn.sunvibe.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

/**
 * Layer direction inside survey-service (backend-architecture.md, layer-guide.md), the same rules as
 * {@link PropertyLayerArchTest}: the domain depends on no other layer and on no framework (pure Java), the application
 * only on the domain, infrastructure never on presentation, and only persistence rebuilds the aggregate. Besides the
 * layers, every controller carries a class-level {@code @PreAuthorize}: the security chain only requires sign-in on
 * these paths, so a controller without one would let any signed-in role in (SV-274).
 */
class SurveyLayerArchTest {

	private static final String ROOT = "vn.sunvibe.survey.core";
	private static final String DOMAIN = ROOT + ".domain..";
	private static final String APPLICATION = ROOT + ".application..";
	private static final String INFRASTRUCTURE = ROOT + ".infrastructure..";
	private static final String PRESENTATION = ROOT + ".presentation..";

	private static JavaClasses classes;

	@BeforeAll
	static void importMainClasses() {
		classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages(ROOT);
	}

	@Test
	void importedClasses_everyLayer_isPresent() {
		assertThat(classes.contain(ROOT + ".domain.model.appointment.SurveyAppointment")).isTrue();
		assertThat(classes.contain(ROOT + ".application.service.appointment.SurveyAppointmentCommandService"))
				.isTrue();
		assertThat(classes.contain(
				ROOT + ".infrastructure.persistence.adapter.appointment.SurveyAppointmentRepositoryAdapter")).isTrue();
		assertThat(classes.contain(ROOT + ".presentation.controller.appointment.LeadSurveyController")).isTrue();
	}

	@Test
	void infrastructure_doesNotDependOnPresentation() {
		noClasses().that().resideInAPackage(INFRASTRUCTURE)
				.should().dependOnClassesThat().resideInAPackage(PRESENTATION)
				.check(classes);
	}

	@Test
	void domain_dependsOnNoOtherLayer() {
		noClasses().that().resideInAPackage(DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE, PRESENTATION)
				.check(classes);
	}

	@Test
	void domain_usesNoFramework() {
		noClasses().that().resideInAPackage(DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
						"tools.jackson..", "com.fasterxml..", "org.hibernate..", "software.amazon..")
				.check(classes);
	}

	@Test
	void application_dependsNeitherOnInfrastructureNorOnPresentation() {
		noClasses().that().resideInAPackage(APPLICATION)
				.should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, PRESENTATION)
				.check(classes);
	}

	@Test
	void aggregateLoad_isCalledOnlyByPersistence() {
		noClasses().that().resideOutsideOfPackage(ROOT + ".infrastructure.persistence..")
				.and().resideOutsideOfPackage(ROOT + ".domain..")
				.should().callMethodWhere(target(name("load")).and(target(owner(type(SurveyAppointment.class)))
						.or(target(owner(type(SurveyChecklist.class))))
						.or(target(owner(type(SurveyMedia.class))))))
				.allowEmptyShould(true)
				.check(classes);
	}

	/** SV-274: a method-level annotation narrows the class rule; it never replaces it. */
	@Test
	void controllers_carryAClassLevelPreAuthorize() {
		classes().that().resideInAPackage(PRESENTATION).and().areAnnotatedWith(RestController.class)
				.should().beAnnotatedWith(PreAuthorize.class)
				.check(classes);
	}
}
