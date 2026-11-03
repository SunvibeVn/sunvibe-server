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
    implementation(project(":modules:acquisition-service"))
    implementation(project(":modules:survey-service"))
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
    // survey-media spec A-15: the object store contract on MinIO in the build (R2 itself: r2ContractTest)
    testImplementation("org.testcontainers:testcontainers-minio")
    testImplementation(platform("software.amazon.awssdk:bom:2.57.8"))
    testImplementation("software.amazon.awssdk:s3")
    testImplementation("software.amazon.awssdk:apache-client")
    // Source-level module boundary test (ModuleBoundaryArchTest, ADR-009); core library only, not managed by the Boot BOM
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // The API tests keep about twenty Spring contexts, each with its own PostgreSQL container, plus the checklist
    // limits test's 50-space answers: more than Gradle's default 512 MB test heap holds
    maxHeapSize = "1g"
}

// The contract export runs only through exportOpenApi, so a normal build never rewrites api/openapi.json. The tagged
// survey-media jobs (plan PD-7) run only through their own tasks: they need a production-size copy, R2 test
// credentials, a real clamd or the owner's phone corpus.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("openapi-export", "migration-timing", "r2-contract", "real-clamd", "phone-corpus")
    }
}

// ./gradlew :app:r2ContractTest: the object store contract against a Cloudflare R2 test bucket (survey-media spec
// A-15, plan PD-7); needs R2_TEST_ENDPOINT, R2_TEST_ACCESS_KEY_ID, R2_TEST_SECRET_ACCESS_KEY, R2_TEST_QUARANTINE_BUCKET
// and R2_TEST_MEDIA_BUCKET, and fails without them. Run before go-live.
tasks.register<Test>("r2ContractTest") {
    description = "Runs the media object store contract against a Cloudflare R2 test bucket (plan PD-7)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("r2-contract")
    }
    outputs.upToDateWhen { false }
}

// ./gradlew :app:realClamdTest: the ClamAV adapter against a real clamd container (survey-media spec A-15, plan PD-7);
// slow (the image loads its signatures), so it runs in its own CI job and before go-live.
tasks.register<Test>("realClamdTest") {
    description = "Runs the ClamAV adapter against a real clamd container (plan PD-7)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("real-clamd")
    }
    outputs.upToDateWhen { false }
}

// ./gradlew :app:phoneCorpusTest: the processing container on the owner's real phone photos (survey-media SM-1,
// plan PD-6, SV-042); needs SUNVIBE_PHONE_CORPUS_DIR (photos outside git) and fails without it.
tasks.register<Test>("phoneCorpusTest") {
    description = "Runs the processing container on the phone corpus of SUNVIBE_PHONE_CORPUS_DIR (plan PD-6)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("phone-corpus")
    }
    outputs.upToDateWhen { false }
}

// ./gradlew :app:migrationTimingTest: times the survey-media index build and VALIDATE on a production-size copy
// (survey-media plan PD-8). Needs Docker and a few GB of disk; prints the timings and the locks held.
tasks.register<Test>("migrationTimingTest") {
    description = "Times the survey-media migrations 4 and 5 on a synthetic three-year copy (plan PD-8)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("migration-timing")
    }
    testLogging {
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }
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