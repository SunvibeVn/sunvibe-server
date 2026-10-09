rootProject.name = "sunvibe-server"

// Main Module
include("app")

// domain modules
include("modules:auth-service")
include("modules:listing-service")
include("modules:booking-service")
include("modules:property-service")
include("modules:review-service")
include("modules:customer-service")
include("modules:payment-service")
include("modules:notification-service")
include("modules:campaign-service")
include("modules:analyst-service")
include("modules:email-service")
include("modules:chat-service")
include("modules:common")

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}