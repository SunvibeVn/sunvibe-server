// Apply Java plugin
apply(plugin = "java")

// Configure Java toolchain
extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure tests
tasks.withType<Test> {
    useJUnitPlatform()
}

// Add common dependencies
dependencies {
    // Lombok
    add("compileOnly", "org.projectlombok:lombok:1.18.30")
    add("annotationProcessor", "org.projectlombok:lombok:1.18.30")
    
    // Test dependencies
    add("testImplementation", platform("org.junit:junit-bom:5.10.0"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}
