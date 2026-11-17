package vn.sunvibe.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import vn.sunvibe.auth.core.application.usecase.role.OwnerStandingUseCase;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginUseCase;
import vn.sunvibe.property.core.application.usecase.intake.PropertyOwnershipUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationUseCase;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingUseCase;

/**
 * The viewer-less published reads have no viewer of their own, so only an adapter in {@code vn.sunvibe.integration} may
 * call them, for an operation that has already checked its caller (ADR-009; ADR-012 decision 2; register SV-311 (3);
 * E05-S6b S6b-D8). A controller or service of the providing module would otherwise reach them with no check at all.
 */
class ViewerlessReadArchTest {

	private static final String INTEGRATION = "vn.sunvibe.integration..";

	private static final List<Class<?>> VIEWERLESS_READS = List.of(CompletedIntakeUseCase.class, UnitIntakeUseCase.class,
			PropertyOwnershipUseCase.class, SourceStandingUseCase.class, OwnerStandingUseCase.class,
			IntakeOriginUseCase.class, UnitLocationUseCase.class);

	private static JavaClasses classes;

	@BeforeAll
	static void importMainClasses() {
		// As ModuleBoundaryArchTest imports them: main classes of app and every module, no tests.
		classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("vn.sunvibe");
	}

	@Test
	void importedClasses_everyViewerlessRead_isImported() {
		VIEWERLESS_READS.forEach(type -> assertThat(classes.contain(type)).as(type.getName()).isTrue());
	}

	@Test
	void viewerlessReads_usedOutsideIntegration_isForbidden() {
		for (Class<?> type : VIEWERLESS_READS) {
			noClasses().that().resideOutsideOfPackage(INTEGRATION).and().areNotAssignableTo(type)
					.should().dependOnClassesThat().areAssignableTo(type)
					.because("ADR-009, ADR-012 decision 2 and SV-311 (3): a viewer-less published read is called only through"
							+ " an adapter in vn.sunvibe.integration, for an operation that has already checked its caller")
					.check(classes);
		}
	}
}
