package vn.sunvibe.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

	/**
	 * listing-publish E06-S7 spec E06S7-D6, PB-12 (ADR-012 decision 1, the architecture test): the anonymous controller
	 * reaches only its use case and the view it answers, the response DTOs, its converter and shared or framework
	 * types, so no repository, aggregate or staff read can feed the public answer.
	 */
	@Test
	void publicController_dependsOnItsUseCaseDtosConverterAndSharedTypesOnly() {
		String controller = ROOT + ".presentation.controller.ListingPublicController";
		assertThat(classes.contain(controller)).isTrue();

		classes().that().haveFullyQualifiedName(controller)
				.should().onlyDependOnClassesThat(resideInAnyPackage("java..", "org.springframework..", "io.swagger..",
						"jakarta..", "lombok..", "vn.sunvibe.shared..", ROOT + ".presentation.dto.response..")
						.or(name(controller))
						.or(name(ROOT + ".application.usecase.ListingPublicQueryUseCase"))
						.or(name(ROOT + ".application.query.ListingPublicView"))
						.or(name(ROOT + ".presentation.converter.ListingPublicResponseConverter")))
				.check(classes);
	}
}
