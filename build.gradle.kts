plugins {
	java
	id("io.spring.dependency-management") version "1.1.7"
}

group = "vn.sunvibe"
version = "0.0.1-SNAPSHOT"

// Apply Spring Boot dependency management to all subprojects
subprojects {
	apply(plugin = "io.spring.dependency-management")
	
	the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
		imports {
			mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
		}
	}
	
	apply(from = rootProject.file("gradle/conventions/java-library-convention.gradle.kts"))
	
	group = "vn.sunvibe"
	version = "0.0.1-SNAPSHOT"
	
	dependencies {
		implementation("jakarta.persistence:jakarta.persistence-api")
		implementation("jakarta.annotation:jakarta.annotation-api")
		implementation("org.springframework:spring-web")
		implementation("org.springframework.boot:spring-boot-starter-web")
		implementation("org.springframework:spring-tx")
		implementation("org.springframework:spring-context")
		implementation("org.springframework.boot:spring-boot-starter-validation")
		implementation("org.springframework.boot:spring-boot-starter-security")
		implementation("org.springframework.boot:spring-boot-starter-data-jpa")
		implementation("io.github.kronst:snowflake-id-spring-boot-starter:1.0.0")
		implementation("com.fasterxml.jackson.core:jackson-annotations")
		implementation("com.github.f4b6a3:ulid-creator:5.2.3")
		implementation("org.springframework.boot:spring-boot-starter-mail")
		implementation("org.mapstruct:mapstruct:1.6.3")
		annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
		// OpenAPI annotations (@Operation, @ApiResponse) for controllers; same version as springdoc 3.1.1 uses
		implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.55")

		implementation("org.slf4j:slf4j-api")
	}
}

// Module boundaries: a module may depend only on :modules:shared-service (ADR-009), and shared-service
// depends on no module (ADR-003). Only :app may depend on any module (it wires the adapters).
// Checks declared project dependencies only; source-level import checks wait for ArchUnit (ADR-003/ADR-009).
val boundaryCheckedProjects = subprojects.filter { it.path != ":app" }
boundaryCheckedProjects.forEach { evaluationDependsOn(it.path) }

val checkModuleBoundaries = tasks.register("checkModuleBoundaries") {
	group = "verification"
	description = "Fails if a module depends on another module except :modules:shared-service (ADR-009)."
	val sharedPath = ":modules:shared-service"
	val violations = provider {
		boundaryCheckedProjects.flatMap { module ->
			module.configurations.flatMap { configuration ->
				configuration.dependencies.withType<ProjectDependency>()
					.filter { it.path != module.path && (it.path != sharedPath || module.path == sharedPath) }
					.map { "${module.path} -> ${it.path} (${configuration.name})" }
			}
		}.distinct()
	}
	doLast {
		val found = violations.get()
		if (found.isNotEmpty()) {
			throw GradleException(
				"Module boundary violation (ADR-009, ../docs/decisions/ADR-009-cross-module-ports.md):\n" +
					found.joinToString("\n") { "  $it" } +
					"\nDeclare a port in the consuming module and put the adapter in :app (vn.sunvibe.integration)."
			)
		}
	}
}

tasks.check {
	dependsOn(checkModuleBoundaries)
}
