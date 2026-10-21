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

import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.unit.Unit;

/**
 * Layer direction inside property-service (backend-architecture.md, layer-guide.md), the same rules as
 * {@link AcquisitionLayerArchTest}: the domain depends on no other layer and on no framework (pure Java), the
 * application only on the domain, and infrastructure never on presentation.
 */
class PropertyLayerArchTest {

	private static final String ROOT = "vn.sunvibe.property.core";
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
		assertThat(classes.contain(ROOT + ".domain.model.aggregate.Property")).isTrue();
		assertThat(classes.contain(ROOT + ".application.service.PropertyCreateService")).isTrue();
		assertThat(classes.contain(ROOT + ".infrastructure.persistence.adapter.PropertyRepositoryAdapter")).isTrue();
		assertThat(classes.contain(ROOT + ".presentation.controller.property.PropertyController")).isTrue();
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

	/**
	 * Rebuilding a stored property or unit and recording its saved version belong to persistence: elsewhere they would
	 * let code overwrite a row or skip the stale-version check (property-units BR-14). It sees property-service only; other
	 * modules cannot reach these classes ({@link ModuleBoundaryArchTest}), and app adapters only use published use cases.
	 */
	@Test
	void loadAndMarkPersisted_ofPropertyAndUnit_areCalledOnlyByPersistence() {
		noClasses().that().resideOutsideOfPackage(ROOT + ".infrastructure.persistence..")
				.should().callMethodWhere(target(name("load")).and(target(owner(type(Property.class)))))
				.orShould().callMethodWhere(target(name("markPersisted")).and(target(owner(type(Property.class)))))
				.orShould().callMethodWhere(target(name("load")).and(target(owner(type(Unit.class)))))
				.orShould().callMethodWhere(target(name("markPersisted")).and(target(owner(type(Unit.class)))))
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
