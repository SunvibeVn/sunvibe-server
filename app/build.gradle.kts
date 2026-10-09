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
    implementation(project(":modules:common"))
    implementation(project(":modules:auth-service"))
    implementation(project(":modules:listing-service"))
    implementation(project(":modules:booking-service"))
    implementation(project(":modules:property-service"))
    implementation(project(":modules:review-service"))
    implementation(project(":modules:customer-service"))
    implementation(project(":modules:payment-service"))
    implementation(project(":modules:notification-service"))
    implementation(project(":modules:campaign-service"))
    implementation(project(":modules:analyst-service"))
    implementation(project(":modules:email-service"))
    implementation(project(":modules:chat-service"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}