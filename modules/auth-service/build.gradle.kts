// Auth service module
dependencies {
    implementation(project(":modules:shared-service"))
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    // One phone policy (auth-email-access spec A-13): plain Java, so the domain stays framework free
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.32")
    // The Java backfill of the identifier keys (auth-email-access spec 5.3); the app supplies Flyway at run time
    compileOnly("org.flywaydb:flyway-core")
}
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("com.h2database:h2")
}
