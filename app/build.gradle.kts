plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    implementation(project(":modules:shared-service"))
    implementation(project(":modules:auth-service"))
    implementation(project(":modules:listing-service"))
    implementation(project(":modules:property-service"))
    implementation(project(":modules:notification-service"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // OpenAPI docs at /v3/api-docs and Swagger UI (disabled in the prod profile)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
    // PostgreSQL in Docker for the Flyway migration test (FlywayPostgresMigrationTest); needs Docker locally and in CI
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The contract export runs only through exportOpenApi, so a normal build never rewrites api/openapi.json.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("openapi-export")
    }
}

// ./gradlew exportOpenApi -> api/openapi.json (commit it). Uses the H2 test profile, so no database is needed.
tasks.register<Test>("exportOpenApi") {
    description = "Writes the OpenAPI contract to api/openapi.json."
    group = "documentation"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("openapi-export")
    }
    systemProperty("openapi.output", rootProject.file("api/openapi.json").absolutePath)
    outputs.upToDateWhen { false }
}