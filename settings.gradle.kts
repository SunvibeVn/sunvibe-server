rootProject.name = "sunvibe-server"

// Main Module
include("app")

// domain modules
include("modules:auth-service")
include("modules:listing-service")
include("modules:property-service")
include("modules:acquisition-service")
include("modules:survey-service")
include("modules:notification-service")
include("modules:shared-service")

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}