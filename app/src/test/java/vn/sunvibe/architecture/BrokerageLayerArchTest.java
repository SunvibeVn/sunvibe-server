package vn.sunvibe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Layer direction inside brokerage-service (backend-architecture.md, layer-guide.md; brokerage-agreement E05-S3 spec
 * S3-D20, SV-311 (1)), the same rules as {@link AcquisitionLayerArchTest} and {@link PropertyLayerArchTest}: the domain
 * depends on no other layer and on no framework (pure Java), the application only on the domain, and infrastructure
 * never on presentation.
 */
class BrokerageLayerArchTest {

	private static final String ROOT = "vn.sunvibe.brokerage.core";
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
	void importedClasses_holdEveryLayer() {
		assertThat(classes.contain(ROOT + ".domain.model.agreement.BrokerageAgreement")).isTrue();
		assertThat(classes.contain(ROOT + ".application.service.AgreementCommandService")).isTrue();
		assertThat(classes.contain(ROOT + ".infrastructure.persistence.adapter.AgreementRepositoryAdapter")).isTrue();
		assertThat(classes.contain(ROOT + ".presentation.controller.BrokerageAgreementController")).isTrue();
	}

	@Test
	void domain_dependsOnNoOtherLayer() {
		noClasses().that().resideInAPackage(DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE, PRESENTATION)
				.check(classes);
	}

	@Test
	void domain_dependsOnNoFramework() {
		noClasses().that().resideInAPackage(DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..")
				.check(classes);
	}

	@Test
	void application_dependsNeitherOnInfrastructureNorOnPresentation() {
		noClasses().that().resideInAPackage(APPLICATION)
				.should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, PRESENTATION)
				.check(classes);
	}

	@Test
	void infrastructure_doesNotDependOnPresentation() {
		noClasses().that().resideInAPackage(INFRASTRUCTURE)
				.should().dependOnClassesThat().resideInAPackage(PRESENTATION)
				.check(classes);
	}
}
