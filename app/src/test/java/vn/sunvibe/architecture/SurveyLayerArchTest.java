package vn.sunvibe.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;

/**
 * Layer direction inside survey-service (backend-architecture.md, layer-guide.md), the same rules as
 * {@link PropertyLayerArchTest}: the domain depends on no other layer and on no framework (pure Java), the application
 * only on the domain, infrastructure never on presentation, and only persistence rebuilds the aggregate.
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
		assertThat(classes.contain(ROOT + ".application.service.SurveyAppointmentCommandService")).isTrue();
		assertThat(classes.contain(ROOT + ".infrastructure.persistence.adapter.SurveyAppointmentRepositoryAdapter"))
				.isTrue();
		assertThat(classes.contain(ROOT + ".presentation.controller.LeadSurveyController")).isTrue();
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
						"tools.jackson..", "com.fasterxml..", "org.hibernate..")
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
						.or(target(owner(type(SurveyChecklist.class))))))
				.allowEmptyShould(true)
				.check(classes);
	}
}
