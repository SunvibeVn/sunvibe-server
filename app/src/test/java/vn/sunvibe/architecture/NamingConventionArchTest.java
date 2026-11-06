package vn.sunvibe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

	private static GivenClassesConjunction topLevelClassesIn(String packageIdentifier) {
		return classes().that().resideInAPackage(packageIdentifier)
				.and().areTopLevelClasses()
				.and().haveSimpleNameNotEndingWith("Impl");
	}
}
