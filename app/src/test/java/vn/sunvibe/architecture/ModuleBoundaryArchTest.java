package vn.sunvibe.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.Dependency.Predicates.dependency;
import static com.tngtech.archunit.core.domain.Dependency.Predicates.dependencyTarget;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.conditions.ArchConditions.onlyHaveDependenciesWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import vn.sunvibe.SunvibeServerApplication;

/**
 * Module boundaries of ADR-003/ADR-009 at the class dependency level (W7d). The Gradle task
 * {@code checkModuleBoundaries} checks declared project dependencies; this test checks what the compiled main classes
 * of every module actually use. See ../docs/decisions/ADR-009-cross-module-ports.md.
 */
class ModuleBoundaryArchTest {

	private static final String FEATURE_CORE = "vn.sunvibe.*.core..";
	private static final String SHARED = "vn.sunvibe.shared..";
	private static final String INTEGRATION = "vn.sunvibe.integration..";

	// The only feature classes an app class outside vn.sunvibe.integration may use (owner 2026-10-16).
	// A new entry needs an ADR-009 change first (../docs/decisions/ADR-009-cross-module-ports.md).
	private static final String SECURITY_CONFIG = "vn.sunvibe.config.SecurityConfig";
	private static final String ALLOWED_ROLE_NAME = "vn.sunvibe.auth.core.domain.model.user.enums.RoleName";
	private static final String ALLOWED_JWT_FILTER =
			"vn.sunvibe.auth.core.infrastructure.security.filter.JWTRequestFilter";

	private static JavaClasses classes;

	@BeforeAll
	static void importMainClasses() {
		// Main classes of app and every module, no tests. Gradle puts the modules on the app test classpath as jars,
		// so ImportOption.DoNotIncludeJars would drop them; importing only vn.sunvibe skips third-party jars instead.
		classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("vn.sunvibe");
	}

	@Test
	void importedClasses_mainClassesOfEveryModule_areImported() {
		assertThat(classes.contain("vn.sunvibe.integration.AuthUserDirectoryAdapter")).isTrue();
		assertThat(classes.contain(SECURITY_CONFIG)).isTrue();
		assertThat(classes.contain(ALLOWED_ROLE_NAME)).isTrue();
		assertThat(classes.contain(ALLOWED_JWT_FILTER)).isTrue();
		assertThat(classes.that(resideInAPackage("vn.sunvibe.auth.core.."))).isNotEmpty();
		assertThat(classes.that(resideInAPackage("vn.sunvibe.property.core.."))).isNotEmpty();
		assertThat(classes.that(resideInAPackage(SHARED))).isNotEmpty();
		assertThat(classes.contain("vn.sunvibe.architecture.ModuleBoundaryArchTest")).isFalse();
	}

	@Test
	void importedClasses_outsideTheCheckedPackages_noneFound() {
		// The rules below assume every class is shared, a feature core, or an app class in config/integration.
		assertThat(classes.that(not(resideInAnyPackage(SHARED, FEATURE_CORE, "vn.sunvibe.config..", INTEGRATION))
				.and(not(belongToAnyOf(SunvibeServerApplication.class)))))
				.extracting(JavaClass::getName)
				.isEmpty();
	}

	@Test
	void securityConfigAllowlist_everyEntry_isStillUsed() {
		// A dead allowlist entry would let a new dependency in without an ADR-009 change; remove it instead.
		assertThat(classes.get(SECURITY_CONFIG).getDirectDependenciesFromSelf())
				.extracting(dependency -> dependency.getTargetClass().getName())
				.contains(ALLOWED_ROLE_NAME, ALLOWED_JWT_FILTER);
	}

	@Test
	void featureModules_useEachOther_isForbidden() {
		slices().matching("vn.sunvibe.(*).core..")
				.should().notDependOnEachOther()
				.because("ADR-009: a feature module reaches another one only through a port and an adapter in app")
				.check(classes);
	}

	@Test
	void sharedService_usesOtherSunvibeClasses_isForbidden() {
		noClasses().that().resideInAPackage(SHARED)
				.should().dependOnClassesThat(resideInAPackage("vn.sunvibe..").and(not(resideInAPackage(SHARED))))
				.because("ADR-009/ADR-003: shared-service depends on no module")
				.check(classes);
	}

	@Test
	void featureAndSharedClasses_useAppClasses_isForbidden() {
		noClasses().that().resideInAnyPackage(SHARED, FEATURE_CORE)
				.should().dependOnClassesThat(resideInAnyPackage("vn.sunvibe.config..", INTEGRATION)
						.or(belongToAnyOf(SunvibeServerApplication.class)))
				.because("ADR-009/ADR-003: only app depends on modules, never the other way round")
				.check(classes);
	}

	@Test
	void integrationAdapters_useFeatureInternals_isForbidden() {
		noClasses().that().resideInAPackage(INTEGRATION)
				.should().dependOnClassesThat(resideInAPackage(FEATURE_CORE)
						.and(not(resideInAnyPackage(
								"vn.sunvibe.*.core.application.usecase..",
								"vn.sunvibe.*.core.application.shared.external..")))
						// auth-service keeps its use case implementations in usecase/<area>/impl (not published)
						.or(resideInAPackage("vn.sunvibe.*.core.application.usecase..impl..")))
				.because("ADR-009: an adapter uses only the provider's published use case and the consumer's port")
				.check(classes);
	}

	@Test
	void otherAppClasses_useFeatureClasses_onlyNamedAllowlistIsAllowed() {
		classes().that(resideInAPackage("vn.sunvibe..").and(resideOutsideOfPackages(SHARED, INTEGRATION, FEATURE_CORE)))
				.should(onlyHaveDependenciesWhere(not(dependencyTarget(resideInAPackage(FEATURE_CORE)))
						.or(dependency(SECURITY_CONFIG, ALLOWED_ROLE_NAME))
						.or(dependency(SECURITY_CONFIG, ALLOWED_JWT_FILTER))))
				.because("ADR-009: app classes outside vn.sunvibe.integration use no feature class, except the "
						+ "named SecurityConfig allowlist")
				.check(classes);
	}
}
