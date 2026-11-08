package vn.sunvibe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;

import jakarta.persistence.AttributeConverter;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.property.core.domain.model.property.Property;
import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

/**
 * The names and packages of {@code .claude/rules/naming-layout.md}, on the compiled main classes of every feature core
 * (clean-up spec section 5). The name rules N-1 to N-8 read top-level classes only, never the {@code *Impl} that
 * MapStruct generates, and fail on a package that holds no class, so an empty package is a finding too. The layout rules
 * N-9 to N-11 read nested classes too, and N-10 and N-11 stay green once a package is gone.
 */
class NamingConventionArchTest {

	private static final String CORE = "vn.sunvibe.*.core";
	private static final String PRESENTATION_CONVERTER = CORE + ".presentation.converter..";
	private static final String PRESENTATION_RESOLVER = CORE + ".presentation.resolver..";
	private static final String PERSISTENCE_MAPPER = CORE + ".infrastructure.persistence.mapper..";
	private static final String PERSISTENCE_CONVERTER = CORE + ".infrastructure.persistence.converter..";
	private static final String PERSISTENCE_REPOSITORY = CORE + ".infrastructure.persistence.repository..";
	private static final String PERSISTENCE_ADAPTER = CORE + ".infrastructure.persistence.adapter..";

	/**
	 * N-14: the top-level presentation classes that may still use a repository, rules or an aggregate, each with its
	 * reason; their nested classes follow them. The list only shrinks.
	 */
	private static final Map<String, String> PRESENTATION_DOMAIN_ALLOWLIST = Map.of(
			"vn.sunvibe.survey.core.presentation.converter.ChecklistResponseConverter",
			"ChecklistView hands SurveyChecklist and SurveyAppointment to it (spec 4.2)",
			"vn.sunvibe.property.core.presentation.converter.PropertyCreateResponseConverter",
			"PropertyFindByIdUseCase returns the Property aggregate; a result record would change that use case and"
					+ " PropertyFindByIdService, outside presentation (W31 plan Task E1)");

	/** N-14: the aggregates the {@code *LayerArchTest} classes name. */
	private static final Set<String> AGGREGATES = Set.of(
			AppUser.class.getName(), OwnerLead.class.getName(), Property.class.getName(), Unit.class.getName(),
			SurveyAppointment.class.getName(), SurveyChecklist.class.getName(), SurveyMedia.class.getName());

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

	/**
	 * Clean-up spec N-9: a use case package holds interfaces and the records and exceptions they publish, never an
	 * implementation.
	 */
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

	/**
	 * Clean-up spec N-13: a converter maps what it is given. The caller comes from a resolver or
	 * {@code CurrentPrincipal}, so no converter fails a request on its own reading of the security context.
	 */
	@Test
	void presentationConverters_doNotReadTheSecurityContext() {
		noClasses().that().resideInAPackage(PRESENTATION_CONVERTER)
				.should().dependOnClassesThat().belongToAnyOf(SecurityContextHolder.class)
				.check(classes);
	}

	/**
	 * Clean-up spec N-14 (section 4.2): presentation may use domain enums, records and exceptions, but no repository
	 * interface, no class that holds rules ({@code *Policy}, {@code *Rules}) and no aggregate of a {@code *LayerArchTest}.
	 * ArchUnit counts method calls and field reads too, not only imports. What stays is in
	 * {@link #PRESENTATION_DOMAIN_ALLOWLIST}, which only shrinks, except that splitting a listed class replaces its entry
	 * with the new classes.
	 */
	@Test
	void presentation_usesNoRepositoryRulesOrAggregate() {
		noClasses().that().resideInAPackage(CORE + ".presentation..")
				.and(DescribedPredicate.not(allowlistedInPresentation()))
				.should().dependOnClassesThat(repositoryRulesOrAggregate())
				.check(classes);
	}

	/**
	 * Clean-up spec N-15 (amendment 2026-11-07): an exception sits in an exception package, or with the contract it
	 * belongs to, a use case or a port, because {@code ModuleBoundaryArchTest} lets an integration adapter read only
	 * those two packages (ADR-009).
	 */
	@Test
	void exceptions_resideInAnExceptionPackage() {
		topLevelClassesIn(CORE + "..").and().haveSimpleNameEndingWith("Exception")
				.should().resideInAnyPackage(CORE + "..exception..", CORE + ".application.usecase..",
						CORE + ".application.shared.external..")
				.check(classes);
	}

	/**
	 * Clean-up spec N-16 (amendment 2026-11-07): {@code domain/policy/} holds only concept folders. Written without
	 * {@code that()} on the package, so it stays green once the package holds no loose class.
	 */
	@Test
	void domainPolicy_hasNoLooseClass() {
		noClasses().that().areTopLevelClasses().should().resideInAPackage(CORE + ".domain.policy").check(classes);
	}

	private static DescribedPredicate<JavaClass> allowlistedInPresentation() {
		return DescribedPredicate.describe("on the N-14 allowlist",
				origin -> PRESENTATION_DOMAIN_ALLOWLIST.containsKey(topLevelName(origin)));
	}

	private static DescribedPredicate<JavaClass> repositoryRulesOrAggregate() {
		DescribedPredicate<JavaClass> inDomain = JavaClass.Predicates.resideInAPackage(CORE + ".domain..");
		return DescribedPredicate.describe("a domain repository interface, Policy or Rules class, or an aggregate",
				target -> {
					if (AGGREGATES.contains(target.getName())) {
						return true;
					}
					if (!inDomain.test(target) || target.isEnum() || target.isRecord()) {
						return false;
					}
					String name = target.getSimpleName();
					return target.isInterface() && name.endsWith("Repository")
							|| name.endsWith("Policy") || name.endsWith("Rules");
				});
	}

	private static String topLevelName(JavaClass javaClass) {
		JavaClass current = javaClass;
		while (current.getEnclosingClass().isPresent()) {
			current = current.getEnclosingClass().get();
		}
		return current.getName();
	}

	private static GivenClassesConjunction topLevelClassesIn(String packageIdentifier) {
		return classes().that().resideInAPackage(packageIdentifier)
				.and().areTopLevelClasses()
				.and().haveSimpleNameNotEndingWith("Impl");
	}
}
