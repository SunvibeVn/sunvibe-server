package vn.sunvibe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Layer direction inside listing-service (backend-architecture.md, layer-guide.md, domain-layer.md; listing-publish
 * E06-S1 spec E06S1-D14, LC-17), the same rules as {@link BrokerageLayerArchTest}: the domain depends on no other layer,
 * the port's records included, and on no framework (pure Java), the application only on the domain, and infrastructure
 * never on presentation.
 */
class ListingLayerArchTest {

	private static final String ROOT = "vn.sunvibe.listing.core";
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
		assertThat(classes.contain(ROOT + ".domain.model.listing.Listing")).isTrue();
		assertThat(classes.contain(ROOT + ".application.service.ListingCommandService")).isTrue();
		assertThat(classes.contain(ROOT + ".infrastructure.persistence.adapter.ListingRepositoryAdapter")).isTrue();
		assertThat(classes.contain(ROOT + ".presentation.controller.ListingController")).isTrue();
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
