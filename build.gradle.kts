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
