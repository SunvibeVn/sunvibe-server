package vn.sunvibe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;

import jakarta.persistence.AttributeConverter;

/**
 * The names and packages of {@code .claude/rules/naming-layout.md}, on the compiled main classes of every feature core
 * (clean-up spec section 5). Top-level classes only, and never the {@code *Impl} that MapStruct generates. A rule on a
 * package that holds no class fails, so an empty package is a finding too.
 */
class NamingConventionArchTest {

	private static final String CORE = "vn.sunvibe.*.core";
	private static final String PRESENTATION_CONVERTER = CORE + ".presentation.converter..";
	private static final String PRESENTATION_RESOLVER = CORE + ".presentation.resolver..";
	private static final String PERSISTENCE_MAPPER = CORE + ".infrastructure.persistence.mapper..";
	private static final String PERSISTENCE_CONVERTER = CORE + ".infrastructure.persistence.converter..";
	private static final String PERSISTENCE_REPOSITORY = CORE + ".infrastructure.persistence.repository..";
	private static final String PERSISTENCE_ADAPTER = CORE + ".infrastructure.persistence.adapter..";

	private static JavaClasses classes;

	@BeforeAll
	static void importMainClasses() {
		classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("vn.sunvibe");
	}

	/** Clean-up spec N-1. */
	@Test
	void presentationConverters_endWithConverter() {
		topLevelClassesIn(PRESENTATION_CONVERTER).should().haveSimpleNameEndingWith("Converter").check(classes);
	}

	/** Clean-up spec N-2. */
	@Test
	void persistenceMappers_endWithMapper() {
		topLevelClassesIn(PERSISTENCE_MAPPER).should().haveSimpleNameEndingWith("Mapper").check(classes);
	}

	/** Clean-up spec N-3. */
	@Test
	void persistenceConverters_endWithAttributeConverter() {
		topLevelClassesIn(PERSISTENCE_CONVERTER).should().haveSimpleNameEndingWith("AttributeConverter").check(classes);
	}

	/** Clean-up spec N-4. */
	@Test
	void attributeConverters_resideInPersistenceConverter() {
		classes().that().implement(AttributeConverter.class)
				.should().resideInAPackage(PERSISTENCE_CONVERTER)
				.check(classes);
	}

	/** Clean-up spec N-5. */
	@Test
	void presentationResolvers_endWithResolver() {
		topLevelClassesIn(PRESENTATION_RESOLVER).should().haveSimpleNameEndingWith("Resolver").check(classes);
	}

	/** Clean-up spec N-6. */
	@Test
	void jpaRepositories_endWithJpaRepository() {
		topLevelClassesIn(PERSISTENCE_REPOSITORY).should().haveSimpleNameEndingWith("JpaRepository").check(classes);
	}

	/** Clean-up spec N-7: package-private helpers may stay beside their adapter. */
	@Test
	void publicPersistenceAdapters_endWithAdapter() {
		topLevelClassesIn(PERSISTENCE_ADAPTER).and().arePublic().should().haveSimpleNameEndingWith("Adapter")
				.check(classes);
	}

	/** Clean-up spec N-8: each of the three words has one meaning, so it has one home. */
	@Test
	void mappingNames_resideInTheirPackage() {
		CompositeArchRule
				.of(topLevelClassesIn(CORE + "..").and().haveSimpleNameEndingWith("Mapper")
						.should().resideInAPackage(PERSISTENCE_MAPPER))
				.and(topLevelClassesIn(CORE + "..").and().haveSimpleNameEndingWith("Converter")
						.should().resideInAnyPackage(PRESENTATION_CONVERTER, PERSISTENCE_CONVERTER))
				.and(topLevelClassesIn(CORE + "..").and().haveSimpleNameEndingWith("Resolver")
						.should().resideInAPackage(PRESENTATION_RESOLVER))
				.check(classes);
	}

	/** Clean-up spec N-9: a use case package holds interfaces and the records they publish, never an implementation. */
	@Test
	void useCases_holdNoServiceOrComponent() {
		noClasses().that().resideInAPackage(CORE + ".application.usecase..")
				.should().beAnnotatedWith(Service.class)
				.orShould().beAnnotatedWith(Component.class)
				.check(classes);
	}

	/** Clean-up spec N-9. */
	@Test
	void noImplPackage() {
		noClasses().should().resideInAPackage(CORE + "..impl..").check(classes);
	}

	/** Clean-up spec N-10: ports live in {@code application/shared/external/}. */
	@Test
	void noPortsUnderUseCase() {
		noClasses().should().resideInAPackage(CORE + ".application.usecase.external..").check(classes);
	}

	/** Clean-up spec N-10: a scheduled trigger is not HTTP presentation. */
	@Test
	void noScheduledMethodInPresentation() {
		noMethods().that().areDeclaredInClassesThat().resideInAPackage(CORE + ".presentation..")
				.should().beAnnotatedWith(Scheduled.class)
				.check(classes);
	}

	/** Clean-up spec N-11: written without {@code that()}, so it stays green once the folders are gone. */
	@Test
	void domainModel_hasNoKindFolder() {
		noClasses().should().resideInAnyPackage(CORE + ".domain.model.aggregate", CORE + ".domain.model.vo",
				CORE + ".domain.model.enums").check(classes);
	}

	/** Clean-up spec N-12: package-private helpers such as the auth API docs may stay beside their controller. */
	@Test
	void publicControllers_endWithController() {
		topLevelClassesIn(CORE + ".presentation.controller..").and().arePublic()
				.should().haveSimpleNameEndingWith("Controller").check(classes);
	}

	private static GivenClassesConjunction topLevelClassesIn(String packageIdentifier) {
		return classes().that().resideInAPackage(packageIdentifier)
				.and().areTopLevelClasses()
				.and().haveSimpleNameNotEndingWith("Impl");
	}
}
