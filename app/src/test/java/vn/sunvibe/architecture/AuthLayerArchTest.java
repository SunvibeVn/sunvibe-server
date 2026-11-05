package vn.sunvibe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;

/**
 * Layer direction inside auth-service (auth-email-access S-6, BR-23): the identifier policy lives in plain domain
 * classes, so the domain depends on no framework and on no other layer of the module.
 */
class AuthLayerArchTest {

	private static final String ROOT = "vn.sunvibe.auth.core";
	private static final String DOMAIN = ROOT + ".domain..";

	private static JavaClasses classes;

	@BeforeAll
	static void importMainClasses() {
		classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages(ROOT);
	}

	@Test
	void importedClasses_policyClasses_arePresent() {
		assertThat(classes.contain(ROOT + ".domain.model.user.vo.Email")).isTrue();
		assertThat(classes.contain(ROOT + ".domain.model.user.vo.PhoneNumber")).isTrue();
	}

	@Test
	void archunit_domainHasNoFrameworkDependency() {
		noClasses().that().resideInAPackage(DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..")
				.check(classes);
	}

	/** auth-email-access I-4: an update needs the handle of a lock, so nothing but the adapter may make one. */
	@Test
	void lockedUser_isBuiltOnlyByTheAdapter() {
		noClasses().that().doNotHaveFullyQualifiedName(
				ROOT + ".infrastructure.persistence.adapter.user.UserRepositoryAdapter")
				.should().callConstructor(LockedUser.class, AppUser.class)
				.check(classes);
	}

	@Test
	void archunit_domainDependsOnNoOtherLayer() {
		noClasses().that().resideInAPackage(DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".application..",
						ROOT + ".infrastructure..", ROOT + ".presentation..")
				.check(classes);
	}
}
